package com.careerconnect.candidate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ResumeResponse(
        Long id,
        String originalFilename,
        String contentType,
        long sizeBytes,
        @JsonProperty("isCurrent")
        boolean isCurrent,
        Instant createdAt,
        Instant updatedAt
) {}
