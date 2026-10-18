package com.careerconnect.company.controller;

import com.careerconnect.common.response.ApiResponse;
import com.careerconnect.company.dto.RecruiterCompanyResponseDto;
import com.careerconnect.company.dto.RecruiterCompanyUpsertRequest;
import com.careerconnect.company.service.RecruiterCompanyService;
import com.careerconnect.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recruiter/company")
@PreAuthorize("hasRole('RECRUITER')")
public class RecruiterCompanyController {

    private final RecruiterCompanyService recruiterCompanyService;

    public RecruiterCompanyController(RecruiterCompanyService recruiterCompanyService) {
        this.recruiterCompanyService = recruiterCompanyService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<RecruiterCompanyResponseDto>> getCompany() {
        Long recruiterId = SecurityUtils.getRequiredRecruiterId();
        RecruiterCompanyResponseDto company = recruiterCompanyService.getCompany(recruiterId);
        return ResponseEntity.ok(ApiResponse.success(company));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<RecruiterCompanyResponseDto>> upsertCompany(
            @Valid @RequestBody RecruiterCompanyUpsertRequest request) {
        Long recruiterId = SecurityUtils.getRequiredRecruiterId();
        RecruiterCompanyResponseDto company = recruiterCompanyService.upsertCompany(recruiterId, request);
        return ResponseEntity.ok(ApiResponse.success(company));
    }
}
