package com.careerconnect.jobs.dto;

import com.careerconnect.jobs.model.ExperienceLevel;
import com.careerconnect.jobs.model.JobType;
import com.careerconnect.jobs.model.WorkMode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecruiterJobUpdateRequest(
        @NotBlank(message = "Job title cannot be blank")
        @Size(max = 200, message = "Job title cannot exceed 200 characters")
        String title,

        @NotBlank(message = "Job description cannot be blank")
        @Size(max = 10000, message = "Job description cannot exceed 10000 characters")
        String description,

        @Size(max = 10000, message = "Responsibilities cannot exceed 10000 characters")
        String responsibilities,

        @Size(max = 10000, message = "Requirements cannot exceed 10000 characters")
        String requirements,

        @NotNull(message = "Job type is required")
        JobType jobType,

        @NotNull(message = "Experience level is required")
        ExperienceLevel experienceLevel,

        @NotNull(message = "Work mode is required")
        WorkMode workMode,

        @Size(max = 150, message = "Location cannot exceed 150 characters")
        String location,

        @DecimalMin(value = "0.01", message = "Minimum salary must be greater than zero")
        BigDecimal salaryMinLpa,

        @DecimalMin(value = "0.01", message = "Maximum salary must be greater than zero")
        BigDecimal salaryMaxLpa,

        @Size(max = 10, message = "Salary currency cannot exceed 10 characters")
        String salaryCurrency,

        @NotNull(message = "Application deadline is required")
        @FutureOrPresent(message = "Application deadline cannot be in the past")
        LocalDate applicationDeadline,

        @NotEmpty(message = "At least one required skill ID must be provided")
        List<Long> requiredSkillIds
) {
    public RecruiterJobUpdateRequest {
        if (title != null) {
            title = title.trim();
        }
        if (description != null) {
            description = description.trim();
        }
        if (responsibilities != null) {
            responsibilities = responsibilities.trim();
        }
        if (requirements != null) {
            requirements = requirements.trim();
        }
        if (location != null) {
            location = location.trim();
        }
        if (salaryCurrency != null && !salaryCurrency.trim().isEmpty()) {
            salaryCurrency = salaryCurrency.trim();
        } else {
            salaryCurrency = "INR";
        }
        if (requiredSkillIds != null) {
            requiredSkillIds = requiredSkillIds.stream().distinct().toList();
        }
    }
}
