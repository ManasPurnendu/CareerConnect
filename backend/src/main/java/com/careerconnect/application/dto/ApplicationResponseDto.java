package com.careerconnect.application.dto;

import com.careerconnect.application.domain.ApplicationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicationResponseDto(
        Long applicationId,
        Long jobId,
        Long candidateId,
        ApplicationStatus status,
        String coverLetter,
        Instant appliedAt,
        String jobTitle,
        String companyName,
        CandidateSnapshot candidateSnapshot,
        ResumeSnapshot resumeSnapshot,
        List<ApplicationStatusHistoryDto> statusHistory
) {
    public ApplicationResponseDto(
            Long applicationId,
            Long jobId,
            Long candidateId,
            ApplicationStatus status,
            String coverLetter,
            Instant appliedAt
    ) {
        this(applicationId, jobId, candidateId, status, coverLetter, appliedAt, null, null, null, null, null);
    }
}
