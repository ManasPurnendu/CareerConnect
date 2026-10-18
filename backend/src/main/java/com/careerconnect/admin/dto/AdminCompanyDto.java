package com.careerconnect.admin.dto;

import java.time.Instant;

/**
 * Data transfer object representing a company for administrative review.
 */
public record AdminCompanyDto(
        Long id,
        String name,
        String industry,
        String location,
        String website,
        String description,
        String companySize,
        boolean active,
        long jobCount,
        long recruiterCount,
        Instant createdAt,
        Instant updatedAt
) {
}
