package com.middleware.logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Reusable Logging Middleware Service
 * 
 * This service provides a centralized logging mechanism that captures the entire
 * lifecycle of events in the application (success, warnings, info, debug, errors).
 * 
 * Each log call makes an HTTP request to the Test Server's logging endpoint.
 * 
 * Usage:
 *   LoggerService logger = new LoggerService("http://20.207.122.201/evaluation-service");
 *   logger.setAccessToken("your_bearer_token");
 *   logger.log("backend", "info", "controller", "User registration successful");
 */
public class LoggerService {

    private final String testServerUrl;
    private String accessToken;
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    // Valid stack types
    private static final String[] VALID_STACKS = {"backend", "frontend"};

    // Valid log levels
    private static final String[] VALID_LEVELS = {"debug", "info", "warn", "error", "fatal"};

    // Valid packages for backend
    private static final String[] VALID_BACKEND_PACKAGES = {
            "cache", "controller", "cron_job", "db", "domain", "handler", 
            "repository", "route", "service"
    };

    // Valid packages for frontend
    private static final String[] VALID_FRONTEND_PACKAGES = {
            "api", "component", "hook", "page", "state", "style"
    };

    // Valid packages for both
    private static final String[] VALID_BOTH_PACKAGES = {
            "auth", "config", "middleware", "utils"
    };

    /**
     * Constructor
     * @param testServerUrl Base URL of the test server (e.g., "http://20.207.122.201/evaluation-service")
     */
    public LoggerService(String testServerUrl) {
        this.testServerUrl = testServerUrl;
    }

    /**
     * Set the access token for authentication with the test server
     * @param token Bearer token obtained from authentication endpoint
     */
    public void setAccessToken(String token) {
        this.accessToken = token;
    }

    /**
     * Main logging function - sends a log to the test server
     * 
     * @param stack "backend" or "frontend"
     * @param level "debug", "info", "warn", "error", "fatal"
     * @param packageName Package where the log originates from
     * @param message Descriptive log message
     * @return logID if successful, null otherwise
     */
    public String log(String stack, String level, String packageName, String message) {
        // Validate input parameters
        if (!isValidStack(stack)) {
            System.err.println("ERROR: Invalid stack: '" + stack + "'. Must be 'backend' or 'frontend'");
            return null;
        }

        if (!isValidLevel(level)) {
            System.err.println("ERROR: Invalid level: '" + level + "'. Must be one of: debug, info, warn, error, fatal");
            return null;
        }

        if (!isValidPackage(stack, packageName)) {
            System.err.println("ERROR: Invalid package: '" + packageName + "' for stack: '" + stack + "'");
            return null;
        }

        if (accessToken == null || accessToken.trim().isEmpty()) {
            System.err.println("ERROR: Access token not set. Call setAccessToken() first.");
            return null;
        }

        if (message == null || message.trim().isEmpty()) {
            System.err.println("WARNING: Log message is empty");
            message = "";
        }

        try {
            // Build request body as JSON
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("stack", stack);
            requestBody.put("level", level);
            requestBody.put("package", packageName);
            requestBody.put("message", message);

            String jsonBody = mapToJson(requestBody);

            // Construct the logs endpoint URL
            String url = testServerUrl + "/logs";

            // Build HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            // Send request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Check response status
            if (response.statusCode() == 200) {
                // Extract logID from response
                String logID = extractJsonValue(response.body(), "logID");
                System.out.println("LOG SENT: [" + level.toUpperCase() + "] " + packageName + " - " + message);
                return logID;
            } else {
                System.err.println("ERROR: Failed to create log. HTTP Status: " + response.statusCode());
                System.err.println("Response: " + response.body());
                return null;
            }

        } catch (InterruptedException e) {
            System.err.println("ERROR: Request interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            System.err.println("ERROR: Failed to send log: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Validate if stack is one of the allowed values
     */
    private boolean isValidStack(String stack) {
        if (stack == null) return false;
        for (String valid : VALID_STACKS) {
            if (valid.equals(stack)) return true;
        }
        return false;
    }

    /**
     * Validate if level is one of the allowed values
     */
    private boolean isValidLevel(String level) {
        if (level == null) return false;
        for (String valid : VALID_LEVELS) {
            if (valid.equals(level)) return true;
        }
        return false;
    }

    /**
     * Validate if package is allowed for the given stack
     */
    private boolean isValidPackage(String stack, String packageName) {
        if (packageName == null) return false;

        // Check common packages for both stacks
        for (String valid : VALID_BOTH_PACKAGES) {
            if (valid.equals(packageName)) return true;
        }

        // Check stack-specific packages
        if ("backend".equals(stack)) {
            for (String valid : VALID_BACKEND_PACKAGES) {
                if (valid.equals(packageName)) return true;
            }
        } else if ("frontend".equals(stack)) {
            for (String valid : VALID_FRONTEND_PACKAGES) {
                if (valid.equals(packageName)) return true;
            }
        }

        return false;
    }

    /**
     * Convert a Map to JSON string (no external libraries)
     */
    private String mapToJson(Map<String, String> map) {
        StringBuilder json = new StringBuilder("{");
        int count = 0;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (count > 0) json.append(",");
            json.append("\"").append(escapeJson(entry.getKey())).append("\":");
            json.append("\"").append(escapeJson(entry.getValue())).append("\"");
            count++;
        }
        json.append("}");
        return json.toString();
    }

    /**
     * Escape special characters in JSON strings
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * Extract a value from a JSON string (no external libraries)
     */
    private String extractJsonValue(String json, String key) {
        if (json == null || key == null) return null;

        String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            // Try without quotes (for non-string values)
            searchKey = "\"" + key + "\":";
            startIndex = json.indexOf(searchKey);
            if (startIndex == -1) return null;
            startIndex += searchKey.length();

            // Find the value end (comma or closing brace)
            int endIndex = json.indexOf(",", startIndex);
            if (endIndex == -1) endIndex = json.indexOf("}", startIndex);
            if (endIndex == -1) return null;

            return json.substring(startIndex, endIndex).trim();
        }

        startIndex += searchKey.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) return null;

        return json.substring(startIndex, endIndex);
    }
}
