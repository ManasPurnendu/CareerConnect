package com.careerconnect.application.service;

import com.careerconnect.application.domain.ApplicationStatus;
import com.careerconnect.application.dto.ApplicationResponseDto;
import com.careerconnect.application.dto.ApplicationStatusHistoryDto;
import com.careerconnect.application.dto.ApplicationSubmissionRequest;
import com.careerconnect.application.dto.CandidateSnapshot;
import com.careerconnect.application.dto.ResumeSnapshot;
import com.careerconnect.application.repository.ApplicationRepository;
import com.careerconnect.common.pagination.PageRequestDto;
import com.careerconnect.common.pagination.PageResult;
import com.careerconnect.candidate.dto.CandidateProfileResponse;
import com.careerconnect.candidate.dto.CandidateSkillDto;
import com.careerconnect.candidate.dto.EducationDto;
import com.careerconnect.candidate.dto.ExperienceDto;
import com.careerconnect.candidate.dto.ProfileReadinessDto;
import com.careerconnect.candidate.dto.ProjectDto;
import com.careerconnect.candidate.repository.CandidateEducationRepository;
import com.careerconnect.candidate.repository.CandidateExperienceRepository;
import com.careerconnect.candidate.repository.CandidateProfileRepository;
import com.careerconnect.candidate.repository.CandidateProjectRepository;
import com.careerconnect.candidate.repository.CandidateResumeRepository;
import com.careerconnect.candidate.repository.CandidateSkillRepository;
import com.careerconnect.candidate.service.CandidateProfileReadinessService;
import com.careerconnect.common.exception.BusinessRuleException;
import com.careerconnect.common.exception.ConflictException;
import com.careerconnect.common.exception.ErrorCode;
import com.careerconnect.common.exception.ResourceNotFoundException;
import com.careerconnect.security.SecurityUser;
import com.careerconnect.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final CandidateProfileReadinessService readinessService;
    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateEducationRepository candidateEducationRepository;
    private final CandidateSkillRepository candidateSkillRepository;
    private final CandidateProjectRepository candidateProjectRepository;
    private final CandidateExperienceRepository candidateExperienceRepository;
    private final CandidateResumeRepository candidateResumeRepository;
    private final ObjectMapper objectMapper;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            CandidateProfileReadinessService readinessService,
            CandidateProfileRepository candidateProfileRepository,
            CandidateEducationRepository candidateEducationRepository,
            CandidateSkillRepository candidateSkillRepository,
            CandidateProjectRepository candidateProjectRepository,
            CandidateExperienceRepository candidateExperienceRepository,
            CandidateResumeRepository candidateResumeRepository,
            ObjectMapper objectMapper) {
        this.applicationRepository = applicationRepository;
        this.readinessService = readinessService;
        this.candidateProfileRepository = candidateProfileRepository;
        this.candidateEducationRepository = candidateEducationRepository;
        this.candidateSkillRepository = candidateSkillRepository;
        this.candidateProjectRepository = candidateProjectRepository;
        this.candidateExperienceRepository = candidateExperienceRepository;
        this.candidateResumeRepository = candidateResumeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ApplicationResponseDto submitApplication(Long jobId, ApplicationSubmissionRequest request) {
        // 1. Resolve candidate identity strictly from SecurityUtils
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        Long userId = SecurityUtils.getCurrentUserId();

        // 2. Validate job existence and eligibility
        ApplicationRepository.JobEligibility job = applicationRepository.findJobEligibility(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job with ID " + jobId + " not found", ErrorCode.JOB_NOT_FOUND));

        if (job.deletedAt() != null || !"ACTIVE".equalsIgnoreCase(job.status())) {
            throw new BusinessRuleException("Job is not active", ErrorCode.JOB_NOT_ACTIVE);
        }

        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        if (job.applicationDeadline() != null && job.applicationDeadline().isBefore(todayUtc)) {
            throw new BusinessRuleException("Job application deadline has passed", ErrorCode.JOB_DEADLINE_PASSED);
        }

        // 3. Validate candidate profile completeness & resume readiness
        ProfileReadinessDto readiness = readinessService.computeReadiness(candidateId);
        if (!readiness.profileComplete() || !readiness.hasEducation() || !readiness.hasSkills()) {
            throw new BusinessRuleException(
                    "Candidate profile is incomplete. Profile, education, and skills are required.",
                    ErrorCode.CANDIDATE_PROFILE_INCOMPLETE);
        }
        if (!readiness.hasResume()) {
            throw new BusinessRuleException(
                    "A current resume is required to apply for jobs.",
                    ErrorCode.CANDIDATE_RESUME_REQUIRED);
        }

        // 4. Duplicate application check
        if (applicationRepository.existsByCandidateIdAndJobId(candidateId, jobId)) {
            throw new ConflictException("You have already applied to this job.", ErrorCode.DUPLICATE_APPLICATION);
        }

        // 5. Build immutable candidate and resume snapshots
        CandidateSnapshot candidateSnapshot = buildCandidateSnapshot(candidateId);
        ResumeSnapshot resumeSnapshot = buildResumeSnapshot(candidateId);

        String candidateSnapshotJson;
        String resumeSnapshotJson;
        try {
            candidateSnapshotJson = objectMapper.writeValueAsString(candidateSnapshot);
            resumeSnapshotJson = objectMapper.writeValueAsString(resumeSnapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize application snapshot", e);
        }

        // 6. Persist application with initial status APPLIED
        Instant appliedAt = Instant.now();
        String coverLetter = request != null && request.getCoverLetter() != null
                ? request.getCoverLetter().trim() : null;

        Long applicationId;
        try {
            applicationId = applicationRepository.insertApplication(
                    jobId,
                    candidateId,
                    coverLetter,
                    ApplicationStatus.APPLIED,
                    candidateSnapshotJson,
                    resumeSnapshotJson,
                    appliedAt
            );
        } catch (DuplicateKeyException e) {
            throw new ConflictException("You have already applied to this job.", ErrorCode.DUPLICATE_APPLICATION);
        }

        // 7. Insert initial status history event (atomic with application insert)
        applicationRepository.insertStatusHistory(
                applicationId,
                null,
                ApplicationStatus.APPLIED,
                userId,
                null,
                appliedAt
        );

        // 8. Return safe response DTO
        return new ApplicationResponseDto(
                applicationId,
                jobId,
                candidateId,
                ApplicationStatus.APPLIED,
                coverLetter,
                appliedAt
        );
    }

    private CandidateSnapshot buildCandidateSnapshot(Long candidateId) {
        CandidateProfileResponse profile = candidateProfileRepository.findById(candidateId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Candidate profile not found.", ErrorCode.CANDIDATE_PROFILE_INCOMPLETE));

        String email = SecurityUtils.getCurrentUser().map(SecurityUser::getUsername).orElse(null);

        List<EducationDto> educationList = candidateEducationRepository.findByCandidateId(candidateId);
        List<CandidateSnapshot.CandidateEducationSnapshot> educationSnapshots = educationList.stream()
                .map(e -> new CandidateSnapshot.CandidateEducationSnapshot(
                        e.id(),
                        e.institution(),
                        e.degree(),
                        e.fieldOfStudy(),
                        e.startYear(),
                        e.endYear(),
                        e.grade(),
                        e.sortOrder()
                )).toList();

        List<CandidateSkillDto> skillList = candidateSkillRepository.findByCandidateId(candidateId);
        List<String> skillNames = skillList.stream().map(CandidateSkillDto::name).toList();

        List<ProjectDto> projectList = candidateProjectRepository.findByCandidateId(candidateId);
        List<CandidateSnapshot.CandidateProjectSnapshot> projectSnapshots = projectList.stream()
                .map(p -> new CandidateSnapshot.CandidateProjectSnapshot(
                        p.id(),
                        p.title(),
                        p.description(),
                        p.projectUrl(),
                        p.sortOrder()
                )).toList();

        List<ExperienceDto> experienceList = candidateExperienceRepository.findByCandidateId(candidateId);
        List<CandidateSnapshot.CandidateExperienceSnapshot> experienceSnapshots = experienceList.stream()
                .map(x -> new CandidateSnapshot.CandidateExperienceSnapshot(
                        x.id(),
                        x.companyName(),
                        x.roleTitle(),
                        x.startDate() != null ? x.startDate().toString() : null,
                        x.endDate() != null ? x.endDate().toString() : null,
                        x.isCurrent(),
                        x.description(),
                        x.sortOrder()
                )).toList();

        return new CandidateSnapshot(
                candidateId,
                profile.fullName(),
                email,
                profile.headline(),
                profile.location(),
                profile.summary(),
                educationSnapshots,
                skillNames,
                projectSnapshots,
                experienceSnapshots
        );
    }

    private ResumeSnapshot buildResumeSnapshot(Long candidateId) {
        CandidateResumeRepository.ResumeEntity resume = candidateResumeRepository.findCurrentByCandidateId(candidateId)
                .orElseThrow(() -> new BusinessRuleException(
                        "A current resume is required to apply for jobs.", ErrorCode.CANDIDATE_RESUME_REQUIRED));

        return new ResumeSnapshot(
                resume.id(),
                resume.originalFilename(),
                resume.storageKey(),
                resume.contentType(),
                resume.sizeBytes()
        );
    }

    public void validateTransition(ApplicationStatus currentStatus, ApplicationStatus targetStatus) {
        if (currentStatus == null || !currentStatus.isValidTransition(targetStatus)) {
            throw new BusinessRuleException(
                    "Invalid application status transition from " + currentStatus + " to " + targetStatus,
                    ErrorCode.INVALID_STATUS_TRANSITION
            );
        }
    }

    @Transactional
    public ApplicationResponseDto withdrawApplication(Long applicationId) {
        // 1. Resolve candidate identity strictly from SecurityUtils
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        Long userId = SecurityUtils.getCurrentUserId();

        // 2. Fetch candidate-owned application (404 if non-existent or owned by another candidate)
        ApplicationRepository.ApplicationEntity application = applicationRepository
                .findByIdAndCandidateId(applicationId, candidateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Application with ID " + applicationId + " not found",
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        // 3. Validate transition from current status to WITHDRAWN
        validateTransition(application.status(), ApplicationStatus.WITHDRAWN);

        // 4. Update status to WITHDRAWN with candidate ownership predicate
        Instant now = Instant.now();
        int rowsUpdated = applicationRepository.updateStatusForCandidate(applicationId, candidateId, ApplicationStatus.WITHDRAWN, now);
        if (rowsUpdated == 0) {
            throw new ResourceNotFoundException(
                    "Application with ID " + applicationId + " not found",
                    ErrorCode.RESOURCE_NOT_FOUND
            );
        }

        // 5. Insert status history audit row
        applicationRepository.insertStatusHistory(
                applicationId,
                application.status(),
                ApplicationStatus.WITHDRAWN,
                userId,
                null,
                now
        );

        // 6. Return response DTO
        return new ApplicationResponseDto(
                application.id(),
                application.jobId(),
                application.candidateId(),
                ApplicationStatus.WITHDRAWN,
                application.coverLetter(),
                application.appliedAt()
        );
    }

    public CandidateSnapshot parseCandidateSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json);
            if (node.isTextual()) {
                return objectMapper.readValue(node.asText(), CandidateSnapshot.class);
            }
            return objectMapper.treeToValue(node, CandidateSnapshot.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize candidate snapshot", e);
        }
    }

    public ResumeSnapshot parseResumeSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json);
            if (node.isTextual()) {
                return objectMapper.readValue(node.asText(), ResumeSnapshot.class);
            }
            return objectMapper.treeToValue(node, ResumeSnapshot.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize resume snapshot", e);
        }
    }

    @Transactional(readOnly = true)
    public PageResult<ApplicationResponseDto> getCandidateApplications(PageRequestDto pageRequest) {
        Long candidateId = SecurityUtils.getRequiredCandidateId();

        if (pageRequest == null) {
            pageRequest = new PageRequestDto();
        }

        int limit = pageRequest.getSize();
        int offset = pageRequest.getOffset();

        List<ApplicationRepository.ApplicationWithJobEntity> entities =
                applicationRepository.findByCandidateId(candidateId, limit, offset);
        long totalElements = applicationRepository.countByCandidateId(candidateId);

        List<ApplicationResponseDto> items = entities.stream()
                .map(e -> new ApplicationResponseDto(
                        e.id(),
                        e.jobId(),
                        e.candidateId(),
                        e.status(),
                        e.coverLetter(),
                        e.appliedAt(),
                        e.jobTitle(),
                        e.companyName(),
                        parseCandidateSnapshot(e.candidateSnapshot()),
                        parseResumeSnapshot(e.resumeSnapshot()),
                        null
                ))
                .toList();

        return PageResult.of(items, pageRequest.getPage(), pageRequest.getSize(), totalElements);
    }

    @Transactional(readOnly = true)
    public ApplicationResponseDto getCandidateApplicationDetail(Long applicationId) {
        Long candidateId = SecurityUtils.getRequiredCandidateId();

        ApplicationRepository.ApplicationWithJobEntity entity = applicationRepository
                .findDetailByIdAndCandidateId(applicationId, candidateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Application with ID " + applicationId + " not found",
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

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

        return new ApplicationResponseDto(
                entity.id(),
                entity.jobId(),
                entity.candidateId(),
                entity.status(),
                entity.coverLetter(),
                entity.appliedAt(),
                entity.jobTitle(),
                entity.companyName(),
                parseCandidateSnapshot(entity.candidateSnapshot()),
                parseResumeSnapshot(entity.resumeSnapshot()),
                history
        );
    }
}
