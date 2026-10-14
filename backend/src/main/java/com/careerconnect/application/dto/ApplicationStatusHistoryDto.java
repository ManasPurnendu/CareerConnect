package com.careerconnect.application.dto;

import com.careerconnect.application.domain.ApplicationStatus;

import java.time.Instant;

public record ApplicationStatusHistoryDto(
        Long id,
        Long applicationId,
        ApplicationStatus fromStatus,
        ApplicationStatus toStatus,
        Long changedByUserId,
        String note,
        Instant createdAt
) {}
