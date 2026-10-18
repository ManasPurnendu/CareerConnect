package com.careerconnect.admin.audit.repository;

import com.careerconnect.admin.audit.model.AuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditLogRepositoryTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testAdminUserId;

    @BeforeEach
    void setUp() {
        String uniqueEmail = "audit_admin_" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("""
                INSERT INTO users (email, password_hash, role, is_active, created_at, updated_at)
                VALUES (?, 'HASH', 'ADMIN', true, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """, uniqueEmail);
        testAdminUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, uniqueEmail);
    }

    @Test
    @DisplayName("Audit event can be inserted successfully and returns generated ID")
    void insert_withValidData_returnsGeneratedId() {
        Instant now = Instant.now();
        Long auditId = auditLogRepository.insert(
                testAdminUserId,
                "USER_DEACTIVATED",
                "USER",
                123L,
                "{\"reason\":\"Violation of TOS\"}",
                now
        );

        assertThat(auditId).isNotNull().isPositive();

        Optional<AuditLog> found = auditLogRepository.findById(auditId);
        assertThat(found).isPresent();
        AuditLog auditLog = found.get();
        assertThat(auditLog.getId()).isEqualTo(auditId);
        assertThat(auditLog.getActorUserId()).isEqualTo(testAdminUserId);
        assertThat(auditLog.getAction()).isEqualTo("USER_DEACTIVATED");
        assertThat(auditLog.getEntityType()).isEqualTo("USER");
        assertThat(auditLog.getEntityId()).isEqualTo(123L);
        assertThat(auditLog.getMetadata()).contains("Violation of TOS");
        assertThat(auditLog.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Audit event with null entityId and null metadata persists cleanly")
    void insert_withNullEntityIdAndMetadata_persistsSuccessfully() {
        Long auditId = auditLogRepository.insert(
                testAdminUserId,
                "GLOBAL_CONFIG_UPDATED",
                "SYSTEM",
                null,
                null,
                Instant.now()
        );

        assertThat(auditId).isNotNull().isPositive();

        Optional<AuditLog> found = auditLogRepository.findById(auditId);
        assertThat(found).isPresent();
        AuditLog auditLog = found.get();
        assertThat(auditLog.getEntityId()).isNull();
        assertThat(auditLog.getMetadata()).isNull();
        assertThat(auditLog.getAction()).isEqualTo("GLOBAL_CONFIG_UPDATED");
        assertThat(auditLog.getEntityType()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("Parameterized SQL safely handles special characters and quotes without injection")
    void insert_withSpecialCharacters_handledSafely() {
        String trickyAction = "ACTION'; DROP TABLE audit_logs; --";
        String trickyMetadata = "{\"comment\":\"'; DROP TABLE audit_logs; --\", \"active\":true}";

        Long auditId = auditLogRepository.insert(
                testAdminUserId,
                trickyAction,
                "SECURITY_TEST",
                999L,
                trickyMetadata,
                Instant.now()
        );

        assertThat(auditId).isNotNull().isPositive();

        Optional<AuditLog> found = auditLogRepository.findById(auditId);
        assertThat(found).isPresent();
        assertThat(found.get().getAction()).isEqualTo(trickyAction);
        assertThat(found.get().getMetadata()).isEqualTo(trickyMetadata);

        // Verify audit_logs table still exists and is completely intact
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Integer.class);
        assertThat(count).isNotNull().isGreaterThan(0);
    }

    @Test
    @DisplayName("Append-only contract: AuditLogRepository exposes NO update, delete, or mutate methods")
    void appendOnlyContract_noMutateOrDeleteMethods() {
        List<String> forbiddenPrefixes = Arrays.asList("update", "delete", "remove", "patch", "modify", "truncate");

        Method[] methods = AuditLogRepository.class.getDeclaredMethods();
        for (Method method : methods) {
            String methodName = method.getName().toLowerCase();
            for (String prefix : forbiddenPrefixes) {
                assertThat(methodName)
                        .withFailMessage("AuditLogRepository violates append-only invariant by exposing: %s", method.getName())
                        .doesNotStartWith(prefix);
            }
        }
    }

    @Test
    @DisplayName("findById with null returns Optional.empty()")
    void findById_nullId_returnsEmpty() {
        assertThat(auditLogRepository.findById(null)).isEmpty();
    }
}
