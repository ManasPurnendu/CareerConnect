package com.careerconnect.jobs.dto;

import com.careerconnect.company.dto.CompanySummaryDto;
import com.careerconnect.jobs.model.ExperienceLevel;
import com.careerconnect.jobs.model.JobStatus;
import com.careerconnect.jobs.model.JobType;
import com.careerconnect.jobs.model.WorkMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record JobDetailDto(
        Long id,
        String title,
        String description,
        String responsibilities,
        String requirements,
        CompanySummaryDto company,
        JobType jobType,
        ExperienceLevel experienceLevel,
        WorkMode workMode,
        String location,
        BigDecimal salaryMinLpa,
        BigDecimal salaryMaxLpa,
        String salaryCurrency,
        LocalDate applicationDeadline,
        JobStatus status,
        List<SkillDto> skills,
        Instant createdAt,
        Instant updatedAt
) {}
