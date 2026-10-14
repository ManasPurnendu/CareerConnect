package com.careerconnect.candidate.dto;

import java.util.List;

public record ProfileReadinessDto(
        boolean readyToApply,
        boolean profileComplete,
        boolean hasEducation,
        boolean hasSkills,
        boolean hasResume,
        List<String> missingRequirements
) {}
