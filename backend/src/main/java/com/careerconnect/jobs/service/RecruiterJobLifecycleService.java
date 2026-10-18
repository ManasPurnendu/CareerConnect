package com.careerconnect.jobs.service;

import com.careerconnect.auth.repository.RecruiterAuthRepository;
import com.careerconnect.common.exception.BusinessRuleException;
import com.careerconnect.common.exception.ErrorCode;
import com.careerconnect.common.exception.ResourceNotFoundException;
import com.careerconnect.jobs.dto.JobDetailDto;
import com.careerconnect.jobs.model.JobStatus;
import com.careerconnect.jobs.model.WorkMode;
import com.careerconnect.jobs.repository.JobRepository;
import com.careerconnect.jobs.repository.JobRepository.JobLifecycleEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class RecruiterJobLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(RecruiterJobLifecycleService.class);

    private final JobRepository jobRepository;
    private final RecruiterAuthRepository recruiterAuthRepository;
    private final com.careerconnect.notification.service.NotificationService notificationService;

    public RecruiterJobLifecycleService(
            JobRepository jobRepository,
            RecruiterAuthRepository recruiterAuthRepository,
            com.careerconnect.notification.service.NotificationService notificationService) {
        this.jobRepository = jobRepository;
        this.recruiterAuthRepository = recruiterAuthRepository;
        this.notificationService = notificationService;
    }

    /**
     * Publishes a DRAFT job to ACTIVE status.
     * Enforces company ownership, current DRAFT status, valid fields, deadline rules, and required skills.
     */
    @Transactional
    public JobDetailDto publishJob(Long recruiterId, Long jobId) {
        Long companyId = resolveAuthoritativeCompanyId(recruiterId);

        JobLifecycleEntity job = jobRepository.findJobByIdForRecruiter(jobId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job with ID " + jobId + " not found or not owned by your company.",
                        ErrorCode.JOB_NOT_FOUND
                ));

        // Status transition validation: DRAFT -> ACTIVE
        validateTransition(job.status(), JobStatus.ACTIVE);

        // Validate publishing requirements
        validatePublishingRequirements(job);

        // Perform status update to ACTIVE
        boolean updated = jobRepository.updateJobStatus(jobId, companyId, JobStatus.ACTIVE);
        if (!updated) {
            throw new IllegalStateException("Failed to update job status to ACTIVE for job ID: " + jobId);
        }

        log.info("Recruiter {} published job {} for company {}", recruiterId, jobId, companyId);

        return jobRepository.findJobDetailByIdAndCompanyId(jobId, companyId)
                .orElseThrow(() -> new IllegalStateException("Published job could not be retrieved: ID " + jobId));
    }

    /**
     * Closes an ACTIVE job to CLOSED status.
     * Enforces company ownership and current ACTIVE status.
     */
    @Transactional
    public JobDetailDto closeJob(Long recruiterId, Long jobId) {
        Long companyId = resolveAuthoritativeCompanyId(recruiterId);

        JobLifecycleEntity job = jobRepository.findJobByIdForRecruiter(jobId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job with ID " + jobId + " not found or not owned by your company.",
                        ErrorCode.JOB_NOT_FOUND
                ));

        // Status transition validation: ACTIVE -> CLOSED
        validateTransition(job.status(), JobStatus.CLOSED);

        // Perform status update to CLOSED
        boolean updated = jobRepository.updateJobStatus(jobId, companyId, JobStatus.CLOSED);
        if (!updated) {
            throw new IllegalStateException("Failed to update job status to CLOSED for job ID: " + jobId);
        }

        // Notify affected applicants of job closure
        notificationService.notifyJobClosure(jobId, job.title());

        log.info("Recruiter {} closed job {} for company {}", recruiterId, jobId, companyId);

        return jobRepository.findJobDetailByIdAndCompanyId(jobId, companyId)
                .orElseThrow(() -> new IllegalStateException("Closed job could not be retrieved: ID " + jobId));
    }

    /**
     * Centralized transition validator.
     * Throws BusinessRuleException(INVALID_STATUS_TRANSITION) if the requested transition is illegal.
     */
    public void validateTransition(JobStatus currentStatus, JobStatus requestedStatus) {
        if (currentStatus == null || !currentStatus.isValidTransition(requestedStatus)) {
            throw new BusinessRuleException(
                    "Invalid job status transition from " + currentStatus + " to " + requestedStatus,
                    ErrorCode.INVALID_STATUS_TRANSITION
            );
        }
    }

    /**
     * Enforces publishing requirements:
     * - Title is present and not blank
     * - Description is present and not blank
     * - Job type is valid
     * - Experience level is valid
     * - Work mode is valid (and location present if not REMOTE)
     * - Application deadline exists and is not in the past
     * - At least one required skill exists
     * - Salary rules remain valid if provided
     */
    private void validatePublishingRequirements(JobLifecycleEntity job) {
        if (job.title() == null || job.title().trim().isEmpty()) {
            throw new BusinessRuleException("Job title is required to publish", ErrorCode.VALIDATION_FAILED);
        }

        if (job.description() == null || job.description().trim().isEmpty()) {
            throw new BusinessRuleException("Job description is required to publish", ErrorCode.VALIDATION_FAILED);
        }

        if (job.jobType() == null) {
            throw new BusinessRuleException("Job type is required to publish", ErrorCode.VALIDATION_FAILED);
        }

        if (job.experienceLevel() == null) {
            throw new BusinessRuleException("Experience level is required to publish", ErrorCode.VALIDATION_FAILED);
        }

        if (job.workMode() == null) {
            throw new BusinessRuleException("Work mode is required to publish", ErrorCode.VALIDATION_FAILED);
        }

        if (job.workMode() != WorkMode.REMOTE && (job.location() == null || job.location().trim().isEmpty())) {
            throw new BusinessRuleException("Location is required for non-remote jobs", ErrorCode.VALIDATION_FAILED);
        }

        // Deadline validation
        if (job.applicationDeadline() == null) {
            throw new BusinessRuleException("Application deadline is required to publish", ErrorCode.JOB_DEADLINE_INVALID);
        }

        if (job.applicationDeadline().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("Job application deadline has already passed", ErrorCode.JOB_DEADLINE_PASSED);
        }

        // Required skills validation
        List<String> requiredSkills = jobRepository.findRequiredSkillNamesByJobId(job.id());
        if (requiredSkills == null || requiredSkills.isEmpty()) {
            throw new BusinessRuleException("At least one required skill is required to publish", ErrorCode.JOB_SKILL_REQUIRED);
        }

        // Salary range validation if provided
        if (job.salaryMinLpa() != null && job.salaryMinLpa().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Minimum salary must be greater than zero", ErrorCode.VALIDATION_FAILED);
        }

        if (job.salaryMinLpa() != null && job.salaryMaxLpa() != null && job.salaryMaxLpa().compareTo(job.salaryMinLpa()) < 0) {
            throw new BusinessRuleException("Maximum salary cannot be less than minimum salary", ErrorCode.VALIDATION_FAILED);
        }
    }

    /**
     * Resolves recruiter's company from the database (stale JWT protection).
     */
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
