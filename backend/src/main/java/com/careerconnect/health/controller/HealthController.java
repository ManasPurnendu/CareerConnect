package com.careerconnect.health.controller;

import com.careerconnect.common.response.ApiResponse;
import com.careerconnect.health.dto.HealthResponse;
import com.careerconnect.health.service.HealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<HealthResponse>> getHealth() {
        HealthResponse health = healthService.checkHealth();
        return ResponseEntity.ok(ApiResponse.success(health));
    }
}
