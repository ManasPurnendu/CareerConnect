package com.careerconnect.application.controller;

import com.careerconnect.application.dto.ApplicationResponseDto;
import com.careerconnect.application.service.ApplicationService;
import com.careerconnect.common.pagination.PageRequestDto;
import com.careerconnect.common.pagination.PageResult;
import com.careerconnect.common.response.ApiResponse;
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
@RequestMapping("/api/candidate/applications")
public class CandidateApplicationController {

    private final ApplicationService applicationService;

    public CandidateApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<List<ApplicationResponseDto>>> getApplications(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        PageRequestDto pageRequest = new PageRequestDto(page, size);
        PageResult<ApplicationResponseDto> result = applicationService.getCandidateApplications(pageRequest);
        return ResponseEntity.ok(ApiResponse.success(result.getItems(), result.getPagination()));
    }

    @GetMapping("/{applicationId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<ApplicationResponseDto>> getApplicationDetail(
            @PathVariable Long applicationId) {
        ApplicationResponseDto response = applicationService.getCandidateApplicationDetail(applicationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{applicationId}/withdraw")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<ApplicationResponseDto>> withdrawApplication(
            @PathVariable Long applicationId) {
        ApplicationResponseDto response = applicationService.withdrawApplication(applicationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
