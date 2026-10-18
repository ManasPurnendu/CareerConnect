package com.careerconnect.admin.audit.service;

import com.careerconnect.admin.audit.dto.AuditLogDto;
import com.careerconnect.common.exception.ForbiddenException;
import com.careerconnect.common.exception.UnauthorizedException;
import com.careerconnect.common.exception.ValidationException;
import com.careerconnect.common.model.Role;
import com.careerconnect.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditLogServiceTest {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testAdminUserId;

    @BeforeEach
    void setUp() {
        String uniqueEmail = "audit_svc_admin_" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("""
                INSERT INTO users (email, password_hash, role, is_active, created_at, updated_at)
                VALUES (?, 'HASH', 'ADMIN', true, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """, uniqueEmail);
        testAdminUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, uniqueEmail);

        setAdminSecurityContext(testAdminUserId, uniqueEmail);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAdminSecurityContext(Long userId, String email) {
        SecurityUser admin = new SecurityUser(userId, email, "HASH", Role.ADMIN, "Admin User", null, null, null, true);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void setCandidateSecurityContext(Long userId, String email) {
        SecurityUser candidate = new SecurityUser(userId, email, "HASH", Role.CANDIDATE, "Candidate User", 101L, null, null, true);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(candidate, null, candidate.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void setRecruiterSecurityContext(Long userId, String email) {
        SecurityUser recruiter = new SecurityUser(userId, email, "HASH", Role.RECRUITER, "Recruiter User", null, 201L, 301L, true);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(recruiter, null, recruiter.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @DisplayName("AuditLogService successfully records an audit event with map metadata")
    void recordEvent_withMapMetadata_persistsSuccessfully() {
        Map<String, Object> metadata = Map.of(
                "targetUserEmail", "badactor@example.com",
                "reason", "Spam activity detected",
                "moderatorAction", "SUSPEND"
        );

        Long auditId = auditLogService.recordEvent("USER_SUSPENDED", "USER", 555L, metadata);
        assertThat(auditId).isNotNull().isPositive();

        Optional<AuditLogDto> found = auditLogService.findById(auditId);
        assertThat(found).isPresent();
        AuditLogDto dto = found.get();
        assertThat(dto.id()).isEqualTo(auditId);
        assertThat(dto.actorUserId()).isEqualTo(testAdminUserId);
        assertThat(dto.action()).isEqualTo("USER_SUSPENDED");
        assertThat(dto.entityType()).isEqualTo("USER");
        assertThat(dto.entityId()).isEqualTo(555L);
        assertThat(dto.metadata()).contains("Spam activity detected");
        assertThat(dto.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("AuditLogService successfully records an audit event with pre-formatted JSON string metadata")
    void recordEvent_withJsonStringMetadata_persistsSuccessfully() {
        String jsonMetadata = "{\"action\":\"JOB_ARCHIVE\",\"previousStatus\":\"ACTIVE\",\"jobId\":777}";

        Long auditId = auditLogService.recordEvent("JOB_ARCHIVED", "JOB", 777L, jsonMetadata);
        assertThat(auditId).isNotNull().isPositive();

        Optional<AuditLogDto> found = auditLogService.findById(auditId);
        assertThat(found).isPresent();
        assertThat(found.get().metadata()).isEqualTo(jsonMetadata);
    }

    @Test
    @DisplayName("AuditLogService successfully records an audit event with null metadata")
    void recordEvent_withNullMetadata_persistsSuccessfully() {
        Long auditId = auditLogService.recordEvent("COMPANY_VERIFIED", "COMPANY", 888L);
        assertThat(auditId).isNotNull().isPositive();

        Optional<AuditLogDto> found = auditLogService.findById(auditId);
        assertThat(found).isPresent();
        assertThat(found.get().metadata()).isNull();
        assertThat(found.get().action()).isEqualTo("COMPANY_VERIFIED");
    }

    @Test
    @DisplayName("Actor identity is strictly server-authoritative from authenticated ADMIN context")
    void recordEvent_actorIdentityServerAuthoritative() {
        Long auditId = auditLogService.recordEvent("USER_ACTIVATED", "USER", 999L);

        Optional<AuditLogDto> found = auditLogService.findById(auditId);
        assertThat(found).isPresent();
        // Guaranteed to match testAdminUserId from SecurityContextHolder
        assertThat(found.get().actorUserId()).isEqualTo(testAdminUserId);
    }

    @Test
    @DisplayName("Unauthenticated context throws UnauthorizedException when recording audit event")
    void recordEvent_unauthenticated_throwsUnauthorized() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> auditLogService.recordEvent("USER_DEACTIVATED", "USER", 1L))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Authentication is required");
    }

    @Test
    @DisplayName("CANDIDATE context throws ForbiddenException when recording audit event")
    void recordEvent_candidateContext_throwsForbidden() {
        setCandidateSecurityContext(2L, "candidate@example.com");

        assertThatThrownBy(() -> auditLogService.recordEvent("USER_DEACTIVATED", "USER", 1L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only administrators can perform this action");
    }

    @Test
    @DisplayName("RECRUITER context throws ForbiddenException when recording audit event")
    void recordEvent_recruiterContext_throwsForbidden() {
        setRecruiterSecurityContext(3L, "recruiter@example.com");

        assertThatThrownBy(() -> auditLogService.recordEvent("USER_DEACTIVATED", "USER", 1L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only administrators can perform this action");
    }

    @Test
    @DisplayName("Blank action throws ValidationException")
    void recordEvent_blankAction_throwsValidationException() {
        assertThatThrownBy(() -> auditLogService.recordEvent("   ", "USER", 1L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Audit action cannot be blank");
    }

    @Test
    @DisplayName("Action exceeding 100 characters throws ValidationException")
    void recordEvent_actionTooLong_throwsValidationException() {
        String longAction = "A".repeat(101);
        assertThatThrownBy(() -> auditLogService.recordEvent(longAction, "USER", 1L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Audit action cannot exceed 100 characters");
    }

    @Test
    @DisplayName("Blank entityType throws ValidationException")
    void recordEvent_blankEntityType_throwsValidationException() {
        assertThatThrownBy(() -> auditLogService.recordEvent("USER_DEACTIVATED", "   ", 1L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Audit entity type cannot be blank");
    }

    @Test
    @DisplayName("EntityType exceeding 50 characters throws ValidationException")
    void recordEvent_entityTypeTooLong_throwsValidationException() {
        String longEntityType = "E".repeat(51);
        assertThatThrownBy(() -> auditLogService.recordEvent("USER_DEACTIVATED", longEntityType, 1L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Audit entity type cannot exceed 50 characters");
    }

    @Test
    @DisplayName("Malformed JSON metadata string throws ValidationException")
    void recordEvent_malformedJson_throwsValidationException() {
        String invalidJson = "{ bad: json, missing quotes }";
        assertThatThrownBy(() -> auditLogService.recordEvent("USER_DEACTIVATED", "USER", 1L, invalidJson))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid JSON format for audit metadata");
    }

    @Test
    @DisplayName("Append-only contract: AuditLogService exposes NO update, delete, or modify methods")
    void appendOnlyContract_noMutateOrDeleteMethods() {
        List<String> forbiddenPrefixes = Arrays.asList("update", "delete", "remove", "patch", "modify", "truncate");

        Method[] methods = AuditLogService.class.getDeclaredMethods();
        for (Method method : methods) {
            String methodName = method.getName().toLowerCase();
            for (String prefix : forbiddenPrefixes) {
                assertThat(methodName)
                        .withFailMessage("AuditLogService violates append-only invariant by exposing: %s", method.getName())
                        .doesNotStartWith(prefix);
            }
        }
    }
}
