package com.careerconnect.admin;

import com.careerconnect.common.model.Role;
import com.careerconnect.common.util.JdbcUtils;
import com.careerconnect.security.JwtService;
import com.careerconnect.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminJobModerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private Long adminUserId;
    private String adminToken;
    private String candidateToken;
    private String recruiterToken;
    private Long companyId;

    @BeforeEach
    void setUp() {
        tearDown();

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
        adminToken = jwtService.generateToken(adminUser);

        SecurityUser candidateUser = new SecurityUser(998L, "cand@example.com", "HASH", Role.CANDIDATE, "Candidate", 55L, null, null, true);
        candidateToken = jwtService.generateToken(candidateUser);

        SecurityUser recruiterUser = new SecurityUser(999L, "rec@example.com", "HASH", Role.RECRUITER, "Recruiter", null, 66L, 77L, true);
        recruiterToken = jwtService.generateToken(recruiterUser);

        // Create standard active test company
        KeyHolder ckh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO companies (name, industry, location, website, description, company_size, is_active, created_at, updated_at) " +
                            "VALUES ('Mod Corp', 'Engineering', 'Hyderabad', 'https://example.com', 'Tech', '11-50', true, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            return ps;
        }, ckh);
        companyId = JdbcUtils.extractGeneratedId(ckh);
    }

    @AfterEach
    void tearDown() {
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

    private Long createJob(String title, String status) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO jobs (company_id, title, description, job_type, experience_level, work_mode, location, " +
                            "salary_min_lpa, salary_max_lpa, salary_currency, application_deadline, status, created_at, updated_at) " +
                            "VALUES (?, ?, 'Job Desc', 'FULL_TIME', 'FRESHER', 'REMOTE', 'Hyderabad', 12.0, 18.0, 'INR', ?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, companyId);
            ps.setString(2, title);
            ps.setDate(3, java.sql.Date.valueOf(LocalDate.now().plusMonths(1)));
            ps.setString(4, status);
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }

    private Long createCandidate(String email, String fullName) {
        KeyHolder ukh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO users (email, password_hash, role, is_active, created_at, updated_at) " +
                            "VALUES (?, 'HASH', 'CANDIDATE', true, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, email);
            return ps;
        }, ukh);
        Long userId = JdbcUtils.extractGeneratedId(ukh);

        KeyHolder ckh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO candidates (user_id, full_name, created_at, updated_at) " +
                            "VALUES (?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, userId);
            ps.setString(2, fullName);
            return ps;
        }, ckh);
        return JdbcUtils.extractGeneratedId(ckh);
    }

    private Long createApplication(Long candidateId, Long jobId, String status, String candidateSnapshot, String resumeSnapshot) {
        KeyHolder akh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO applications (candidate_id, job_id, status, cover_letter, candidate_snapshot, resume_snapshot, applied_at, created_at, updated_at) " +
                            "VALUES (?, ?, ?, 'Cover letter', ?, ?, NOW(), NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, candidateId);
            ps.setLong(2, jobId);
            ps.setString(3, status);
            ps.setString(4, candidateSnapshot);
            ps.setString(5, resumeSnapshot);
            return ps;
        }, akh);
        return JdbcUtils.extractGeneratedId(akh);
    }

    @Test
    @DisplayName("P8.3-JOB-01: Admin archives DRAFT, ACTIVE, and CLOSED jobs successfully and records audit logs")
    void adminArchivesJobsAcrossLifecycleStates() throws Exception {
        Long draftJobId = createJob("Draft Role", "DRAFT");
        Long activeJobId = createJob("Active Role", "ACTIVE");
        Long closedJobId = createJob("Closed Role", "CLOSED");

        // 1. DRAFT -> ARCHIVED
        mockMvc.perform(patch("/api/admin/jobs/{jobId}/archive", draftJobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("ARCHIVED")));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, draftJobId))
                .isEqualTo("ARCHIVED");

        // 2. ACTIVE -> ARCHIVED
        mockMvc.perform(patch("/api/admin/jobs/{jobId}/archive", activeJobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ARCHIVED")));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, activeJobId))
                .isEqualTo("ARCHIVED");

        // 3. CLOSED -> ARCHIVED
        mockMvc.perform(patch("/api/admin/jobs/{jobId}/archive", closedJobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ARCHIVED")));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, closedJobId))
                .isEqualTo("ARCHIVED");

        // Exactly 3 audit logs recorded
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'JOB_ARCHIVED'", Integer.class);
        assertThat(auditCount).isEqualTo(3);
    }

    @Test
    @DisplayName("P8.3-JOB-02: Archiving an already ARCHIVED job is rejected with 422 INVALID_STATUS_TRANSITION")
    void archivingArchivedJobRejected() throws Exception {
        Long archivedJobId = createJob("Archived Role", "ARCHIVED");

        mockMvc.perform(patch("/api/admin/jobs/{jobId}/archive", archivedJobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATUS_TRANSITION")));

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs", Integer.class);
        assertThat(auditCount).isEqualTo(0);
    }

    @Test
    @DisplayName("P8.3-JOB-03: Archived job is hidden from public discovery endpoints")
    void archivedJobHiddenFromPublicDiscovery() throws Exception {
        Long jobId = createJob("Specialist Engineer", "ACTIVE");

        // Visible before archive
        mockMvc.perform(get("/api/jobs/{id}", jobId))
                .andExpect(status().isOk());

        // Archive job
        mockMvc.perform(patch("/api/admin/jobs/{jobId}/archive", jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Hidden from public discovery after archive
        mockMvc.perform(get("/api/jobs/{id}", jobId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/jobs").param("q", "Specialist Engineer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("P8.3-JOB-04: Archiving job preserves existing applications, statuses, and immutable snapshots")
    void archivingJobPreservesApplicationsAndSnapshots() throws Exception {
        Long candId = createCandidate("cand.app.safe@example.com", "Safe Candidate");
        Long jobId = createJob("Frontend Lead", "ACTIVE");

        String candSnapshot = "{\"fullName\": \"Original Candidate\", \"headline\": \"Dev\"}";
        String resumeSnapshot = "{\"originalFilename\": \"resume_v1.pdf\", \"storageKey\": \"resumes/uuid.pdf\"}";
        Long appId = createApplication(candId, jobId, "UNDER_REVIEW", candSnapshot, resumeSnapshot);

        // Archive the job
        mockMvc.perform(patch("/api/admin/jobs/{jobId}/archive", jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Assert application row in DB is completely intact
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, appId);
        String candSnapStr = jdbcTemplate.queryForObject(
                "SELECT candidate_snapshot FROM applications WHERE id = ?", String.class, appId);
        String resumeSnapStr = jdbcTemplate.queryForObject(
                "SELECT resume_snapshot FROM applications WHERE id = ?", String.class, appId);

        assertThat(status).isEqualTo("UNDER_REVIEW");
        assertThat(candSnapStr).contains("Original Candidate");
        assertThat(resumeSnapStr).contains("resume_v1.pdf");
    }

    @Test
    @DisplayName("P8.3-JOB-05: Restoring an ARCHIVED job transitions it strictly to CLOSED and records exactly one audit log")
    void restoreArchivedJobToClosed() throws Exception {
        Long jobId = createJob("Restore Target", "ARCHIVED");

        mockMvc.perform(patch("/api/admin/jobs/{jobId}/restore", jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(jobId.intValue())))
                .andExpect(jsonPath("$.data.status", is("CLOSED")));

        // Verify status in DB
        String jobStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(jobStatus).isEqualTo("CLOSED");

        // Verify audit log
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'JOB_RESTORED' AND entity_id = ?",
                Integer.class, jobId);
        assertThat(auditCount).isEqualTo(1);

        Long actorId = jdbcTemplate.queryForObject(
                "SELECT actor_user_id FROM audit_logs WHERE action = 'JOB_RESTORED' AND entity_id = ?",
                Long.class, jobId);
        String metadataStr = jdbcTemplate.queryForObject(
                "SELECT metadata FROM audit_logs WHERE action = 'JOB_RESTORED' AND entity_id = ?",
                String.class, jobId);

        assertThat(actorId).isEqualTo(adminUserId);
        assertThat(metadataStr).contains("ARCHIVED").contains("CLOSED");
    }

    @Test
    @DisplayName("P8.3-JOB-06: Restoring DRAFT, ACTIVE, or CLOSED jobs is rejected with 422 INVALID_STATUS_TRANSITION")
    void invalidJobRestoreRejected() throws Exception {
        Long draftId = createJob("Draft Job", "DRAFT");
        Long activeId = createJob("Active Job", "ACTIVE");
        Long closedId = createJob("Closed Job", "CLOSED");

        // 1. DRAFT -> restore rejected
        mockMvc.perform(patch("/api/admin/jobs/{jobId}/restore", draftId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATUS_TRANSITION")));

        // 2. ACTIVE -> restore rejected
        mockMvc.perform(patch("/api/admin/jobs/{jobId}/restore", activeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATUS_TRANSITION")));

        // 3. CLOSED -> restore rejected
        mockMvc.perform(patch("/api/admin/jobs/{jobId}/restore", closedId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATUS_TRANSITION")));

        // Zero audit records created
        Integer auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Integer.class);
        assertThat(auditCount).isEqualTo(0);
    }

    @Test
    @DisplayName("P8.3-JOB-07: Non-existent or soft-deleted jobs return 404 RESOURCE_NOT_FOUND on archive/restore")
    void nonExistentOrDeletedJobReturns404() throws Exception {
        // Non-existent
        mockMvc.perform(patch("/api/admin/jobs/{jobId}/archive", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("RESOURCE_NOT_FOUND")));

        mockMvc.perform(patch("/api/admin/jobs/{jobId}/restore", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("RESOURCE_NOT_FOUND")));

        // Soft-deleted job
        Long deletedJobId = createJob("Deleted Job", "ACTIVE");
        jdbcTemplate.update("UPDATE jobs SET deleted_at = NOW() WHERE id = ?", deletedJobId);

        mockMvc.perform(patch("/api/admin/jobs/{jobId}/archive", deletedJobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/admin/jobs/{jobId}/restore", deletedJobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("P8.3-JOB-08: Guest (401), Candidate (403), and Recruiter (403) are denied archive and restore access")
    void authorizationChecks() throws Exception {
        Long jobId = createJob("Auth Target", "ACTIVE");

        // Archive: Guest 401, Candidate 403, Recruiter 403
        mockMvc.perform(patch("/api/admin/jobs/{jobId}/archive", jobId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/admin/jobs/{jobId}/archive", jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candidateToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/jobs/{jobId}/archive", jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recruiterToken))
                .andExpect(status().isForbidden());

        // Restore: Guest 401, Candidate 403, Recruiter 403
        mockMvc.perform(patch("/api/admin/jobs/{jobId}/restore", jobId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/admin/jobs/{jobId}/restore", jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candidateToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/jobs/{jobId}/restore", jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recruiterToken))
                .andExpect(status().isForbidden());
    }
}
