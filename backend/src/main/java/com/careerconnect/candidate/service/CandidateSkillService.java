package com.careerconnect.candidate.service;

import com.careerconnect.candidate.dto.AssignSkillRequest;
import com.careerconnect.candidate.dto.CandidateSkillDto;
import com.careerconnect.candidate.repository.CandidateSkillRepository;
import com.careerconnect.common.exception.ConflictException;
import com.careerconnect.common.exception.ErrorCode;
import com.careerconnect.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CandidateSkillService {

    private final CandidateSkillRepository candidateSkillRepository;

    public CandidateSkillService(CandidateSkillRepository candidateSkillRepository) {
        this.candidateSkillRepository = candidateSkillRepository;
    }

    @Transactional(readOnly = true)
    public List<CandidateSkillDto> getCandidateSkills(Long candidateId) {
        return candidateSkillRepository.findByCandidateId(candidateId);
    }

    @Transactional
    public CandidateSkillDto assignSkill(Long candidateId, AssignSkillRequest request) {
        String trimmedName = request.getName().trim();
        String normalizedName = trimmedName.toLowerCase();

        // Find or create skill definition
        var existingSkill = candidateSkillRepository.findSkillByNormalizedName(normalizedName);
        Long skillId;
        String canonicalName;

        if (existingSkill.isPresent()) {
            skillId = existingSkill.get().id();
            canonicalName = existingSkill.get().name();
        } else {
            skillId = candidateSkillRepository.createSkill(trimmedName, normalizedName);
            canonicalName = trimmedName;
        }

        // Prevent duplicate assignment
        if (candidateSkillRepository.hasSkill(candidateId, skillId)) {
            throw new ConflictException("Skill is already assigned to this candidate.", ErrorCode.VALIDATION_FAILED);
        }

        candidateSkillRepository.assignSkill(candidateId, skillId);
        return new CandidateSkillDto(skillId, canonicalName);
    }

    @Transactional
    public void removeSkill(Long candidateId, Long skillId) {
        int rowsAffected = candidateSkillRepository.deleteSkill(candidateId, skillId);
        if (rowsAffected == 0) {
            throw new ResourceNotFoundException("Skill assignment with ID " + skillId + " not found for this candidate.");
        }
    }
}
