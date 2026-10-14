package com.careerconnect.application.dto;

import java.util.List;

public record JobMatchDto(
        List<String> matchedSkills,
        List<String> missingSkills,
        List<String> additionalSkills,
        int percentage
) {}
