package com.careerconnect.application.controller;

import com.careerconnect.application.dto.ApplicationResponseDto;
import com.careerconnect.application.dto.ApplicationSubmissionRequest;
import com.careerconnect.application.service.ApplicationService;
import com.careerconnect.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/{jobId}/applications")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<ApplicationResponseDto>> submitApplication(
            @PathVariable Long jobId,
            @Valid @RequestBody(required = false) ApplicationSubmissionRequest request) {

        if (request == null) {
            request = new ApplicationSubmissionRequest();
        }

        ApplicationResponseDto response = applicationService.submitApplication(jobId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
