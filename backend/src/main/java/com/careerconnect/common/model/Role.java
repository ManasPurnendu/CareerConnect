package com.careerconnect.common.model;

public enum Role {
    CANDIDATE,
    RECRUITER,
    ADMIN;

    public boolean isCandidate() {
        return this == CANDIDATE;
    }

    public boolean isRecruiter() {
        return this == RECRUITER;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }
}
