package com.careerconnect.admin.dto;

import com.careerconnect.common.model.Role;

import java.time.Instant;

/**
 * Data transfer object representing a platform user for moderation review.
 * Sensitive authentication data (password hashes, tokens) are strictly excluded.
 */
public record AdminUserDto(
        Long id,
        String email,
        Role role,
        String fullName,
        boolean active,
        Long candidateId,
        Long recruiterId,
        Long companyId,
        String companyName,
        Instant createdAt,
        Instant updatedAt
) {
}
