package com.scheduler.vehicle.model;

/*
 - Represents a vehicle with maintenance tasks
 - Each vehicle has a unique TaskID, duration (in hours), and impact score
 */
public class Vehicle {
    private String taskId;
    private Integer duration;
    private Integer impact;

    public Vehicle() {}

    public Vehicle(String taskId, Integer duration, Integer impact) {
        this.taskId = taskId;
        this.duration = duration;
        this.impact = impact;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Integer getImpact() {
        return impact;
    }

    public void setImpact(Integer impact) {
        this.impact = impact;
    }

    @Override
    public String toString() {
        return "Vehicle{" +
                "taskId='" + taskId + '\'' +
                ", duration=" + duration +
                ", impact=" + impact +
                '}';
    }
}
