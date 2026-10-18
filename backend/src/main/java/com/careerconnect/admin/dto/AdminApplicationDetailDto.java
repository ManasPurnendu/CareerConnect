package com.careerconnect.admin.dto;

import com.careerconnect.application.domain.ApplicationStatus;
import com.careerconnect.application.dto.ApplicationStatusHistoryDto;
import com.careerconnect.application.dto.CandidateSnapshot;
import com.careerconnect.application.dto.JobMatchDto;
import com.careerconnect.application.dto.ResumeSnapshot;

import java.time.Instant;
import java.util.List;

/**
 * Data transfer object representing the complete application details for administrative review,
 * preserving immutable candidate and resume snapshots, complete status history, and transparent match scoring.
 */
public record AdminApplicationDetailDto(
        Long applicationId,
        Long jobId,
        String jobTitle,
        Long companyId,
        String companyName,
        Long candidateId,
        ApplicationStatus status,
        String coverLetter,
        Instant appliedAt,
        CandidateSnapshot candidateSnapshot,
        ResumeSnapshot resumeSnapshot,
        List<ApplicationStatusHistoryDto> statusHistory,
        JobMatchDto match
) {
}
