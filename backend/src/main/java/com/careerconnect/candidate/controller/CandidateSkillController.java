package com.careerconnect.candidate.controller;

import com.careerconnect.candidate.dto.AssignSkillRequest;
import com.careerconnect.candidate.dto.CandidateSkillDto;
import com.careerconnect.candidate.service.CandidateSkillService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/candidate/skills")
public class CandidateSkillController {

    private final CandidateSkillService candidateSkillService;

    public CandidateSkillController(CandidateSkillService candidateSkillService) {
        this.candidateSkillService = candidateSkillService;
    }

    @GetMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<List<CandidateSkillDto>>> getSkills() {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        List<CandidateSkillDto> skills = candidateSkillService.getCandidateSkills(candidateId);
        return ResponseEntity.ok(ApiResponse.success(skills));
    }

    @PostMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApiResponse<CandidateSkillDto>> assignSkill(@Valid @RequestBody AssignSkillRequest request) {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        CandidateSkillDto skill = candidateSkillService.assignSkill(candidateId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(skill, "Skill assigned successfully"));
    }

    @DeleteMapping("/{skillId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<Void> removeSkill(@PathVariable Long skillId) {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        candidateSkillService.removeSkill(candidateId, skillId);
        return ResponseEntity.noContent().build();
    }
}
