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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Map;

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
class AdminCompanyModerationControllerTest {

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
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM audit_logs");
        jdbcTemplate.execute("DELETE FROM application_status_history");
        jdbcTemplate.execute("DELETE FROM applications");
        jdbcTemplate.execute("DELETE FROM job_skills");
        jdbcTemplate.execute("DELETE FROM jobs");
        jdbcTemplate.execute("DELETE FROM recruiters");
        jdbcTemplate.execute("DELETE FROM candidates");
        jdbcTemplate.execute("DELETE FROM companies");
        jdbcTemplate.execute("DELETE FROM users WHERE email != 'admin@careerconnect.com'");
    }

    private Long createCompany(String name, boolean active) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO companies (name, industry, location, website, description, company_size, is_active, created_at, updated_at) " +
                            "VALUES (?, 'Tech', 'Bangalore', 'https://example.com', 'Tech Corp', '51-200', ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, name);
            ps.setBoolean(2, active);
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }

    private Long createJob(Long companyId, String title, String status) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO jobs (company_id, title, description, job_type, experience_level, work_mode, location, " +
                            "salary_min_lpa, salary_max_lpa, salary_currency, application_deadline, status, created_at, updated_at) " +
                            "VALUES (?, ?, 'Desc', 'FULL_TIME', 'FRESHER', 'REMOTE', 'Bangalore', 10.0, 20.0, 'INR', ?, ?, NOW(), NOW())",
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

    @Test
    @DisplayName("P8.3-CMP-01: Admin successfully deactivates an active company and records exactly one audit record")
    void adminDeactivatesActiveCompany() throws Exception {
        Long companyId = createCompany("Alpha Corp", true);

        mockMvc.perform(patch("/api/admin/companies/{companyId}/status", companyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(companyId.intValue())))
                .andExpect(jsonPath("$.data.active", is(false)));

        // Verify DB
        Boolean isActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM companies WHERE id = ?", Boolean.class, companyId);
        assertThat(isActive).isFalse();

        // Verify audit log
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'COMPANY' AND entity_id = ?",
                Integer.class, companyId);
        assertThat(auditCount).isEqualTo(1);

        Long actorId = jdbcTemplate.queryForObject(
                "SELECT actor_user_id FROM audit_logs WHERE entity_type = 'COMPANY' AND entity_id = ?",
                Long.class, companyId);
        String action = jdbcTemplate.queryForObject(
                "SELECT action FROM audit_logs WHERE entity_type = 'COMPANY' AND entity_id = ?",
                String.class, companyId);
        String metadataStr = jdbcTemplate.queryForObject(
                "SELECT metadata FROM audit_logs WHERE entity_type = 'COMPANY' AND entity_id = ?",
                String.class, companyId);

        assertThat(actorId).isEqualTo(adminUserId);
        assertThat(action).isEqualTo("COMPANY_DEACTIVATED");
        assertThat(metadataStr).contains("previousIsActive").contains("newIsActive");
    }

    @Test
    @DisplayName("P8.3-CMP-02: Admin successfully reactivates an inactive company and records exactly one audit record")
    void adminReactivatesInactiveCompany() throws Exception {
        Long companyId = createCompany("Beta Corp", false);

        mockMvc.perform(patch("/api/admin/companies/{companyId}/status", companyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(companyId.intValue())))
                .andExpect(jsonPath("$.data.active", is(true)));

        Boolean isActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM companies WHERE id = ?", Boolean.class, companyId);
        assertThat(isActive).isTrue();

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'COMPANY' AND entity_id = ?",
                Integer.class, companyId);
        assertThat(auditCount).isEqualTo(1);

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT actor_user_id, action FROM audit_logs WHERE entity_type = 'COMPANY' AND entity_id = ?",
                companyId);
        assertThat(audit.get("actor_user_id")).isEqualTo(adminUserId);
        assertThat(audit.get("action")).isEqualTo("COMPANY_REACTIVATED");
    }

    @Test
    @DisplayName("P8.3-CMP-03: Company deactivation hides jobs from public discovery; reactivation restores visibility")
    void companyDeactivationHidesJobsFromPublicDiscovery() throws Exception {
        Long companyId = createCompany("Discovery Corp", true);
        Long jobId = createJob(companyId, "Backend Engineer", "ACTIVE");

        // 1. When company is active, job is visible in public discovery
        mockMvc.perform(get("/api/jobs")
                        .param("q", "Backend Engineer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(jobId.intValue())));

        mockMvc.perform(get("/api/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(jobId.intValue())));

        // 2. Admin deactivates the company
        mockMvc.perform(patch("/api/admin/companies/{companyId}/status", companyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isOk());

        // 3. Now public discovery must hide the job!
        mockMvc.perform(get("/api/jobs")
                        .param("q", "Backend Engineer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(get("/api/jobs/{id}", jobId))
                .andExpect(status().isNotFound());

        // 4. Existing job retains its ACTIVE status in DB (no mass status mutation)
        String jobStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(jobStatus).isEqualTo("ACTIVE");

        // 5. Admin reactivates the company
        mockMvc.perform(patch("/api/admin/companies/{companyId}/status", companyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": true}"))
                .andExpect(status().isOk());

        // 6. Job is visible in public discovery again!
        mockMvc.perform(get("/api/jobs")
                        .param("q", "Backend Engineer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(jobId.intValue())));
    }

    @Test
    @DisplayName("P8.3-CMP-04: Same-state transitions return 422 INVALID_STATUS_TRANSITION and create no audit record")
    void sameStateTransitionsRejected() throws Exception {
        Long activeCompanyId = createCompany("Active Corp", true);
        Long inactiveCompanyId = createCompany("Inactive Corp", false);

        // Active -> Active rejected
        mockMvc.perform(patch("/api/admin/companies/{companyId}/status", activeCompanyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": true}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATUS_TRANSITION")));

        // Inactive -> Inactive rejected
        mockMvc.perform(patch("/api/admin/companies/{companyId}/status", inactiveCompanyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATUS_TRANSITION")));

        Integer auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Integer.class);
        assertThat(auditCount).isEqualTo(0);
    }

    @Test
    @DisplayName("P8.3-CMP-05: Non-existent company returns 404 RESOURCE_NOT_FOUND")
    void nonExistentCompanyReturns404() throws Exception {
        mockMvc.perform(patch("/api/admin/companies/{companyId}/status", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("P8.3-CMP-06: Guest (401), Candidate (403), and Recruiter (403) are denied access")
    void authorizationChecks() throws Exception {
        Long companyId = createCompany("Auth Corp", true);

        // Guest -> 401
        mockMvc.perform(patch("/api/admin/companies/{companyId}/status", companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isUnauthorized());

        // Candidate -> 403
        mockMvc.perform(patch("/api/admin/companies/{companyId}/status", companyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isForbidden());

        // Recruiter -> 403
        mockMvc.perform(patch("/api/admin/companies/{companyId}/status", companyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isForbidden());
    }
}
