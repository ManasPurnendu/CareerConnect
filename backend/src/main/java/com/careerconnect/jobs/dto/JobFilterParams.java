package com.careerconnect.jobs.dto;

import com.careerconnect.jobs.model.ExperienceLevel;
import com.careerconnect.jobs.model.JobType;
import com.careerconnect.jobs.model.WorkMode;

import java.math.BigDecimal;

public record JobFilterParams(
        String q,
        String location,
        JobType jobType,
        ExperienceLevel experienceLevel,
        WorkMode workMode,
        String skill,
        BigDecimal minSalaryLpa,
        String sort,
        Integer page,
        Integer size
) {
    public int getPage() {
        return (page != null && page >= 0) ? page : 0;
    }

    public int getSize() {
        if (size == null || size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    public int getOffset() {
        return getPage() * getSize();
    }
}
