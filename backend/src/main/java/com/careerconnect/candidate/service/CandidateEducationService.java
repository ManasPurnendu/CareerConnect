package com.careerconnect.candidate.service;

import com.careerconnect.candidate.dto.CreateEducationRequest;
import com.careerconnect.candidate.dto.EducationDto;
import com.careerconnect.candidate.dto.UpdateEducationRequest;
import com.careerconnect.candidate.repository.CandidateEducationRepository;
import com.careerconnect.common.exception.ResourceNotFoundException;
import com.careerconnect.common.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CandidateEducationService {

    private final CandidateEducationRepository candidateEducationRepository;

    public CandidateEducationService(CandidateEducationRepository candidateEducationRepository) {
        this.candidateEducationRepository = candidateEducationRepository;
    }

    @Transactional
    public EducationDto createEducation(Long candidateId, CreateEducationRequest request) {
        validateYears(request.getStartYear(), request.getEndYear());

        Long id = candidateEducationRepository.insert(candidateId, request);
        return candidateEducationRepository.findByIdAndCandidateId(id, candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Education record not found after creation."));
    }

    @Transactional
    public EducationDto updateEducation(Long id, Long candidateId, UpdateEducationRequest request) {
        validateYears(request.getStartYear(), request.getEndYear());

        int rowsAffected = candidateEducationRepository.update(id, candidateId, request);
        if (rowsAffected == 0) {
            throw new ResourceNotFoundException("Education record with ID " + id + " not found.");
        }

        return candidateEducationRepository.findByIdAndCandidateId(id, candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Education record with ID " + id + " not found."));
    }

    @Transactional
    public void deleteEducation(Long id, Long candidateId) {
        int rowsAffected = candidateEducationRepository.delete(id, candidateId);
        if (rowsAffected == 0) {
            throw new ResourceNotFoundException("Education record with ID " + id + " not found.");
        }
    }

    @Transactional(readOnly = true)
    public List<EducationDto> getEducationList(Long candidateId) {
        return candidateEducationRepository.findByCandidateId(candidateId);
    }

    @Transactional(readOnly = true)
    public EducationDto getEducation(Long id, Long candidateId) {
        return candidateEducationRepository.findByIdAndCandidateId(id, candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Education record with ID " + id + " not found."));
    }

    private void validateYears(Integer startYear, Integer endYear) {
        if (startYear != null && endYear != null && endYear < startYear) {
            throw new ValidationException("End year cannot be before start year.");
        }
    }
}
