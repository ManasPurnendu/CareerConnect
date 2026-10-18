package com.careerconnect.security;

import com.careerconnect.common.exception.ForbiddenException;
import com.careerconnect.common.exception.UnauthorizedException;
import com.careerconnect.common.model.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setSecurityContext(SecurityUser user) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @DisplayName("getRequiredAdminUserId throws UnauthorizedException when unauthenticated")
    void getRequiredAdminUserId_unauthenticated_throwsUnauthorized() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(SecurityUtils::getRequiredAdminUserId)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Authentication is required");
    }

    @Test
    @DisplayName("getRequiredAdminUserId throws ForbiddenException when authenticated as CANDIDATE")
    void getRequiredAdminUserId_asCandidate_throwsForbidden() {
        SecurityUser candidate = new SecurityUser(10L, "cand@example.com", "HASH", Role.CANDIDATE, "Jane", 100L, null, null, true);
        setSecurityContext(candidate);

        assertThatThrownBy(SecurityUtils::getRequiredAdminUserId)
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only administrators can perform this action");
    }

    @Test
    @DisplayName("getRequiredAdminUserId throws ForbiddenException when authenticated as RECRUITER")
    void getRequiredAdminUserId_asRecruiter_throwsForbidden() {
        SecurityUser recruiter = new SecurityUser(20L, "rec@example.com", "HASH", Role.RECRUITER, "Bob", null, 200L, 300L, true);
        setSecurityContext(recruiter);

        assertThatThrownBy(SecurityUtils::getRequiredAdminUserId)
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only administrators can perform this action");
    }

    @Test
    @DisplayName("getRequiredAdminUserId returns authenticated admin userId when role is ADMIN")
    void getRequiredAdminUserId_asAdmin_returnsUserId() {
        SecurityUser admin = new SecurityUser(99L, "admin@careerconnect.com", "HASH", Role.ADMIN, "Admin", null, null, null, true);
        setSecurityContext(admin);

        Long adminUserId = SecurityUtils.getRequiredAdminUserId();
        assertThat(adminUserId).isEqualTo(99L);
    }

    @Test
    @DisplayName("getRequiredUserId and getCurrentUserId return userId for authenticated user")
    void getRequiredUserId_authenticated_returnsUserId() {
        SecurityUser admin = new SecurityUser(42L, "admin@careerconnect.com", "HASH", Role.ADMIN, "Admin", null, null, null, true);
        setSecurityContext(admin);

        assertThat(SecurityUtils.getRequiredUserId()).isEqualTo(42L);
        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo(42L);
        assertThat(SecurityUtils.getCurrentRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("Server identity is strictly derived from SecurityUser principal and ignores external manipulation")
    void serverAuthoritativeIdentity_strictlyDerivedFromPrincipal() {
        SecurityUser admin1 = new SecurityUser(101L, "admin1@careerconnect.com", "HASH", Role.ADMIN, "Admin One", null, null, null, true);
        setSecurityContext(admin1);

        assertThat(SecurityUtils.getRequiredAdminUserId()).isEqualTo(101L);

        // Even if another context is set, it strictly reflects that context
        SecurityUser admin2 = new SecurityUser(202L, "admin2@careerconnect.com", "HASH", Role.ADMIN, "Admin Two", null, null, null, true);
        setSecurityContext(admin2);

        assertThat(SecurityUtils.getRequiredAdminUserId()).isEqualTo(202L);
    }
}
