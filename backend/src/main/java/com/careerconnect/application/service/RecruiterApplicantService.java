package com.careerconnect.application.service;

import com.careerconnect.application.domain.ApplicationStatus;
import com.careerconnect.application.dto.ApplicantDetailDto;
import com.careerconnect.application.dto.ApplicantSummaryDto;
import com.careerconnect.application.dto.ApplicationStatusHistoryDto;
import com.careerconnect.application.dto.CandidateSnapshot;
import com.careerconnect.application.dto.JobMatchDto;
import com.careerconnect.application.dto.ResumeSnapshot;
import com.careerconnect.application.repository.ApplicationRepository;
import com.careerconnect.auth.repository.RecruiterAuthRepository;
import com.careerconnect.common.exception.BusinessRuleException;
import com.careerconnect.common.exception.ErrorCode;
import com.careerconnect.common.exception.ResourceNotFoundException;
import com.careerconnect.common.pagination.PageRequestDto;
import com.careerconnect.common.pagination.PageResult;
import com.careerconnect.jobs.repository.JobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RecruiterApplicantService {

    private static final Logger log = LoggerFactory.getLogger(RecruiterApplicantService.class);

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final RecruiterAuthRepository recruiterAuthRepository;
    private final MatchService matchService;
    private final ObjectMapper objectMapper;
    private final com.careerconnect.notification.service.NotificationService notificationService;
    private final com.careerconnect.candidate.service.CandidateResumeService candidateResumeService;
    private final com.careerconnect.candidate.repository.CandidateResumeRepository candidateResumeRepository;

    public RecruiterApplicantService(
            ApplicationRepository applicationRepository,
            JobRepository jobRepository,
            RecruiterAuthRepository recruiterAuthRepository,
            MatchService matchService,
            ObjectMapper objectMapper,
            com.careerconnect.notification.service.NotificationService notificationService,
            com.careerconnect.candidate.service.CandidateResumeService candidateResumeService,
            com.careerconnect.candidate.repository.CandidateResumeRepository candidateResumeRepository) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.recruiterAuthRepository = recruiterAuthRepository;
        this.matchService = matchService;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.candidateResumeService = candidateResumeService;
        this.candidateResumeRepository = candidateResumeRepository;
    }

    public record ResumeDownloadDto(
            Long id,
            String filename,
            String contentType,
            long sizeBytes,
            byte[] content,
            boolean isCurrent,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /**
     * Protected resume access for an application submitted to a job owned by the recruiter's company.
     * Enforces recruiter company ownership, job not soft-deleted, and retrieves candidate's current resume.
     */
    public ResumeDownloadDto getApplicantResume(Long recruiterId, Long applicationId) {
        Long companyId = resolveAuthoritativeCompanyId(recruiterId);

        ApplicationRepository.RecruiterApplicantDetailEntity entity = applicationRepository
                .findApplicantDetailByIdAndCompanyId(applicationId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Application with ID " + applicationId + " not found.",
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        com.careerconnect.candidate.repository.CandidateResumeRepository.ResumeEntity resume = candidateResumeRepository
                .findCurrentByCandidateId(entity.candidateId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Resume not found for applicant in application " + applicationId + ".",
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        com.careerconnect.candidate.service.CandidateResumeService.ResumeFileResource file = candidateResumeService
                .getResumeFile(resume.storageKey(), resume.originalFilename(), resume.contentType());

        return new ResumeDownloadDto(
                resume.id(),
                file.filename(),
                file.contentType(),
                file.sizeBytes(),
                file.content(),
                resume.isCurrent(),
                resume.createdAt(),
                resume.updatedAt()
        );
    }

    /**
     * Retrieves a paginated list of applicants for a job owned by the authenticated recruiter's company.
     * Enforces company ownership, non-deleted job status, and deterministic ordering (applied_at DESC, id DESC).
     */
    public PageResult<ApplicantSummaryDto> getJobApplicants(
            Long recruiterId,
            Long jobId,
            ApplicationStatus statusFilter,
            PageRequestDto pageRequest) {

        Long companyId = resolveAuthoritativeCompanyId(recruiterId);

        // Verify the job exists and belongs to the recruiter's company
        jobRepository.findJobByIdForRecruiter(jobId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job with ID " + jobId + " not found or not owned by your company.",
                        ErrorCode.JOB_NOT_FOUND
                ));

        if (pageRequest == null) {
            pageRequest = new PageRequestDto();
        }

        int limit = pageRequest.getSize();
        int offset = pageRequest.getOffset();

        List<ApplicationRepository.ApplicantSummaryEntity> entities =
                applicationRepository.findApplicantsByJobIdAndCompanyId(jobId, companyId, statusFilter, limit, offset);

        long totalElements = applicationRepository.countApplicantsByJobIdAndCompanyId(jobId, companyId, statusFilter);

        // Fetch required skills for match percentage calculation
        List<String> jobSkills = jobRepository.findRequiredSkillNamesByJobId(jobId);

        List<ApplicantSummaryDto> items = entities.stream()
                .map(e -> {
                    CandidateSnapshot snapshot = parseCandidateSnapshot(e.candidateSnapshotJson());
                    List<String> candidateSkills = (snapshot != null && snapshot.skills() != null)
                            ? snapshot.skills()
                            : List.of();
                    int matchPercentage = matchService.calculateMatch(jobSkills, candidateSkills).percentage();

                    return new ApplicantSummaryDto(
                            e.applicationId(),
                            e.jobId(),
                            e.candidateId(),
                            snapshot != null ? snapshot.fullName() : null,
                            snapshot != null ? snapshot.email() : null,
                            snapshot != null ? snapshot.headline() : null,
                            e.status(),
                            e.appliedAt(),
                            matchPercentage
                    );
                })
                .toList();

        log.debug("Found {} applicants for job {} (company {})", items.size(), jobId, companyId);
        return PageResult.of(items, pageRequest.getPage(), pageRequest.getSize(), totalElements);
    }

    /**
     * Retrieves full applicant details for an application on a job owned by the recruiter's company.
     * Exposes immutable candidate snapshot, resume snapshot, full status timeline, and deterministic match.
     */
    public ApplicantDetailDto getApplicantDetail(Long recruiterId, Long applicationId) {
        Long companyId = resolveAuthoritativeCompanyId(recruiterId);

        ApplicationRepository.RecruiterApplicantDetailEntity entity = applicationRepository
                .findApplicantDetailByIdAndCompanyId(applicationId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Application with ID " + applicationId + " not found or not owned by your company.",
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        CandidateSnapshot candidateSnapshot = parseCandidateSnapshot(entity.candidateSnapshotJson());
        ResumeSnapshot resumeSnapshot = parseResumeSnapshot(entity.resumeSnapshotJson());

        List<String> jobSkills = jobRepository.findRequiredSkillNamesByJobId(entity.jobId());
        List<String> candidateSkills = (candidateSnapshot != null && candidateSnapshot.skills() != null)
                ? candidateSnapshot.skills()
                : List.of();

        JobMatchDto match = matchService.calculateMatch(jobSkills, candidateSkills);

        List<ApplicationStatusHistoryDto> history = applicationRepository
                .findStatusHistoryByApplicationId(applicationId)
                .stream()
                .map(h -> new ApplicationStatusHistoryDto(
                        h.id(),
                        h.applicationId(),
                        h.fromStatus(),
                        h.toStatus(),
                        h.changedByUserId(),
                        h.note(),
                        h.createdAt()
                ))
                .toList();

        return new ApplicantDetailDto(
                entity.applicationId(),
                entity.jobId(),
                entity.jobTitle(),
                entity.candidateId(),
                entity.status(),
                entity.coverLetter(),
                entity.appliedAt(),
                candidateSnapshot,
                resumeSnapshot,
                history,
                match
        );
    }

    /**
     * Updates an applicant's status on a company-owned job.
     * Enforces company ownership, state machine transitions, non-terminal constraints,
     * atomic status history logging, and transactional rollback.
     */
    @Transactional
    public ApplicantDetailDto updateApplicationStatus(
            Long recruiterId,
            Long userId,
            Long applicationId,
            ApplicationStatus targetStatus,
            String note
    ) {
        Long companyId = resolveAuthoritativeCompanyId(recruiterId);

        // 1. Fetch current application under company ownership (404 if not found or foreign)
        ApplicationRepository.RecruiterApplicantDetailEntity currentEntity = applicationRepository
                .findApplicantDetailByIdAndCompanyId(applicationId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Application with ID " + applicationId + " not found or not owned by your company.",
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        // 2. Validate recruiter transition permissions & state machine
        ApplicationStatus currentStatus = currentEntity.status();
        if (targetStatus == null || targetStatus == ApplicationStatus.WITHDRAWN || !currentStatus.isValidTransition(targetStatus)) {
            throw new BusinessRuleException(
                    "Invalid application status transition from " + currentStatus + " to " + targetStatus,
                    ErrorCode.INVALID_STATUS_TRANSITION
            );
        }

        // 3. Perform ownership-scoped status update
        Instant now = Instant.now();
        int updated = applicationRepository.updateStatusForCompany(applicationId, companyId, targetStatus, now);
        if (updated == 0) {
            throw new ResourceNotFoundException(
                    "Application with ID " + applicationId + " not found or not owned by your company.",
                    ErrorCode.RESOURCE_NOT_FOUND
            );
        }

        // 4. Append audit record to application_status_history
        String trimmedNote = (note != null && !note.trim().isEmpty()) ? note.trim() : null;
        applicationRepository.insertStatusHistory(
                applicationId,
                currentStatus,
                targetStatus,
                userId,
                trimmedNote,
                now
        );

        // 5. Create in-app notification for the application candidate
        notificationService.notifyApplicationStatusChange(
                currentEntity.candidateUserId(),
                applicationId,
                currentEntity.jobTitle(),
                targetStatus
        );

        log.info("Recruiter {} (user {}) transitioned application {} from {} to {} (note present: {})",
                recruiterId, userId, applicationId, currentStatus, targetStatus, trimmedNote != null);

        // 6. Return updated applicant detail
        return getApplicantDetail(recruiterId, applicationId);
    }

    public CandidateSnapshot parseCandidateSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isTextual()) {
                return objectMapper.readValue(node.asText(), CandidateSnapshot.class);
            }
            return objectMapper.treeToValue(node, CandidateSnapshot.class);
        } catch (Exception e) {
            log.error("Failed to deserialize candidate snapshot: {}", e.getMessage());
            throw new IllegalStateException("Failed to deserialize candidate snapshot", e);
        }
    }

    public ResumeSnapshot parseResumeSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isTextual()) {
                return objectMapper.readValue(node.asText(), ResumeSnapshot.class);
            }
            return objectMapper.treeToValue(node, ResumeSnapshot.class);
        } catch (Exception e) {
            log.error("Failed to deserialize resume snapshot: {}", e.getMessage());
            throw new IllegalStateException("Failed to deserialize resume snapshot", e);
        }
    }

    private Long resolveAuthoritativeCompanyId(Long recruiterId) {
        if (recruiterId == null) {
            throw new ResourceNotFoundException("Recruiter identity not found.", ErrorCode.RESOURCE_NOT_FOUND);
        }

        return recruiterAuthRepository.findCompanyIdByRecruiterId(recruiterId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Recruiter must belong to a company to perform job operations.",
                        ErrorCode.COMPANY_REQUIRED
                ));
    }
}
