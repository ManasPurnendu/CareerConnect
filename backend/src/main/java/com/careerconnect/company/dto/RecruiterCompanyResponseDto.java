package com.careerconnect.company.dto;

import java.time.Instant;

public record RecruiterCompanyResponseDto(
        Long id,
        String name,
        String logo,
        String description,
        String industry,
        String website,
        String location,
        String companySize,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
    public static RecruiterCompanyResponseDto of(
            Long id,
            String name,
            String description,
            String industry,
            String website,
            String location,
            String companySize,
            boolean isActive,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new RecruiterCompanyResponseDto(
                id,
                name,
                null, // logo column not in schema; provided as null for API compliance
                description,
                industry,
                website,
                location,
                companySize,
                isActive,
                createdAt,
                updatedAt
        );
    }
}
