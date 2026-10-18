package com.careerconnect.admin.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for administrative user active status transitions.
 */
public record AdminUpdateUserStatusRequest(
        @NotNull(message = "Active status is required")
        @JsonProperty("isActive")
        @JsonAlias({"active", "status"})
        Boolean isActive
) {
}
