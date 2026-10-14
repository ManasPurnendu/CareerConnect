package com.careerconnect.security;

import com.careerconnect.common.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "careerconnect-secure-jwt-secret-key-32-bytes-minimum-length-academic-project";
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 30);
    }

    @Test
    @DisplayName("Short secret key should be rejected during initialization")
    void shouldRejectShortSecretKey() {
        assertThatThrownBy(() -> new JwtService("short-secret", 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    @DisplayName("Should generate valid JWT token containing user claims")
    void shouldGenerateAndExtractClaims() {
        SecurityUser user = new SecurityUser(
                10L,
                "jane.candidate@example.com",
                "secret-hash",
                Role.CANDIDATE,
                "Jane Doe",
                5L,
                null,
                null,
                true
        );

        String token = jwtService.generateToken(user);

        assertThat(token).isNotBlank();
        Instant expiry = jwtService.getExpirationTimestamp(token);
        assertThat(expiry).isAfter(Instant.now());

        Optional<SecurityUser> extracted = jwtService.validateAndExtractSecurityUser(token);
        assertThat(extracted).isPresent();
        SecurityUser parsedUser = extracted.get();

        assertThat(parsedUser.getUserId()).isEqualTo(10L);
        assertThat(parsedUser.getUsername()).isEqualTo("jane.candidate@example.com");
        assertThat(parsedUser.getRole()).isEqualTo(Role.CANDIDATE);
        assertThat(parsedUser.getFullName()).isEqualTo("Jane Doe");
        assertThat(parsedUser.getCandidateId()).isEqualTo(5L);
        assertThat(parsedUser.getRecruiterId()).isNull();
        assertThat(parsedUser.getPassword()).isEmpty(); // Password hash never included in token
    }

    @Test
    @DisplayName("Should validate recruiter token with company ID")
    void shouldGenerateAndExtractRecruiterClaims() {
        SecurityUser recruiter = new SecurityUser(
                20L,
                "bob.recruiter@techcorp.com",
                "secret-hash",
                Role.RECRUITER,
                "Bob Smith",
                null,
                8L,
                15L,
                true
        );

        String token = jwtService.generateToken(recruiter);
        Optional<SecurityUser> extracted = jwtService.validateAndExtractSecurityUser(token);

        assertThat(extracted).isPresent();
        SecurityUser parsedUser = extracted.get();
        assertThat(parsedUser.getUserId()).isEqualTo(20L);
        assertThat(parsedUser.getRole()).isEqualTo(Role.RECRUITER);
        assertThat(parsedUser.getRecruiterId()).isEqualTo(8L);
        assertThat(parsedUser.getCompanyId()).isEqualTo(15L);
    }

    @Test
    @DisplayName("Tampered token must fail validation")
    void shouldRejectTamperedToken() {
        SecurityUser user = new SecurityUser(
                1L, "user@example.com", "hash", Role.CANDIDATE, "User", 1L, null, null, true
        );
        String token = jwtService.generateToken(user);
        String tamperedToken = token + "xyz";

        Optional<SecurityUser> extracted = jwtService.validateAndExtractSecurityUser(tamperedToken);
        assertThat(extracted).isEmpty();
    }
}
