package com.careerconnect.admin.dto;

import com.careerconnect.application.domain.ApplicationStatus;

import java.time.Instant;

/**
 * Data transfer object representing a high-level summary of an application for administrative listing.
 */
public record AdminApplicationSummaryDto(
        Long id,
        Long jobId,
        String jobTitle,
        Long companyId,
        String companyName,
        Long candidateId,
        String candidateName,
        String candidateEmail,
        ApplicationStatus status,
        Instant appliedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
