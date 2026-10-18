package com.careerconnect.admin.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for administrative company active status transitions.
 */
public record AdminUpdateCompanyStatusRequest(
        @NotNull(message = "Active status is required")
        @JsonProperty("isActive")
        @JsonAlias({"active", "status"})
        Boolean isActive
) {
}
