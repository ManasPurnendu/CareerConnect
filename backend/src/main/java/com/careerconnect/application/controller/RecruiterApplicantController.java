package com.careerconnect.application.controller;

import com.careerconnect.application.domain.ApplicationStatus;
import com.careerconnect.application.dto.ApplicantDetailDto;
import com.careerconnect.application.dto.ApplicantSummaryDto;
import com.careerconnect.application.dto.UpdateApplicationStatusRequest;
import com.careerconnect.application.service.RecruiterApplicantService;
import com.careerconnect.common.pagination.PageRequestDto;
import com.careerconnect.common.pagination.PageResult;
import com.careerconnect.common.response.ApiResponse;
import com.careerconnect.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recruiter")
@PreAuthorize("hasRole('RECRUITER')")
public class RecruiterApplicantController {

    private final RecruiterApplicantService recruiterApplicantService;

    public RecruiterApplicantController(RecruiterApplicantService recruiterApplicantService) {
        this.recruiterApplicantService = recruiterApplicantService;
    }

    @GetMapping("/jobs/{jobId}/applications")
    public ResponseEntity<ApiResponse<List<ApplicantSummaryDto>>> getJobApplicants(
            @PathVariable Long jobId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        Long recruiterId = SecurityUtils.getRequiredRecruiterId();
        PageRequestDto pageRequest = new PageRequestDto(page, size);

        PageResult<ApplicantSummaryDto> result = recruiterApplicantService.getJobApplicants(
                recruiterId, jobId, status, pageRequest);

        return ResponseEntity.ok(ApiResponse.success(result.getItems(), result.getPagination()));
    }

    @GetMapping("/applications/{applicationId}")
    public ResponseEntity<ApiResponse<ApplicantDetailDto>> getApplicantDetail(
            @PathVariable Long applicationId) {

        Long recruiterId = SecurityUtils.getRequiredRecruiterId();
        ApplicantDetailDto detail = recruiterApplicantService.getApplicantDetail(recruiterId, applicationId);

        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @PatchMapping("/applications/{applicationId}/status")
    public ResponseEntity<ApiResponse<ApplicantDetailDto>> updateApplicationStatus(
            @PathVariable Long applicationId,
            @Valid @RequestBody UpdateApplicationStatusRequest request) {

        Long recruiterId = SecurityUtils.getRequiredRecruiterId();
        Long userId = SecurityUtils.getCurrentUserId();

        ApplicantDetailDto updated = recruiterApplicantService.updateApplicationStatus(
                recruiterId,
                userId,
                applicationId,
                request.status(),
                request.note()
        );

        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @GetMapping({"/applications/{applicationId}/resume", "/applications/{applicationId}/resume/download"})
    public ResponseEntity<?> getApplicantResume(
            @PathVariable Long applicationId,
            @RequestParam(value = "metadata", required = false, defaultValue = "false") boolean metadataOnly,
            @org.springframework.web.bind.annotation.RequestHeader(value = org.springframework.http.HttpHeaders.ACCEPT, required = false) String acceptHeader) {

        Long recruiterId = SecurityUtils.getRequiredRecruiterId();
        RecruiterApplicantService.ResumeDownloadDto resume = recruiterApplicantService
                .getApplicantResume(recruiterId, applicationId);

        if (metadataOnly || (acceptHeader != null && acceptHeader.contains(org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
                && !acceptHeader.contains(org.springframework.http.MediaType.APPLICATION_PDF_VALUE) && !acceptHeader.contains("*/*"))) {
            com.careerconnect.candidate.dto.ResumeResponse response = new com.careerconnect.candidate.dto.ResumeResponse(
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
                .contentType(org.springframework.http.MediaType.parseMediaType(resume.contentType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resume.filename() + "\"")
                .contentLength(resume.sizeBytes())
                .body(resume.content());
    }
}
