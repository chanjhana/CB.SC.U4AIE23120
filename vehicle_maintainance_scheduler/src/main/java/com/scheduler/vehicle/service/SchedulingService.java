package com.scheduler.vehicle.service;

import com.scheduler.vehicle.algorithm.KnapsackScheduler;
import com.scheduler.vehicle.model.Depot;
import com.scheduler.vehicle.model.ScheduleResult;
import com.scheduler.vehicle.model.Vehicle;
import com.middleware.logger.LoggerService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Service
public class SchedulingService {

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private final LoggerService loggerService;
    private final String testServerUrl;
    private String accessToken;
    private String lastDepotsFetchError;
    private String lastVehiclesFetchError;

    public SchedulingService() {
        this.testServerUrl = "http://20.207.122.201/evaluation-service";
        this.loggerService = new LoggerService(testServerUrl);
    }

    //Set the bearer token for API authentication

    public void setAccessToken(String token) {
        this.accessToken = normalizeToken(token);
        this.loggerService.setAccessToken(this.accessToken);
    }

    private String normalizeToken(String token) {
        if (token == null) {
            return null;
        }

        String trimmed = token.trim();
        if (trimmed.startsWith("Bearer ")) {
            return trimmed.substring(7).trim();
        }

        return trimmed;
    }

    /**
     * Fetch all depots from the test server (protected route)
     */
    public List<Depot> fetchDepots() {
        List<Depot> depots = new ArrayList<>();
        lastDepotsFetchError = null;

        if (accessToken == null || accessToken.isBlank()) {
            lastDepotsFetchError = "Authorization token not set";
            loggerService.log("backend", "error", "service", "Cannot fetch depots: authorization token not set");
            return depots;
        }
        
        try {
            loggerService.log("backend", "info", "service", "Fetching depots from test server");

            String url = testServerUrl + "/depots";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                loggerService.log("backend", "info", "service", "Successfully fetched depots from test server");
                depots = parseDepots(response.body());
                loggerService.log("backend", "info", "service", "Parsed " + depots.size() + " depots");
            } else {
                lastDepotsFetchError = "HTTP " + response.statusCode() + ": " + response.body();
                loggerService.log("backend", "error", "service", "Failed to fetch depots. " + lastDepotsFetchError);
            }

        } catch (Exception e) {
            lastDepotsFetchError = "Exception: " + e.getMessage();
            loggerService.log("backend", "fatal", "service", "Exception while fetching depots: " + e.getMessage());
        }

