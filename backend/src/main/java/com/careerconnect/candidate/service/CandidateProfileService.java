package com.careerconnect.candidate.service;

import com.careerconnect.candidate.dto.CandidateProfileResponse;
import com.careerconnect.candidate.dto.UpdateProfileRequest;
import com.careerconnect.candidate.repository.CandidateProfileRepository;
import com.careerconnect.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateProfileService {

    private final CandidateProfileRepository candidateProfileRepository;

    public CandidateProfileService(CandidateProfileRepository candidateProfileRepository) {
        this.candidateProfileRepository = candidateProfileRepository;
    }

    @Transactional(readOnly = true)
    public CandidateProfileResponse getProfile(Long candidateId) {
        return candidateProfileRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found for ID: " + candidateId));
    }

    @Transactional
    public CandidateProfileResponse updateProfile(Long candidateId, UpdateProfileRequest request) {
        if (!candidateProfileRepository.existsById(candidateId)) {
            throw new ResourceNotFoundException("Candidate profile not found for ID: " + candidateId);
        }

        candidateProfileRepository.update(candidateId, request);
        return getProfile(candidateId);
    }
}
