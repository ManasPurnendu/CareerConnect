package com.careerconnect.candidate.service;

import com.careerconnect.candidate.dto.CreateExperienceRequest;
import com.careerconnect.candidate.dto.ExperienceDto;
import com.careerconnect.candidate.dto.UpdateExperienceRequest;
import com.careerconnect.candidate.repository.CandidateExperienceRepository;
import com.careerconnect.common.exception.ResourceNotFoundException;
import com.careerconnect.common.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class CandidateExperienceService {

    private final CandidateExperienceRepository candidateExperienceRepository;

    public CandidateExperienceService(CandidateExperienceRepository candidateExperienceRepository) {
        this.candidateExperienceRepository = candidateExperienceRepository;
    }

    @Transactional
    public ExperienceDto createExperience(Long candidateId, CreateExperienceRequest request) {
        validateDates(request.getStartDate(), request.getEndDate(), request.getIsCurrent());

        Long id = candidateExperienceRepository.insert(candidateId, request);
        return candidateExperienceRepository.findByIdAndCandidateId(id, candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Experience record not found after creation."));
    }

    @Transactional
    public ExperienceDto updateExperience(Long id, Long candidateId, UpdateExperienceRequest request) {
        validateDates(request.getStartDate(), request.getEndDate(), request.getIsCurrent());

        int rowsAffected = candidateExperienceRepository.update(id, candidateId, request);
        if (rowsAffected == 0) {
            throw new ResourceNotFoundException("Experience record with ID " + id + " not found.");
        }

        return candidateExperienceRepository.findByIdAndCandidateId(id, candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Experience record with ID " + id + " not found."));
    }

    @Transactional
    public void deleteExperience(Long id, Long candidateId) {
        int rowsAffected = candidateExperienceRepository.delete(id, candidateId);
        if (rowsAffected == 0) {
            throw new ResourceNotFoundException("Experience record with ID " + id + " not found.");
        }
    }

    @Transactional(readOnly = true)
    public List<ExperienceDto> getExperiences(Long candidateId) {
        return candidateExperienceRepository.findByCandidateId(candidateId);
    }

    @Transactional(readOnly = true)
    public ExperienceDto getExperience(Long id, Long candidateId) {
        return candidateExperienceRepository.findByIdAndCandidateId(id, candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Experience record with ID " + id + " not found."));
    }

    private void validateDates(LocalDate startDate, LocalDate endDate, Boolean isCurrent) {
        if (startDate == null) {
            throw new ValidationException("Start date is required.");
        }

        boolean current = Boolean.TRUE.equals(isCurrent);

        if (current) {
            if (endDate != null && endDate.isBefore(startDate)) {
                throw new ValidationException("End date cannot be before start date.");
            }
        } else {
            if (endDate == null) {
                throw new ValidationException("End date is required for past experience.");
            }
            if (endDate.isBefore(startDate)) {
                throw new ValidationException("End date cannot be before start date.");
            }
        }
    }
}
