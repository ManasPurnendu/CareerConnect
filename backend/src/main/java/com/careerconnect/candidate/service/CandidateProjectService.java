package com.careerconnect.candidate.service;

import com.careerconnect.candidate.dto.CreateProjectRequest;
import com.careerconnect.candidate.dto.ProjectDto;
import com.careerconnect.candidate.dto.UpdateProjectRequest;
import com.careerconnect.candidate.repository.CandidateProjectRepository;
import com.careerconnect.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CandidateProjectService {

    private final CandidateProjectRepository candidateProjectRepository;

    public CandidateProjectService(CandidateProjectRepository candidateProjectRepository) {
        this.candidateProjectRepository = candidateProjectRepository;
    }

    @Transactional
    public ProjectDto createProject(Long candidateId, CreateProjectRequest request) {
        Long id = candidateProjectRepository.insert(candidateId, request);
        return candidateProjectRepository.findByIdAndCandidateId(id, candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found after creation."));
    }

    @Transactional
    public ProjectDto updateProject(Long id, Long candidateId, UpdateProjectRequest request) {
        int rowsAffected = candidateProjectRepository.update(id, candidateId, request);
        if (rowsAffected == 0) {
            throw new ResourceNotFoundException("Project with ID " + id + " not found.");
        }

        return candidateProjectRepository.findByIdAndCandidateId(id, candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Project with ID " + id + " not found."));
    }

    @Transactional
    public void deleteProject(Long id, Long candidateId) {
        int rowsAffected = candidateProjectRepository.delete(id, candidateId);
        if (rowsAffected == 0) {
            throw new ResourceNotFoundException("Project with ID " + id + " not found.");
        }
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> getProjects(Long candidateId) {
        return candidateProjectRepository.findByCandidateId(candidateId);
    }

    @Transactional(readOnly = true)
    public ProjectDto getProject(Long id, Long candidateId) {
        return candidateProjectRepository.findByIdAndCandidateId(id, candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Project with ID " + id + " not found."));
    }
}