        return depots;
    }

    /**
     * Fetch all vehicles from the test server (protected route)
     */
    public List<Vehicle> fetchVehicles() {
        List<Vehicle> vehicles = new ArrayList<>();
        lastVehiclesFetchError = null;

        if (accessToken == null || accessToken.isBlank()) {
            lastVehiclesFetchError = "Authorization token not set";
            loggerService.log("backend", "error", "service", "Cannot fetch vehicles: authorization token not set");
            return vehicles;
        }

        try {
            loggerService.log("backend", "info", "service", "Fetching vehicles from test server");

            String url = testServerUrl + "/vehicles";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                loggerService.log("backend", "info", "service", "Successfully fetched vehicles from test server");
                vehicles = parseVehicles(response.body());
                loggerService.log("backend", "info", "service", "Parsed " + vehicles.size() + " vehicles with maintenance tasks");
            } else {
                lastVehiclesFetchError = "HTTP " + response.statusCode() + ": " + response.body();
                loggerService.log("backend", "error", "service", "Failed to fetch vehicles. " + lastVehiclesFetchError);
            }

        } catch (Exception e) {
            lastVehiclesFetchError = "Exception: " + e.getMessage();
            loggerService.log("backend", "fatal", "service", "Exception while fetching vehicles: " + e.getMessage());
        }

        return vehicles;
    }

    /**
     * Compute optimal schedule for a specific depot using knapsack algorithm
     */
    public ScheduleResult computeOptimalSchedule(Integer depotId, Integer mechanicHours, List<Vehicle> vehicles) {
        loggerService.log("backend", "info", "service", 
                "Computing optimal schedule for depot " + depotId + " with capacity " + mechanicHours);

        // Solve knapsack
        List<String> selectedTaskIds = KnapsackScheduler.solveSchedule(vehicles, mechanicHours);
        Integer totalDuration = KnapsackScheduler.calculateTotalDuration(vehicles, selectedTaskIds);
        Integer totalImpact = KnapsackScheduler.calculateTotalImpact(vehicles, selectedTaskIds);

        loggerService.log("backend", "info", "service", 
                "Knapsack solution: selected " + selectedTaskIds.size() + " tasks with total impact " + totalImpact);

        ScheduleResult result = new ScheduleResult(depotId, selectedTaskIds, totalDuration, totalImpact, mechanicHours);

        loggerService.log("backend", "info", "service", 
                "Schedule computed: totalDuration=" + totalDuration + ", totalImpact=" + totalImpact + 
                ", remaining=" + result.getRemainingMechanicHours());

        return result;
    }

    /**
     * Main orchestration: fetch data and compute schedules for all depots
     */
    public Map<String, Object> scheduleAllDepots() {
        loggerService.log("backend", "info", "controller", "Starting vehicle maintenance scheduling process");

        try {
            // Fetch depots and vehicles
            List<Depot> depots = fetchDepots();
            List<Vehicle> vehicles = fetchVehicles();

            if (depots.isEmpty()) {
                loggerService.log("backend", "warn", "service", "No depots found");
                Map<String, Object> error = new HashMap<>();
                error.put("error", "No depots available");
                error.put("depotFetchError", lastDepotsFetchError == null ? "Unknown error" : lastDepotsFetchError);
                return error;
            }

            if (vehicles.isEmpty()) {
                loggerService.log("backend", "warn", "service", "No vehicles found");
                Map<String, Object> error = new HashMap<>();
                error.put("error", "No vehicles available");
                error.put("vehicleFetchError", lastVehiclesFetchError == null ? "Unknown error" : lastVehiclesFetchError);
                return error;
            }

            loggerService.log("backend", "info", "service", 
                    "Processing " + depots.size() + " depots with " + vehicles.size() + " vehicles");

            // Compute schedule for each depot
            List<ScheduleResult> schedules = new ArrayList<>();
            for (Depot depot : depots) {
                ScheduleResult schedule = computeOptimalSchedule(depot.getId(), depot.getMechanicHours(), vehicles);
                schedules.add(schedule);
            }

            loggerService.log("backend", "info", "controller", 
                    "Successfully computed schedules for all " + schedules.size() + " depots");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalDepots", depots.size());
            response.put("totalVehicles", vehicles.size());
            response.put("schedules", schedules);

            return response;

        } catch (Exception e) {
            loggerService.log("backend", "fatal", "controller", "Unexpected error during scheduling: " + e.getMessage());
            return Map.of("error", "Scheduling failed: " + e.getMessage());
        }
    }

    /**
     * Parse depots from JSON response (no external libraries)
     */
    private List<Depot> parseDepots(String json) {
        List<Depot> depots = new ArrayList<>();
        
        // Find "depots" array
        int depotsStart = json.indexOf("\"depots\":[");
        if (depotsStart == -1) return depots;

        int arrayStart = json.indexOf("[", depotsStart);
        int arrayEnd = json.lastIndexOf("]");

        String depotsArray = json.substring(arrayStart + 1, arrayEnd);
        
        // Split by object boundaries
        String[] objects = depotsArray.split("\\},\\{");
        
        for (String obj : objects) {
            obj = obj.replace("{", "").replace("}", "");
            
            Integer id = extractIntValue(obj, "ID");
            Integer mechanicHours = extractIntValue(obj, "MechanicHours");
            
            if (id != null && mechanicHours != null) {
                depots.add(new Depot(id, mechanicHours));
            }
        }

        return depots;
    }

    /**
     * Parse vehicles from JSON response (no external libraries)
     */
    private List<Vehicle> parseVehicles(String json) {
        List<Vehicle> vehicles = new ArrayList<>();

        // Find "vehicles" array
        int vehiclesStart = json.indexOf("\"vehicles\":[");
        if (vehiclesStart == -1) return vehicles;

        int arrayStart = json.indexOf("[", vehiclesStart);
        int arrayEnd = json.lastIndexOf("]");

        String vehiclesArray = json.substring(arrayStart + 1, arrayEnd);
        
        // Split by object boundaries
        String[] objects = vehiclesArray.split("\\},\\{");
        
        for (String obj : objects) {
            obj = obj.replace("{", "").replace("}", "");
            
            String taskId = extractStringValue(obj, "TaskID");
            Integer duration = extractIntValue(obj, "Duration");
            Integer impact = extractIntValue(obj, "Impact");
            
            if (taskId != null && duration != null && impact != null) {
                vehicles.add(new Vehicle(taskId, duration, impact));
            }
        }

        return vehicles;
    }

    /**
     * Extract string value from JSON object (no external libraries)
     */
    private String extractStringValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) return null;

        startIndex += searchKey.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) return null;

        return json.substring(startIndex, endIndex);
    }

    /**
     * Extract integer value from JSON object (no external libraries)
     */
    private Integer extractIntValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) return null;

        startIndex += searchKey.length();
        
        // Skip whitespace
        while (startIndex < json.length() && Character.isWhitespace(json.charAt(startIndex))) {
            startIndex++;
        }

        int endIndex = startIndex;
        while (endIndex < json.length() && Character.isDigit(json.charAt(endIndex))) {
            endIndex++;
        }

        if (endIndex == startIndex) return null;

        try {
            return Integer.parseInt(json.substring(startIndex, endIndex));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
