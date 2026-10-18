package com.careerconnect.admin.audit.dto;

import java.time.Instant;

/**
 * Data transfer object representing an audit log entry.
 */
public record AuditLogDto(
        Long id,
        Long actorUserId,
        String action,
        String entityType,
        Long entityId,
        String metadata,
        Instant createdAt
) {
}
