package com.careerconnect.candidate.controller;

import com.careerconnect.candidate.dto.CreateProjectRequest;
import com.careerconnect.candidate.dto.ProjectDto;
import com.careerconnect.candidate.dto.UpdateProjectRequest;
import com.careerconnect.candidate.service.CandidateProjectService;
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
@RequestMapping("/api/candidate/projects")
public class CandidateProjectController {

    private final CandidateProjectService candidateProjectService;

    public CandidateProjectController(CandidateProjectService candidateProjectService) {
        this.candidateProjectService = candidateProjectService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<ProjectDto>> createProject(@Valid @RequestBody CreateProjectRequest request) {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        ProjectDto created = candidateProjectService.createProject(candidateId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created, "Project created successfully"));
    }

    @PutMapping("/{projectId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<ProjectDto>> updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        ProjectDto updated = candidateProjectService.updateProject(projectId, candidateId, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Project updated successfully"));
    }

    @DeleteMapping("/{projectId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<Void> deleteProject(@PathVariable Long projectId) {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        candidateProjectService.deleteProject(projectId, candidateId);
        return ResponseEntity.noContent().build();
    }
}
