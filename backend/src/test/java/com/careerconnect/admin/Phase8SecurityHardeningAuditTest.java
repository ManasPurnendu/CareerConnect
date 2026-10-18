package com.careerconnect.admin;

import com.careerconnect.admin.audit.repository.AuditLogRepository;
import com.careerconnect.admin.audit.service.AuditLogService;
import com.careerconnect.admin.dto.AdminUpdateCompanyStatusRequest;
import com.careerconnect.admin.dto.AdminUpdateUserStatusRequest;
import com.careerconnect.admin.repository.AdminMutationRepository;
import com.careerconnect.admin.repository.AdminQueryRepository;
import com.careerconnect.admin.service.AdminMutationService;
import com.careerconnect.auth.dto.LoginRequest;
import com.careerconnect.candidate.repository.CandidateResumeRepository;
import com.careerconnect.common.exception.BusinessRuleException;
import com.careerconnect.common.model.Role;
import com.careerconnect.common.util.JdbcUtils;
import com.careerconnect.jobs.model.JobStatus;
import com.careerconnect.security.JwtService;
import com.careerconnect.security.SecurityUser;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P8.4 Security Hardening & Comprehensive Admin Security Audit Test Suite.
 * Enforces all 30 adversarial security invariants required by CC-MASTER-001 v1.2 specification.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase8SecurityHardeningAuditTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AdminMutationRepository adminMutationRepository;

    @Autowired
    private AdminQueryRepository adminQueryRepository;

    @Autowired
    private CandidateResumeRepository candidateResumeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long adminUserId;
    private String adminToken;

    private Long candidateUserId;
    private Long candidateId;
    private String candidateToken;

    private Long recruiterUserId;
    private Long recruiterId;
    private Long companyId;
    private String recruiterToken;

    @BeforeEach
    void setUp() {
        tearDown();

        // 1. Ensure Admin User
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

        // 2. Setup standard test company, recruiter, and candidate
        companyId = insertCompany("P84 Audit Corp " + UUID.randomUUID(), true);

        recruiterUserId = insertUser("recruiter." + UUID.randomUUID() + "@example.com", "Password123!", Role.RECRUITER, true);
        recruiterId = insertRecruiter(recruiterUserId, companyId, "Audit Recruiter");
        SecurityUser recUser = new SecurityUser(recruiterUserId, "recruiter@example.com", "HASH", Role.RECRUITER, "Audit Recruiter", null, recruiterId, companyId, true);
        recruiterToken = jwtService.generateToken(recUser);

        candidateUserId = insertUser("candidate." + UUID.randomUUID() + "@example.com", "Password123!", Role.CANDIDATE, true);
        candidateId = insertCandidate(candidateUserId, "Audit Candidate");
        SecurityUser candUser = new SecurityUser(candidateUserId, "candidate@example.com", "HASH", Role.CANDIDATE, "Audit Candidate", candidateId, null, null, true);
        candidateToken = jwtService.generateToken(candUser);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM audit_logs");
        jdbcTemplate.execute("DELETE FROM application_status_history");
        jdbcTemplate.execute("DELETE FROM applications");
        jdbcTemplate.execute("DELETE FROM resumes");
        jdbcTemplate.execute("DELETE FROM job_skills");
        jdbcTemplate.execute("DELETE FROM jobs");
        jdbcTemplate.execute("DELETE FROM recruiters");
        jdbcTemplate.execute("DELETE FROM candidates");
        jdbcTemplate.execute("DELETE FROM companies");
        jdbcTemplate.execute("DELETE FROM users WHERE role != 'ADMIN'");
    }

    // =========================================================================
    // 1. Guest -> Admin endpoint -> 401
    // =========================================================================
    @Test
    @DisplayName("1. Guest accessing any admin endpoint is rejected with 401 Unauthorized")
    void test01_guestAccess_returns401() throws Exception {
        String[] adminEndpoints = {
                "/api/admin/dashboard",
                "/api/admin/users",
                "/api/admin/companies",
                "/api/admin/jobs",
                "/api/admin/applications",
                "/api/admin/audit-logs"
        };

        for (String endpoint : adminEndpoints) {
            mockMvc.perform(get(endpoint).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        }

        mockMvc.perform(patch("/api/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(patch("/api/admin/companies/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(patch("/api/admin/jobs/1/archive")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(patch("/api/admin/jobs/1/restore")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    // =========================================================================
    // 2. Candidate -> Admin endpoint -> 403
    // =========================================================================
    @Test
    @DisplayName("2. Candidate accessing any admin endpoint is rejected with 403 Forbidden")
    void test02_candidateAccess_returns403() throws Exception {
        String[] adminEndpoints = {
                "/api/admin/dashboard",
                "/api/admin/users",
                "/api/admin/companies",
                "/api/admin/jobs",
                "/api/admin/applications",
                "/api/admin/audit-logs"
        };

        for (String endpoint : adminEndpoints) {
            mockMvc.perform(get(endpoint)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + candidateToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        }

        mockMvc.perform(patch("/api/admin/users/1/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    // =========================================================================
    // 3. Recruiter -> Admin endpoint -> 403
    // =========================================================================
    @Test
    @DisplayName("3. Recruiter accessing any admin endpoint is rejected with 403 Forbidden")
    void test03_recruiterAccess_returns403() throws Exception {
        String[] adminEndpoints = {
                "/api/admin/dashboard",
                "/api/admin/users",
                "/api/admin/companies",
                "/api/admin/jobs",
                "/api/admin/applications",
                "/api/admin/audit-logs"
        };

        for (String endpoint : adminEndpoints) {
            mockMvc.perform(get(endpoint)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + recruiterToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        }

        mockMvc.perform(patch("/api/admin/jobs/1/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    // =========================================================================
    // 4. Admin -> Admin endpoint -> Success (200 OK)
    // =========================================================================
    @Test
    @DisplayName("4. Authenticated Admin accessing admin endpoints succeeds with 200 OK")
    void test04_adminAccess_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.users.total").isNumber());

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // =========================================================================
    // 5. Actor identity tampering ignored
    // =========================================================================
    @Test
    @DisplayName("5. Actor identity tampering in body or headers is ignored; audit uses authenticated admin")
    void test05_actorIdentityTampering_ignoredInAudit() throws Exception {
        String tamperingBody = """
                {
                    "isActive": false,
                    "actorUserId": 999999,
                    "adminId": 999999,
                    "userId": 888888
                }
                """;

        mockMvc.perform(patch("/api/admin/users/" + candidateUserId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .header("X-Actor-User-Id", "999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tamperingBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        // Invariant: The audit record actor_user_id MUST be adminUserId, NEVER 999999 or 888888
        List<Map<String, Object>> logs = jdbcTemplate.queryForList(
                "SELECT actor_user_id, action, entity_id FROM audit_logs WHERE action = 'USER_DEACTIVATED'");
        assertThat(logs).hasSize(1);
        assertThat(((Number) logs.get(0).get("actor_user_id")).longValue()).isEqualTo(adminUserId);
        assertThat(((Number) logs.get(0).get("entity_id")).longValue()).isEqualTo(candidateUserId);
    }

    // =========================================================================
    // 6. Role tampering ignored
    // =========================================================================
    @Test
    @DisplayName("6. Role tampering in client payloads cannot escalate or alter user roles")
    void test06_roleTampering_cannotEscalateOrMutate() throws Exception {
        // Candidate trying to include role ADMIN in an update request
        String tamperingPayload = "{\"isActive\": false, \"role\": \"ADMIN\"}";

        mockMvc.perform(patch("/api/admin/users/" + candidateUserId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tamperingPayload))
                .andExpect(status().isForbidden());

        // Admin updates status with role injected in body -> role remains CANDIDATE
        mockMvc.perform(patch("/api/admin/users/" + candidateUserId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tamperingPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CANDIDATE"));

        String roleInDb = jdbcTemplate.queryForObject("SELECT role FROM users WHERE id = ?", String.class, candidateUserId);
        assertThat(roleInDb).isEqualTo("CANDIDATE");
    }

    // =========================================================================
    // 7. Self-deactivation blocked
    // =========================================================================
    @Test
    @DisplayName("7. Admin cannot deactivate their own account; produces zero audit logs")
    void test07_selfDeactivation_blockedWith422AndNoAudit() throws Exception {
        long initialAuditCount = auditLogRepository.countAuditLogs(null, null, null, null);

        mockMvc.perform(patch("/api/admin/users/" + adminUserId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.message").value(containsString("Administrators cannot deactivate their own account")));

        Boolean isActive = jdbcTemplate.queryForObject("SELECT is_active FROM users WHERE id = ?", Boolean.class, adminUserId);
        assertThat(isActive).isTrue();

        long postAuditCount = auditLogRepository.countAuditLogs(null, null, null, null);
        assertThat(postAuditCount).isEqualTo(initialAuditCount);
    }

    // =========================================================================
    // 8. Deactivated user login blocked
    // =========================================================================
    @Test
    @DisplayName("8. Deactivated users cannot login; reactivation restores login ability")
    void test08_deactivatedUserLogin_blockedAndReactivationRestores() throws Exception {
        String rawPassword = "CandidatePassword123!";
        Long targetUserId = insertUser("deact.test." + UUID.randomUUID() + "@example.com", rawPassword, Role.CANDIDATE, true);
        insertCandidate(targetUserId, "Deact Test Candidate");

        String email = jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String.class, targetUserId);

        // 1. Deactivate via admin
        mockMvc.perform(patch("/api/admin/users/" + targetUserId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isOk());

        // 2. Attempt login -> 403 Forbidden
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, rawPassword))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("account has been deactivated")));

        // 3. Reactivate via admin
        mockMvc.perform(patch("/api/admin/users/" + targetUserId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": true}"))
                .andExpect(status().isOk());

        // 4. Attempt login -> 200 OK with valid JWT
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, rawPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.user.email").value(email));
    }

    // =========================================================================
    // 9. Company deactivation hides jobs
    // =========================================================================
    @Test
    @DisplayName("9. Company deactivation excludes its jobs from public discovery without mutating jobs")
    void test09_companyDeactivation_hidesJobsInPublicDiscovery() throws Exception {
        Long testCompId = insertCompany("Visible Corp " + UUID.randomUUID(), true);
        Long testJobId = insertJob(testCompId, "Visible Software Engineer", JobStatus.ACTIVE);

        // Public discovery returns job
        mockMvc.perform(get("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + testJobId + ")]").exists());

        // Deactivate company
        mockMvc.perform(patch("/api/admin/companies/" + testCompId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isOk());

        // Public discovery now excludes the job
        mockMvc.perform(get("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + testJobId + ")]").doesNotExist());

        // Public job detail returns 404
        mockMvc.perform(get("/api/jobs/" + testJobId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // Invariant: Job in database retains its ACTIVE status and is NOT deleted
        String statusInDb = jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, testJobId);
        assertThat(statusInDb).isEqualTo("ACTIVE");
    }

    // =========================================================================
    // 10. Company reactivation restores visibility
    // =========================================================================
    @Test
    @DisplayName("10. Company reactivation restores job visibility in public discovery")
    void test10_companyReactivation_restoresJobVisibility() throws Exception {
        Long testCompId = insertCompany("Reactivate Corp " + UUID.randomUUID(), false);
        Long testJobId = insertJob(testCompId, "Restored Software Engineer", JobStatus.ACTIVE);

        // Reactivate company
        mockMvc.perform(patch("/api/admin/companies/" + testCompId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));

        // Public discovery now includes the job
        mockMvc.perform(get("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + testJobId + ")]").exists());

        mockMvc.perform(get("/api/jobs/" + testJobId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(testJobId));
    }

    // =========================================================================
    // 11. Job archive valid transitions
    // =========================================================================
    @Test
    @DisplayName("11. Valid job archive transitions: DRAFT, ACTIVE, CLOSED all transition to ARCHIVED with audit")
    void test11_jobArchive_validTransitions() throws Exception {
        Long draftJobId = insertJob(companyId, "Draft Job", JobStatus.DRAFT);
        Long activeJobId = insertJob(companyId, "Active Job", JobStatus.ACTIVE);
        Long closedJobId = insertJob(companyId, "Closed Job", JobStatus.CLOSED);

        // Archive DRAFT
        mockMvc.perform(patch("/api/admin/jobs/" + draftJobId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        // Archive ACTIVE
        mockMvc.perform(patch("/api/admin/jobs/" + activeJobId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        // Archive CLOSED
        mockMvc.perform(patch("/api/admin/jobs/" + closedJobId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        long archiveAudits = auditLogRepository.countAuditLogs(adminUserId, "JOB_ARCHIVED", "JOB", null);
        assertThat(archiveAudits).isEqualTo(3);
    }

    // =========================================================================
    // 12. Job archive invalid transitions
    // =========================================================================
    @Test
    @DisplayName("12. Job archive rejected when job is already ARCHIVED; produces zero audit logs")
    void test12_jobArchive_invalidTransitionRejected() throws Exception {
        Long job = insertJob(companyId, "Archived Job", JobStatus.ARCHIVED);
        long initialAudits = auditLogRepository.countAuditLogs(null, null, null, null);

        mockMvc.perform(patch("/api/admin/jobs/" + job + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.message").value(containsString("already archived")));

        long postAudits = auditLogRepository.countAuditLogs(null, null, null, null);
        assertThat(postAudits).isEqualTo(initialAudits);
    }

    // =========================================================================
    // 13. Job restore only ARCHIVED -> CLOSED
    // =========================================================================
    @Test
    @DisplayName("13. Job restore strictly allows ARCHIVED -> CLOSED; rejects all other source states")
    void test13_jobRestore_onlyArchivedToClosed() throws Exception {
        Long archivedJob = insertJob(companyId, "Archived Job", JobStatus.ARCHIVED);
        Long draftJob = insertJob(companyId, "Draft Job", JobStatus.DRAFT);
        Long activeJob = insertJob(companyId, "Active Job", JobStatus.ACTIVE);
        Long closedJob = insertJob(companyId, "Closed Job", JobStatus.CLOSED);

        // Restore ARCHIVED -> CLOSED (valid)
        mockMvc.perform(patch("/api/admin/jobs/" + archivedJob + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // Reject DRAFT -> CLOSED via restore
        mockMvc.perform(patch("/api/admin/jobs/" + draftJob + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // Reject ACTIVE -> CLOSED via restore
        mockMvc.perform(patch("/api/admin/jobs/" + activeJob + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // Reject CLOSED -> CLOSED via restore
        mockMvc.perform(patch("/api/admin/jobs/" + closedJob + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    // =========================================================================
    // 14. Archived job public invisibility
    // =========================================================================
    @Test
    @DisplayName("14. Archived jobs do not appear in public discovery; restored jobs (CLOSED) remain non-public")
    void test14_archivedJob_publicInvisibility() throws Exception {
        Long job = insertJob(companyId, "Secret Archival Job", JobStatus.ACTIVE);

        // Archive job
        mockMvc.perform(patch("/api/admin/jobs/" + job + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Invisibility in public list
        mockMvc.perform(get("/api/jobs").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + job + ")]").doesNotExist());

        // Invisibility in public detail
        mockMvc.perform(get("/api/jobs/" + job).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // Restore to CLOSED
        mockMvc.perform(patch("/api/admin/jobs/" + job + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // Restored CLOSED jobs must STILL not appear in public discovery list
        mockMvc.perform(get("/api/jobs").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + job + ")]").doesNotExist());
    }

    // =========================================================================
    // 15. Application preservation during archive
    // =========================================================================
    @Test
    @DisplayName("15. Archiving a job does not mutate or delete existing applications")
    void test15_applicationPreservationDuringArchive() throws Exception {
        Long job = insertJob(companyId, "Role With App", JobStatus.ACTIVE);
        Long app = insertApplication(job, candidateId, "APPLIED");

        // Archive job
        mockMvc.perform(patch("/api/admin/jobs/" + job + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Verify application in DB is untouched
        String appStatus = jdbcTemplate.queryForObject("SELECT status FROM applications WHERE id = ?", String.class, app);
        assertThat(appStatus).isEqualTo("APPLIED");
    }

    // =========================================================================
    // 16. Snapshot preservation during archive
    // =========================================================================
    @Test
    @DisplayName("16. Archiving a job leaves candidate_snapshot and resume_snapshot strictly immutable")
    void test16_snapshotPreservationDuringArchive() throws Exception {
        Long job = insertJob(companyId, "Snapshot Job", JobStatus.ACTIVE);
        Long app = insertApplication(job, candidateId, "APPLIED");

        String candSnapBefore = jdbcTemplate.queryForObject(
                "SELECT candidate_snapshot FROM applications WHERE id = ?", String.class, app);
        String resumeSnapBefore = jdbcTemplate.queryForObject(
                "SELECT resume_snapshot FROM applications WHERE id = ?", String.class, app);

        // Archive job
        mockMvc.perform(patch("/api/admin/jobs/" + job + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        String candSnapAfter = jdbcTemplate.queryForObject(
                "SELECT candidate_snapshot FROM applications WHERE id = ?", String.class, app);
        String resumeSnapAfter = jdbcTemplate.queryForObject(
                "SELECT resume_snapshot FROM applications WHERE id = ?", String.class, app);

        assertThat(candSnapAfter).isEqualTo(candSnapBefore);
        assertThat(resumeSnapAfter).isEqualTo(resumeSnapBefore);
    }

    // =========================================================================
    // 17. Audit append-only behavior
    // =========================================================================
    @Test
    @DisplayName("17. Audit logs are strictly append-only; update, replace, and delete HTTP methods are rejected")
    void test17_auditLog_appendOnlySemantics() throws Exception {
        // Attempt POST, PUT, PATCH, DELETE on /api/admin/audit-logs -> 405 Method Not Allowed
        mockMvc.perform(post("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\": \"FAKE\"}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(put("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\": \"MODIFIED\"}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(patch("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\": \"MODIFIED\"}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isMethodNotAllowed());

        // Attempting to delete or modify individual log by ID is not mapped -> 404 Not Found
        mockMvc.perform(delete("/api/admin/audit-logs/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        // Repository level verification: no delete or update methods exist on AuditLogRepository
        for (java.lang.reflect.Method m : AuditLogRepository.class.getDeclaredMethods()) {
            assertThat(m.getName().toLowerCase())
                    .doesNotContain("delete")
                    .doesNotContain("update");
        }
    }

    // =========================================================================
    // 18. Failed mutation produces no audit
    // =========================================================================
    @Test
    @DisplayName("18. Failed or rejected mutations produce zero audit log records")
    void test18_failedMutationProducesZeroAuditRecords() throws Exception {
        long initialAudits = auditLogRepository.countAuditLogs(null, null, null, null);

        // 1. Non-existent user
        mockMvc.perform(patch("/api/admin/users/9999999/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isNotFound());

        // 2. Non-existent company
        mockMvc.perform(patch("/api/admin/companies/9999999/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isNotFound());

        // 3. Non-existent job archive
        mockMvc.perform(patch("/api/admin/jobs/9999999/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // 4. Same state transition on company
        mockMvc.perform(patch("/api/admin/companies/" + companyId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": true}"))
                .andExpect(status().isUnprocessableEntity());

        long finalAudits = auditLogRepository.countAuditLogs(null, null, null, null);
        assertThat(finalAudits).isEqualTo(initialAudits);
    }

    // =========================================================================
    // 19. Audit failure rolls back mutation (Transaction Atomicity)
    // =========================================================================
    @Test
    @DisplayName("19. Audit log failure rolls back the entire mutation transaction atomically")
    void test19_auditFailureRollsBackMutation() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        AuditLogService failingAuditService = new AuditLogService(auditLogRepository, objectMapper) {
            @Override
            public Long recordEvent(String action, String entityType, Long entityId, Map<String, Object> metadata) {
                throw new RuntimeException("Simulated audit disk failure");
            }
        };

        AdminMutationService serviceWithFailingAudit = new AdminMutationService(
                adminMutationRepository,
                adminQueryRepository,
                failingAuditService
        );

        SecurityUser adminUser = new SecurityUser(adminUserId, "admin@careerconnect.com", "HASH", Role.ADMIN, "Admin", null, null, null, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminUser, null, adminUser.getAuthorities())
        );

        Long testJob = insertJob(companyId, "Rollback Job", JobStatus.ACTIVE);

        assertThatThrownBy(() -> txTemplate.execute(status -> serviceWithFailingAudit.archiveJob(testJob)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated audit disk failure");

        // Status must NOT remain ARCHIVED (rolled back to ACTIVE)
        String statusInDb = jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, testJob);
        assertThat(statusInDb).isEqualTo("ACTIVE");

        // Verify zero audit logs created
        long audits = auditLogRepository.countAuditLogs(null, null, "JOB", testJob);
        assertThat(audits).isEqualTo(0);
    }

    // =========================================================================
    // 20. Cross-resource application protection & platform-wide visibility
    // =========================================================================
    @Test
    @DisplayName("20. Admin can inspect applications platform-wide; Recruiter cannot access admin applications endpoint")
    void test20_crossResourceApplicationProtection() throws Exception {
        Long otherCompany = insertCompany("Foreign Corp", true);
        Long otherJob = insertJob(otherCompany, "Foreign Position", JobStatus.ACTIVE);
        Long otherApp = insertApplication(otherJob, candidateId, "APPLIED");

        // Admin can view application from any company
        mockMvc.perform(get("/api/admin/applications/" + otherApp)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value(otherApp))
                .andExpect(jsonPath("$.data.companyName").value("Foreign Corp"));

        // Recruiter cannot access admin endpoint
        mockMvc.perform(get("/api/admin/applications/" + otherApp)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 21. Resume authorization
    // =========================================================================
    @Test
    @DisplayName("21. Admin resume endpoint is strictly authorized to ADMIN; Guest/Candidate/Recruiter rejected")
    void test21_resumeAuthorization_strictlyAdminOnly() throws Exception {
        Long job = insertJob(companyId, "Resume Position", JobStatus.ACTIVE);
        Long app = insertApplication(job, candidateId, "APPLIED");

        // Create actual physical resume file
        Path resumeDir = Paths.get("./target/test-uploads/resumes");
        Files.createDirectories(resumeDir);
        String storageKey = "secure_test_" + UUID.randomUUID() + ".pdf";
        Path filePath = resumeDir.resolve(storageKey);
        Files.write(filePath, "%PDF-1.4\nTest Resume Content".getBytes(StandardCharsets.UTF_8));
        insertResume(candidateId, storageKey);

        // Guest -> 401
        mockMvc.perform(get("/api/admin/applications/" + app + "/resume?metadata=true"))
                .andExpect(status().isUnauthorized());

        // Candidate -> 403
        mockMvc.perform(get("/api/admin/applications/" + app + "/resume?metadata=true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candidateToken))
                .andExpect(status().isForbidden());

        // Recruiter -> 403
        mockMvc.perform(get("/api/admin/applications/" + app + "/resume?metadata=true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recruiterToken))
                .andExpect(status().isForbidden());

        // Admin -> 200 OK
        mockMvc.perform(get("/api/admin/applications/" + app + "/resume?metadata=true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalFilename").value("test.pdf"));

        // Cleanup test file
        try {
            Files.deleteIfExists(filePath);
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // 22. Resume path traversal protection
    // =========================================================================
    @Test
    @DisplayName("22. Resume access with path traversal keys (../../etc/passwd) safely blocks without leaking server paths")
    void test22_resumePathTraversalProtection() throws Exception {
        Long job = insertJob(companyId, "Traversal Position", JobStatus.ACTIVE);
        Long app = insertApplication(job, candidateId, "APPLIED");

        // Insert resume with malicious path traversal storage key
        insertResume(candidateId, "../../etc/passwd");

        mockMvc.perform(get("/api/admin/applications/" + app + "/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(not(containsString("/etc/passwd"))))
                .andExpect(jsonPath("$.message").value(not(containsString("java.nio.file"))));
    }

    // =========================================================================
    // 23. Sensitive metadata leakage prevention
    // =========================================================================
    @Test
    @DisplayName("23. Admin endpoints never expose password hashes, tokens, or absolute filesystem paths")
    void test23_sensitiveMetadataLeakagePrevention() throws Exception {
        MvcResult usersResult = mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String body = usersResult.getResponse().getContentAsString();
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain("password_hash");
        assertThat(body).doesNotContain("$2a$12$");

        Long job = insertJob(companyId, "Leakage Position", JobStatus.ACTIVE);
        Long app = insertApplication(job, candidateId, "APPLIED");

        MvcResult appResult = mockMvc.perform(get("/api/admin/applications/" + app)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String appBody = appResult.getResponse().getContentAsString();
        assertThat(appBody).doesNotContain("password");
        assertThat(appBody).doesNotContain("storageKey");
        assertThat(appBody).doesNotContain("/Users/");
        assertThat(appBody).doesNotContain("/tmp/");
    }

    // =========================================================================
    // 24. SQL / input hardening
    // =========================================================================
    @Test
    @DisplayName("24. Search queries with SQL injection payloads are safely parameterized and do not alter syntax")
    void test24_sqlInjectionHardening() throws Exception {
        String[] injectionQueries = {
                "' OR 1=1 --",
                "'; DROP TABLE users; --",
                "' UNION SELECT 1, 'admin', 'pass', true, NOW(), NOW() --"
        };

        for (String q : injectionQueries) {
            mockMvc.perform(get("/api/admin/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .param("q", q)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            mockMvc.perform(get("/api/admin/companies")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .param("q", q)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            mockMvc.perform(get("/api/admin/jobs")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .param("q", q)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        // Verify users table is still intact and not dropped
        Long userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        assertThat(userCount).isGreaterThan(0);
    }

    // =========================================================================
    // 25. Pagination boundary protection
    // =========================================================================
    @Test
    @DisplayName("25. Negative page/size and oversized limits are clamped safely without pathological memory use")
    void test25_paginationBoundaryProtection() throws Exception {
        // Negative page and size
        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("page", "-10")
                        .param("size", "-50")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(20));

        // Excessively large size -> clamped to 100
        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "1000000")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.size").value(100));
    }

    // =========================================================================
    // 26. Cross-phase role isolation
    // =========================================================================
    @Test
    @DisplayName("26. Cross-phase role boundaries remain strictly isolated")
    void test26_crossPhaseRoleIsolation() throws Exception {
        // Candidate accessing recruiter company API -> 403
        mockMvc.perform(get("/api/recruiter/company")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // Recruiter accessing admin dashboard -> 403
        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // Admin attempting to mutate candidate job application directly via candidate API without candidate profile -> 403
        mockMvc.perform(post("/api/jobs/1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverLetter\": \"hello\"}"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 27. Stale JWT / account-state behavior
    // =========================================================================
    @Test
    @DisplayName("27. Deactivated user token cannot perform operations against /api/auth/me")
    void test27_staleJwtAccountStateBehavior() throws Exception {
        // Deactivate candidate
        mockMvc.perform(patch("/api/admin/users/" + candidateUserId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isOk());

        // User possessing previously issued valid JWT attempts /api/auth/me -> 403 Forbidden
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("account has been deactivated")));
    }

    // =========================================================================
    // 28. Client ID tampering
    // =========================================================================
    @Test
    @DisplayName("28. Client-supplied body IDs (userId, companyId) cannot override path variable identity")
    void test28_clientIdTamperingIgnored() throws Exception {
        Long userA = insertUser("user.a." + UUID.randomUUID() + "@example.com", "Password123!", Role.CANDIDATE, true);
        Long userB = insertUser("user.b." + UUID.randomUUID() + "@example.com", "Password123!", Role.CANDIDATE, true);

        // Body supplies userB, but path specifies userA
        String tamperingPayload = "{\"isActive\": false, \"userId\": " + userB + ", \"companyId\": 9999}";

        mockMvc.perform(patch("/api/admin/users/" + userA + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tamperingPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userA))
                .andExpect(jsonPath("$.data.active").value(false));

        // userA is updated, userB is NOT updated
        Boolean userAActive = jdbcTemplate.queryForObject("SELECT is_active FROM users WHERE id = ?", Boolean.class, userA);
        Boolean userBActive = jdbcTemplate.queryForObject("SELECT is_active FROM users WHERE id = ?", Boolean.class, userB);

        assertThat(userAActive).isFalse();
        assertThat(userBActive).isTrue();
    }

    // =========================================================================
    // 29. Soft-deleted resource protection
    // =========================================================================
    @Test
    @DisplayName("29. Soft-deleted jobs cannot be archived or restored; returns safe 404")
    void test29_softDeletedResourceProtection() throws Exception {
        Long deletedJob = insertJob(companyId, "Soft Deleted Job", JobStatus.ACTIVE);
        jdbcTemplate.update("UPDATE jobs SET deleted_at = NOW() WHERE id = ?", deletedJob);

        // Attempt archive -> 404
        mockMvc.perform(patch("/api/admin/jobs/" + deletedJob + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Attempt restore -> 404
        mockMvc.perform(patch("/api/admin/jobs/" + deletedJob + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // =========================================================================
    // 30. Duplicate / repeated mutation audit protection
    // =========================================================================
    @Test
    @DisplayName("30. Repeated same-state mutation is rejected with 422 and generates zero audit logs")
    void test30_duplicateRepeatedMutationAuditProtection() throws Exception {
        // Company is already active
        long initialAudits = auditLogRepository.countAuditLogs(null, null, null, null);

        mockMvc.perform(patch("/api/admin/companies/" + companyId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": true}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.message").value(containsString("already active")));

        long postAudits = auditLogRepository.countAuditLogs(null, null, null, null);
        assertThat(postAudits).isEqualTo(initialAudits);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Long insertCompany(String name, boolean isActive) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO companies (name, is_active, created_at, updated_at) VALUES (?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, name);
            ps.setBoolean(2, isActive);
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }

    private Long insertUser(String email, String rawPassword, Role role, boolean isActive) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO users (email, password_hash, role, is_active, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, email);
            ps.setString(2, passwordEncoder.encode(rawPassword));
            ps.setString(3, role.name());
            ps.setBoolean(4, isActive);
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }

    private Long insertRecruiter(Long userId, Long compId, String name) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO recruiters (user_id, company_id, full_name, created_at, updated_at) " +
                            "VALUES (?, ?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, userId);
            ps.setLong(2, compId);
            ps.setString(3, name);
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }

    private Long insertCandidate(Long userId, String name) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO candidates (user_id, full_name, created_at, updated_at) " +
                            "VALUES (?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, userId);
            ps.setString(2, name);
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }

    private Long insertJob(Long compId, String title, JobStatus status) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO jobs (company_id, title, description, job_type, experience_level, " +
                            "work_mode, location, salary_min_lpa, salary_max_lpa, application_deadline, status, " +
                            "created_at, updated_at) " +
                            "VALUES (?, ?, 'Description', 'FULL_TIME', 'FRESHER', 'REMOTE', 'Remote', " +
                            "10.00, 20.00, ?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, compId);
            ps.setString(2, title);
            ps.setDate(3, Date.valueOf(LocalDate.now().plusMonths(1)));
            ps.setString(4, status.name());
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }

    private Long insertApplication(Long jobId, Long candId, String appStatus) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO applications (job_id, candidate_id, status, cover_letter, " +
                            "candidate_snapshot, resume_snapshot, created_at, updated_at) " +
                            "VALUES (?, ?, ?, 'Cover letter', '{\"skills\":[\"Java\"]}', '{\"filename\":\"resume.pdf\"}', NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, jobId);
            ps.setLong(2, candId);
            ps.setString(3, appStatus);
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }

    private Long insertResume(Long candId, String storageKey) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO resumes (candidate_id, original_filename, storage_key, content_type, size_bytes, is_current, created_at, updated_at) " +
                            "VALUES (?, 'test.pdf', ?, 'application/pdf', 1024, true, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, candId);
            ps.setString(2, storageKey);
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }
}
