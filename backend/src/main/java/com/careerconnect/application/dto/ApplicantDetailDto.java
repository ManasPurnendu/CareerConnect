package com.careerconnect.application.dto;

import com.careerconnect.application.domain.ApplicationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicantDetailDto(
        Long applicationId,
        Long jobId,
        String jobTitle,
        Long candidateId,
        ApplicationStatus status,
        String coverLetter,
        Instant appliedAt,
        CandidateSnapshot candidateSnapshot,
        ResumeSnapshot resumeSnapshot,
        List<ApplicationStatusHistoryDto> statusHistory,
        JobMatchDto match
) {}
