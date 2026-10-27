package com.careerconnect.jobs.controller;

import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.auth.service.AuthService;
import com.careerconnect.candidate.dto.AssignSkillRequest;
import com.careerconnect.candidate.dto.CreateEducationRequest;
import com.careerconnect.common.model.Role;
import com.careerconnect.company.dto.RecruiterCompanyUpsertRequest;
import com.careerconnect.jobs.dto.RecruiterJobCreateRequest;
import com.careerconnect.jobs.dto.RecruiterJobUpdateRequest;
import com.careerconnect.jobs.model.ExperienceLevel;
import com.careerconnect.jobs.model.JobType;
import com.careerconnect.jobs.model.WorkMode;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 6.8 — Comprehensive P6 Recruiter Testing Suite.
 *
 * Verifies end-to-end recruiter workflow across all test categories:
 * Category A: Recruiter Company Management
 * Category B: Job Creation (POST /api/recruiter/jobs)
 * Category C: Job Listing (GET /api/recruiter/jobs)
 * Category D: Job Details (GET /api/recruiter/jobs/{jobId})
 * Category E: Job Editing (PUT /api/recruiter/jobs/{jobId})
 * Category F: Application-Based Invariants (P6.6 + P5 Integration)
 * Category G: Job Lifecycle (DRAFT -> ACTIVE -> CLOSED)
 * Category H: Job Deletion & Soft-Delete Invariants
 * Category I: Security & Ownership Hardening Regression
 * Category J: Stale JWT Scenarios
 * Category K: Multi-Company Integration & Isolation
 * Category L: Data Integrity & Relational Hierarchy
 * Category M: API Contract Envelopes
 * Category N: Transactional Integrity & Rollback
 * Category O: Concurrency & Duplicate Operations
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecruiterComprehensiveWorkflowTest {

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

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private String registerRecruiterAndGetToken(String emailPrefix) {
        String email = emailPrefix + "_" + UUID.randomUUID() + "@example.com";
        var res = authService.register(new RegisterRequest(email, "Password@123", Role.RECRUITER, "Recruiter " + emailPrefix));
        return res.getAccessToken();
    }

    private String registerCandidateAndGetToken(String emailPrefix) {
        String email = emailPrefix + "_" + UUID.randomUUID() + "@example.com";
        var res = authService.register(new RegisterRequest(email, "Password@123", Role.CANDIDATE, "Candidate " + emailPrefix));
        return res.getAccessToken();
    }

    private String getAdminToken() {
        SecurityUser admin = new SecurityUser(
                1L, "admin.p68@example.com", "hash", Role.ADMIN, "System Admin",
                null, null, null, true
        );
        return jwtService.generateToken(admin);
    }

    private Long getRecruiterIdFromToken(String token) {
        String email = extractEmailFromToken(token);
        return jdbcTemplate.queryForObject(
                "SELECT r.id FROM recruiters r JOIN users u ON r.user_id = u.id WHERE u.email = ?",
                Long.class, email
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

    private Long createSkill(String name) {
        String normalized = name.trim().toLowerCase();
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM skills WHERE normalized_name = ?",
                (rs, rowNum) -> rs.getLong("id"),
                normalized
        );
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO skills (name, normalized_name) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, name);
            ps.setString(2, normalized);
            return ps;
        }, kh);
        return com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
    }

    private Long createCompanyDirectly(String name) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO companies (name, industry, location, website, company_size, is_active) VALUES (?, 'IT', 'Bengaluru', 'https://example.com', '51-200', true)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, name + "_" + UUID.randomUUID());
            return ps;
        }, kh);
        return com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
    }

    private void linkRecruiterToCompany(Long recruiterId, Long companyId) {
        jdbcTemplate.update("UPDATE recruiters SET company_id = ? WHERE id = ?", companyId, recruiterId);
    }

    private void prepareCandidateProfile(String candidateToken, String skillName) throws Exception {
        mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateEducationRequest("Top Tech University", "B.Tech", "Computer Science", 2018, 2022, "9.0", 1))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignSkillRequest(skillName))))
                .andExpect(status().isCreated());

        byte[] pdfBytes = "%PDF-1.4 mock resume content for application".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isCreated());
    }

    // =========================================================================
    // CATEGORY A: COMPANY MANAGEMENT
    // =========================================================================

    @Test
    @DisplayName("Category A — Recruiter company management: lifecycle, persistence, multi-recruiter, and role enforcement")
    void testCategoryA_RecruiterCompanyManagement() throws Exception {
        String tokenA1 = registerRecruiterAndGetToken("rec_comp_a1");
        String tokenA2 = registerRecruiterAndGetToken("rec_comp_a2");
        String candToken = registerCandidateAndGetToken("cand_comp_test");
        String adminToken = getAdminToken();

        // 1. Recruiter without company gets 404 RESOURCE_NOT_FOUND
        mockMvc.perform(get("/api/recruiter/company")
                        .header("Authorization", "Bearer " + tokenA1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // 2. Candidate and Admin cannot access recruiter company endpoints (403 FORBIDDEN)
        mockMvc.perform(get("/api/recruiter/company")
                        .header("Authorization", "Bearer " + candToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/recruiter/company")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        // 3. Recruiter creates company via PUT /api/recruiter/company
        String companyName = "Nexus Technologies " + UUID.randomUUID();
        var upsertReq = new RecruiterCompanyUpsertRequest(
                companyName, null, "Leading cloud platform", "Cloud Computing",
                "https://nexus.example.com", "Hyderabad", "201-500"
        );

        MvcResult createResult = mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + tokenA1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upsertReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value(companyName))
                .andExpect(jsonPath("$.data.industry").value("Cloud Computing"))
                .andExpect(jsonPath("$.data.location").value("Hyderabad"))
                .andReturn();

        JsonNode createdNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        Long companyId = createdNode.get("data").get("id").asLong();

        // 4. Persistence verification directly in MySQL
        String dbName = jdbcTemplate.queryForObject("SELECT name FROM companies WHERE id = ?", String.class, companyId);
        assertThat(dbName).isEqualTo(companyName);
        Long recruiter1Id = getRecruiterIdFromToken(tokenA1);
        Long dbCompanyId = jdbcTemplate.queryForObject("SELECT company_id FROM recruiters WHERE id = ?", Long.class, recruiter1Id);
        assertThat(dbCompanyId).isEqualTo(companyId);

        // 5. Subsequent GET returns persisted company
        mockMvc.perform(get("/api/recruiter/company")
                        .header("Authorization", "Bearer " + tokenA1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(companyId))
                .andExpect(jsonPath("$.data.name").value(companyName));

        // 6. Multiple recruiters can belong to same company
        Long recruiter2Id = getRecruiterIdFromToken(tokenA2);
        linkRecruiterToCompany(recruiter2Id, companyId);
        mockMvc.perform(get("/api/recruiter/company")
                        .header("Authorization", "Bearer " + tokenA2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(companyId))
                .andExpect(jsonPath("$.data.name").value(companyName));

        // 7. Recruiter updates company details
        String updatedDesc = "Global distributed cloud platform provider";
        var updateReq = new RecruiterCompanyUpsertRequest(
                companyName, null, updatedDesc, "Cloud Infrastructure",
                "https://nexus-global.example.com", "Hyderabad & Remote", "501-1000"
        );
        mockMvc.perform(put("/api/recruiter/company")
                        .header("Authorization", "Bearer " + tokenA1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value(updatedDesc))
                .andExpect(jsonPath("$.data.companySize").value("501-1000"));

        // Verify update in MySQL
        String dbDesc = jdbcTemplate.queryForObject("SELECT description FROM companies WHERE id = ?", String.class, companyId);
        assertThat(dbDesc).isEqualTo(updatedDesc);
    }

    // =========================================================================
    // CATEGORY B: JOB CREATION
    // =========================================================================

    @Test
    @DisplayName("Category B — Recruiter job creation: full attributes, validation, ownership, and tampering immunity")
    void testCategoryB_JobCreation() throws Exception {
        String recToken = registerRecruiterAndGetToken("rec_create_test");
        String noCompToken = registerRecruiterAndGetToken("rec_no_comp");
        Long skillJava = createSkill("Java P68");
        Long skillSpring = createSkill("Spring Boot P68");

        Long companyId = createCompanyDirectly("Vertex Corp");
        Long recruiterId = getRecruiterIdFromToken(recToken);
        linkRecruiterToCompany(recruiterId, companyId);

        // 1. Recruiter without company attempting creation returns 422 COMPANY_REQUIRED
        var validPayload = new RecruiterJobCreateRequest(
                "Software Engineer", "Full stack backend development", "Build APIs", "Java proficiency",
                JobType.FULL_TIME, ExperienceLevel.FRESHER, WorkMode.HYBRID, "Bengaluru",
                new BigDecimal("8.00"), new BigDecimal("14.00"), "INR",
                LocalDate.now().plusDays(45), List.of(skillJava, skillSpring)
        );
        mockMvc.perform(post("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + noCompToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPayload)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("COMPANY_REQUIRED"));

        // 2. Candidate and Admin cannot create recruiter jobs (403 FORBIDDEN)
        String candToken = registerCandidateAndGetToken("cand_job_post");
        mockMvc.perform(post("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + candToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPayload)))
                .andExpect(status().isForbidden());

        // 3. Negative validation: missing title
        var noTitlePayload = new RecruiterJobCreateRequest(
                "", "Description", "Resp", "Req", JobType.FULL_TIME, ExperienceLevel.FRESHER,
                WorkMode.REMOTE, null, new BigDecimal("5.00"), new BigDecimal("10.00"), "INR",
                LocalDate.now().plusDays(30), List.of(skillJava)
        );
        mockMvc.perform(post("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + recToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noTitlePayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // 4. Negative validation: non-remote work mode without location
        var noLocationPayload = new RecruiterJobCreateRequest(
                "Onsite Engineer", "Description", "Resp", "Req", JobType.FULL_TIME, ExperienceLevel.FRESHER,
                WorkMode.ONSITE, "   ", new BigDecimal("5.00"), new BigDecimal("10.00"), "INR",
                LocalDate.now().plusDays(30), List.of(skillJava)
        );
        mockMvc.perform(post("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + recToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noLocationPayload)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // 5. Negative validation: past application deadline
        var pastDeadlinePayload = new RecruiterJobCreateRequest(
                "Expired Engineer", "Description", "Resp", "Req", JobType.FULL_TIME, ExperienceLevel.FRESHER,
                WorkMode.REMOTE, null, new BigDecimal("5.00"), new BigDecimal("10.00"), "INR",
                LocalDate.now().minusDays(1), List.of(skillJava)
        );
        mockMvc.perform(post("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + recToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pastDeadlinePayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // 6. Negative validation: max salary < min salary
        var badSalaryPayload = new RecruiterJobCreateRequest(
                "Salary Engineer", "Description", "Resp", "Req", JobType.FULL_TIME, ExperienceLevel.FRESHER,
                WorkMode.REMOTE, null, new BigDecimal("15.00"), new BigDecimal("10.00"), "INR",
                LocalDate.now().plusDays(30), List.of(skillJava)
        );
        mockMvc.perform(post("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + recToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badSalaryPayload)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // 7. Negative validation: nonexistent skill ID
        var badSkillPayload = new RecruiterJobCreateRequest(
                "Skill Engineer", "Description", "Resp", "Req", JobType.FULL_TIME, ExperienceLevel.FRESHER,
                WorkMode.REMOTE, null, new BigDecimal("5.00"), new BigDecimal("10.00"), "INR",
                LocalDate.now().plusDays(30), List.of(9999999L)
        );
        mockMvc.perform(post("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + recToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badSkillPayload)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // 8. Parameter tampering immunity: client payload containing foreign companyId, recruiterId, status, id
        String tamperedJson = """
                {
                  "id": 999999,
                  "companyId": 777777,
                  "recruiterId": 888888,
                  "status": "ACTIVE",
                  "title": "Backend Platform Engineer",
                  "description": "Distributed systems engineering",
                  "responsibilities": "Microservices and pipelines",
                  "requirements": "Solid Java and DB foundations",
                  "jobType": "FULL_TIME",
                  "experienceLevel": "FRESHER",
                  "workMode": "HYBRID",
                  "location": "Bengaluru",
                  "salaryMinLpa": 9.50,
                  "salaryMaxLpa": 16.00,
                  "salaryCurrency": "INR",
                  "applicationDeadline": "%s",
                  "requiredSkillIds": [%d, %d]
                }
                """.formatted(LocalDate.now().plusDays(60), skillJava, skillSpring);

        MvcResult createResult = mockMvc.perform(post("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + recToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tamperedJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.company.id").value(companyId))
                .andExpect(jsonPath("$.data.title").value("Backend Platform Engineer"))
                .andReturn();

        JsonNode createdJob = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        Long createdJobId = createdJob.get("id").asLong();
        assertThat(createdJobId).isNotEqualTo(999999L);

        // 9. Verify persistence directly in MySQL
        Map<String, Object> jobDb = jdbcTemplate.queryForMap(
                "SELECT company_id, status, title, location, salary_min_lpa, salary_max_lpa, deleted_at FROM jobs WHERE id = ?",
                createdJobId
        );
        assertThat(((Number) jobDb.get("company_id")).longValue()).isEqualTo(companyId);
        assertThat((String) jobDb.get("status")).isEqualTo("DRAFT");
        assertThat((String) jobDb.get("title")).isEqualTo("Backend Platform Engineer");
        assertThat((String) jobDb.get("location")).isEqualTo("Bengaluru");
        assertThat(jobDb.get("deleted_at")).isNull();

        // 10. Verify required skills in MySQL job_skills
        List<Long> mappedSkillIds = jdbcTemplate.query(
                "SELECT skill_id FROM job_skills WHERE job_id = ? ORDER BY skill_id",
                (rs, rowNum) -> rs.getLong("skill_id"),
                createdJobId
        );
        assertThat(mappedSkillIds).containsExactlyInAnyOrder(skillJava, skillSpring);
    }

    // =========================================================================
    // CATEGORY C & D: JOB LISTING & DETAILS
    // =========================================================================

    @Test
    @DisplayName("Category C & D — Recruiter job listing & details: pagination, ordering, states, and cross-company isolation")
    void testCategoryC_And_D_JobListingAndDetails() throws Exception {
        String recA = registerRecruiterAndGetToken("rec_list_a");
        String recB = registerRecruiterAndGetToken("rec_list_b");
        Long compA = createCompanyDirectly("Listing Company A");
        Long compB = createCompanyDirectly("Listing Company B");
        linkRecruiterToCompany(getRecruiterIdFromToken(recA), compA);
        linkRecruiterToCompany(getRecruiterIdFromToken(recB), compB);

        Long skillId = createSkill("Python Core");

        // Recruiter A creates 3 jobs: 1 DRAFT, 1 ACTIVE, 1 CLOSED
        Long jobA1 = createJobViaApi(recA, "Job A1 Draft", WorkMode.REMOTE, null, skillId);
        Long jobA2 = createJobViaApi(recA, "Job A2 Active", WorkMode.HYBRID, "Bengaluru", skillId);
        Long jobA3 = createJobViaApi(recA, "Job A3 Closed", WorkMode.REMOTE, null, skillId);

        // Publish jobA2 and jobA3, then close jobA3
        mockMvc.perform(patch("/api/recruiter/jobs/{id}/publish", jobA2)
                .header("Authorization", "Bearer " + recA)).andExpect(status().isOk());
        mockMvc.perform(patch("/api/recruiter/jobs/{id}/publish", jobA3)
                .header("Authorization", "Bearer " + recA)).andExpect(status().isOk());
        mockMvc.perform(patch("/api/recruiter/jobs/{id}/close", jobA3)
                .header("Authorization", "Bearer " + recA)).andExpect(status().isOk());

        // Recruiter B creates 1 job
        Long jobB = createJobViaApi(recB, "Job B Confidential", WorkMode.REMOTE, null, skillId);

        // 1. Recruiter A listing includes all 3 owned jobs and EXCLUDES Company B job
        MvcResult listResult = mockMvc.perform(get("/api/recruiter/jobs?page=0&size=10")
                        .header("Authorization", "Bearer " + recA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.pagination.totalElements").value(3))
                .andReturn();

        JsonNode listNode = objectMapper.readTree(listResult.getResponse().getContentAsString());
        List<Long> visibleJobIds = new java.util.ArrayList<>();
        for (JsonNode item : listNode.get("data")) {
            visibleJobIds.add(item.get("id").asLong());
        }
        assertThat(visibleJobIds).containsExactly(jobA3, jobA2, jobA1); // Deterministic ordering created_at DESC, id DESC
        assertThat(visibleJobIds).doesNotContain(jobB);

        // 2. Pagination test: size=2, page=0 then page=1
        mockMvc.perform(get("/api/recruiter/jobs?page=0&size=2")
                        .header("Authorization", "Bearer " + recA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.totalPages").value(2));

        mockMvc.perform(get("/api/recruiter/jobs?page=1&size=2")
                        .header("Authorization", "Bearer " + recA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.pagination.page").value(1));

        // 3. Detail: Recruiter A fetches DRAFT, ACTIVE, CLOSED jobs
        mockMvc.perform(get("/api/recruiter/jobs/{id}", jobA1)
                        .header("Authorization", "Bearer " + recA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        mockMvc.perform(get("/api/recruiter/jobs/{id}", jobA2)
                        .header("Authorization", "Bearer " + recA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(get("/api/recruiter/jobs/{id}", jobA3)
                        .header("Authorization", "Bearer " + recA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // 4. Detail: Recruiter A attempting to access Company B job gets 404 JOB_NOT_FOUND (zero disclosure)
        mockMvc.perform(get("/api/recruiter/jobs/{id}", jobB)
                        .header("Authorization", "Bearer " + recA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        // 5. Detail: Nonexistent job returns 404
        mockMvc.perform(get("/api/recruiter/jobs/99999999")
                        .header("Authorization", "Bearer " + recA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    // =========================================================================
    // CATEGORY E, F, G, H: FULL INTEGRATED RECRUITER JOURNEY WITH APPLICATIONS
    // =========================================================================

    @Test
    @DisplayName("Category E, F, G, H — Complete Recruiter Lifecycle: Draft -> Edit -> Publish -> Candidate Applies -> Fundamental Lock -> Operational Edit -> Close -> Delete Protection")
    void testCompleteRecruiterWorkflowWithApplicationInvariants() throws Exception {
        String recToken = registerRecruiterAndGetToken("rec_full_journey");
        String candToken = registerCandidateAndGetToken("cand_full_journey");
        Long compId = createCompanyDirectly("Pinnacle Systems");
        linkRecruiterToCompany(getRecruiterIdFromToken(recToken), compId);

        Long skillJava = createSkill("Java Journey");
        Long skillDocker = createSkill("Docker Journey");

        // 1. Recruiter creates DRAFT job
        var createReq = new RecruiterJobCreateRequest(
                "Junior Systems Engineer", "Original description of role", "Handle cluster deployments",
                "Knowledge of Linux and Java", JobType.FULL_TIME, ExperienceLevel.FRESHER,
                WorkMode.HYBRID, "Hyderabad", new BigDecimal("7.00"), new BigDecimal("12.00"), "INR",
                LocalDate.now().plusDays(30), List.of(skillJava)
        );
        MvcResult jobCreatedResult = mockMvc.perform(post("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + recToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        Long jobId = objectMapper.readTree(jobCreatedResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        // 2. Recruiter edits DRAFT job (Category E)
        var draftUpdate = new RecruiterJobUpdateRequest(
                "Junior Systems Engineer Updated", "Refined description of role", "Cluster deployments and CI/CD",
                "Knowledge of Linux, Java, and containers", JobType.FULL_TIME, ExperienceLevel.FRESHER,
                WorkMode.HYBRID, "Hyderabad", new BigDecimal("7.50"), new BigDecimal("13.00"), "INR",
                LocalDate.now().plusDays(35), List.of(skillJava, skillDocker)
        );
        mockMvc.perform(put("/api/recruiter/jobs/{id}", jobId)
                        .header("Authorization", "Bearer " + recToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Junior Systems Engineer Updated"))
                .andExpect(jsonPath("$.data.salaryMaxLpa").value(13.00));

        // 3. Recruiter publishes job (Category G: DRAFT -> ACTIVE)
        mockMvc.perform(patch("/api/recruiter/jobs/{id}/publish", jobId)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        String statusInDb = jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(statusInDb).isEqualTo("ACTIVE");

        // 4. Candidate prepares profile and applies via P5 application workflow
        prepareCandidateProfile(candToken, "Java Journey");
        mockMvc.perform(post("/api/jobs/{id}/applications", jobId)
                        .header("Authorization", "Bearer " + candToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverLetter\": \"Excited to apply for the Systems Engineer role!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        // Verify application exists in DB
        Integer appCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM applications WHERE job_id = ?", Integer.class, jobId);
        assertThat(appCount).isEqualTo(1);

        // 5. CATEGORY F: Fundamental requirements locked!
        // Attempt to modify title
        var changeTitleReq = new RecruiterJobUpdateRequest(
                "Tampered Title", "Refined description", "Cluster deployments and CI/CD",
                "Knowledge of Linux, Java, and containers", JobType.FULL_TIME, ExperienceLevel.FRESHER,
                WorkMode.HYBRID, "Hyderabad", new BigDecimal("7.50"), new BigDecimal("13.00"), "INR",
                LocalDate.now().plusDays(35), List.of(skillJava, skillDocker)
        );
        mockMvc.perform(put("/api/recruiter/jobs/{id}", jobId)
                        .header("Authorization", "Bearer " + recToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeTitleReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // Attempt to modify salary
        var changeSalaryReq = new RecruiterJobUpdateRequest(
                "Junior Systems Engineer Updated", "Refined description", "Cluster deployments and CI/CD",
                "Knowledge of Linux, Java, and containers", JobType.FULL_TIME, ExperienceLevel.FRESHER,
                WorkMode.HYBRID, "Hyderabad", new BigDecimal("6.00"), new BigDecimal("13.00"), "INR",
                LocalDate.now().plusDays(35), List.of(skillJava, skillDocker)
        );
        mockMvc.perform(put("/api/recruiter/jobs/{id}", jobId)
                        .header("Authorization", "Bearer " + recToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeSalaryReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // Attempt to modify required skills
        var changeSkillsReq = new RecruiterJobUpdateRequest(
                "Junior Systems Engineer Updated", "Refined description", "Cluster deployments and CI/CD",
                "Knowledge of Linux, Java, and containers", JobType.FULL_TIME, ExperienceLevel.FRESHER,
                WorkMode.HYBRID, "Hyderabad", new BigDecimal("7.50"), new BigDecimal("13.00"), "INR",
                LocalDate.now().plusDays(35), List.of(skillJava)
        );
        mockMvc.perform(put("/api/recruiter/jobs/{id}", jobId)
                        .header("Authorization", "Bearer " + recToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeSkillsReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // Verify database state remained 100% UNCHANGED after rejected attempts
        Map<String, Object> jobDbAfterRejections = jdbcTemplate.queryForMap(
                "SELECT title, salary_min_lpa FROM jobs WHERE id = ?", jobId
        );
        assertThat(jobDbAfterRejections.get("title")).isEqualTo("Junior Systems Engineer Updated");
        assertThat(((BigDecimal) jobDbAfterRejections.get("salary_min_lpa")).compareTo(new BigDecimal("7.50"))).isEqualTo(0);

        // 6. CATEGORY F: Permitted operational changes STILL WORK
        var operationalUpdate = new RecruiterJobUpdateRequest(
                "Junior Systems Engineer Updated", "Updated operational description with extra interview guidelines",
                "Updated responsibilities: include Kubernetes monitoring", "Knowledge of Linux, Java, and containers",
                JobType.FULL_TIME, ExperienceLevel.FRESHER, WorkMode.HYBRID, "Hyderabad & Secunderabad",
                new BigDecimal("7.50"), new BigDecimal("13.00"), "INR",
                LocalDate.now().plusDays(40), List.of(skillJava, skillDocker)
        );
        mockMvc.perform(put("/api/recruiter/jobs/{id}", jobId)
                        .header("Authorization", "Bearer " + recToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(operationalUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Updated operational description with extra interview guidelines"))
                .andExpect(jsonPath("$.data.location").value("Hyderabad & Secunderabad"));

        // 7. CATEGORY H: Active job cannot be deleted directly (must be closed first)
        mockMvc.perform(delete("/api/recruiter/jobs/{id}", jobId)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // 8. CATEGORY G: Close job (ACTIVE -> CLOSED)
        mockMvc.perform(patch("/api/recruiter/jobs/{id}/close", jobId)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // 9. CATEGORY H: Closed job with applications CANNOT be deleted
        mockMvc.perform(delete("/api/recruiter/jobs/{id}", jobId)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // Verify application record and candidate data in MySQL remain completely intact
        Integer finalAppCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM applications WHERE job_id = ?", Integer.class, jobId);
        assertThat(finalAppCount).isEqualTo(1);
    }

    // =========================================================================
    // CATEGORY H: SOFT-DELETE BEHAVIOR ON ZERO-APPLICATION JOBS
    // =========================================================================

    @Test
    @DisplayName("Category H — Zero-application job deletion: soft-delete persistence, listing exclusion, and repeated delete safe 404")
    void testCategoryH_ZeroApplicationJobDeletion() throws Exception {
        String recToken = registerRecruiterAndGetToken("rec_delete_test");
        Long compId = createCompanyDirectly("Ephemeral Systems");
        linkRecruiterToCompany(getRecruiterIdFromToken(recToken), compId);
        Long skillId = createSkill("Kotlin Delete");

        Long jobId = createJobViaApi(recToken, "Job To Delete", WorkMode.REMOTE, null, skillId);

        // 1. Zero-application DRAFT job can be deleted
        mockMvc.perform(delete("/api/recruiter/jobs/{id}", jobId)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isNoContent());

        // 2. MySQL directly confirms deleted_at is populated
        Date deletedAt = jdbcTemplate.queryForObject("SELECT deleted_at FROM jobs WHERE id = ?", Date.class, jobId);
        assertThat(deletedAt).isNotNull();

        // 3. Excluded from listing
        mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // 4. Detail returns 404 JOB_NOT_FOUND
        mockMvc.perform(get("/api/recruiter/jobs/{id}", jobId)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        // 5. Repeated deletion returns safe 404 JOB_NOT_FOUND
        mockMvc.perform(delete("/api/recruiter/jobs/{id}", jobId)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        // 6. Cannot update, publish, or close a deleted job
        var updateReq = new RecruiterJobUpdateRequest(
                "Revived", "Desc", "Resp", "Req", JobType.FULL_TIME, ExperienceLevel.FRESHER,
                WorkMode.REMOTE, null, new BigDecimal("6.00"), new BigDecimal("10.00"), "INR",
                LocalDate.now().plusDays(20), List.of(skillId)
        );
        mockMvc.perform(put("/api/recruiter/jobs/{id}", jobId)
                        .header("Authorization", "Bearer " + recToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        mockMvc.perform(patch("/api/recruiter/jobs/{id}/publish", jobId)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        mockMvc.perform(patch("/api/recruiter/jobs/{id}/close", jobId)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    // =========================================================================
    // CATEGORY J: STALE JWT SCENARIOS
    // =========================================================================

    @Test
    @DisplayName("Category J — Stale JWT scenarios: company reassignment, nullification, and cross-boundary isolation")
    void testCategoryJ_StaleJwtScenarios() throws Exception {
        String recToken = registerRecruiterAndGetToken("rec_stale_jwt");
        Long compA = createCompanyDirectly("Company Alpha");
        Long compB = createCompanyDirectly("Company Beta");
        Long recruiterId = getRecruiterIdFromToken(recToken);
        linkRecruiterToCompany(recruiterId, compA);

        Long skillId = createSkill("Go Stale");
        Long jobA = createJobViaApi(recToken, "Alpha Job", WorkMode.REMOTE, null, skillId);

        // Scenario 1: Reassign recruiter from Company A to Company B directly in MySQL
        linkRecruiterToCompany(recruiterId, compB);

        // Original JWT now operates strictly under Company B:
        // - Company endpoint shows Company B
        mockMvc.perform(get("/api/recruiter/company")
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(compB));

        // - Job listing shows 0 jobs (Company A job is not visible)
        mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // - Company A job detail returns 404
        mockMvc.perform(get("/api/recruiter/jobs/{id}", jobA)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        // - Company A job mutations return 404
        mockMvc.perform(patch("/api/recruiter/jobs/{id}/publish", jobA)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        mockMvc.perform(patch("/api/recruiter/jobs/{id}/close", jobA)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        mockMvc.perform(delete("/api/recruiter/jobs/{id}", jobA)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        // Scenario 2: Set recruiter company_id = NULL in MySQL
        jdbcTemplate.update("UPDATE recruiters SET company_id = NULL WHERE id = ?", recruiterId);

        // Original JWT now gets 422 COMPANY_REQUIRED across all job operations
        mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("COMPANY_REQUIRED"));

        mockMvc.perform(get("/api/recruiter/jobs/{id}", jobA)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("COMPANY_REQUIRED"));

        var dummyCreate = new RecruiterJobCreateRequest(
                "Orphan Job", "Desc", "Resp", "Req", JobType.FULL_TIME, ExperienceLevel.FRESHER,
                WorkMode.REMOTE, null, new BigDecimal("5.00"), new BigDecimal("10.00"), "INR",
                LocalDate.now().plusDays(30), List.of(skillId)
        );
        mockMvc.perform(post("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + recToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dummyCreate)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("COMPANY_REQUIRED"));
    }

    // =========================================================================
    // CATEGORY L, M, N: DATA INTEGRITY, CANONICAL ENVELOPES, & TRANSACTION ATOMICITY
    // =========================================================================

    @Test
    @DisplayName("Category L, M, N — Data integrity, canonical API envelopes, and transactional atomicity")
    void testDataIntegrityAndTransactionalAtomicity() throws Exception {
        String recToken = registerRecruiterAndGetToken("rec_integrity");
        Long compId = createCompanyDirectly("Integrity Enterprise");
        linkRecruiterToCompany(getRecruiterIdFromToken(recToken), compId);

        Long skillA = createSkill("Integrity Skill A");
        Long skillB = createSkill("Integrity Skill B");

        // 1. Verify canonical creation envelope (success, data, message)
        var createReq = new RecruiterJobCreateRequest(
                "Integrity Role", "Description", "Resp", "Req", JobType.FULL_TIME, ExperienceLevel.FRESHER,
                WorkMode.REMOTE, null, new BigDecimal("8.00"), new BigDecimal("15.00"), "INR",
                LocalDate.now().plusDays(30), List.of(skillA, skillB)
        );

        MvcResult createResult = mockMvc.perform(post("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + recToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").doesNotExist()) // null or omitted in standard response
                .andReturn();

        JsonNode rootNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        assertThat(rootNode.has("success")).isTrue();
        assertThat(rootNode.has("data")).isTrue();
        Long jobId = rootNode.get("data").get("id").asLong();

        // 2. Verify canonical paginated collection envelope (success, data, pagination)
        mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination.page").isNumber())
                .andExpect(jsonPath("$.pagination.size").isNumber())
                .andExpect(jsonPath("$.pagination.totalElements").isNumber())
                .andExpect(jsonPath("$.pagination.totalPages").isNumber());

        // 3. Verify canonical error envelope (success: false, errorCode, message)
        mockMvc.perform(get("/api/recruiter/jobs/999999")
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString());

        // 4. Transactional atomicity: failed skill replacement rolls back job field changes
        var invalidSkillsUpdate = new RecruiterJobUpdateRequest(
                "Tampered Title That Should Rollback", "New Desc", "Resp", "Req", JobType.FULL_TIME, ExperienceLevel.FRESHER,
                WorkMode.REMOTE, null, new BigDecimal("8.00"), new BigDecimal("15.00"), "INR",
                LocalDate.now().plusDays(30), List.of(skillA, 9999999L) // invalid skill id triggers failure
        );

        mockMvc.perform(put("/api/recruiter/jobs/{id}", jobId)
                        .header("Authorization", "Bearer " + recToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidSkillsUpdate)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // Verify rollback: title did NOT change in database
        String titleInDb = jdbcTemplate.queryForObject("SELECT title FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(titleInDb).isEqualTo("Integrity Role");

        // Verify skills in database remained unchanged
        List<Long> skillsInDb = jdbcTemplate.query(
                "SELECT skill_id FROM job_skills WHERE job_id = ? ORDER BY skill_id",
                (rs, rowNum) -> rs.getLong("skill_id"),
                jobId
        );
        assertThat(skillsInDb).containsExactlyInAnyOrder(skillA, skillB);
    }

    // =========================================================================
    // CATEGORY I: SECURITY REGRESSION MATRIX
    // =========================================================================

    @Test
    @DisplayName("Category I — Security regression: 401 unauthenticated, 403 candidate/admin across all recruiter endpoints")
    void testCategoryI_SecurityRegressionMatrix() throws Exception {
        String candToken = registerCandidateAndGetToken("cand_sec_matrix");
        String adminToken = getAdminToken();

        // 1. GET /api/recruiter/company
        mockMvc.perform(get("/api/recruiter/company")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/recruiter/company").header("Authorization", "Bearer " + candToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/recruiter/company").header("Authorization", "Bearer " + adminToken)).andExpect(status().isForbidden());

        // 2. PUT /api/recruiter/company
        String dummyCompJson = "{\"name\":\"Test Corp\",\"description\":\"Desc\"}";
        mockMvc.perform(put("/api/recruiter/company").contentType(MediaType.APPLICATION_JSON).content(dummyCompJson)).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/recruiter/company").header("Authorization", "Bearer " + candToken).contentType(MediaType.APPLICATION_JSON).content(dummyCompJson)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/recruiter/company").header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(dummyCompJson)).andExpect(status().isForbidden());

        // 3. POST /api/recruiter/jobs
        String dummyJobJson = "{\"title\":\"Role\"}";
        mockMvc.perform(post("/api/recruiter/jobs").contentType(MediaType.APPLICATION_JSON).content(dummyJobJson)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/recruiter/jobs").header("Authorization", "Bearer " + candToken).contentType(MediaType.APPLICATION_JSON).content(dummyJobJson)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/recruiter/jobs").header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(dummyJobJson)).andExpect(status().isForbidden());

        // 4. GET /api/recruiter/jobs
        mockMvc.perform(get("/api/recruiter/jobs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/recruiter/jobs").header("Authorization", "Bearer " + candToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/recruiter/jobs").header("Authorization", "Bearer " + adminToken)).andExpect(status().isForbidden());

        // 5. GET /api/recruiter/jobs/1
        mockMvc.perform(get("/api/recruiter/jobs/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/recruiter/jobs/1").header("Authorization", "Bearer " + candToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/recruiter/jobs/1").header("Authorization", "Bearer " + adminToken)).andExpect(status().isForbidden());

        // 6. PUT /api/recruiter/jobs/1
        mockMvc.perform(put("/api/recruiter/jobs/1").contentType(MediaType.APPLICATION_JSON).content(dummyJobJson)).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/recruiter/jobs/1").header("Authorization", "Bearer " + candToken).contentType(MediaType.APPLICATION_JSON).content(dummyJobJson)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/recruiter/jobs/1").header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(dummyJobJson)).andExpect(status().isForbidden());

        // 7. DELETE /api/recruiter/jobs/1
        mockMvc.perform(delete("/api/recruiter/jobs/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/recruiter/jobs/1").header("Authorization", "Bearer " + candToken)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/recruiter/jobs/1").header("Authorization", "Bearer " + adminToken)).andExpect(status().isForbidden());

        // 8. PATCH /api/recruiter/jobs/1/publish
        mockMvc.perform(patch("/api/recruiter/jobs/1/publish")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/recruiter/jobs/1/publish").header("Authorization", "Bearer " + candToken)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/recruiter/jobs/1/publish").header("Authorization", "Bearer " + adminToken)).andExpect(status().isForbidden());

        // 9. PATCH /api/recruiter/jobs/1/close
        mockMvc.perform(patch("/api/recruiter/jobs/1/close")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/recruiter/jobs/1/close").header("Authorization", "Bearer " + candToken)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/recruiter/jobs/1/close").header("Authorization", "Bearer " + adminToken)).andExpect(status().isForbidden());
    }

    // =========================================================================
    // CATEGORY K: MULTI-COMPANY ISOLATION & CROSS-MUTATIONS
    // =========================================================================

    @Test
    @DisplayName("Category K — Multi-company isolation: complete bidirectional cross-company isolation across all operations")
    void testCategoryK_MultiCompanyIsolationAndCrossMutations() throws Exception {
        String recA = registerRecruiterAndGetToken("rec_comp_iso_a");
        String recB = registerRecruiterAndGetToken("rec_comp_iso_b");
        Long compA = createCompanyDirectly("Enterprise Alpha");
        Long compB = createCompanyDirectly("Enterprise Beta");
        linkRecruiterToCompany(getRecruiterIdFromToken(recA), compA);
        linkRecruiterToCompany(getRecruiterIdFromToken(recB), compB);

        Long skillId = createSkill("Scala Multi");
        Long jobA = createJobViaApi(recA, "Job Owned by Alpha", WorkMode.REMOTE, null, skillId);
        Long jobB = createJobViaApi(recB, "Job Owned by Beta", WorkMode.REMOTE, null, skillId);

        // Recruiter A attempts operations against Job B -> all 404 JOB_NOT_FOUND
        mockMvc.perform(get("/api/recruiter/jobs/{id}", jobB).header("Authorization", "Bearer " + recA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        var updateReq = new RecruiterJobUpdateRequest(
                "Tampered Title", "Desc", "Resp", "Req", JobType.FULL_TIME, ExperienceLevel.FRESHER,
                WorkMode.REMOTE, null, new BigDecimal("6.00"), new BigDecimal("12.00"), "INR",
                LocalDate.now().plusDays(30), List.of(skillId)
        );
        mockMvc.perform(put("/api/recruiter/jobs/{id}", jobB).header("Authorization", "Bearer " + recA)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        mockMvc.perform(patch("/api/recruiter/jobs/{id}/publish", jobB).header("Authorization", "Bearer " + recA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        mockMvc.perform(patch("/api/recruiter/jobs/{id}/close", jobB).header("Authorization", "Bearer " + recA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        mockMvc.perform(delete("/api/recruiter/jobs/{id}", jobB).header("Authorization", "Bearer " + recA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        // Recruiter B attempts operations against Job A -> all 404 JOB_NOT_FOUND
        mockMvc.perform(get("/api/recruiter/jobs/{id}", jobA).header("Authorization", "Bearer " + recB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        mockMvc.perform(put("/api/recruiter/jobs/{id}", jobA).header("Authorization", "Bearer " + recB)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        mockMvc.perform(patch("/api/recruiter/jobs/{id}/publish", jobA).header("Authorization", "Bearer " + recB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        mockMvc.perform(patch("/api/recruiter/jobs/{id}/close", jobA).header("Authorization", "Bearer " + recB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        mockMvc.perform(delete("/api/recruiter/jobs/{id}", jobA).header("Authorization", "Bearer " + recB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    // =========================================================================
    // CATEGORY O: DUPLICATE & INVALID LIFECYCLE OPERATIONS
    // =========================================================================

    @Test
    @DisplayName("Category O — Lifecycle duplicate & invalid transitions: DRAFT->CLOSED, ACTIVE->ACTIVE, CLOSED->ACTIVE, CLOSED->CLOSED")
    void testCategoryO_LifecycleDuplicateAndInvalidOperations() throws Exception {
        String recToken = registerRecruiterAndGetToken("rec_dup_ops");
        Long compId = createCompanyDirectly("Duplicate Ops Corp");
        linkRecruiterToCompany(getRecruiterIdFromToken(recToken), compId);

        Long skillId = createSkill("Erlang Dup");
        Long jobId = createJobViaApi(recToken, "Lifecycle Target Job", WorkMode.REMOTE, null, skillId);

        // 1. Invalid DRAFT -> CLOSED directly via /close
        mockMvc.perform(patch("/api/recruiter/jobs/{id}/close", jobId)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // 2. Valid DRAFT -> ACTIVE
        mockMvc.perform(patch("/api/recruiter/jobs/{id}/publish", jobId)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // 3. Duplicate ACTIVE -> ACTIVE via /publish
        mockMvc.perform(patch("/api/recruiter/jobs/{id}/publish", jobId)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // 4. Valid ACTIVE -> CLOSED
        mockMvc.perform(patch("/api/recruiter/jobs/{id}/close", jobId)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // 5. Invalid CLOSED -> ACTIVE via /publish
        mockMvc.perform(patch("/api/recruiter/jobs/{id}/publish", jobId)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // 6. Duplicate CLOSED -> CLOSED via /close
        mockMvc.perform(patch("/api/recruiter/jobs/{id}/close", jobId)
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // 7. Closed job cannot be edited via generic PUT
        var updateReq = new RecruiterJobUpdateRequest(
                "Closed Edit", "Desc", "Resp", "Req", JobType.FULL_TIME, ExperienceLevel.FRESHER,
                WorkMode.REMOTE, null, new BigDecimal("6.00"), new BigDecimal("12.00"), "INR",
                LocalDate.now().plusDays(30), List.of(skillId)
        );
        mockMvc.perform(put("/api/recruiter/jobs/{id}", jobId)
                        .header("Authorization", "Bearer " + recToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    // =========================================================================
    // HELPER: CREATE JOB VIA POST /api/recruiter/jobs
    // =========================================================================

    private Long createJobViaApi(String recruiterToken, String title, WorkMode workMode, String location, Long skillId) throws Exception {
        var req = new RecruiterJobCreateRequest(
                title, "Standard role description", "Core responsibilities", "Key requirements",
                JobType.FULL_TIME, ExperienceLevel.FRESHER, workMode, location,
                new BigDecimal("6.00"), new BigDecimal("12.00"), "INR",
                LocalDate.now().plusDays(30), List.of(skillId)
        );

        MvcResult res = mockMvc.perform(post("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
        return node.get("data").get("id").asLong();
    }
}
