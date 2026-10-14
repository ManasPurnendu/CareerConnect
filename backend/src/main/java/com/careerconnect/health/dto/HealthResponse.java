package com.careerconnect.health.dto;

import java.time.Instant;

public class HealthResponse {

    private String status;
    private String database;
    private Instant timestamp;
    private String version;

    public HealthResponse() {
    }

    public HealthResponse(String status, String database, Instant timestamp, String version) {
        this.status = status;
        this.database = database;
        this.timestamp = timestamp;
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
