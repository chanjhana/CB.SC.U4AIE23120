# Notification System Design

This document contains my stage-wise design for a campus notification platform. I am treating it like a real product, not just a theory question, so I am focusing on simple structure, scale, and predictable behavior.

## Stage 1

### Goal
Design the REST contract and overall structure for notifications shown to logged-in students.

### Core idea
I would keep the design around one notification service that receives events from other campus modules and stores each notification properly. The frontend should not guess the format of a notification. It should just call a stable API and render the response.

### High-level architecture

Placement / Event / Result Services -> Notification API -> Notification Store
Notification API -> Real-time Gateway -> Web / Mobile Clients
Notification API -> Async Queue -> Email Service

### REST API design

I would keep the endpoints simple and predictable:

#### 1. List notifications for the logged-in student
`GET /api/v1/notifications`

Query params:
- `page` default `0`
- `size` default `20`
- `type` optional: `placement`, `event`, `result`
- `isRead` optional: `true` or `false`

Example response:
```json
{
	"data": [
		{
			"id": "ntf_101",
			"studentId": "stu_10021",
			"type": "placement",
			"title": "New placement drive announced",
			"message": "TCS is visiting campus next week.",
			"referenceId": "plc_7781",
			"isRead": false,
			"priority": 90,
			"createdAt": "2026-05-06T09:12:11Z"
		}
	],
	"page": 0,
	"size": 20,
	"totalElements": 124,
	"totalPages": 7
}
```

#### 2. Mark a notification as read
`PATCH /api/v1/notifications/{notificationId}/read`

Example response:
```json
{
	"message": "Notification marked as read"
}
```

#### 3. Unread count for badge UI
`GET /api/v1/notifications/unread-count`

Example response:
```json
{
	"unreadCount": 8
}
```

#### 4. Create notification internally
`POST /api/v1/internal/notifications`

This is not for the student UI. It is for trusted campus systems to publish notifications into the service.

Request body:
```json
{
	"studentIds": ["stu_10021", "stu_10022"],
	"type": "placement",
	"title": "Shortlist published",
	"message": "Your interview shortlist is now available.",
	"referenceId": "plc_7781",
	"priority": 80
}
```

### Request headers

For user-facing APIs:
- `Authorization: Bearer <jwt>`
- `Content-Type: application/json`

For internal event publishers:
- service-to-service auth header or signed token

### Notification payload shape

I would keep each notification small and easy to understand:

```json
{
	"id": "string",
	"studentId": "string",
	"type": "placement | event | result",
	"title": "string",
	"message": "string",
	"referenceId": "string",
	"channel": "in_app | email | both",
	"priority": 1,
	"isRead": false,
	"createdAt": "timestamp"
}
```

### Real-time delivery

For real-time notifications, I would use Server-Sent Events or WebSockets.

My preference here is:
- **WebSocket** if the UI needs live two-way updates and instant badge counts
- **SSE** if the UI only needs server-to-client push and the implementation should stay simpler

For this campus use case, SSE is usually enough for browser clients, but WebSocket is fine if the same channel later supports chat-like behavior.

### My design choice

I would make the notification service event-driven:
- source systems publish events
- the notification service persists them
- the realtime layer pushes them to active users
- email is handled asynchronously

That keeps the UI fast and avoids coupling every campus module directly to the frontend.

## Stage 2

### Recommended database

I would use **PostgreSQL** as the main database.

### Why PostgreSQL
- notifications are relational and naturally tied to students
- filtering by read/unread, type, time, and student is common
- pagination and indexing are straightforward
- JSONB can be used if some notification payloads vary a lot
- it is reliable for audit-style data and reporting

### Suggested schema

#### students
```sql
CREATE TABLE students (
	id            BIGSERIAL PRIMARY KEY,
	student_code  VARCHAR(50) NOT NULL UNIQUE,
	full_name     VARCHAR(150) NOT NULL,
	email         VARCHAR(150) NOT NULL UNIQUE,
	created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
```

#### notifications
```sql
CREATE TABLE notifications (
	id             BIGSERIAL PRIMARY KEY,
	student_id     BIGINT NOT NULL REFERENCES students(id),
	type           VARCHAR(20) NOT NULL,
	title          VARCHAR(200) NOT NULL,
	message        TEXT NOT NULL,
	reference_id   VARCHAR(100),
	channel        VARCHAR(20) NOT NULL DEFAULT 'in_app',
	priority       INT NOT NULL DEFAULT 1,
	is_read        BOOLEAN NOT NULL DEFAULT FALSE,
	created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
	updated_at     TIMESTAMP NOT NULL DEFAULT NOW()
);
```

