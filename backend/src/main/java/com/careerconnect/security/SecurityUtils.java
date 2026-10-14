package com.careerconnect.security;

import com.careerconnect.common.exception.ForbiddenException;
import com.careerconnect.common.exception.UnauthorizedException;
import com.careerconnect.common.model.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<SecurityUser> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return Optional.of(securityUser);
        }

        return Optional.empty();
    }

    public static SecurityUser getRequiredCurrentUser() {
        return getCurrentUser().orElseThrow(() ->
                new UnauthorizedException("Authentication is required to perform this action."));
    }

    public static Long getCurrentUserId() {
        return getRequiredCurrentUser().getUserId();
    }

    public static Role getCurrentRole() {
        return getRequiredCurrentUser().getRole();
    }

    public static Long getRequiredCandidateId() {
        SecurityUser user = getRequiredCurrentUser();
        if (user.getRole() != Role.CANDIDATE || user.getCandidateId() == null) {
            throw new ForbiddenException("Only candidates can perform this action.");
        }
        return user.getCandidateId();
    }

    public static Long getRequiredRecruiterId() {
        SecurityUser user = getRequiredCurrentUser();
        if (user.getRole() != Role.RECRUITER || user.getRecruiterId() == null) {
            throw new ForbiddenException("Only recruiters can perform this action.");
        }
        return user.getRecruiterId();
    }

    public static Long getRequiredCompanyId() {
        SecurityUser user = getRequiredCurrentUser();
        if (user.getRole() != Role.RECRUITER) {
            throw new ForbiddenException("Only recruiters can perform this action.");
        }
        if (user.getCompanyId() == null) {
            throw new ForbiddenException("Recruiter must be associated with a company before performing this action.");
        }
        return user.getCompanyId();
    }
}
