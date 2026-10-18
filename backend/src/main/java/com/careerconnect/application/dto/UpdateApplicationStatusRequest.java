package com.careerconnect.application.dto;

import com.careerconnect.application.domain.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateApplicationStatusRequest(
        @NotNull(message = "Target status is required")
        ApplicationStatus status,

        @Size(max = 500, message = "Note cannot exceed 500 characters")
        String note
) {}
