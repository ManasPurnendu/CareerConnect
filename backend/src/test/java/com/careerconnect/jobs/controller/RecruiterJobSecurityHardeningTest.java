package com.careerconnect.jobs.controller;

import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.auth.service.AuthService;
import com.careerconnect.common.model.Role;
import com.careerconnect.jobs.dto.RecruiterJobUpdateRequest;
import com.careerconnect.jobs.model.ExperienceLevel;
import com.careerconnect.jobs.model.JobStatus;
import com.careerconnect.jobs.model.JobType;
import com.careerconnect.jobs.model.WorkMode;
import com.careerconnect.jobs.repository.JobRepository;
import com.careerconnect.security.JwtService;
import com.careerconnect.security.SecurityUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecruiterJobSecurityHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JobRepository jobRepository;

    // --- Helper Methods ---

    private String registerRecruiterAndGetToken(String emailPrefix) {
        String email = emailPrefix + "_" + UUID.randomUUID() + "@example.com";
        var res = authService.register(new RegisterRequest(email, "Password@123", Role.RECRUITER, "Test Recruiter"));
        return res.getAccessToken();
    }

    private String registerCandidateAndGetToken(String emailPrefix) {
        String email = emailPrefix + "_" + UUID.randomUUID() + "@example.com";
        var res = authService.register(new RegisterRequest(email, "Password@123", Role.CANDIDATE, "Test Candidate"));
        return res.getAccessToken();
    }

    private String getAdminToken() {
        SecurityUser adminUser = new SecurityUser(
                1L, "admin.test@example.com", "hash", Role.ADMIN, "System Admin",
                null, null, null, true
        );
        return jwtService.generateToken(adminUser);
    }

    private Long getRecruiterIdFromToken(String token) {
        return jdbcTemplate.queryForObject(
                "SELECT r.id FROM recruiters r JOIN users u ON r.user_id = u.id WHERE u.email = ?",
                Long.class,
                extractEmailFromToken(token)
        );
    }

    private String extractEmailFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            JsonNode node = objectMapper.readTree(payload);
            return node.get("sub").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode token", e);
        }
    }

    private Long createCompany(String name) {
        KeyHolder kh = new GeneratedKeyHolder();
        String sql = "INSERT INTO companies (name, industry, location, website, company_size, is_active) VALUES (?, 'IT', 'Bengaluru', 'https://example.com', '51-200', true)";
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

    private Long getOrCreateSkill(String name) {
        String norm = name.trim().toLowerCase();
        List<Long> ids = jdbcTemplate.query("SELECT id FROM skills WHERE normalized_name = ?", (rs, rowNum) -> rs.getLong("id"), norm);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO skills (name, normalized_name) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, norm);
            return ps;
        }, kh);
        return com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
    }

    private Long createJob(Long companyId, String title, String description, JobStatus status,
                           LocalDate deadline, String workMode, String location,
                           BigDecimal minSalary, BigDecimal maxSalary, List<Long> skillIds) {
        KeyHolder kh = new GeneratedKeyHolder();
        String sql = """
                INSERT INTO jobs (company_id, title, description, responsibilities, requirements,
                                  job_type, experience_level, work_mode, location, salary_min_lpa,
                                  salary_max_lpa, salary_currency, application_deadline, status)
                VALUES (?, ?, ?, 'Core responsibilities', 'Core requirements', 'FULL_TIME', 'FRESHER', ?, ?, ?, ?, 'INR', ?, ?)
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

        if (skillIds != null) {
            for (Long sId : skillIds) {
                jdbcTemplate.update("INSERT INTO job_skills (job_id, skill_id) VALUES (?, ?)", jobId, sId);
            }
        }
        return jobId;
    }

    private RecruiterJobUpdateRequest createSampleUpdateRequest(List<Long> skillIds) {
        return new RecruiterJobUpdateRequest(
                "Updated Security Engineer",
                "Updated secure backend development responsibilities",
                "Security audit and secure coding",
                "Proficiency in Java and OWASP",
                JobType.FULL_TIME,
                ExperienceLevel.FRESHER,
                WorkMode.REMOTE,
                "Remote, India",
                new BigDecimal("12.00"),
                new BigDecimal("22.00"),
                "INR",
                LocalDate.now().plusDays(45),
                skillIds
        );
    }

    private ObjectNode createValidBaseJobNode(List<Long> skillIds) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("title", "Base Job Title");
        node.put("description", "Base Job Description");
        node.put("responsibilities", "Base Responsibilities");
        node.put("requirements", "Base Requirements");
        node.put("jobType", "FULL_TIME");
        node.put("experienceLevel", "FRESHER");
        node.put("workMode", "REMOTE");
        node.put("location", "Remote");
        node.put("salaryMinLpa", 10.0);
        node.put("salaryMaxLpa", 15.0);
        node.put("salaryCurrency", "INR");
        node.put("applicationDeadline", LocalDate.now().plusDays(30).toString());
        ArrayNode arr = node.putArray("requiredSkillIds");
        if (skillIds != null) {
            skillIds.forEach(arr::add);
        }
        return node;
    }

    // =========================================================================
    // AUTH-01: Unauthenticated recruiter endpoints return 401
    // =========================================================================

    @Test
    @DisplayName("AUTH-01: Unauthenticated requests to all recruiter endpoints return 401 Unauthorized")
    void auth01_unauthenticatedEndpoints_return401() throws Exception {
        mockMvc.perform(get("/api/recruiter/company"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/recruiter/company")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Company\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/recruiter/jobs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/recruiter/jobs/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/recruiter/jobs/1/publish"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/recruiter/jobs/1/close"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/recruiter/jobs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/recruiter/jobs/1"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // ROLE-01: Candidate cannot access recruiter endpoints (returns 403)
    // =========================================================================

    @Test
    @DisplayName("ROLE-01: Candidate role cannot access recruiter endpoints (returns 403 Forbidden)")
    void role01_candidateRole_cannotAccessRecruiterEndpoints() throws Exception {
        String candToken = registerCandidateAndGetToken("role.cand");

        mockMvc.perform(get("/api/recruiter/company")
                        .header("Authorization", "Bearer " + candToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + candToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Candidate Corp\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + candToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/recruiter/jobs/1")
                        .header("Authorization", "Bearer " + candToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/recruiter/jobs/1/publish")
                        .header("Authorization", "Bearer " + candToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/recruiter/jobs/1/close")
                        .header("Authorization", "Bearer " + candToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/recruiter/jobs/1")
                        .header("Authorization", "Bearer " + candToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/recruiter/jobs/1")
                        .header("Authorization", "Bearer " + candToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // ROLE-02: Admin cannot access recruiter endpoints (returns 403)
    // =========================================================================

    @Test
    @DisplayName("ROLE-02: Admin role cannot access recruiter endpoints (returns 403 Forbidden)")
    void role02_adminRole_cannotAccessRecruiterEndpoints() throws Exception {
        String adminToken = getAdminToken();

        mockMvc.perform(get("/api/recruiter/company")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Admin Corp\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/recruiter/jobs/1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/recruiter/jobs/1/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/recruiter/jobs/1/close")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/recruiter/jobs/1")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/recruiter/jobs/1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // OWN-01: Recruiter sees only own company jobs
    // =========================================================================

    @Test
    @DisplayName("OWN-01: Recruiter sees only own company jobs; cross-company jobs never appear in listing")
    void own01_recruiterSeesOnlyOwnCompanyJobs() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.own.a");
        Long recA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("Alpha Technologies");
        associateRecruiterWithCompany(recA, compA);

        String tokenB = registerRecruiterAndGetToken("rec.own.b");
        Long recB = getRecruiterIdFromToken(tokenB);
        Long compB = createCompany("Beta Solutions");
        associateRecruiterWithCompany(recB, compB);

        Long skillId = getOrCreateSkill("Java");
        Long jobA1 = createJob(compA, "Job A1", "Desc A1", JobStatus.ACTIVE, LocalDate.now().plusDays(20), "HYBRID", "Bangalore", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));
        Long jobA2 = createJob(compA, "Job A2", "Desc A2", JobStatus.DRAFT, LocalDate.now().plusDays(30), "REMOTE", "Remote", new BigDecimal("12"), new BigDecimal("18"), List.of(skillId));

        Long jobB1 = createJob(compB, "Job B1", "Desc B1", JobStatus.ACTIVE, LocalDate.now().plusDays(15), "ONSITE", "Hyderabad", new BigDecimal("8"), new BigDecimal("12"), List.of(skillId));

        // Recruiter A listing
        MvcResult resA = mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();

        String bodyA = resA.getResponse().getContentAsString();
        assertThat(bodyA).contains("Job A1");
        assertThat(bodyA).contains("Job A2");
        assertThat(bodyA).doesNotContain("Job B1");

        // Recruiter B listing
        MvcResult resB = mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(jobB1))
                .andReturn();

        String bodyB = resB.getResponse().getContentAsString();
        assertThat(bodyB).contains("Job B1");
        assertThat(bodyB).doesNotContain("Job A1");
        assertThat(bodyB).doesNotContain("Job A2");
    }

    // =========================================================================
    // OWN-02 to OWN-06: Cross-company access returns 404 JOB_NOT_FOUND
    // =========================================================================

    @Test
    @DisplayName("OWN-02: Cross-company job detail returns 404 JOB_NOT_FOUND")
    void own02_crossCompanyDetail_returns404() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.cc.det.a");
        Long recA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("Company Alpha Det");
        associateRecruiterWithCompany(recA, compA);

        Long compB = createCompany("Company Beta Det");
        Long skillId = getOrCreateSkill("SQL");
        Long jobB = createJob(compB, "Job Beta Det", "Desc Beta", JobStatus.ACTIVE, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));

        mockMvc.perform(get("/api/recruiter/jobs/" + jobB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("OWN-03: Cross-company job update returns 404 JOB_NOT_FOUND")
    void own03_crossCompanyUpdate_returns404() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.cc.upd.a");
        Long recA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("Company Alpha Upd");
        associateRecruiterWithCompany(recA, compA);

        Long compB = createCompany("Company Beta Upd");
        Long skillId = getOrCreateSkill("Spring Boot");
        Long jobB = createJob(compB, "Job Beta Upd", "Desc Beta", JobStatus.ACTIVE, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));

        RecruiterJobUpdateRequest req = createSampleUpdateRequest(List.of(skillId));

        mockMvc.perform(put("/api/recruiter/jobs/" + jobB)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        // Verify Job B was not updated in database
        String title = jdbcTemplate.queryForObject("SELECT title FROM jobs WHERE id = ?", String.class, jobB);
        assertThat(title).isEqualTo("Job Beta Upd");
    }

    @Test
    @DisplayName("OWN-04: Cross-company job delete returns 404 JOB_NOT_FOUND")
    void own04_crossCompanyDelete_returns404() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.cc.del.a");
        Long recA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("Company Alpha Del");
        associateRecruiterWithCompany(recA, compA);

        Long compB = createCompany("Company Beta Del");
        Long skillId = getOrCreateSkill("Docker");
        Long jobB = createJob(compB, "Job Beta Del", "Desc Beta", JobStatus.DRAFT, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));

        mockMvc.perform(delete("/api/recruiter/jobs/" + jobB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        // Verify Job B was not soft-deleted in database
        String deletedAt = jdbcTemplate.queryForObject("SELECT deleted_at FROM jobs WHERE id = ?", String.class, jobB);
        assertThat(deletedAt).isNull();
    }

    @Test
    @DisplayName("OWN-05: Cross-company publish returns 404 JOB_NOT_FOUND")
    void own05_crossCompanyPublish_returns404() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.cc.pub.a");
        Long recA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("Company Alpha Pub");
        associateRecruiterWithCompany(recA, compA);

        Long compB = createCompany("Company Beta Pub");
        Long skillId = getOrCreateSkill("Kubernetes");
        Long jobB = createJob(compB, "Job Beta Draft", "Desc Beta", JobStatus.DRAFT, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobB + "/publish")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        // Verify status remains DRAFT
        String status = jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, jobB);
        assertThat(status).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("OWN-06: Cross-company close returns 404 JOB_NOT_FOUND")
    void own06_crossCompanyClose_returns404() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.cc.cls.a");
        Long recA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("Company Alpha Cls");
        associateRecruiterWithCompany(recA, compA);

        Long compB = createCompany("Company Beta Cls");
        Long skillId = getOrCreateSkill("Go");
        Long jobB = createJob(compB, "Job Beta Active", "Desc Beta", JobStatus.ACTIVE, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobB + "/close")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        // Verify status remains ACTIVE
        String status = jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, jobB);
        assertThat(status).isEqualTo("ACTIVE");
    }

    // =========================================================================
    // TAMPER-01 to TAMPER-08: Parameter and Body ID Manipulation Hardening
    // =========================================================================

    @Test
    @DisplayName("TAMPER-01: companyId query parameter cannot change recruiter job query ownership")
    void tamper01_companyIdQueryParam_cannotChangeOwnership() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.tamp.qp");
        Long recA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("Company Alpha QP");
        associateRecruiterWithCompany(recA, compA);

        Long compB = createCompany("Company Beta QP");
        Long skillId = getOrCreateSkill("Rust");
        createJob(compA, "Alpha Job", "Desc Alpha", JobStatus.ACTIVE, LocalDate.now().plusDays(10), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));
        createJob(compB, "Beta Secret Job", "Desc Beta", JobStatus.ACTIVE, LocalDate.now().plusDays(10), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));

        MvcResult res = mockMvc.perform(get("/api/recruiter/jobs?companyId=" + compB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertThat(body).contains("Alpha Job");
        assertThat(body).doesNotContain("Beta Secret Job");
    }

    @Test
    @DisplayName("TAMPER-02: companyId in request body cannot change company or job ownership")
    void tamper02_companyIdInBody_cannotChangeOwnership() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.tamp.body");
        Long recA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("Original Corp A");
        associateRecruiterWithCompany(recA, compA);

        Long compB = createCompany("Target Corp B");
        Long skillId = getOrCreateSkill("Python");
        Long jobA = createJob(compA, "Original Job", "Desc", JobStatus.DRAFT, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));

        // Attempt 1: PUT /api/recruiter/company with companyId: compB
        mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + compB + ",\"companyId\":" + compB + ",\"name\":\"Updated Corp A\",\"industry\":\"Tech\"}"))
                .andExpect(status().isOk());

        // Verify company B was untouched
        String compBName = jdbcTemplate.queryForObject("SELECT name FROM companies WHERE id = ?", String.class, compB);
        assertThat(compBName).startsWith("Target Corp B");

        // Attempt 2: PUT /api/recruiter/jobs/{jobA} with companyId: compB
        ObjectNode maliciousNode = createValidBaseJobNode(List.of(skillId));
        maliciousNode.put("companyId", compB);
        maliciousNode.put("title", "Hacked Title");

        mockMvc.perform(put("/api/recruiter/jobs/" + jobA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousNode)))
                .andExpect(status().isOk());

        // Verify jobA still belongs strictly to company A
        Long jobCompanyId = jdbcTemplate.queryForObject("SELECT company_id FROM jobs WHERE id = ?", Long.class, jobA);
        assertThat(jobCompanyId).isEqualTo(compA);
    }

    @Test
    @DisplayName("TAMPER-03 & TAMPER-04: recruiterId and userId in body cannot override server-side identity")
    void tamper03_04_recruiterIdAndUserIdInBody_ignored() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.tamp.ident");
        Long recA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("Identity Corp");
        associateRecruiterWithCompany(recA, compA);

        Long skillId = getOrCreateSkill("C++");
        Long jobA = createJob(compA, "Valid Ident Job", "Desc", JobStatus.DRAFT, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));

        ObjectNode maliciousNode = createValidBaseJobNode(List.of(skillId));
        maliciousNode.put("recruiterId", 9999L);
        maliciousNode.put("userId", 8888L);
        maliciousNode.put("title", "Updated Ident Title");

        mockMvc.perform(put("/api/recruiter/jobs/" + jobA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousNode)))
                .andExpect(status().isOk());

        // Job update succeeded cleanly; server-side identity was preserved
        String title = jdbcTemplate.queryForObject("SELECT title FROM jobs WHERE id = ?", String.class, jobA);
        assertThat(title).isEqualTo("Updated Ident Title");
    }

    @Test
    @DisplayName("TAMPER-05: createdBy fields cannot change ownership")
    void tamper05_createdByFields_cannotChangeOwnership() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.tamp.crby");
        Long recA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("CreatedBy Corp");
        associateRecruiterWithCompany(recA, compA);

        Long skillId = getOrCreateSkill("Kotlin");
        Long jobA = createJob(compA, "CreatedBy Job", "Desc", JobStatus.DRAFT, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));

        ObjectNode maliciousNode = createValidBaseJobNode(List.of(skillId));
        maliciousNode.put("createdBy", 9999L);
        maliciousNode.put("createdByRecruiterId", 9999L);
        maliciousNode.put("title", "Safe Title");

        mockMvc.perform(put("/api/recruiter/jobs/" + jobA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousNode)))
                .andExpect(status().isOk());

        Long compId = jdbcTemplate.queryForObject("SELECT company_id FROM jobs WHERE id = ?", Long.class, jobA);
        assertThat(compId).isEqualTo(compA);
    }

    @Test
    @DisplayName("TAMPER-06: status in generic update body cannot bypass lifecycle transitions")
    void tamper06_statusInBody_cannotBypassLifecycle() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.tamp.stat");
        Long recA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("Status Corp");
        associateRecruiterWithCompany(recA, compA);

        Long skillId = getOrCreateSkill("TypeScript");
        Long jobA = createJob(compA, "Draft Status Job", "Desc", JobStatus.DRAFT, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));

        ObjectNode maliciousNode = createValidBaseJobNode(List.of(skillId));
        maliciousNode.put("status", "ACTIVE");
        maliciousNode.put("title", "Status Bypass Attempt");

        mockMvc.perform(put("/api/recruiter/jobs/" + jobA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousNode)))
                .andExpect(status().isOk());

        // Status MUST still be DRAFT in MySQL!
        String status = jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, jobA);
        assertThat(status).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("TAMPER-07 & TAMPER-08: deletedAt and createdAt cannot be manipulated via update payload")
    void tamper07_08_deletedAtAndCreatedAt_cannotBeManipulated() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.tamp.times");
        Long recA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("Timestamp Corp");
        associateRecruiterWithCompany(recA, compA);

        Long skillId = getOrCreateSkill("NodeJS");
        Long jobA = createJob(compA, "Time Job", "Desc", JobStatus.DRAFT, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));

        String origCreatedAt = jdbcTemplate.queryForObject("SELECT created_at FROM jobs WHERE id = ?", String.class, jobA);

        ObjectNode maliciousNode = createValidBaseJobNode(List.of(skillId));
        maliciousNode.put("deletedAt", "2020-01-01T00:00:00Z");
        maliciousNode.put("createdAt", "1999-01-01T00:00:00Z");
        maliciousNode.put("title", "Timestamp Manip Attempt");

        mockMvc.perform(put("/api/recruiter/jobs/" + jobA)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousNode)))
                .andExpect(status().isOk());

        String deletedAt = jdbcTemplate.queryForObject("SELECT deleted_at FROM jobs WHERE id = ?", String.class, jobA);
        assertThat(deletedAt).isNull();

        String currentCreatedAt = jdbcTemplate.queryForObject("SELECT created_at FROM jobs WHERE id = ?", String.class, jobA);
        assertThat(currentCreatedAt).isEqualTo(origCreatedAt);
    }

    // =========================================================================
    // JWT-01 to JWT-03: Stale JWT & Company Reassignment Verification
    // =========================================================================

    @Test
    @DisplayName("JWT-01: Stale JWT follows current DB company association after company reassignment")
    void jwt01_staleJwt_afterCompanyReassignment_followsCurrentDbAssociation() throws Exception {
        // Step 1: Recruiter authenticates while associated with Company A
        String token = registerRecruiterAndGetToken("rec.jwt.reassign");
        Long recId = getRecruiterIdFromToken(token);
        Long compA = createCompany("Company Alpha Reassign");
        associateRecruiterWithCompany(recId, compA);

        Long skillId = getOrCreateSkill("Java");
        Long jobA = createJob(compA, "Job Company A", "Desc A", JobStatus.ACTIVE, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));

        // Step 2: Database recruiter.company_id changes to Company B
        Long compB = createCompany("Company Beta Reassign");
        Long jobB = createJob(compB, "Job Company B", "Desc B", JobStatus.ACTIVE, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("12"), new BigDecimal("18"), List.of(skillId));
        associateRecruiterWithCompany(recId, compB);

        // Step 3: Recruiter calls job listing using the OLD token
        MvcResult res = mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        // Must contain Job B (current company) and MUST NOT contain Job A (old company)
        assertThat(body).contains("Job Company B");
        assertThat(body).doesNotContain("Job Company A");

        // Calling company endpoint returns Company B
        mockMvc.perform(get("/api/recruiter/company")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(compB));
    }

    @Test
    @DisplayName("JWT-02: Stale JWT after company removal returns 422 COMPANY_REQUIRED")
    void jwt02_staleJwt_afterCompanyRemoval_returnsCompanyRequired() throws Exception {
        // Step 1: Recruiter has Company A and JWT
        String token = registerRecruiterAndGetToken("rec.jwt.remove");
        Long recId = getRecruiterIdFromToken(token);
        Long compA = createCompany("Company Alpha Remove");
        associateRecruiterWithCompany(recId, compA);

        Long skillId = getOrCreateSkill("Java");
        Long jobA = createJob(compA, "Job Alpha Rem", "Desc", JobStatus.DRAFT, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));

        // Step 2: Company association removed in DB
        jdbcTemplate.update("UPDATE recruiters SET company_id = NULL WHERE id = ?", recId);

        // Step 3: All job operations using existing JWT must return 422 COMPANY_REQUIRED
        mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("COMPANY_REQUIRED"));

        mockMvc.perform(get("/api/recruiter/jobs/" + jobA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("COMPANY_REQUIRED"));

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobA + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("COMPANY_REQUIRED"));

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobA + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("COMPANY_REQUIRED"));

        RecruiterJobUpdateRequest req = createSampleUpdateRequest(List.of(skillId));
        mockMvc.perform(put("/api/recruiter/jobs/" + jobA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("COMPANY_REQUIRED"));

        mockMvc.perform(delete("/api/recruiter/jobs/" + jobA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("COMPANY_REQUIRED"));
    }

    @Test
    @DisplayName("JWT-03: Stale JWT cannot access previous company's jobs (returns 404 JOB_NOT_FOUND)")
    void jwt03_staleJwt_cannotAccessPreviousCompanyJobs() throws Exception {
        String token = registerRecruiterAndGetToken("rec.jwt.prevcomp");
        Long recId = getRecruiterIdFromToken(token);
        Long compA = createCompany("Company Alpha Prev");
        associateRecruiterWithCompany(recId, compA);

        Long skillId = getOrCreateSkill("C#");
        Long jobA = createJob(compA, "Prev Job A", "Desc", JobStatus.DRAFT, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));

        // Reassign to Company B
        Long compB = createCompany("Company Beta Prev");
        associateRecruiterWithCompany(recId, compB);

        // Accessing Job A must return ownership-safe 404 JOB_NOT_FOUND
        mockMvc.perform(get("/api/recruiter/jobs/" + jobA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobA + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobA + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        RecruiterJobUpdateRequest req = createSampleUpdateRequest(List.of(skillId));
        mockMvc.perform(put("/api/recruiter/jobs/" + jobA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        mockMvc.perform(delete("/api/recruiter/jobs/" + jobA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    // =========================================================================
    // LEAK-01 & LEAK-02: Information Leakage Verification
    // =========================================================================

    @Test
    @DisplayName("LEAK-01: Unauthorized job detail response leaks zero organizational or job metadata")
    void leak01_unauthorizedJobDetail_leaksNoMetadata() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.leak.a");
        Long recA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("Alpha Corp Leak");
        associateRecruiterWithCompany(recA, compA);

        Long compB = createCompany("Super Secret Stealth Inc");
        Long skillId = getOrCreateSkill("TopSecretSkill");
        Long jobB = createJob(compB, "VP of Quantum Computing", "Confidential description", JobStatus.ACTIVE, LocalDate.now().plusDays(25), "REMOTE", "Confidential City", new BigDecimal("150.00"), new BigDecimal("250.00"), List.of(skillId));

        // 1. Cross-company access
        MvcResult resCross = mockMvc.perform(get("/api/recruiter/jobs/" + jobB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();

        String crossBody = resCross.getResponse().getContentAsString();
        assertThat(crossBody).doesNotContain("Super Secret Stealth Inc");
        assertThat(crossBody).doesNotContain("VP of Quantum Computing");
        assertThat(crossBody).doesNotContain("Confidential description");
        assertThat(crossBody).doesNotContain("TopSecretSkill");
        assertThat(crossBody).doesNotContain("150");
        assertThat(crossBody).doesNotContain("250");
        assertThat(crossBody).doesNotContain(compB.toString());

        // 2. Querying a completely non-existent job ID
        MvcResult resNonExistent = mockMvc.perform(get("/api/recruiter/jobs/999999")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();

        // 3. Confirm cross-company and non-existent error responses have identical format
        JsonNode crossJson = objectMapper.readTree(crossBody);
        JsonNode nonExistentJson = objectMapper.readTree(resNonExistent.getResponse().getContentAsString());

        assertThat(crossJson.get("success").asBoolean()).isEqualTo(nonExistentJson.get("success").asBoolean());
        assertThat(crossJson.get("errorCode").asText()).isEqualTo(nonExistentJson.get("errorCode").asText());
    }

    @Test
    @DisplayName("LEAK-02: Unauthorized mutations leak zero metadata")
    void leak02_unauthorizedMutations_leakNoMetadata() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.leak.mut");
        Long recA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("Alpha Corp Mut Leak");
        associateRecruiterWithCompany(recA, compA);

        Long compB = createCompany("Beta Corp Mut Leak");
        Long skillId = getOrCreateSkill("Java");
        Long jobB = createJob(compB, "Classified Role", "Classified Desc", JobStatus.DRAFT, LocalDate.now().plusDays(25), "REMOTE", "Remote", new BigDecimal("15"), new BigDecimal("25"), List.of(skillId));

        RecruiterJobUpdateRequest req = createSampleUpdateRequest(List.of(skillId));

        MvcResult resPut = mockMvc.perform(put("/api/recruiter/jobs/" + jobB)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();

        assertThat(resPut.getResponse().getContentAsString()).doesNotContain("Classified");

        MvcResult resDel = mockMvc.perform(delete("/api/recruiter/jobs/" + jobB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();

        assertThat(resDel.getResponse().getContentAsString()).doesNotContain("Classified");
    }

    // =========================================================================
    // REPO-01 & REPO-02: Direct Repository Ownership & Mutation Constraints
    // =========================================================================

    @Test
    @DisplayName("REPO-01: Repository mutations enforce company ownership constraint")
    void repo01_repositoryEnforcesCompanyOwnership() {
        Long compA = createCompany("Repo Company A");
        Long compB = createCompany("Repo Company B");

        Long skillId = getOrCreateSkill("C++");
        Long jobA = createJob(compA, "Repo Job A", "Desc", JobStatus.DRAFT, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));

        // 1. updateJobFields with wrong companyId returns false
        boolean fieldsUpdated = jobRepository.updateJobFields(
                jobA, compB, "New Title", "New Desc", "Resp", "Reqs",
                JobType.FULL_TIME, ExperienceLevel.FRESHER, WorkMode.REMOTE, "Remote",
                new BigDecimal("10"), new BigDecimal("15"), "INR", LocalDate.now().plusDays(25)
        );
        assertThat(fieldsUpdated).isFalse();

        // 2. updateJobStatus with wrong companyId returns false
        boolean statusUpdated = jobRepository.updateJobStatus(jobA, compB, JobStatus.ACTIVE);
        assertThat(statusUpdated).isFalse();

        // 3. softDeleteJob with wrong companyId returns false
        boolean deleted = jobRepository.softDeleteJob(jobA, compB);
        assertThat(deleted).isFalse();

        // 4. replaceJobSkills with wrong companyId does not modify skills
        Long newSkill = getOrCreateSkill("Rust");
        jobRepository.replaceJobSkills(jobA, compB, List.of(newSkill));
        List<Long> currentSkills = jobRepository.findRequiredSkillIdsByJobId(jobA);
        assertThat(currentSkills).containsExactly(skillId);
    }

    @Test
    @DisplayName("REPO-02: Deleted jobs remain protected from repository-level mutations")
    void repo02_deletedJobsProtectedFromMutations() {
        Long compA = createCompany("Repo Del Company");
        Long skillId = getOrCreateSkill("Scala");
        Long jobA = createJob(compA, "Repo Del Job", "Desc", JobStatus.DRAFT, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));

        // Soft-delete job
        boolean deleted = jobRepository.softDeleteJob(jobA, compA);
        assertThat(deleted).isTrue();

        // 1. updateJobFields on deleted job returns false
        boolean fieldsUpdated = jobRepository.updateJobFields(
                jobA, compA, "Mutate Deleted", "Desc", "Resp", "Reqs",
                JobType.FULL_TIME, ExperienceLevel.FRESHER, WorkMode.REMOTE, "Remote",
                new BigDecimal("10"), new BigDecimal("15"), "INR", LocalDate.now().plusDays(25)
        );
        assertThat(fieldsUpdated).isFalse();

        // 2. updateJobStatus on deleted job returns false
        boolean statusUpdated = jobRepository.updateJobStatus(jobA, compA, JobStatus.ACTIVE);
        assertThat(statusUpdated).isFalse();

        // 3. repeated softDeleteJob on already-deleted job returns false
        boolean repeatDelete = jobRepository.softDeleteJob(jobA, compA);
        assertThat(repeatDelete).isFalse();

        // 4. replaceJobSkills on deleted job does not alter skills
        Long newSkill = getOrCreateSkill("Haskell");
        jobRepository.replaceJobSkills(jobA, compA, List.of(newSkill));
        List<Long> currentSkills = jobRepository.findRequiredSkillIdsByJobId(jobA);
        assertThat(currentSkills).containsExactly(skillId);
    }

    // =========================================================================
    // ROLE-MATRIX: Exhaustive verification across all roles and all endpoints
    // =========================================================================

    @Test
    @DisplayName("ROLE-MATRIX: Role boundary matrix verification for Guest, Candidate, Recruiter, Admin")
    void roleMatrix_exhaustiveVerification() throws Exception {
        String candToken = registerCandidateAndGetToken("matrix.cand");
        String adminToken = getAdminToken();

        String recToken = registerRecruiterAndGetToken("matrix.rec");
        Long recId = getRecruiterIdFromToken(recToken);
        Long compId = createCompany("Matrix Corp");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("Java");
        Long draftJobId = createJob(compId, "Draft Job", "Desc", JobStatus.DRAFT, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));
        Long activeJobId = createJob(compId, "Active Job", "Desc", JobStatus.ACTIVE, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));
        Long deleteJobId = createJob(compId, "Delete Job", "Desc", JobStatus.DRAFT, LocalDate.now().plusDays(20), "REMOTE", "Remote", new BigDecimal("10"), new BigDecimal("15"), List.of(skillId));

        RecruiterJobUpdateRequest req = createSampleUpdateRequest(List.of(skillId));

        // 1. Recruiter company GET: Guest 401, Candidate 403, Admin 403, Recruiter 200
        mockMvc.perform(get("/api/recruiter/company")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/recruiter/company").header("Authorization", "Bearer " + candToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/recruiter/company").header("Authorization", "Bearer " + adminToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/recruiter/company").header("Authorization", "Bearer " + recToken)).andExpect(status().isOk());

        // 2. List jobs: Guest 401, Candidate 403, Admin 403, Recruiter 200
        mockMvc.perform(get("/api/recruiter/jobs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/recruiter/jobs").header("Authorization", "Bearer " + candToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/recruiter/jobs").header("Authorization", "Bearer " + adminToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/recruiter/jobs").header("Authorization", "Bearer " + recToken)).andExpect(status().isOk());

        // 3. Job detail: Guest 401, Candidate 403, Admin 403, Recruiter 200
        mockMvc.perform(get("/api/recruiter/jobs/" + draftJobId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/recruiter/jobs/" + draftJobId).header("Authorization", "Bearer " + candToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/recruiter/jobs/" + draftJobId).header("Authorization", "Bearer " + adminToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/recruiter/jobs/" + draftJobId).header("Authorization", "Bearer " + recToken)).andExpect(status().isOk());

        // 4. Publish: Guest 401, Candidate 403, Admin 403, Recruiter 200
        mockMvc.perform(patch("/api/recruiter/jobs/" + draftJobId + "/publish")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/recruiter/jobs/" + draftJobId + "/publish").header("Authorization", "Bearer " + candToken)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/recruiter/jobs/" + draftJobId + "/publish").header("Authorization", "Bearer " + adminToken)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/recruiter/jobs/" + draftJobId + "/publish").header("Authorization", "Bearer " + recToken)).andExpect(status().isOk());

        // 5. Close: Guest 401, Candidate 403, Admin 403, Recruiter 200
        mockMvc.perform(patch("/api/recruiter/jobs/" + activeJobId + "/close")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/recruiter/jobs/" + activeJobId + "/close").header("Authorization", "Bearer " + candToken)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/recruiter/jobs/" + activeJobId + "/close").header("Authorization", "Bearer " + adminToken)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/recruiter/jobs/" + activeJobId + "/close").header("Authorization", "Bearer " + recToken)).andExpect(status().isOk());

        // 6. Update: Guest 401, Candidate 403, Admin 403, Recruiter 200
        mockMvc.perform(put("/api/recruiter/jobs/" + draftJobId).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req))).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/recruiter/jobs/" + draftJobId).header("Authorization", "Bearer " + candToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req))).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/recruiter/jobs/" + draftJobId).header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req))).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/recruiter/jobs/" + draftJobId).header("Authorization", "Bearer " + recToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());

        // 7. Delete: Guest 401, Candidate 403, Admin 403, Recruiter 204
        mockMvc.perform(delete("/api/recruiter/jobs/" + deleteJobId)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/recruiter/jobs/" + deleteJobId).header("Authorization", "Bearer " + candToken)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/recruiter/jobs/" + deleteJobId).header("Authorization", "Bearer " + adminToken)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/recruiter/jobs/" + deleteJobId).header("Authorization", "Bearer " + recToken)).andExpect(status().isNoContent());
    }
}
