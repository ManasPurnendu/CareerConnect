package com.careerconnect.candidate.service;

import com.careerconnect.candidate.dto.ProfileReadinessDto;
import com.careerconnect.candidate.repository.CandidateEducationRepository;
import com.careerconnect.candidate.repository.CandidateProfileRepository;
import com.careerconnect.candidate.repository.CandidateResumeRepository;
import com.careerconnect.candidate.repository.CandidateSkillRepository;
import com.careerconnect.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CandidateProfileReadinessService {

    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateEducationRepository candidateEducationRepository;
    private final CandidateSkillRepository candidateSkillRepository;
    private final CandidateResumeRepository candidateResumeRepository;

    public CandidateProfileReadinessService(
            CandidateProfileRepository candidateProfileRepository,
            CandidateEducationRepository candidateEducationRepository,
            CandidateSkillRepository candidateSkillRepository,
            CandidateResumeRepository candidateResumeRepository) {
        this.candidateProfileRepository = candidateProfileRepository;
        this.candidateEducationRepository = candidateEducationRepository;
        this.candidateSkillRepository = candidateSkillRepository;
        this.candidateResumeRepository = candidateResumeRepository;
    }

    public ProfileReadinessDto getReadiness() {
        Long candidateId = SecurityUtils.getRequiredCandidateId();
        return computeReadiness(candidateId);
    }

    public ProfileReadinessDto computeReadiness(Long candidateId) {
        boolean profileComplete = candidateProfileRepository.existsByIdWithFullName(candidateId);
        boolean hasEducation = candidateEducationRepository.existsByCandidateId(candidateId);
        boolean hasSkills = candidateSkillRepository.existsByCandidateId(candidateId);
        boolean hasResume = candidateResumeRepository.findCurrentByCandidateId(candidateId).isPresent();

        // Deterministic ordering: PROFILE, EDUCATION, SKILLS, RESUME
        List<String> missing = new ArrayList<>();
        if (!profileComplete) missing.add("PROFILE");
        if (!hasEducation)   missing.add("EDUCATION");
        if (!hasSkills)      missing.add("SKILLS");
        if (!hasResume)      missing.add("RESUME");

        boolean readyToApply = missing.isEmpty();

        return new ProfileReadinessDto(readyToApply, profileComplete, hasEducation, hasSkills, hasResume, List.copyOf(missing));
    }
}
