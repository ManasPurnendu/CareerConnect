package com.careerconnect.admin.dto;

import com.careerconnect.jobs.model.ExperienceLevel;
import com.careerconnect.jobs.model.JobStatus;
import com.careerconnect.jobs.model.JobType;
import com.careerconnect.jobs.model.WorkMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Data transfer object representing a job across the platform for administrative moderation.
 */
public record AdminJobDto(
        Long id,
        Long companyId,
        String companyName,
        String title,
        String description,
        JobType jobType,
        ExperienceLevel experienceLevel,
        WorkMode workMode,
        String location,
        BigDecimal salaryMinLpa,
        BigDecimal salaryMaxLpa,
        LocalDate applicationDeadline,
        JobStatus status,
        long applicationCount,
        List<String> requiredSkills,
        Instant createdAt,
        Instant updatedAt
) {
}
