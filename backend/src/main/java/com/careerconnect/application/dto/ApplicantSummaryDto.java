package com.careerconnect.application.dto;

import com.careerconnect.application.domain.ApplicationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicantSummaryDto(
        Long applicationId,
        Long jobId,
        Long candidateId,
        String candidateName,
        String candidateEmail,
        String candidateHeadline,
        ApplicationStatus status,
        Instant appliedAt,
        Integer matchPercentage
) {}