#### notification_delivery_log
```sql
CREATE TABLE notification_delivery_log (
	id               BIGSERIAL PRIMARY KEY,
	notification_id  BIGINT NOT NULL REFERENCES notifications(id),
	delivery_channel  VARCHAR(20) NOT NULL,
	status            VARCHAR(20) NOT NULL,
	provider_message  TEXT,
	created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### Indexes I would add

```sql
CREATE INDEX idx_notifications_student_read_created
ON notifications (student_id, is_read, created_at DESC);

CREATE INDEX idx_notifications_student_type_created
ON notifications (student_id, type, created_at DESC);

CREATE INDEX idx_notifications_student_priority_created
ON notifications (student_id, priority DESC, created_at DESC);
```

### Problems as volume increases

If notification volume grows, the first issues will usually be:
- unread lookup gets slower
- pagination over a large table becomes expensive
- email and push delivery can block the main request path
- reporting queries can compete with live reads

### How I would solve them

- keep the read path separate from the write path
- use pagination with indexed sort columns
- archive very old notifications into a cold table or partition
- use a queue for email and push delivery
- cache unread counts per student if the badge API becomes hot

### About SQL vs NoSQL

I would still choose PostgreSQL first. NoSQL is not needed unless the notification structure changes a lot or the write load becomes very large. For this assignment, a relational database is the better choice.

## Stage 3

### Query under review

```sql
SELECT * FROM notifications
WHERE studentID = 1042 AND isRead = false
ORDER BY createdAt DESC;
```

### Is it accurate?

Logically yes, but the column names should follow the schema style. I would write it as:

```sql
SELECT id, student_id, type, title, message, reference_id, priority, is_read, created_at
FROM notifications
WHERE student_id = 1042 AND is_read = FALSE
ORDER BY created_at DESC;
```

### Why is it slow?

It is slow because the database must either scan a large portion of the table or sort many rows after filtering.

The main reasons are:
- `student_id` and `is_read` are filtered together, but there may be no useful composite index
- `ORDER BY created_at DESC` needs an index to avoid sorting large result sets
- `SELECT *` fetches more columns than needed

### What I would change

I would add a composite index that matches the filter and sort pattern:

```sql
CREATE INDEX idx_notifications_student_read_created
ON notifications (student_id, is_read, created_at DESC);
```

This is the key improvement, not indexing every column.

### Would indexing every column help?

No. That advice is not effective.

Reasons:
- every index has a write cost
- too many indexes slow inserts and updates
- storage grows quickly
- the planner still may not use many of them

Indexes should be chosen based on real query patterns, not added blindly.

### Likely computation cost

Without the index, the query can degrade toward a large scan plus sort, which gets expensive as the table grows.

With the index, the database can narrow to the student’s rows, filter unread messages, and return them already in the right order.

### Placement notifications query

To find students who got a placement notification in the last 7 days:

```sql
SELECT DISTINCT s.id, s.student_code, s.full_name, s.email
FROM students s
JOIN notifications n ON n.student_id = s.id
WHERE n.type = 'placement'
	AND n.created_at >= NOW() - INTERVAL '7 days';
```

If I only need the student list, I would avoid `SELECT *` and return just the fields required by the UI or report.

### Helpful index for this query

```sql
CREATE INDEX idx_notifications_type_created_student
ON notifications (type, created_at DESC, student_id);
```

## Stage 4

### Problem

Notifications are fetched on every page load. That makes the database do repeated work for data that does not change every second.

### Best solution

I would combine **caching**, **pagination**, and **incremental updates**.

### What I would do

#### 1. Cache unread counts and the first page

Store the latest unread count and the most recent notifications in Redis.

This helps because:
- the badge count is a hot read
- the first page is usually the most viewed
- repeat page loads no longer hit the database every time

#### 2. Keep pagination

Do not send all notifications at once.

Use page-based or cursor-based pagination so the UI asks for only a small slice of data.

#### 3. Push new notifications to the client

When a new notification arrives, the server should push it to active users through SSE or WebSocket so the UI updates without a full refresh.

#### 4. Use an event queue for write-heavy flows

The write path should not wait on email or push delivery.

### Tradeoffs

#### Caching
Pros:
- very fast reads
- reduces database pressure

Cons:
- cache invalidation is needed
- slightly more system complexity

#### Pagination only
Pros:
- simple to implement
- reduces payload size

Cons:
- still hits the database on every refresh

#### Push-based updates
Pros:
- best user experience
- no repeated polling

Cons:
- more infrastructure and connection management

### My recommendation

For this system, I would use:
- Redis for unread counts and first-page cache
- pagination for the full history list
- SSE or WebSocket for new notification push

That gives a good balance between performance and maintainability.

## Stage 5

### Problems in the original pseudocode

The given implementation is not reliable enough for 50,000 students because:
- it sends emails one by one in a tight loop
- if one call fails midway, the rest of the batch is affected
- it mixes database work and network work in the same sequential flow
- it will be slow and hard to retry cleanly

### Revised approach

I would split the work into three steps:

1. create the notification records in the database
2. publish delivery jobs to a queue
3. let worker jobs send email and push messages asynchronously

### Why this is better

This design is reliable because each notification is saved first. After that, delivery happens independently.

If the email service fails for some users, only those delivery jobs need retries.

### Revised pseudocode

```text
function notify_all(student_ids: array, message: string):
    notification_ids = []

    begin transaction
    for student_id in student_ids:
        notification_id = save_to_db(student_id, message, status='pending')
        notification_ids.append(notification_id)
    commit transaction

    for notification_id in notification_ids:
        enqueue_job(notification_id)

    return success


