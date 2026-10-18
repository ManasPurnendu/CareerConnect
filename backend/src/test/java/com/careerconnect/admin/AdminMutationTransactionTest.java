package com.careerconnect.admin;

import com.careerconnect.admin.audit.repository.AuditLogRepository;
import com.careerconnect.admin.service.AdminMutationService;
import com.careerconnect.common.model.Role;
import com.careerconnect.common.util.JdbcUtils;
import com.careerconnect.jobs.model.JobStatus;
import com.careerconnect.security.SecurityUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
public class AdminMutationTransactionTest {

    public static class TransactionTestAuditLogRepository extends AuditLogRepository {
        public static volatile boolean failAudit = false;

        public TransactionTestAuditLogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
            super(jdbcTemplate, objectMapper);
        }

        @Override
        public Long insert(Long actorUserId, String action, String entityType, Long entityId, String metadataJson, Instant createdAt) {
            if (failAudit) {
                throw new RuntimeException("Simulated audit insertion failure for transaction rollback test");
            }
            return super.insert(actorUserId, action, entityType, entityId, metadataJson, createdAt);
        }
    }

    @TestConfiguration
    static class TestRepoConfig {
        @Bean
        @Primary
        public AuditLogRepository testAuditLogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
            return new TransactionTestAuditLogRepository(jdbcTemplate, objectMapper);
        }
    }

    @Autowired
    private AdminMutationService adminMutationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long adminUserId;
    private Long companyId;

    @BeforeEach
    void setUp() {
        tearDown();
        TransactionTestAuditLogRepository.failAudit = false;

        Long existingAdminId = jdbcTemplate.query(
                "SELECT id FROM users WHERE email = 'admin@careerconnect.com'",
                (rs, rowNum) -> rs.getLong("id")
        ).stream().findFirst().orElse(null);

        if (existingAdminId == null) {
            KeyHolder kh = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO users (email, password_hash, role, is_active, created_at, updated_at) " +
                                "VALUES ('admin@careerconnect.com', '$2a$12$dummyHashForAdminTestPass1234567890123456', 'ADMIN', true, NOW(), NOW())",
                        Statement.RETURN_GENERATED_KEYS
                );
                return ps;
            }, kh);
            adminUserId = JdbcUtils.extractGeneratedId(kh);
        } else {
            adminUserId = existingAdminId;
        }

        SecurityUser adminUser = new SecurityUser(adminUserId, "admin@careerconnect.com", "HASH", Role.ADMIN, "Admin User", null, null, null, true);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(adminUser, null, adminUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        KeyHolder ckh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO companies (name, industry, location, website, description, company_size, is_active, created_at, updated_at) " +
                            "VALUES ('Tx Corp', 'Finance', 'Mumbai', 'https://tx.com', 'Tx Description', '11-50', true, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            return ps;
        }, ckh);
        companyId = JdbcUtils.extractGeneratedId(ckh);
    }

    @AfterEach
    void tearDown() {
        TransactionTestAuditLogRepository.failAudit = false;
        SecurityContextHolder.clearContext();
        jdbcTemplate.execute("DELETE FROM audit_logs");
        jdbcTemplate.execute("DELETE FROM application_status_history");
        jdbcTemplate.execute("DELETE FROM applications");
        jdbcTemplate.execute("DELETE FROM job_skills");
        jdbcTemplate.execute("DELETE FROM jobs");
        jdbcTemplate.execute("DELETE FROM candidates");
        jdbcTemplate.execute("DELETE FROM recruiters");
        jdbcTemplate.execute("DELETE FROM companies");
        jdbcTemplate.execute("DELETE FROM users WHERE email != 'admin@careerconnect.com'");
    }

    private Long createUser(String email, Role role, boolean active) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO users (email, password_hash, role, is_active, created_at, updated_at) " +
                            "VALUES (?, 'HASH', ?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, email);
            ps.setString(2, role.name());
            ps.setBoolean(3, active);
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }

    private Long createJob(Long compId, String title, String status) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO jobs (company_id, title, description, job_type, experience_level, work_mode, location, " +
                            "salary_min_lpa, salary_max_lpa, salary_currency, application_deadline, status, created_at, updated_at) " +
                            "VALUES (?, ?, 'Desc', 'FULL_TIME', 'FRESHER', 'REMOTE', 'Mumbai', 10.0, 20.0, 'INR', ?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, compId);
            ps.setString(2, title);
            ps.setDate(3, java.sql.Date.valueOf(LocalDate.now().plusMonths(1)));
            ps.setString(4, status);
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }

    @Test
    @DisplayName("P8.3-TX-01: User status mutation rolls back completely if audit log insertion fails")
    void userStatusMutationRollsBackOnAuditFailure() {
        Long targetUserId = createUser("tx.cand@example.com", Role.CANDIDATE, true);

        // Before state
        Boolean initialActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM users WHERE id = ?", Boolean.class, targetUserId);
        assertThat(initialActive).isTrue();

        // Arm failure
        TransactionTestAuditLogRepository.failAudit = true;

        assertThatThrownBy(() -> adminMutationService.updateUserStatus(targetUserId, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated audit insertion failure");

        // After failure: user must still be active!
        Boolean postFailureActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM users WHERE id = ?", Boolean.class, targetUserId);
        assertThat(postFailureActive).isTrue();

        // Zero audit records in DB
        Integer auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Integer.class);
        assertThat(auditCount).isEqualTo(0);

        // Disarm failure: now mutation succeeds and commits both
        TransactionTestAuditLogRepository.failAudit = false;
        adminMutationService.updateUserStatus(targetUserId, false);

        Boolean committedActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM users WHERE id = ?", Boolean.class, targetUserId);
        assertThat(committedActive).isFalse();

        Integer committedAudits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'USER' AND entity_id = ?",
                Integer.class, targetUserId);
        assertThat(committedAudits).isEqualTo(1);
    }

    @Test
    @DisplayName("P8.3-TX-02: Company status mutation rolls back completely if audit log insertion fails")
    void companyStatusMutationRollsBackOnAuditFailure() {
        // Before state
        Boolean initialActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM companies WHERE id = ?", Boolean.class, companyId);
        assertThat(initialActive).isTrue();

        // Arm failure
        TransactionTestAuditLogRepository.failAudit = true;

        assertThatThrownBy(() -> adminMutationService.updateCompanyStatus(companyId, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated audit insertion failure");

        // Company must still be active!
        Boolean postFailureActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM companies WHERE id = ?", Boolean.class, companyId);
        assertThat(postFailureActive).isTrue();

        Integer auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Integer.class);
        assertThat(auditCount).isEqualTo(0);

        // Disarm failure: mutation succeeds
        TransactionTestAuditLogRepository.failAudit = false;
        adminMutationService.updateCompanyStatus(companyId, false);

        Boolean committedActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM companies WHERE id = ?", Boolean.class, companyId);
        assertThat(committedActive).isFalse();

        Integer committedAudits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'COMPANY' AND entity_id = ?",
                Integer.class, companyId);
        assertThat(committedAudits).isEqualTo(1);
    }

    @Test
    @DisplayName("P8.3-TX-03: Job archive rolls back completely if audit log insertion fails")
    void jobArchiveRollsBackOnAuditFailure() {
        Long jobId = createJob(companyId, "Cloud Architect", "ACTIVE");

        // Before state
        String initialStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(initialStatus).isEqualTo("ACTIVE");

        // Arm failure
        TransactionTestAuditLogRepository.failAudit = true;

        assertThatThrownBy(() -> adminMutationService.archiveJob(jobId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated audit insertion failure");

        // Job status must still be ACTIVE!
        String postFailureStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(postFailureStatus).isEqualTo("ACTIVE");

        Integer auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Integer.class);
        assertThat(auditCount).isEqualTo(0);

        // Disarm failure: archive succeeds
        TransactionTestAuditLogRepository.failAudit = false;
        adminMutationService.archiveJob(jobId);

        String committedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(committedStatus).isEqualTo("ARCHIVED");

        Integer committedAudits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'JOB' AND entity_id = ? AND action = 'JOB_ARCHIVED'",
                Integer.class, jobId);
        assertThat(committedAudits).isEqualTo(1);
    }

    @Test
    @DisplayName("P8.3-TX-04: Job restore rolls back completely if audit log insertion fails")
    void jobRestoreRollsBackOnAuditFailure() {
        Long jobId = createJob(companyId, "Archived Architect", "ARCHIVED");

        // Before state
        String initialStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(initialStatus).isEqualTo("ARCHIVED");

        // Arm failure
        TransactionTestAuditLogRepository.failAudit = true;

        assertThatThrownBy(() -> adminMutationService.restoreJob(jobId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated audit insertion failure");

        // Job status must still be ARCHIVED!
        String postFailureStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(postFailureStatus).isEqualTo("ARCHIVED");

        Integer auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Integer.class);
        assertThat(auditCount).isEqualTo(0);

        // Disarm failure: restore succeeds
        TransactionTestAuditLogRepository.failAudit = false;
        adminMutationService.restoreJob(jobId);

        String committedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(committedStatus).isEqualTo("CLOSED");

        Integer committedAudits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'JOB' AND entity_id = ? AND action = 'JOB_RESTORED'",
                Integer.class, jobId);
        assertThat(committedAudits).isEqualTo(1);
    }
}
