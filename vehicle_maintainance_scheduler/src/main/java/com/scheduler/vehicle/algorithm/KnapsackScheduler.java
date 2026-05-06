package com.scheduler.vehicle.algorithm;

import com.scheduler.vehicle.model.Vehicle;
import java.util.*;

/**
 0/1 Knapsack Algorithm using DP
 - Problem: Given a set of vehicles (tasks) with duration and impact,
 - select a subset such that:
 total duration does not exceed available mechanic-hours (capacity)
 - Total impact is maximized
 */
public class KnapsackScheduler {

    /*
     * @param vehicles List of vehicles with duration and impact
     * @param capacity Available mechanic-hours
     * @return List of selected TaskIDs
     */
    public static List<String> solveSchedule(List<Vehicle> vehicles, Integer capacity) {
        if (vehicles == null || vehicles.isEmpty() || capacity == null || capacity <= 0) {
            return new ArrayList<>();
        }

        int n = vehicles.size();
        
        // DP table: dp[i][w] = max impact using first i items with capacity w
        int[][] dp = new int[n + 1][capacity + 1];

        // Fill the DP table
        for (int i = 1; i <= n; i++) {
            Vehicle vehicle = vehicles.get(i - 1);
            int duration = vehicle.getDuration();
            int impact = vehicle.getImpact();

            for (int w = 0; w <= capacity; w++) {
                // Option 1: Don't take this vehicle
                dp[i][w] = dp[i - 1][w];

                // Option 2: Take this vehicle (if it fits)
                if (duration <= w) {
                    int valueWithVehicle = dp[i - 1][w - duration] + impact;
                    dp[i][w] = Math.max(dp[i][w], valueWithVehicle);
                }
            }
        }

        // Backtrack to find which vehicles were selected
        List<String> selectedTaskIds = new ArrayList<>();
        int w = capacity;
        for (int i = n; i > 0 && w > 0; i--) {
            // If this vehicle was included in the optimal solution
            if (dp[i][w] != dp[i - 1][w]) {
                Vehicle vehicle = vehicles.get(i - 1);
                selectedTaskIds.add(vehicle.getTaskId());
                w -= vehicle.getDuration();
            }
        }

        // Reverse to maintain order
        Collections.reverse(selectedTaskIds);
        return selectedTaskIds;
    }

    /**
     * Calculate total duration from selected task IDs
     */
    public static Integer calculateTotalDuration(List<Vehicle> vehicles, List<String> selectedTaskIds) {
        if (selectedTaskIds == null || selectedTaskIds.isEmpty()) {
            return 0;
        }

        int total = 0;
        Set<String> selectedSet = new HashSet<>(selectedTaskIds);

        for (Vehicle vehicle : vehicles) {
            if (selectedSet.contains(vehicle.getTaskId())) {
                total += vehicle.getDuration();
            }
        }

        return total;
    }

    /**
     * Calculate total impact from selected task IDs
     */
    public static Integer calculateTotalImpact(List<Vehicle> vehicles, List<String> selectedTaskIds) {
        if (selectedTaskIds == null || selectedTaskIds.isEmpty()) {
            return 0;
        }

        int total = 0;
        Set<String> selectedSet = new HashSet<>(selectedTaskIds);

        for (Vehicle vehicle : vehicles) {
            if (selectedSet.contains(vehicle.getTaskId())) {
                total += vehicle.getImpact();
            }
        }

        return total;
    }
}
