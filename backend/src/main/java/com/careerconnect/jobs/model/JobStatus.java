package com.careerconnect.jobs.model;

import java.util.Map;
import java.util.Set;

public enum JobStatus {
    DRAFT,
    ACTIVE,
    CLOSED,
    ARCHIVED;

    /**
     * Recruiter-controlled lifecycle transitions:
     * DRAFT -> ACTIVE (publish)
     * ACTIVE -> CLOSED (close)
     *
     * ARCHIVED exists as an administrative state and cannot be transitioned to by recruiters.
     * Terminal states and invalid transitions are strictly prohibited.
     */
    private static final Map<JobStatus, Set<JobStatus>> ALLOWED_TRANSITIONS = Map.of(
            DRAFT, Set.of(ACTIVE),
            ACTIVE, Set.of(CLOSED),
            CLOSED, Set.of(),
            ARCHIVED, Set.of()
    );

    public Set<JobStatus> getAllowedNextStatuses() {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of());
    }

    public boolean isValidTransition(JobStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return getAllowedNextStatuses().contains(target);
    }
}
