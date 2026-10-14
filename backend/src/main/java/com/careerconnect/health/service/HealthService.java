package com.careerconnect.health.service;

import com.careerconnect.common.util.DateTimeUtils;
import com.careerconnect.health.dto.HealthResponse;
import com.careerconnect.health.repository.HealthRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private final HealthRepository healthRepository;
    private final String appVersion;

    public HealthService(HealthRepository healthRepository,
                         @Value("${app.version:1.0.0}") String appVersion) {
        this.healthRepository = healthRepository;
        this.appVersion = appVersion;
    }

    public HealthResponse checkHealth() {
        boolean dbConnected = healthRepository.isDatabaseConnected();
        String dbStatus = dbConnected ? "CONNECTED" : "DISCONNECTED";
        String overallStatus = dbConnected ? "UP" : "DEGRADED";

        return new HealthResponse(
                overallStatus,
                dbStatus,
                DateTimeUtils.nowUtc(),
                appVersion
        );
    }
}
