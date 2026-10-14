package com.careerconnect.candidate.dto;

import java.time.Instant;

public record EducationDto(
        Long id,
        Long candidateId,
        String institution,
        String degree,
        String fieldOfStudy,
        Integer startYear,
        Integer endYear,
        String grade,
        Integer sortOrder,
        Instant createdAt,
        Instant updatedAt
) {}
