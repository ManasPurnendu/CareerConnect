package com.careerconnect.company.dto;

import com.careerconnect.jobs.dto.JobSummaryDto;

import java.time.Instant;
import java.util.List;

public record CompanyDetailDto(
        Long id,
        String name,
        String industry,
        String location,
        String website,
        String description,
        String companySize,
        long activeJobCount,
        List<JobSummaryDto> activeJobs,
        Instant createdAt
) {}
