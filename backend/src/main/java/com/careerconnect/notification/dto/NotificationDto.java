package com.careerconnect.notification.dto;

import com.careerconnect.notification.model.NotificationType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Data transfer object representing an in-app notification.
 */
public record NotificationDto(
        Long id,
        NotificationType type,
        String title,
        String body,
        String resourceType,
        Long resourceId,
        Instant readAt,
        Instant createdAt,
        @JsonProperty("read") boolean read
) {
    public NotificationDto(
            Long id,
            NotificationType type,
            String title,
            String body,
            String resourceType,
            Long resourceId,
            Instant readAt,
            Instant createdAt
    ) {
        this(id, type, title, body, resourceType, resourceId, readAt, createdAt, readAt != null);
    }
}
