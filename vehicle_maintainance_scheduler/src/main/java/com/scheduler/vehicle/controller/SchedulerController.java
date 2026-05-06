package com.scheduler.vehicle.controller;

import com.scheduler.vehicle.service.SchedulingService;
import com.middleware.logger.LoggerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/scheduler")
public class SchedulerController {

    @Autowired
    private SchedulingService schedulingService;

    private final LoggerService loggerService = new LoggerService("http://20.207.122.201/evaluation-service");

    @PostMapping("/set-token")
    public Map<String, Object> setToken(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                        @RequestBody(required = false) Map<String, String> request) {
        String token = extractToken(authorizationHeader);

        if (token == null || token.isEmpty()) {
            if (request != null) {
                token = request.get("access_token");
            }
        }

        if (token == null || token.isEmpty()) {
            return Map.of("success", false, "message", "access_token is required");
        }

        // Set token in both services
        schedulingService.setAccessToken(token);
        loggerService.setAccessToken(token);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Access token set successfully");
        response.put("tokenPreview", token.substring(0, Math.min(50, token.length())) + "...");

        return response;
    }

    /**
     * Compute optimal vehicle maintenance schedules for all depots.
     * The Authorization header can be supplied here directly so the request
     * is aligned with the protected-route requirement.
     */
    @GetMapping("/schedule")
    public Map<String, Object> getOptimalSchedule(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        try {
            String token = extractToken(authorizationHeader);
            if (token != null && !token.isBlank()) {
                schedulingService.setAccessToken(token);
                loggerService.setAccessToken(token);
            }

            if (token == null || token.isBlank()) {
                return Map.of("success", false, "error", "Authorization header with Bearer token is required");
            }

            loggerService.log("backend", "info", "controller", "Received request to compute optimal vehicle schedules");
            
            return schedulingService.scheduleAllDepots();

        } catch (Exception e) {
            loggerService.log("backend", "error", "controller", "Error in /schedule endpoint: " + e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * Health check endpoint
     * GET /api/scheduler/health
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "Vehicle Maintenance Scheduler");
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }

        if (authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7).trim();
        }

        return authorizationHeader.trim();
    }
}
