package com.careerconnect.candidate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;

public record ExperienceDto(
        Long id,
        Long candidateId,
        String companyName,
        String roleTitle,
        LocalDate startDate,
        LocalDate endDate,
        @JsonProperty("isCurrent")
        Boolean isCurrent,
        String description,
        Integer sortOrder,
        Instant createdAt,
        Instant updatedAt
) {}
