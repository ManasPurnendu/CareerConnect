package com.careerconnect.company.dto;

public record CompanySummaryDto(
        Long id,
        String name,
        String industry,
        String location,
        String website,
        String companySize,
        long activeJobCount
) {}
