package com.careerconnect.admin.controller;

import com.careerconnect.admin.dto.AdminApplicationDetailDto;
import com.careerconnect.admin.dto.AdminApplicationSummaryDto;
import com.careerconnect.admin.service.AdminQueryService;
import com.careerconnect.application.domain.ApplicationStatus;
import com.careerconnect.candidate.dto.ResumeResponse;
import com.careerconnect.common.pagination.PageRequestDto;
import com.careerconnect.common.pagination.PageResult;
import com.careerconnect.common.response.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/applications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminApplicationController {

    private final AdminQueryService adminQueryService;

    public AdminApplicationController(AdminQueryService adminQueryService) {
        this.adminQueryService = adminQueryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminApplicationSummaryDto>>> getApplications(
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) Long candidateId,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        PageRequestDto pageRequest = new PageRequestDto(page, size);
        PageResult<AdminApplicationSummaryDto> result = adminQueryService.getApplications(
                jobId, candidateId, companyId, status, pageRequest);

        return ResponseEntity.ok(ApiResponse.success(result.getItems(), result.getPagination()));
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<ApiResponse<AdminApplicationDetailDto>> getApplicationDetail(
            @PathVariable Long applicationId) {

        AdminApplicationDetailDto detail = adminQueryService.getApplicationDetail(applicationId);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @GetMapping({"/{applicationId}/resume", "/{applicationId}/resume/download"})
    public ResponseEntity<?> getApplicationResume(
            @PathVariable Long applicationId,
            @RequestParam(value = "metadata", required = false, defaultValue = "false") boolean metadataOnly,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String acceptHeader) {

        AdminQueryService.AdminResumeDownloadDto resume = adminQueryService.getApplicationResume(applicationId);

        if (metadataOnly || (acceptHeader != null && acceptHeader.contains(MediaType.APPLICATION_JSON_VALUE)
                && !acceptHeader.contains(MediaType.APPLICATION_PDF_VALUE) && !acceptHeader.contains("*/*"))) {
            ResumeResponse response = new ResumeResponse(
                    resume.id(),
                    resume.filename(),
                    resume.contentType(),
                    resume.sizeBytes(),
                    resume.isCurrent(),
                    resume.createdAt(),
                    resume.updatedAt()
            );
            return ResponseEntity.ok(ApiResponse.success(response, "Resume metadata retrieved successfully"));
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resume.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resume.filename() + "\"")
                .contentLength(resume.sizeBytes())
                .body(resume.content());
    }
}
