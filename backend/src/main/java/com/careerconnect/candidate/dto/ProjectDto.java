package com.careerconnect.candidate.dto;

import java.time.Instant;

public record ProjectDto(
        Long id,
        Long candidateId,
        String title,
        String description,
        String projectUrl,
        Integer sortOrder,
        Instant createdAt,
        Instant updatedAt
) {}
