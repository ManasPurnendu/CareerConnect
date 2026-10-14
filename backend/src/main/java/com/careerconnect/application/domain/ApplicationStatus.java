package com.careerconnect.application.domain;

import java.util.Map;
import java.util.Set;

public enum ApplicationStatus {
    APPLIED,
    UNDER_REVIEW,
    SHORTLISTED,
    INTERVIEW,
    SELECTED,
    REJECTED,
    WITHDRAWN;

    private static final Set<ApplicationStatus> WITHDRAWABLE_STATUSES = Set.of(
            APPLIED,
            UNDER_REVIEW,
            SHORTLISTED,
            INTERVIEW
    );

    private static final Set<ApplicationStatus> TERMINAL_STATUSES = Set.of(
            SELECTED,
            REJECTED,
            WITHDRAWN
    );

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS = Map.of(
            APPLIED, Set.of(UNDER_REVIEW, WITHDRAWN),
            UNDER_REVIEW, Set.of(SHORTLISTED, REJECTED, WITHDRAWN),
            SHORTLISTED, Set.of(INTERVIEW, REJECTED, WITHDRAWN),
            INTERVIEW, Set.of(SELECTED, REJECTED, WITHDRAWN),
            SELECTED, Set.of(),
            REJECTED, Set.of(),
            WITHDRAWN, Set.of()
    );

    public boolean isWithdrawable() {
        return WITHDRAWABLE_STATUSES.contains(this);
    }

    public boolean isTerminal() {
        return TERMINAL_STATUSES.contains(this);
    }

    public Set<ApplicationStatus> getAllowedNextStatuses() {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of());
    }

    public boolean isValidTransition(ApplicationStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return getAllowedNextStatuses().contains(target);
    }

    public boolean canTransitionTo(ApplicationStatus target) {
        return isValidTransition(target);
    }
}
