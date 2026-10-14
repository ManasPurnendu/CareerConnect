package com.careerconnect.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CandidateSnapshot(
        Long candidateId,
        String fullName,
        String email,
        String headline,
        String location,
        String summary,
        List<CandidateEducationSnapshot> education,
        List<String> skills,
        List<CandidateProjectSnapshot> projects,
        List<CandidateExperienceSnapshot> experience
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CandidateEducationSnapshot(
            Long id,
            String institution,
            String degree,
            String fieldOfStudy,
            Integer startYear,
            Integer endYear,
            String grade,
            Integer sortOrder
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CandidateProjectSnapshot(
            Long id,
            String title,
            String description,
            String projectUrl,
            Integer sortOrder
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CandidateExperienceSnapshot(
            Long id,
            String companyName,
            String roleTitle,
            String startDate,
            String endDate,
            Boolean isCurrent,
            String description,
            Integer sortOrder
    ) {}
}
