package com.careerconnect.admin.dto;

/**
 * Data transfer object representing high-level administrative platform metrics.
 */
public record AdminDashboardDto(
        UserCounts users,
        CompanyCounts companies,
        JobCounts jobs,
        ApplicationCounts applications
) {
    public record UserCounts(
            long total,
            long candidates,
            long recruiters,
            long active,
            long inactive
    ) {}

    public record CompanyCounts(
            long total,
            long active,
            long inactive
    ) {}

    public record JobCounts(
            long total,
            long active,
            long draft,
            long closed,
            long archived
    ) {}

    public record ApplicationCounts(
            long total,
            long applied,
            long underReview,
            long shortlisted,
            long rejected,
            long selected,
            long withdrawn
    ) {}
}
