package com.careerconnect.jobs.controller;

import com.careerconnect.common.pagination.PageRequestDto;
import com.careerconnect.common.pagination.PageResult;
import com.careerconnect.common.response.ApiResponse;
import com.careerconnect.jobs.dto.JobDetailDto;
import com.careerconnect.jobs.dto.JobSummaryDto;
import com.careerconnect.jobs.dto.RecruiterJobCreateRequest;
import com.careerconnect.jobs.dto.RecruiterJobUpdateRequest;
import com.careerconnect.jobs.service.RecruiterJobLifecycleService;
import com.careerconnect.jobs.service.RecruiterJobMutationService;
import com.careerconnect.jobs.service.RecruiterJobQueryService;
import com.careerconnect.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recruiter/jobs")
@PreAuthorize("hasRole('RECRUITER')")
public class RecruiterJobLifecycleController {

    private final RecruiterJobLifecycleService recruiterJobLifecycleService;
    private final RecruiterJobQueryService recruiterJobQueryService;
    private final RecruiterJobMutationService recruiterJobMutationService;

    public RecruiterJobLifecycleController(
            RecruiterJobLifecycleService recruiterJobLifecycleService,
            RecruiterJobQueryService recruiterJobQueryService,
            RecruiterJobMutationService recruiterJobMutationService) {
        this.recruiterJobLifecycleService = recruiterJobLifecycleService;
        this.recruiterJobQueryService = recruiterJobQueryService;
        this.recruiterJobMutationService = recruiterJobMutationService;
    }

    /**
     * Creates a new job in DRAFT status belonging strictly to the authenticated recruiter's company.
     * Accessible only by ROLE_RECRUITER.
     * Enforces:
     * - Server-side authoritative company association
     * - Non-past application deadline
     * - Required location for non-remote work modes
     * - At least one valid required skill ID
     * - Initial status is DRAFT
     */
    @PostMapping
    public ResponseEntity<ApiResponse<JobDetailDto>> createJob(
            @Valid @RequestBody RecruiterJobCreateRequest request) {
        Long recruiterId = SecurityUtils.getRequiredRecruiterId();
        JobDetailDto created = recruiterJobMutationService.createJob(recruiterId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    /**
     * Retrieves a paginated list of jobs belonging strictly to the authenticated recruiter's company.
     * Accessible only by ROLE_RECRUITER.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<JobSummaryDto>>> getJobs(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Long recruiterId = SecurityUtils.getRequiredRecruiterId();
        PageRequestDto pageRequest = new PageRequestDto(page, size);
        PageResult<JobSummaryDto> result = recruiterJobQueryService.getRecruiterJobs(recruiterId, pageRequest);
        return ResponseEntity.ok(ApiResponse.success(result.getItems(), result.getPagination()));
    }

    /**
     * Retrieves detailed information for a specific job belonging to the authenticated recruiter's company.
     * Accessible only by ROLE_RECRUITER.
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobDetailDto>> getJobDetail(@PathVariable("jobId") Long jobId) {
        Long recruiterId = SecurityUtils.getRequiredRecruiterId();
        JobDetailDto job = recruiterJobQueryService.getRecruiterJobDetail(recruiterId, jobId);
        return ResponseEntity.ok(ApiResponse.success(job));
    }

    /**
     * Publishes a DRAFT job to ACTIVE status.
     * Transition: DRAFT -> ACTIVE
     */
    @PatchMapping("/{jobId}/publish")
    public ResponseEntity<ApiResponse<JobDetailDto>> publishJob(@PathVariable("jobId") Long jobId) {
        Long recruiterId = SecurityUtils.getRequiredRecruiterId();
        JobDetailDto job = recruiterJobLifecycleService.publishJob(recruiterId, jobId);
        return ResponseEntity.ok(ApiResponse.success(job));
    }

    /**
     * Closes an ACTIVE job to CLOSED status.
     * Transition: ACTIVE -> CLOSED
     */
    @PatchMapping("/{jobId}/close")
    public ResponseEntity<ApiResponse<JobDetailDto>> closeJob(@PathVariable("jobId") Long jobId) {
        Long recruiterId = SecurityUtils.getRequiredRecruiterId();
        JobDetailDto job = recruiterJobLifecycleService.closeJob(recruiterId, jobId);
        return ResponseEntity.ok(ApiResponse.success(job));
    }

    /**
     * Updates an existing recruiter-owned job.
     * Accessible only by ROLE_RECRUITER.
     * Enforces:
     * - Recruiter company ownership
     * - Status rules: DRAFT and ACTIVE jobs are editable; CLOSED and ARCHIVED jobs are not editable.
     * - Invariant: if applications exist, fundamental requirements cannot be modified.
     * - Atomic update of job fields and required skill associations.
     */
    @PutMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobDetailDto>> updateJob(
            @PathVariable("jobId") Long jobId,
            @Valid @RequestBody RecruiterJobUpdateRequest request) {
        Long recruiterId = SecurityUtils.getRequiredRecruiterId();
        JobDetailDto job = recruiterJobMutationService.updateJob(recruiterId, jobId, request);
        return ResponseEntity.ok(ApiResponse.success(job));
    }

    /**
     * Soft-deletes a recruiter-owned job.
     * Accessible only by ROLE_RECRUITER.
     * Enforces:
     * - Recruiter company ownership
     * - Non-active zero-application rule: jobs with applications CANNOT be deleted (returns 422).
     * - Active jobs must be closed before deletion (returns 422).
     * - Sets deleted_at = CURRENT_TIMESTAMP(3) preserving application records and candidate data.
     */
    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> deleteJob(@PathVariable("jobId") Long jobId) {
        Long recruiterId = SecurityUtils.getRequiredRecruiterId();
        recruiterJobMutationService.deleteJob(recruiterId, jobId);
        return ResponseEntity.noContent().build();
    }
}
