package com.careerconnect.candidate.controller;

import com.careerconnect.candidate.dto.CreateExperienceRequest;
import com.careerconnect.candidate.dto.ExperienceDto;
import com.careerconnect.candidate.dto.UpdateExperienceRequest;
import com.careerconnect.candidate.service.CandidateExperienceService;
import com.careerconnect.common.response.ApiResponse;
import com.careerconnect.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/candidate/experience")
public class CandidateExperienceController {

    private final CandidateExperienceService candidateExperienceService;

    public CandidateExperienceController(CandidateExperienceService candidateExperienceService) {
        this.candidateExperienceService = candidateExperienceService;
    }

    @GetMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<List<ExperienceDto>>> getExperience() {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        List<ExperienceDto> experience = candidateExperienceService.getExperiences(candidateId);
        return ResponseEntity.ok(ApiResponse.success(experience));
    }

    @PostMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<ExperienceDto>> createExperience(@Valid @RequestBody CreateExperienceRequest request) {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        ExperienceDto created = candidateExperienceService.createExperience(candidateId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created, "Experience added successfully"));
    }

    @PutMapping("/{experienceId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<ExperienceDto>> updateExperience(
            @PathVariable Long experienceId,
            @Valid @RequestBody UpdateExperienceRequest request) {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        ExperienceDto updated = candidateExperienceService.updateExperience(experienceId, candidateId, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Experience updated successfully"));
    }

    @DeleteMapping("/{experienceId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<Void> deleteExperience(@PathVariable Long experienceId) {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        candidateExperienceService.deleteExperience(experienceId, candidateId);
        return ResponseEntity.noContent().build();
    }
}
