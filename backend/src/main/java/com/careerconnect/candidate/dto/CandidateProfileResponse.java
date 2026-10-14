package com.careerconnect.candidate.dto;

public record CandidateProfileResponse(
        Long id,
        String fullName,
        String headline,
        String location,
        String summary
) {}
