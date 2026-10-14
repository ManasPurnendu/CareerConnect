package com.careerconnect.candidate.controller;

import com.careerconnect.candidate.dto.ResumeResponse;
import com.careerconnect.candidate.service.CandidateResumeService;
import com.careerconnect.common.response.ApiResponse;
import com.careerconnect.security.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/candidate/resume")
public class CandidateResumeController {

    private final CandidateResumeService candidateResumeService;

    public CandidateResumeController(CandidateResumeService candidateResumeService) {
        this.candidateResumeService = candidateResumeService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<ResumeResponse>> uploadResume(@RequestParam("file") MultipartFile file) {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        ResumeResponse response = candidateResumeService.uploadResume(candidateId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Resume uploaded successfully"));
    }

    @GetMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<ResumeResponse>> getCurrentResume() {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        ResumeResponse response = candidateResumeService.getCurrentResume(candidateId);
        return ResponseEntity.ok(ApiResponse.success(response, "Current resume retrieved successfully"));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<Void> deleteCurrentResume() {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        candidateResumeService.deleteCurrentResume(candidateId);
        return ResponseEntity.noContent().build();
    }
}
