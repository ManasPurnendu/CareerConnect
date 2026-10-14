package com.careerconnect.candidate.controller;

import com.careerconnect.candidate.dto.CandidateProfileResponse;
import com.careerconnect.candidate.dto.ProfileReadinessDto;
import com.careerconnect.candidate.dto.UpdateProfileRequest;
import com.careerconnect.candidate.service.CandidateProfileReadinessService;
import com.careerconnect.candidate.service.CandidateProfileService;
import com.careerconnect.common.response.ApiResponse;
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
@RequestMapping("/api/candidate/profile")
public class CandidateProfileController {

    private final CandidateProfileService candidateProfileService;
    private final CandidateProfileReadinessService candidateProfileReadinessService;

    public CandidateProfileController(CandidateProfileService candidateProfileService,
                                      CandidateProfileReadinessService candidateProfileReadinessService) {
        this.candidateProfileService = candidateProfileService;
        this.candidateProfileReadinessService = candidateProfileReadinessService;
    }

    @GetMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> getProfile() {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        CandidateProfileResponse profile = candidateProfileService.getProfile(candidateId);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @PutMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        CandidateProfileResponse updated = candidateProfileService.updateProfile(candidateId, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Profile updated successfully"));
    }

    @GetMapping("/readiness")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<ProfileReadinessDto>> getReadiness() {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        ProfileReadinessDto readiness = candidateProfileReadinessService.computeReadiness(candidateId);
        return ResponseEntity.ok(ApiResponse.success(readiness));
    }
}
