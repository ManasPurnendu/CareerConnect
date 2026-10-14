package com.careerconnect.candidate.controller;

import com.careerconnect.candidate.dto.CreateEducationRequest;
import com.careerconnect.candidate.dto.EducationDto;
import com.careerconnect.candidate.dto.UpdateEducationRequest;
import com.careerconnect.candidate.service.CandidateEducationService;
import com.careerconnect.common.response.ApiResponse;
import com.careerconnect.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/candidate/education")
public class CandidateEducationController {

    private final CandidateEducationService candidateEducationService;

    public CandidateEducationController(CandidateEducationService candidateEducationService) {
        this.candidateEducationService = candidateEducationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<EducationDto>> createEducation(@Valid @RequestBody CreateEducationRequest request) {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        EducationDto created = candidateEducationService.createEducation(candidateId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created, "Education added successfully"));
    }

    @PutMapping("/{educationId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<EducationDto>> updateEducation(
            @PathVariable Long educationId,
            @Valid @RequestBody UpdateEducationRequest request) {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        EducationDto updated = candidateEducationService.updateEducation(educationId, candidateId, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Education updated successfully"));
    }

    @DeleteMapping("/{educationId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<Void> deleteEducation(@PathVariable Long educationId) {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        candidateEducationService.deleteEducation(educationId, candidateId);
        return ResponseEntity.noContent().build();
    }
}
