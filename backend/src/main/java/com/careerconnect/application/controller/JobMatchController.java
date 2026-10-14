package com.careerconnect.application.controller;

import com.careerconnect.application.dto.JobMatchDto;
import com.careerconnect.application.service.MatchService;
import com.careerconnect.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobMatchController {

    private final MatchService matchService;

    public JobMatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping("/{jobId}/match")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<JobMatchDto>> getJobMatch(@PathVariable Long jobId) {
        JobMatchDto match = matchService.calculateJobMatch(jobId);
        return ResponseEntity.ok(ApiResponse.success(match));
    }
}
