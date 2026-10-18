package com.careerconnect.admin.controller;

import com.careerconnect.admin.dto.AdminJobDto;
import com.careerconnect.admin.service.AdminMutationService;
import com.careerconnect.admin.service.AdminQueryService;
import com.careerconnect.common.pagination.PageRequestDto;
import com.careerconnect.common.pagination.PageResult;
import com.careerconnect.common.response.ApiResponse;
import com.careerconnect.jobs.model.JobStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/jobs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminJobController {

    private final AdminQueryService adminQueryService;
    private final AdminMutationService adminMutationService;

    public AdminJobController(
            AdminQueryService adminQueryService,
            AdminMutationService adminMutationService) {
        this.adminQueryService = adminQueryService;
        this.adminMutationService = adminMutationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminJobDto>>> getJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        PageRequestDto pageRequest = new PageRequestDto(page, size);
        PageResult<AdminJobDto> result = adminQueryService.getJobs(status, companyId, q, pageRequest);

        return ResponseEntity.ok(ApiResponse.success(result.getItems(), result.getPagination()));
    }

    @PatchMapping("/{jobId}/archive")
    public ResponseEntity<ApiResponse<AdminJobDto>> archiveJob(@PathVariable Long jobId) {
        AdminJobDto archived = adminMutationService.archiveJob(jobId);
        return ResponseEntity.ok(ApiResponse.success(archived));
    }

    @PatchMapping("/{jobId}/restore")
    public ResponseEntity<ApiResponse<AdminJobDto>> restoreJob(@PathVariable Long jobId) {
        AdminJobDto restored = adminMutationService.restoreJob(jobId);
        return ResponseEntity.ok(ApiResponse.success(restored));
    }
}
