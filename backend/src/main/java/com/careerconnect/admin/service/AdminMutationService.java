package com.careerconnect.admin.service;

import com.careerconnect.admin.audit.service.AuditLogService;
import com.careerconnect.admin.dto.AdminCompanyDto;
import com.careerconnect.admin.dto.AdminJobDto;
import com.careerconnect.admin.dto.AdminUserDto;
import com.careerconnect.admin.repository.AdminMutationRepository;
import com.careerconnect.admin.repository.AdminQueryRepository;
import com.careerconnect.common.exception.BusinessRuleException;
import com.careerconnect.common.exception.ErrorCode;
import com.careerconnect.common.exception.ResourceNotFoundException;
import com.careerconnect.jobs.model.JobStatus;
import com.careerconnect.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service orchestrating administrative moderation mutations with atomic audit logging.
 * Guarantees server-authoritative ADMIN security, self-deactivation protection, explicit state transition
 * enforcement, and atomic rollback if either mutation or audit persistence fails.
 */
@Service
public class AdminMutationService {

    private static final Logger log = LoggerFactory.getLogger(AdminMutationService.class);

    private final AdminMutationRepository adminMutationRepository;
    private final AdminQueryRepository adminQueryRepository;
    private final AuditLogService auditLogService;

    public AdminMutationService(
            AdminMutationRepository adminMutationRepository,
            AdminQueryRepository adminQueryRepository,
            AuditLogService auditLogService) {
        this.adminMutationRepository = adminMutationRepository;
        this.adminQueryRepository = adminQueryRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Moderates user active status (deactivate or reactivate).
     * Enforces self-deactivation protection and records audit log in the same transaction.
     *
     * @param targetUserId Target user ID to moderate
     * @param newActive    Desired active status
     * @return Updated AdminUserDto
     */
    @Transactional
    public AdminUserDto updateUserStatus(Long targetUserId, boolean newActive) {
        Long adminUserId = SecurityUtils.getRequiredAdminUserId();

        // Self-deactivation protection (V1 invariant)
        if (adminUserId.equals(targetUserId) && !newActive) {
            log.warn("Self-deactivation attempt blocked for admin userId={}", adminUserId);
            throw new BusinessRuleException(
                    "Administrators cannot deactivate their own account",
                    ErrorCode.INVALID_STATUS_TRANSITION
            );
        }

        AdminMutationRepository.UserStatusRecord userRecord = adminMutationRepository.findUserStatusById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User with ID " + targetUserId + " not found"));

        // Reject same-state no-op mutations
        if (userRecord.active() == newActive) {
            throw new BusinessRuleException(
                    "User is already " + (newActive ? "active" : "inactive"),
                    ErrorCode.INVALID_STATUS_TRANSITION
            );
        }

        int updated = adminMutationRepository.updateUserActiveStatus(targetUserId, newActive);
        if (updated == 0) {
            throw new ResourceNotFoundException("User with ID " + targetUserId + " not found");
        }

        // Record atomic audit log
        String action = newActive ? "USER_REACTIVATED" : "USER_DEACTIVATED";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("previousIsActive", userRecord.active());
        metadata.put("newIsActive", newActive);
        metadata.put("previousState", userRecord.active() ? "ACTIVE" : "INACTIVE");
        metadata.put("newState", newActive ? "ACTIVE" : "INACTIVE");

        auditLogService.recordEvent(action, "USER", targetUserId, metadata);
        log.info("Admin userId={} updated user userId={} active status to {}", adminUserId, targetUserId, newActive);

        return adminQueryRepository.findUserById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User with ID " + targetUserId + " not found"));
    }

    /**
     * Moderates company active status (deactivate or reactivate).
     * Records audit log in the same transaction.
     *
     * @param companyId Target company ID
     * @param newActive Desired active status
     * @return Updated AdminCompanyDto
     */
    @Transactional
    public AdminCompanyDto updateCompanyStatus(Long companyId, boolean newActive) {
        Long adminUserId = SecurityUtils.getRequiredAdminUserId();

        AdminMutationRepository.CompanyStatusRecord companyRecord = adminMutationRepository.findCompanyStatusById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company with ID " + companyId + " not found"));

        // Reject same-state no-op mutations
        if (companyRecord.active() == newActive) {
            throw new BusinessRuleException(
                    "Company is already " + (newActive ? "active" : "inactive"),
                    ErrorCode.INVALID_STATUS_TRANSITION
            );
        }

        int updated = adminMutationRepository.updateCompanyActiveStatus(companyId, newActive);
        if (updated == 0) {
            throw new ResourceNotFoundException("Company with ID " + companyId + " not found");
        }

        // Record atomic audit log
        String action = newActive ? "COMPANY_REACTIVATED" : "COMPANY_DEACTIVATED";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("previousIsActive", companyRecord.active());
        metadata.put("newIsActive", newActive);
        metadata.put("previousState", companyRecord.active() ? "ACTIVE" : "INACTIVE");
        metadata.put("newState", newActive ? "ACTIVE" : "INACTIVE");

        auditLogService.recordEvent(action, "COMPANY", companyId, metadata);
        log.info("Admin userId={} updated company companyId={} active status to {}", adminUserId, companyId, newActive);

        return adminQueryRepository.findCompanyById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company with ID " + companyId + " not found"));
    }

    /**
     * Archives a job from DRAFT, ACTIVE, or CLOSED.
     * Records audit log in the same transaction.
     *
     * @param jobId Target job ID
     * @return Updated AdminJobDto
     */
    @Transactional
    public AdminJobDto archiveJob(Long jobId) {
        Long adminUserId = SecurityUtils.getRequiredAdminUserId();

        AdminMutationRepository.JobStatusRecord jobRecord = adminMutationRepository.findJobStatusById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job with ID " + jobId + " not found"));

        // Reject ARCHIVED -> ARCHIVED
        if (jobRecord.status() == JobStatus.ARCHIVED) {
            throw new BusinessRuleException(
                    "Job is already archived",
                    ErrorCode.INVALID_STATUS_TRANSITION
            );
        }

        int updated = adminMutationRepository.archiveJob(jobId);
        if (updated == 0) {
            throw new BusinessRuleException(
                    "Job could not be archived from status: " + jobRecord.status(),
                    ErrorCode.INVALID_STATUS_TRANSITION
            );
        }

        // Record atomic audit log
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("previousStatus", jobRecord.status().name());
        metadata.put("newStatus", JobStatus.ARCHIVED.name());

        auditLogService.recordEvent("JOB_ARCHIVED", "JOB", jobId, metadata);
        log.info("Admin userId={} archived job jobId={} (from {})", adminUserId, jobId, jobRecord.status());

        return adminQueryRepository.findJobById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job with ID " + jobId + " not found"));
    }

    /**
     * Restores an ARCHIVED job strictly to CLOSED per CC-MASTER-001 specification.
     * Records audit log in the same transaction.
     *
     * @param jobId Target job ID
     * @return Updated AdminJobDto
     */
    @Transactional
    public AdminJobDto restoreJob(Long jobId) {
        Long adminUserId = SecurityUtils.getRequiredAdminUserId();

        AdminMutationRepository.JobStatusRecord jobRecord = adminMutationRepository.findJobStatusById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job with ID " + jobId + " not found"));

        // Only ARCHIVED jobs can be restored (target state: CLOSED)
        if (jobRecord.status() != JobStatus.ARCHIVED) {
            throw new BusinessRuleException(
                    "Only ARCHIVED jobs can be restored (target state: CLOSED). Current status: " + jobRecord.status(),
                    ErrorCode.INVALID_STATUS_TRANSITION
            );
        }

        int updated = adminMutationRepository.restoreJob(jobId);
        if (updated == 0) {
            throw new BusinessRuleException(
                    "Job could not be restored from status: " + jobRecord.status(),
                    ErrorCode.INVALID_STATUS_TRANSITION
            );
        }

        // Record atomic audit log
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("previousStatus", JobStatus.ARCHIVED.name());
        metadata.put("newStatus", JobStatus.CLOSED.name());

        auditLogService.recordEvent("JOB_RESTORED", "JOB", jobId, metadata);
        log.info("Admin userId={} restored job jobId={} (ARCHIVED -> CLOSED)", adminUserId, jobId);

        return adminQueryRepository.findJobById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job with ID " + jobId + " not found"));
    }
}
