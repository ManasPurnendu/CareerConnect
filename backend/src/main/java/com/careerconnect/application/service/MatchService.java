package com.careerconnect.application.service;

import com.careerconnect.application.dto.JobMatchDto;
import com.careerconnect.candidate.dto.CandidateSkillDto;
import com.careerconnect.candidate.repository.CandidateSkillRepository;
import com.careerconnect.common.exception.ErrorCode;
import com.careerconnect.common.exception.ResourceNotFoundException;
import com.careerconnect.jobs.repository.JobRepository;
import com.careerconnect.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MatchService {

    private final JobRepository jobRepository;
    private final CandidateSkillRepository candidateSkillRepository;

    public MatchService(JobRepository jobRepository, CandidateSkillRepository candidateSkillRepository) {
        this.jobRepository = jobRepository;
        this.candidateSkillRepository = candidateSkillRepository;
    }

    public JobMatchDto calculateJobMatch(Long jobId) {
        Long candidateId = SecurityUtils.getRequiredCandidateId();

        if (jobId == null || !jobRepository.existsJobById(jobId)) {
            throw new ResourceNotFoundException(
                    "Job with ID " + jobId + " not found",
                    ErrorCode.JOB_NOT_FOUND
            );
        }

        List<String> jobRequiredSkills = jobRepository.findRequiredSkillNamesByJobId(jobId);
        List<String> candidateSkills = candidateSkillRepository.findByCandidateId(candidateId)
                .stream()
                .map(CandidateSkillDto::name)
                .toList();

        return calculateMatch(jobRequiredSkills, candidateSkills);
    }

    public JobMatchDto calculateMatch(List<String> jobRequiredSkills, List<String> candidateSkills) {
        if (jobRequiredSkills == null) {
            jobRequiredSkills = List.of();
        }
        if (candidateSkills == null) {
            candidateSkills = List.of();
        }

        // 1. Normalize and deduplicate job required skills, preserving first-seen display string
        Map<String, String> normalizedJobSkills = new LinkedHashMap<>();
        for (String skill : jobRequiredSkills) {
            if (skill != null && !skill.trim().isEmpty()) {
                String norm = normalizeSkill(skill);
                normalizedJobSkills.putIfAbsent(norm, skill.trim());
            }
        }

        // 2. Normalize and deduplicate candidate skills, preserving first-seen display string
        Map<String, String> normalizedCandidateSkills = new LinkedHashMap<>();
        for (String skill : candidateSkills) {
            if (skill != null && !skill.trim().isEmpty()) {
                String norm = normalizeSkill(skill);
                normalizedCandidateSkills.putIfAbsent(norm, skill.trim());
            }
        }

        int totalRequired = normalizedJobSkills.size();

        // 3. Matched skills: required job skills possessed by the candidate
        List<String> matchedSkills = new ArrayList<>();
        // 4. Missing skills: required job skills NOT possessed by the candidate
        List<String> missingSkills = new ArrayList<>();

        for (Map.Entry<String, String> entry : normalizedJobSkills.entrySet()) {
            String norm = entry.getKey();
            String display = entry.getValue();
            if (normalizedCandidateSkills.containsKey(norm)) {
                matchedSkills.add(display);
            } else {
                missingSkills.add(display);
            }
        }

        // 5. Additional skills: candidate skills NOT required by the job
        List<String> additionalSkills = new ArrayList<>();
        for (Map.Entry<String, String> entry : normalizedCandidateSkills.entrySet()) {
            String norm = entry.getKey();
            String display = entry.getValue();
            if (!normalizedJobSkills.containsKey(norm)) {
                additionalSkills.add(display);
            }
        }

        // 6. Calculate percentage
        int percentage;
        if (totalRequired == 0) {
            percentage = 100;
        } else {
            percentage = (int) Math.round((double) matchedSkills.size() * 100.0 / totalRequired);
        }

        return new JobMatchDto(matchedSkills, missingSkills, additionalSkills, percentage);
    }

    private String normalizeSkill(String skill) {
        return skill.trim().toLowerCase(Locale.ROOT);
    }
}
