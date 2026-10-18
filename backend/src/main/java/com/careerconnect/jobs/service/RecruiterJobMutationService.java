package com.careerconnect.jobs.service;

import com.careerconnect.auth.repository.RecruiterAuthRepository;
import com.careerconnect.common.exception.BusinessRuleException;
import com.careerconnect.common.exception.ErrorCode;
import com.careerconnect.common.exception.ResourceNotFoundException;
import com.careerconnect.jobs.dto.JobDetailDto;
import com.careerconnect.jobs.dto.RecruiterJobCreateRequest;
import com.careerconnect.jobs.dto.RecruiterJobUpdateRequest;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class RecruiterJobMutationService {

    private static final Logger log = LoggerFactory.getLogger(RecruiterJobMutationService.class);

    private final JobRepository jobRepository;
    private final RecruiterAuthRepository recruiterAuthRepository;

    public RecruiterJobMutationService(
            JobRepository jobRepository,
            RecruiterAuthRepository recruiterAuthRepository) {
        this.jobRepository = jobRepository;
        this.recruiterAuthRepository = recruiterAuthRepository;
    }

    /**
     * Creates a new recruiter-owned job in DRAFT status.
     * Enforces:
     * - Authenticated recruiter identity
     * - Server-side authoritative company ownership
     * - Initial status is strictly DRAFT
     * - Non-past application deadline
     * - Location required if work mode is not REMOTE
     * - At least one valid required skill ID
     * - Valid salary bounds (min > 0, max >= min)
     * - Atomic persistence of job fields and required skill associations
     */
    @Transactional
    public JobDetailDto createJob(Long recruiterId, RecruiterJobCreateRequest request) {
        Long companyId = resolveAuthoritativeCompanyId(recruiterId);

        validateJobCreateRequest(request);

        Long jobId = jobRepository.createJob(
                companyId,
                request.title(),
                request.description(),
                request.responsibilities(),
                request.requirements(),
                request.jobType(),
                request.experienceLevel(),
                request.workMode(),
                request.location(),
                request.salaryMinLpa(),
                request.salaryMaxLpa(),
                request.salaryCurrency(),
                request.applicationDeadline()
        );

        jobRepository.replaceJobSkills(jobId, companyId, request.requiredSkillIds());

        log.info("Recruiter {} successfully created job {} for company {}", recruiterId, jobId, companyId);

        return jobRepository.findJobDetailByIdAndCompanyId(jobId, companyId)
                .orElseThrow(() -> new IllegalStateException("Created job could not be queried: ID " + jobId));
    }

    /**
     * Updates an existing recruiter-owned job.
     * Enforces:
     * - Recruiter company ownership
     * - Status rules: DRAFT and ACTIVE jobs are editable; CLOSED jobs are not editable; ARCHIVED jobs are prohibited.
     * - Application-dependent invariant: if the job already has applications, fundamental requirements
     *   (title, jobType, experienceLevel, workMode, requiredSkills, salary bounds) CANNOT be changed.
     * - Atomic update of job fields and required skill associations.
     */
    @Transactional
    public JobDetailDto updateJob(Long recruiterId, Long jobId, RecruiterJobUpdateRequest request) {
        Long companyId = resolveAuthoritativeCompanyId(recruiterId);

        JobLifecycleEntity job = jobRepository.findJobByIdForRecruiter(jobId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job with ID " + jobId + " not found or not owned by your company.",
                        ErrorCode.JOB_NOT_FOUND
                ));

        // State check: DRAFT and ACTIVE are editable. CLOSED and ARCHIVED are not editable.
        if (job.status() == JobStatus.CLOSED) {
            throw new BusinessRuleException(
                    "Closed jobs cannot be modified.",
                    ErrorCode.INVALID_STATUS_TRANSITION
            );
        }
        if (job.status() == JobStatus.ARCHIVED) {
            throw new BusinessRuleException(
                    "Archived jobs cannot be modified by recruiters.",
                    ErrorCode.INVALID_STATUS_TRANSITION
            );
        }

        // Domain validation
        validateJobUpdateRequest(request);

        // Check applications existence
        boolean hasApplications = jobRepository.hasApplications(jobId);
        if (hasApplications) {
            // Validate that fundamental requirements have NOT been altered
            validateFundamentalRequirementsUnchanged(job, request);
        }

        // Perform transactional update of job fields
        boolean updated = jobRepository.updateJobFields(
                jobId,
                companyId,
                request.title(),
                request.description(),
                request.responsibilities(),
                request.requirements(),
                request.jobType(),
                request.experienceLevel(),
                request.workMode(),
                request.location(),
                request.salaryMinLpa(),
                request.salaryMaxLpa(),
                request.salaryCurrency(),
                request.applicationDeadline()
        );

        if (!updated) {
            throw new IllegalStateException("Failed to update job fields for job ID: " + jobId);
        }

        // Replace required skills associations atomically with company ownership verification
        jobRepository.replaceJobSkills(jobId, companyId, request.requiredSkillIds());

        log.info("Recruiter {} successfully updated job {} for company {}", recruiterId, jobId, companyId);

        return jobRepository.findJobDetailByIdAndCompanyId(jobId, companyId)
                .orElseThrow(() -> new IllegalStateException("Updated job could not be re-queried: ID " + jobId));
    }

    /**
     * Soft-deletes a recruiter-owned job.
     * Enforces:
     * - Recruiter company ownership
     * - Zero-application rule: jobs with applications CANNOT be deleted (returns 422 BUSINESS_RULE_EXCEPTION).
     * - Non-active requirement: per CC-MASTER-001 v1.2, only non-active (or draft/closed) zero-application jobs
     *   can be deleted. If active, reject with 422 BUSINESS_RULE_EXCEPTION instructing to close first.
     */
    @Transactional
    public void deleteJob(Long recruiterId, Long jobId) {
        Long companyId = resolveAuthoritativeCompanyId(recruiterId);

        JobLifecycleEntity job = jobRepository.findJobByIdForRecruiter(jobId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job with ID " + jobId + " not found or not owned by your company.",
                        ErrorCode.JOB_NOT_FOUND
                ));

        // Archived jobs cannot be deleted by recruiter
        if (job.status() == JobStatus.ARCHIVED) {
            throw new BusinessRuleException(
                    "Archived jobs cannot be deleted by recruiters.",
                    ErrorCode.INVALID_STATUS_TRANSITION
            );
        }

        // Check if applications exist
        boolean hasApplications = jobRepository.hasApplications(jobId);
        if (hasApplications) {
            throw new BusinessRuleException(
                    "Jobs with applications cannot be deleted. Close the job instead.",
                    ErrorCode.INVALID_STATUS_TRANSITION
            );
        }

        // CC-MASTER-001 v1.2 rule: "Recruiter soft-deletes only non-active zero-application jobs; jobs with applications only close."
        if (job.status() == JobStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Active jobs cannot be deleted directly. Close the job before deleting.",
                    ErrorCode.INVALID_STATUS_TRANSITION
            );
        }

        boolean deleted = jobRepository.softDeleteJob(jobId, companyId);
        if (!deleted) {
            throw new IllegalStateException("Failed to soft-delete job with ID: " + jobId);
        }

        log.info("Recruiter {} successfully soft-deleted job {} for company {}", recruiterId, jobId, companyId);
    }

    private void validateJobCreateRequest(RecruiterJobCreateRequest req) {
        if (req.workMode() != WorkMode.REMOTE && (req.location() == null || req.location().trim().isEmpty())) {
            throw new BusinessRuleException("Location is required for non-remote jobs", ErrorCode.VALIDATION_FAILED);
        }

        if (req.applicationDeadline() != null && req.applicationDeadline().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("Job application deadline cannot be in the past", ErrorCode.JOB_DEADLINE_PASSED);
        }

        if (req.requiredSkillIds() == null || req.requiredSkillIds().isEmpty()) {
            throw new BusinessRuleException("At least one required skill is required", ErrorCode.JOB_SKILL_REQUIRED);
        }

        List<Long> distinctSkillIds = req.requiredSkillIds().stream().distinct().toList();
        if (jobRepository.countExistingSkillIds(distinctSkillIds) != distinctSkillIds.size()) {
            throw new BusinessRuleException("One or more required skills do not exist", ErrorCode.VALIDATION_FAILED);
        }

        if (req.salaryMinLpa() != null && req.salaryMinLpa().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Minimum salary must be greater than zero", ErrorCode.VALIDATION_FAILED);
        }

        if (req.salaryMinLpa() != null && req.salaryMaxLpa() != null && req.salaryMaxLpa().compareTo(req.salaryMinLpa()) < 0) {
            throw new BusinessRuleException("Maximum salary cannot be less than minimum salary", ErrorCode.VALIDATION_FAILED);
        }
    }

    private void validateJobUpdateRequest(RecruiterJobUpdateRequest req) {
        if (req.workMode() != WorkMode.REMOTE && (req.location() == null || req.location().trim().isEmpty())) {
            throw new BusinessRuleException("Location is required for non-remote jobs", ErrorCode.VALIDATION_FAILED);
        }

        if (req.applicationDeadline() != null && req.applicationDeadline().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("Job application deadline cannot be in the past", ErrorCode.JOB_DEADLINE_PASSED);
        }

        if (req.requiredSkillIds() == null || req.requiredSkillIds().isEmpty()) {
            throw new BusinessRuleException("At least one required skill is required", ErrorCode.JOB_SKILL_REQUIRED);
        }

        List<Long> distinctSkillIds = req.requiredSkillIds().stream().distinct().toList();
        if (jobRepository.countExistingSkillIds(distinctSkillIds) != distinctSkillIds.size()) {
            throw new BusinessRuleException("One or more required skills do not exist", ErrorCode.VALIDATION_FAILED);
        }

        if (req.salaryMinLpa() != null && req.salaryMinLpa().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Minimum salary must be greater than zero", ErrorCode.VALIDATION_FAILED);
        }

        if (req.salaryMinLpa() != null && req.salaryMaxLpa() != null && req.salaryMaxLpa().compareTo(req.salaryMinLpa()) < 0) {
            throw new BusinessRuleException("Maximum salary cannot be less than minimum salary", ErrorCode.VALIDATION_FAILED);
        }
    }

    /**
     * Enforces fundamental requirements immutability once applications exist.
     * Fundamental requirements:
     * - title
     * - jobType
     * - experienceLevel
     * - workMode
     * - requiredSkillIds
     * - salaryMinLpa, salaryMaxLpa, salaryCurrency
     * - core requirements / eligibility criteria
     */
    private void validateFundamentalRequirementsUnchanged(JobLifecycleEntity existing, RecruiterJobUpdateRequest req) {
        boolean titleChanged = !Objects.equals(existing.title(), req.title());
        boolean jobTypeChanged = existing.jobType() != req.jobType();
        boolean expLevelChanged = existing.experienceLevel() != req.experienceLevel();
        boolean workModeChanged = existing.workMode() != req.workMode();

        boolean salaryMinChanged = (existing.salaryMinLpa() == null && req.salaryMinLpa() != null)
                || (existing.salaryMinLpa() != null && req.salaryMinLpa() == null)
                || (existing.salaryMinLpa() != null && req.salaryMinLpa() != null && existing.salaryMinLpa().compareTo(req.salaryMinLpa()) != 0);

        boolean salaryMaxChanged = (existing.salaryMaxLpa() == null && req.salaryMaxLpa() != null)
                || (existing.salaryMaxLpa() != null && req.salaryMaxLpa() == null)
                || (existing.salaryMaxLpa() != null && req.salaryMaxLpa() != null && existing.salaryMaxLpa().compareTo(req.salaryMaxLpa()) != 0);

        String existingCurrency = existing.salaryCurrency() != null ? existing.salaryCurrency().trim() : "INR";
        String newCurrency = req.salaryCurrency() != null ? req.salaryCurrency().trim() : "INR";
        boolean currencyChanged = !existingCurrency.equalsIgnoreCase(newCurrency);

        String existingReqs = existing.requirements() != null ? existing.requirements().trim() : "";
        String newReqs = req.requirements() != null ? req.requirements().trim() : "";
        boolean reqsChanged = !existingReqs.equals(newReqs);

        // Check required skills
        List<Long> currentSkillIds = jobRepository.findRequiredSkillIdsByJobId(existing.id());
        Set<Long> currentSet = new HashSet<>(currentSkillIds);
        Set<Long> newSet = new HashSet<>(req.requiredSkillIds());
        boolean skillsChanged = !currentSet.equals(newSet);

        if (titleChanged || jobTypeChanged || expLevelChanged || workModeChanged
                || salaryMinChanged || salaryMaxChanged || currencyChanged || reqsChanged || skillsChanged) {
            throw new BusinessRuleException(
                    "Fundamental job requirements (title, job type, experience level, work mode, salary, core requirements, required skills) cannot be changed once applications exist.",
                    ErrorCode.INVALID_STATUS_TRANSITION
            );
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
