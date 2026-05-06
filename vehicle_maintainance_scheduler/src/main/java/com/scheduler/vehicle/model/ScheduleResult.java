package com.scheduler.vehicle.model;

import java.util.List;

/* 
 - Result of the vehicle scheduling algorithm
 - Contains selected tasks, total duration, total impact, and remaining hours
 */
public class ScheduleResult {
    private Integer depotId;
    private List<String> selectedTaskIds;
    private Integer totalDuration;
    private Integer totalImpact;
    private Integer availableMechanicHours;
    private Integer remainingMechanicHours;

    public ScheduleResult() {}

    public ScheduleResult(Integer depotId, List<String> selectedTaskIds, 
                         Integer totalDuration, Integer totalImpact, 
                         Integer availableMechanicHours) {
        this.depotId = depotId;
        this.selectedTaskIds = selectedTaskIds;
        this.totalDuration = totalDuration;
        this.totalImpact = totalImpact;
        this.availableMechanicHours = availableMechanicHours;
        this.remainingMechanicHours = availableMechanicHours - totalDuration;
    }

    public Integer getDepotId() {
        return depotId;
    }

    public void setDepotId(Integer depotId) {
        this.depotId = depotId;
    }

    public List<String> getSelectedTaskIds() {
        return selectedTaskIds;
    }

    public void setSelectedTaskIds(List<String> selectedTaskIds) {
        this.selectedTaskIds = selectedTaskIds;
    }

    public Integer getTotalDuration() {
        return totalDuration;
    }

    public void setTotalDuration(Integer totalDuration) {
        this.totalDuration = totalDuration;
    }

    public Integer getTotalImpact() {
        return totalImpact;
    }

    public void setTotalImpact(Integer totalImpact) {
        this.totalImpact = totalImpact;
    }

    public Integer getAvailableMechanicHours() {
        return availableMechanicHours;
    }

    public void setAvailableMechanicHours(Integer availableMechanicHours) {
        this.availableMechanicHours = availableMechanicHours;
    }

    public Integer getRemainingMechanicHours() {
        return remainingMechanicHours;
    }

    public void setRemainingMechanicHours(Integer remainingMechanicHours) {
        this.remainingMechanicHours = remainingMechanicHours;
    }

    @Override
    public String toString() {
        return "ScheduleResult{" +
                "depotId=" + depotId +
                ", selectedTaskIds=" + selectedTaskIds +
                ", totalDuration=" + totalDuration +
                ", totalImpact=" + totalImpact +
                ", availableMechanicHours=" + availableMechanicHours +
                ", remainingMechanicHours=" + remainingMechanicHours +
                '}';
    }
}
