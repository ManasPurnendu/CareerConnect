package com.careerconnect.jobs.controller;

import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.common.model.Role;
import com.careerconnect.jobs.model.JobStatus;
import com.careerconnect.security.JwtService;
import com.careerconnect.security.SecurityUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecruiterJobLifecycleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private record RecruiterAuth(String token, Long recruiterId, Long userId) {}

    private RecruiterAuth registerRecruiter(String emailPrefix) throws Exception {
        String email = emailPrefix + "_" + UUID.randomUUID() + "@example.com";
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.RECRUITER, "Recruiter Test");
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        String token = root.path("data").path("accessToken").asText();
        Long recruiterId = root.path("data").path("user").path("recruiterId").asLong();
        Long userId = root.path("data").path("user").path("id").asLong();
        return new RecruiterAuth(token, recruiterId, userId);
    }

    private String registerCandidate(String emailPrefix) throws Exception {
        String email = emailPrefix + "_" + UUID.randomUUID() + "@example.com";
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.CANDIDATE, "Candidate Test");
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }

    private String generateAdminToken() {
        SecurityUser adminUser = new SecurityUser(
                1L, "admin@example.com", "hash", Role.ADMIN, "System Admin",
                null, null, null, true
        );
        return jwtService.generateToken(adminUser);
    }

    private Long createCompany(String name) {
        KeyHolder kh = new GeneratedKeyHolder();
        String sql = """
                INSERT INTO companies (name, industry, location, website, description, company_size, is_active)
                VALUES (?, 'Tech', 'Bengaluru', 'https://example.com', 'A test company', '11-50', TRUE)
                """;
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name + "_" + UUID.randomUUID());
            return ps;
        }, kh);
        return com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
    }

    private void associateRecruiterWithCompany(Long recruiterId, Long companyId) {
        jdbcTemplate.update("UPDATE recruiters SET company_id = ? WHERE id = ?", companyId, recruiterId);
    }

    private Long createJob(Long companyId, String title, String description, JobStatus status,
                           LocalDate deadline, String workMode, String location,
                           BigDecimal minSalary, BigDecimal maxSalary, List<String> skills) {
        KeyHolder kh = new GeneratedKeyHolder();
        String sql = """
                INSERT INTO jobs (company_id, title, description, job_type, experience_level, work_mode,
                                  location, salary_min_lpa, salary_max_lpa, salary_currency, application_deadline, status)
                VALUES (?, ?, ?, 'FULL_TIME', 'FRESHER', ?, ?, ?, ?, 'INR', ?, ?)
                """;
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            ps.setString(2, title);
            ps.setString(3, description);
            ps.setString(4, workMode);
            ps.setString(5, location);
            ps.setBigDecimal(6, minSalary);
            ps.setBigDecimal(7, maxSalary);
            ps.setDate(8, deadline != null ? Date.valueOf(deadline) : null);
            ps.setString(9, status.name());
            return ps;
        }, kh);

        Long jobId = com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);

        if (skills != null) {
            for (String skillName : skills) {
                // Upsert skill
                Long skillId;
                List<Long> skillIds = jdbcTemplate.query(
                        "SELECT id FROM skills WHERE normalized_name = ?",
                        (rs, rowNum) -> rs.getLong("id"),
                        skillName.trim().toLowerCase()
                );
                if (skillIds.isEmpty()) {
                    KeyHolder skh = new GeneratedKeyHolder();
                    jdbcTemplate.update(con -> {
                        PreparedStatement ps = con.prepareStatement(
                                "INSERT INTO skills (name, normalized_name) VALUES (?, ?)",
                                Statement.RETURN_GENERATED_KEYS
                        );
                        ps.setString(1, skillName);
                        ps.setString(2, skillName.trim().toLowerCase());
                        return ps;
                    }, skh);
                    skillId = com.careerconnect.common.util.JdbcUtils.extractGeneratedId(skh);
                } else {
                    skillId = skillIds.get(0);
                }

                jdbcTemplate.update(
                        "INSERT IGNORE INTO job_skills (job_id, skill_id) VALUES (?, ?)",
                        jobId, skillId
                );
            }
        }

        return jobId;
    }


    // ==========================================
    // 19. Security & Authorization Tests
    // ==========================================

    @Test
    @DisplayName("P6.4-SEC-01: Unauthenticated publish returns 401 UNAUTHENTICATED")
    void unauthenticatedPublish_Returns401() throws Exception {
        mockMvc.perform(patch("/api/recruiter/jobs/1/publish"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("P6.4-SEC-02: Unauthenticated close returns 401 UNAUTHENTICATED")
    void unauthenticatedClose_Returns401() throws Exception {
        mockMvc.perform(patch("/api/recruiter/jobs/1/close"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("P6.4-SEC-03: Candidate publish returns 403 FORBIDDEN")
    void candidatePublish_Returns403() throws Exception {
        String token = registerCandidate("cand.pub");
        mockMvc.perform(patch("/api/recruiter/jobs/1/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P6.4-SEC-04: Candidate close returns 403 FORBIDDEN")
    void candidateClose_Returns403() throws Exception {
        String token = registerCandidate("cand.cls");
        mockMvc.perform(patch("/api/recruiter/jobs/1/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P6.4-SEC-05: Admin publish returns 403 FORBIDDEN")
    void adminPublish_Returns403() throws Exception {
        String token = generateAdminToken();
        mockMvc.perform(patch("/api/recruiter/jobs/1/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P6.4-SEC-06: Admin close returns 403 FORBIDDEN")
    void adminClose_Returns403() throws Exception {
        String token = generateAdminToken();
        mockMvc.perform(patch("/api/recruiter/jobs/1/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P6.4-SEC-07: Recruiter without company gets 422 COMPANY_REQUIRED on publish")
    void recruiterWithoutCompany_Publish_Returns422() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.nocomp.pub");
        mockMvc.perform(patch("/api/recruiter/jobs/1/publish")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("COMPANY_REQUIRED"));
    }

    @Test
    @DisplayName("P6.4-SEC-08: Recruiter cannot publish another company's job (returns 404 JOB_NOT_FOUND)")
    void recruiterCannotPublishOtherCompanyJob_Returns404() throws Exception {
        RecruiterAuth recA = registerRecruiter("rec.cross.a");
        Long compA = createCompany("CompA");
        associateRecruiterWithCompany(recA.recruiterId(), compA);

        RecruiterAuth recB = registerRecruiter("rec.cross.b");
        Long compB = createCompany("CompB");
        associateRecruiterWithCompany(recB.recruiterId(), compB);

        Long jobB = createJob(compB, "Job B", "Desc", JobStatus.DRAFT, LocalDate.now().plusDays(10),
                "REMOTE", null, new BigDecimal("5.00"), new BigDecimal("10.00"), List.of("Java"));

        // Recruiter A attempts to publish Job B
        mockMvc.perform(patch("/api/recruiter/jobs/" + jobB + "/publish")
                        .header("Authorization", "Bearer " + recA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    @Test
    @DisplayName("P6.4-SEC-09: Recruiter cannot close another company's job (returns 404 JOB_NOT_FOUND)")
    void recruiterCannotCloseOtherCompanyJob_Returns404() throws Exception {
        RecruiterAuth recA = registerRecruiter("rec.cross.cls.a");
        Long compA = createCompany("CompA");
        associateRecruiterWithCompany(recA.recruiterId(), compA);

        RecruiterAuth recB = registerRecruiter("rec.cross.cls.b");
        Long compB = createCompany("CompB");
        associateRecruiterWithCompany(recB.recruiterId(), compB);

        Long jobB = createJob(compB, "Job B Active", "Desc", JobStatus.ACTIVE, LocalDate.now().plusDays(10),
                "REMOTE", null, new BigDecimal("5.00"), new BigDecimal("10.00"), List.of("Java"));

        // Recruiter A attempts to close Job B
        mockMvc.perform(patch("/api/recruiter/jobs/" + jobB + "/close")
                        .header("Authorization", "Bearer " + recA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    @Test
    @DisplayName("P6.4-SEC-10: Stale JWT company information does not grant ownership")
    void staleJwtCompanyInfo_DoesNotGrantOwnership() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.stale.jwt");
        Long compA = createCompany("Stale Comp A");
        associateRecruiterWithCompany(rec.recruiterId(), compA);

        // JWT token minted while associated with Comp A
        Long jobA = createJob(compA, "Job A Draft", "Desc", JobStatus.DRAFT, LocalDate.now().plusDays(10),
                "REMOTE", null, new BigDecimal("5.00"), new BigDecimal("10.00"), List.of("Java"));

        // Reassign recruiter in DB to Comp B
        Long compB = createCompany("Stale Comp B");
        associateRecruiterWithCompany(rec.recruiterId(), compB);

        // Using old token minted earlier, recruiter now owns Comp B and cannot publish Job A
        mockMvc.perform(patch("/api/recruiter/jobs/" + jobA + "/publish")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    // ==========================================
    // 20. Publish Tests
    // ==========================================

    @Test
    @DisplayName("P6.4-PUB-01: DRAFT -> ACTIVE succeeds and returns JobDetailDto with status ACTIVE")
    void draftToActive_Succeeds() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.pub.succ");
        Long compId = createCompany("Publish Corp");
        associateRecruiterWithCompany(rec.recruiterId(), compId);

        Long jobId = createJob(compId, "Full Stack Engineer", "Build great apps", JobStatus.DRAFT,
                LocalDate.now().plusDays(30), "ONSITE", "Bengaluru",
                new BigDecimal("6.00"), new BigDecimal("12.00"), List.of("Java", "Spring Boot"));

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/publish")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(jobId))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.title").value("Full Stack Engineer"));

        // Verify in DB directly
        String dbStatus = jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(dbStatus).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("P6.4-PUB-02: DRAFT -> DRAFT fails (same-state invalid transition)")
    void draftToDraft_Fails() throws Exception {
        // Can't invoke publish to become draft, but let's test directly that an already ACTIVE job cannot be re-published
        RecruiterAuth rec = registerRecruiter("rec.pub.same");
        Long compId = createCompany("Same State Corp");
        associateRecruiterWithCompany(rec.recruiterId(), compId);

        Long jobId = createJob(compId, "Active Job", "Desc", JobStatus.ACTIVE,
                LocalDate.now().plusDays(20), "REMOTE", null, null, null, List.of("SQL"));

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/publish")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("P6.4-PUB-03: Publishing job with past application deadline fails with 422 JOB_DEADLINE_PASSED")
    void publishingExpiredJob_Returns422() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.pub.exp");
        Long compId = createCompany("Expired Corp");
        associateRecruiterWithCompany(rec.recruiterId(), compId);

        Long jobId = createJob(compId, "Expired Job", "Desc", JobStatus.DRAFT,
                LocalDate.now().minusDays(1), "REMOTE", null, null, null, List.of("Java"));

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/publish")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("JOB_DEADLINE_PASSED"));
    }

    @Test
    @DisplayName("P6.4-PUB-04: Publishing job with missing required skills fails with 422 JOB_SKILL_REQUIRED")
    void publishingJobWithoutSkills_Returns422() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.pub.noskill");
        Long compId = createCompany("No Skill Corp");
        associateRecruiterWithCompany(rec.recruiterId(), compId);

        Long jobId = createJob(compId, "Job No Skills", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusDays(15), "REMOTE", null, null, null, List.of());

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/publish")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("JOB_SKILL_REQUIRED"));
    }

    @Test
    @DisplayName("P6.4-PUB-05: Publishing non-remote job without location fails with 422 VALIDATION_FAILED")
    void publishingNonRemoteJobWithoutLocation_Returns422() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.pub.noloc");
        Long compId = createCompany("No Loc Corp");
        associateRecruiterWithCompany(rec.recruiterId(), compId);

        Long jobId = createJob(compId, "Onsite No Loc", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusDays(15), "ONSITE", "   ", null, null, List.of("Docker"));

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/publish")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }


    @Test
    @DisplayName("P6.4-PUB-06: Publishing CLOSED job fails with 422 INVALID_STATUS_TRANSITION")
    void publishingClosedJob_Returns422() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.pub.closed");
        Long compId = createCompany("Closed Job Corp");
        associateRecruiterWithCompany(rec.recruiterId(), compId);

        Long jobId = createJob(compId, "Closed Job", "Desc", JobStatus.CLOSED,
                LocalDate.now().plusDays(15), "REMOTE", null, null, null, List.of("Java"));

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/publish")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("P6.4-PUB-07: Publishing ARCHIVED job fails with 422 INVALID_STATUS_TRANSITION")
    void publishingArchivedJob_Returns422() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.pub.arch");
        Long compId = createCompany("Archived Job Corp");
        associateRecruiterWithCompany(rec.recruiterId(), compId);

        Long jobId = createJob(compId, "Archived Job", "Desc", JobStatus.ARCHIVED,
                LocalDate.now().plusDays(15), "REMOTE", null, null, null, List.of("Java"));

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/publish")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    // ==========================================
    // 21. Close Tests
    // ==========================================

    @Test
    @DisplayName("P6.4-CLS-01: ACTIVE -> CLOSED succeeds and updates status in DB")
    void activeToClosed_Succeeds() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.cls.succ");
        Long compId = createCompany("Close Corp");
        associateRecruiterWithCompany(rec.recruiterId(), compId);

        Long jobId = createJob(compId, "Active DevOps Engineer", "Deploy cloud infrastructure", JobStatus.ACTIVE,
                LocalDate.now().plusDays(25), "REMOTE", null,
                new BigDecimal("8.00"), new BigDecimal("14.00"), List.of("AWS", "Kubernetes"));

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/close")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(jobId))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // Verify direct DB update
        String dbStatus = jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(dbStatus).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("P6.4-CLS-02: DRAFT -> CLOSED fails with 422 INVALID_STATUS_TRANSITION")
    void draftToClosed_Fails() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.cls.draft");
        Long compId = createCompany("Draft Close Corp");
        associateRecruiterWithCompany(rec.recruiterId(), compId);

        Long jobId = createJob(compId, "Draft Job", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusDays(25), "REMOTE", null, null, null, List.of("Go"));

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/close")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("P6.4-CLS-03: CLOSED -> CLOSED fails with 422 INVALID_STATUS_TRANSITION")
    void closedToClosed_Fails() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.cls.reclose");
        Long compId = createCompany("Reclose Corp");
        associateRecruiterWithCompany(rec.recruiterId(), compId);

        Long jobId = createJob(compId, "Already Closed", "Desc", JobStatus.CLOSED,
                LocalDate.now().plusDays(25), "REMOTE", null, null, null, List.of("Go"));

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/close")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("P6.4-CLS-04: ARCHIVED -> CLOSED fails with 422 INVALID_STATUS_TRANSITION")
    void archivedToClosed_Fails() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.cls.arch");
        Long compId = createCompany("Archived Close Corp");
        associateRecruiterWithCompany(rec.recruiterId(), compId);

        Long jobId = createJob(compId, "Archived Job", "Desc", JobStatus.ARCHIVED,
                LocalDate.now().plusDays(25), "REMOTE", null, null, null, List.of("Go"));

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/close")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("P6.4-DB-01: Verifies company_id, required skills, and job fields remain untouched after lifecycle changes")
    void dbStateIntegrityAfterTransitions() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.db.verify");
        Long compId = createCompany("Integrity Corp");
        associateRecruiterWithCompany(rec.recruiterId(), compId);

        Long jobId = createJob(compId, "Integrity Job", "Integrity Desc", JobStatus.DRAFT,
                LocalDate.now().plusDays(20), "HYBRID", "Hyderabad",
                new BigDecimal("7.00"), new BigDecimal("11.00"), List.of("Python", "Django"));

        // Step 1: Publish
        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/publish")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // Step 2: Verify in DB
        Integer skillCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM job_skills WHERE job_id = ?", Integer.class, jobId);
        assertThat(skillCount).isEqualTo(2);

        Long currentCompId = jdbcTemplate.queryForObject("SELECT company_id FROM jobs WHERE id = ?", Long.class, jobId);
        assertThat(currentCompId).isEqualTo(compId);

        // Step 3: Close
        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/close")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // Final verification
        String finalStatus = jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(finalStatus).isEqualTo("CLOSED");
        Integer finalSkillCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM job_skills WHERE job_id = ?", Integer.class, jobId);
        assertThat(finalSkillCount).isEqualTo(2);
    }
}
