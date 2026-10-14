package com.careerconnect.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumeSnapshot(
        Long id,
        String originalFilename,
        String storageKey,
        String contentType,
        long sizeBytes
) {}