worker process delivery_job(notification_id):
    notification = load_notification(notification_id)

    send_email(notification.student_id, notification.message)
    push_to_app(notification.student_id, notification.message)

    update_delivery_status(notification_id, 'delivered')
```

### Should DB save and email sending happen together?

No, not in the same synchronous path.

Reason:
- database writes are fast and local
- email calls are external and slow
- if email times out, you do not want to roll back already valid notification data

The right pattern is:
- save first
- send later
- retry failed deliveries independently

### Handling failures

If `send_email` fails for 200 students midway:
- the saved notifications remain in the database
- failed deliveries are marked for retry
- a background worker can retry with exponential backoff
- the UI can still show the notifications as pending or undelivered

### Reliability pattern I would use

I would use the **outbox pattern**:
- write the notification and an outbox event in the same DB transaction
- a background publisher reads the outbox table and dispatches jobs
- workers send email and push notifications

That keeps the system resilient and avoids partial success confusion.

## Stage 6

### Goal

Build a Priority Inbox that always shows the top 10 most important unread notifications first.

### Ranking rules

I would score each notification using:
- **weight**: placement > result > event
- **recency**: newer notifications rank higher
- **read state**: unread notifications always come first

### Suggested priority formula

```text
priority_score = type_weight + recency_bonus + unread_bonus
```

Example weights:
- placement = 300
- result = 200
- event = 100

Example recency bonus:
- created within 1 day = +50
- within 3 days = +30
- within 7 days = +10

Unread bonus:
- unread = +100
- read = +0

### How I would implement it

I would keep the scoring in application code and fetch the top 10 from the database with one query.

If using PostgreSQL only:

```sql
SELECT id, student_id, type, title, message, is_read, created_at
FROM notifications
WHERE student_id = :studentId
ORDER BY
	CASE type
		WHEN 'placement' THEN 300
		WHEN 'result' THEN 200
		WHEN 'event' THEN 100
		ELSE 0
	END +
	CASE
		WHEN created_at >= NOW() - INTERVAL '1 day' THEN 50
		WHEN created_at >= NOW() - INTERVAL '3 days' THEN 30
		WHEN created_at >= NOW() - INTERVAL '7 days' THEN 10
		ELSE 0
	END +
	CASE WHEN is_read = FALSE THEN 100 ELSE 0 END DESC,
	created_at DESC
LIMIT 10;
```

### If I were building the code

In Java/Spring Boot, I would:
- fetch unread notifications for the student
- calculate a score per notification
- sort in memory if the result set is small
- return only the first 10

For large datasets, I would prefer sorting in the database or storing a precomputed score column.

### How to keep it efficient

To maintain top-10 efficiency:
- store a `priority_score` column
- update the score when notification type or read state changes
- add an index on `(student_id, is_read, priority_score DESC, created_at DESC)`
- use Redis sorted sets if the inbox becomes extremely hot

### Why this works

This approach is simple, explainable, and easy to maintain.

It also matches the product requirement because the most important unread items appear first, while old low-value messages naturally fall down the list.

## Final Recommendation

If I were implementing this for real, my stack would be:
- **Backend:** Spring Boot
- **Database:** PostgreSQL
- **Cache:** Redis
- **Async jobs:** RabbitMQ or Kafka
- **Real-time:** SSE or WebSocket
- **Delivery reliability:** outbox pattern + retry workers

That gives a design that is practical for the assignment and still scales well beyond the current load.
