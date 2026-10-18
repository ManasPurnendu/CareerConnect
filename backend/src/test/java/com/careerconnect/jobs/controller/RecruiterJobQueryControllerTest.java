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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecruiterJobQueryControllerTest {

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
                VALUES (?, 'Technology', 'Bengaluru', 'https://example.com', 'A test company', '11-50', TRUE)
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
    // A. Authentication Tests
    // ==========================================

    @Test
    @DisplayName("P6.5-AUTH-01: Unauthenticated recruiter job list returns 401 UNAUTHENTICATED")
    void unauthenticatedJobList_Returns401() throws Exception {
        mockMvc.perform(get("/api/recruiter/jobs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("P6.5-AUTH-02: Unauthenticated recruiter job detail returns 401 UNAUTHENTICATED")
    void unauthenticatedJobDetail_Returns401() throws Exception {
        mockMvc.perform(get("/api/recruiter/jobs/1"))
                .andExpect(status().isUnauthorized());
    }

    // ==========================================
    // B. Role & Authorization Tests
    // ==========================================

    @Test
    @DisplayName("P6.5-SEC-01: Candidate cannot access recruiter job listing (returns 403 FORBIDDEN)")
    void candidateCannotAccessRecruiterJobList_Returns403() throws Exception {
        String token = registerCandidate("cand.list.forbidden");
        mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P6.5-SEC-02: Candidate cannot access recruiter job detail (returns 403 FORBIDDEN)")
    void candidateCannotAccessRecruiterJobDetail_Returns403() throws Exception {
        String token = registerCandidate("cand.detail.forbidden");
        mockMvc.perform(get("/api/recruiter/jobs/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P6.5-SEC-03: Admin cannot access recruiter job listing (returns 403 FORBIDDEN)")
    void adminCannotAccessRecruiterJobList_Returns403() throws Exception {
        String token = generateAdminToken();
        mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P6.5-SEC-04: Admin cannot access recruiter job detail (returns 403 FORBIDDEN)")
    void adminCannotAccessRecruiterJobDetail_Returns403() throws Exception {
        String token = generateAdminToken();
        mockMvc.perform(get("/api/recruiter/jobs/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P6.5-SEC-05: Recruiter without associated company gets 422 COMPANY_REQUIRED on listing")
    void recruiterWithoutCompany_List_Returns422() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.no.comp.list");
        mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("COMPANY_REQUIRED"));
    }

    @Test
    @DisplayName("P6.5-SEC-06: Recruiter without associated company gets 422 COMPANY_REQUIRED on detail")
    void recruiterWithoutCompany_Detail_Returns422() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.no.comp.det");
        mockMvc.perform(get("/api/recruiter/jobs/1")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("COMPANY_REQUIRED"));
    }

    // ==========================================
    // C. Ownership & Cross-Company Security Tests
    // ==========================================

    @Test
    @DisplayName("P6.5-OWN-01: Recruiter sees only jobs belonging to their own company")
    void recruiterSeesOnlyOwnCompanyJobs() throws Exception {
        RecruiterAuth recA = registerRecruiter("rec.own.list.a");
        Long compA = createCompany("Company Alpha");
        associateRecruiterWithCompany(recA.recruiterId(), compA);

        RecruiterAuth recB = registerRecruiter("rec.own.list.b");
        Long compB = createCompany("Company Beta");
        associateRecruiterWithCompany(recB.recruiterId(), compB);

        // Company A jobs
        Long jobA1 = createJob(compA, "Alpha Job 1", "Desc", JobStatus.DRAFT, LocalDate.now().plusDays(10), "REMOTE", null, null, null, List.of("Java"));
        Long jobA2 = createJob(compA, "Alpha Job 2", "Desc", JobStatus.ACTIVE, LocalDate.now().plusDays(20), "REMOTE", null, null, null, List.of("Spring"));

        // Company B jobs
        Long jobB1 = createJob(compB, "Beta Job 1", "Desc", JobStatus.ACTIVE, LocalDate.now().plusDays(15), "REMOTE", null, null, null, List.of("React"));

        // Recruiter A lists jobs -> only Alpha jobs
        MvcResult res = mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + recA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].company.id").value(compA))
                .andExpect(jsonPath("$.data[1].company.id").value(compA))
                .andExpect(jsonPath("$.pagination.totalElements").value(2))
                .andReturn();

        // Recruiter B lists jobs -> only Beta jobs
        mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + recB.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(jobB1))
                .andExpect(jsonPath("$.data[0].company.id").value(compB))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
    }

    @Test
    @DisplayName("P6.5-OWN-02: Recruiter cannot access another company's job detail (returns 404 JOB_NOT_FOUND)")
    void recruiterCannotAccessOtherCompanyJobDetail_Returns404() throws Exception {
        RecruiterAuth recA = registerRecruiter("rec.cross.det.a");
        Long compA = createCompany("Company A Cross");
        associateRecruiterWithCompany(recA.recruiterId(), compA);

        RecruiterAuth recB = registerRecruiter("rec.cross.det.b");
        Long compB = createCompany("Company B Cross");
        associateRecruiterWithCompany(recB.recruiterId(), compB);

        Long jobB = createJob(compB, "Beta Secret Job", "Desc", JobStatus.DRAFT, LocalDate.now().plusDays(10), "REMOTE", null, null, null, List.of("Security"));

        // Recruiter A attempts to view jobB
        mockMvc.perform(get("/api/recruiter/jobs/" + jobB)
                        .header("Authorization", "Bearer " + recA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    @Test
    @DisplayName("P6.5-OWN-03: Client-supplied companyId query param does not change recruiter scope")
    void clientSuppliedCompanyIdQueryParam_Ignored() throws Exception {
        RecruiterAuth recA = registerRecruiter("rec.tamper.param");
        Long compA = createCompany("Company Real");
        associateRecruiterWithCompany(recA.recruiterId(), compA);

        Long compB = createCompany("Company Target");
        Long jobB = createJob(compB, "Target Job", "Desc", JobStatus.ACTIVE, LocalDate.now().plusDays(10), "REMOTE", null, null, null, List.of("Java"));

        // Recruiter A tries to list Company B jobs by passing ?companyId=compB
        mockMvc.perform(get("/api/recruiter/jobs?companyId=" + compB)
                        .header("Authorization", "Bearer " + recA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.pagination.totalElements").value(0));
    }

    // ==========================================
    // D. Listing & Pagination Tests
    // ==========================================

    @Test
    @DisplayName("P6.5-LST-01: Empty company job list returns 200 with empty data array and zero totalElements")
    void emptyCompanyJobList_Returns200WithEmptyArray() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.empty.list");
        Long comp = createCompany("Empty Jobs Corp");
        associateRecruiterWithCompany(rec.recruiterId(), comp);

        mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(20))
                .andExpect(jsonPath("$.pagination.totalElements").value(0))
                .andExpect(jsonPath("$.pagination.totalPages").value(0));
    }

    @Test
    @DisplayName("P6.5-LST-02: Pagination page 0, size, and page > 0 behave correctly")
    void paginationAndPages_WorkCorrectly() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.page.test");
        Long comp = createCompany("Pagination Corp");
        associateRecruiterWithCompany(rec.recruiterId(), comp);

        // Create 5 jobs for this company
        for (int i = 1; i <= 5; i++) {
            createJob(comp, "Paged Job " + i, "Desc " + i, JobStatus.DRAFT, LocalDate.now().plusDays(20), "REMOTE", null, null, null, List.of("Java"));
        }

        // Page 0, size 2 -> 2 items, total 5, totalPages 3
        mockMvc.perform(get("/api/recruiter/jobs?page=0&size=2")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(2))
                .andExpect(jsonPath("$.pagination.totalElements").value(5))
                .andExpect(jsonPath("$.pagination.totalPages").value(3));

        // Page 1, size 2 -> 2 items
        mockMvc.perform(get("/api/recruiter/jobs?page=1&size=2")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.pagination.page").value(1));

        // Page 2, size 2 -> 1 item remaining
        mockMvc.perform(get("/api/recruiter/jobs?page=2&size=2")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.pagination.page").value(2));
    }

    @Test
    @DisplayName("P6.5-LST-03: Maximum page size is capped at 100")
    void maxPageSizeCappedAt100() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.max.size");
        Long comp = createCompany("Max Size Corp");
        associateRecruiterWithCompany(rec.recruiterId(), comp);

        mockMvc.perform(get("/api/recruiter/jobs?page=0&size=500")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.size").value(100));
    }

    @Test
    @DisplayName("P6.5-LST-04: Deterministic ordering: created_at DESC, id DESC")
    void deterministicOrdering() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.order.test");
        Long comp = createCompany("Order Corp");
        associateRecruiterWithCompany(rec.recruiterId(), comp);

        Long j1 = createJob(comp, "First Job", "Desc", JobStatus.DRAFT, LocalDate.now().plusDays(10), "REMOTE", null, null, null, List.of("Java"));
        Long j2 = createJob(comp, "Second Job", "Desc", JobStatus.ACTIVE, LocalDate.now().plusDays(10), "REMOTE", null, null, null, List.of("Spring"));
        Long j3 = createJob(comp, "Third Job", "Desc", JobStatus.CLOSED, LocalDate.now().plusDays(10), "REMOTE", null, null, null, List.of("Docker"));

        mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(j3))
                .andExpect(jsonPath("$.data[1].id").value(j2))
                .andExpect(jsonPath("$.data[2].id").value(j1));
    }

    // ==========================================
    // E. Detail & Field Mapping Tests
    // ==========================================

    @Test
    @DisplayName("P6.5-DET-01: Owned job returns complete details and structured skill DTOs")
    void ownedJobReturnsCompleteDetails() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.det.complete");
        Long comp = createCompany("Detail Corp");
        associateRecruiterWithCompany(rec.recruiterId(), comp);

        Long jobId = createJob(comp, "Principal Architect", "Architectural leadership", JobStatus.ACTIVE,
                LocalDate.now().plusDays(45), "HYBRID", "Bengaluru",
                new BigDecimal("25.00"), new BigDecimal("35.00"), List.of("Microservices", "Java", "Kubernetes"));

        mockMvc.perform(get("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(jobId))
                .andExpect(jsonPath("$.data.title").value("Principal Architect"))
                .andExpect(jsonPath("$.data.description").value("Architectural leadership"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.jobType").value("FULL_TIME"))
                .andExpect(jsonPath("$.data.experienceLevel").value("FRESHER"))
                .andExpect(jsonPath("$.data.workMode").value("HYBRID"))
                .andExpect(jsonPath("$.data.location").value("Bengaluru"))
                .andExpect(jsonPath("$.data.salaryMinLpa").value(25.00))
                .andExpect(jsonPath("$.data.salaryMaxLpa").value(35.00))
                .andExpect(jsonPath("$.data.salaryCurrency").value("INR"))
                .andExpect(jsonPath("$.data.applicationDeadline").value(LocalDate.now().plusDays(45).toString()))
                .andExpect(jsonPath("$.data.skills", hasSize(3)))
                .andExpect(jsonPath("$.data.company.id").value(comp));
    }

    @Test
    @DisplayName("P6.5-DET-02: Non-existent job returns 404 JOB_NOT_FOUND")
    void nonExistentJob_Returns404() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.notfound.det");
        Long comp = createCompany("NotFound Corp");
        associateRecruiterWithCompany(rec.recruiterId(), comp);

        mockMvc.perform(get("/api/recruiter/jobs/999999")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    @Test
    @DisplayName("P6.5-DET-03: Recruiter can view jobs of all statuses (DRAFT, ACTIVE, CLOSED)")
    void recruiterCanViewAllJobStatuses() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.all.statuses");
        Long comp = createCompany("All Statuses Corp");
        associateRecruiterWithCompany(rec.recruiterId(), comp);

        Long draftJob = createJob(comp, "Draft One", "Desc", JobStatus.DRAFT, LocalDate.now().plusDays(10), "REMOTE", null, null, null, List.of("Java"));
        Long activeJob = createJob(comp, "Active One", "Desc", JobStatus.ACTIVE, LocalDate.now().plusDays(10), "REMOTE", null, null, null, List.of("Java"));
        Long closedJob = createJob(comp, "Closed One", "Desc", JobStatus.CLOSED, LocalDate.now().plusDays(10), "REMOTE", null, null, null, List.of("Java"));

        // All 3 appear in recruiter listing (unlike public listing which only shows active)
        mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.pagination.totalElements").value(3));

        // Each individual detail is viewable
        mockMvc.perform(get("/api/recruiter/jobs/" + draftJob)
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(get("/api/recruiter/jobs/" + closedJob)
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }
}
