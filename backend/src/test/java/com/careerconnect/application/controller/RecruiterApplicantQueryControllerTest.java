package com.careerconnect.application.controller;

import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.UpdateProfileRequest;
import com.careerconnect.common.model.Role;
import com.careerconnect.jobs.model.JobStatus;
import com.careerconnect.security.JwtService;
import com.careerconnect.security.SecurityUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecruiterApplicantQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM application_status_history");
        jdbcTemplate.execute("DELETE FROM applications");
        jdbcTemplate.execute("DELETE FROM resumes");
        jdbcTemplate.execute("DELETE FROM candidate_skills");
        jdbcTemplate.execute("DELETE FROM education");
        jdbcTemplate.execute("DELETE FROM job_skills");
        jdbcTemplate.execute("DELETE FROM jobs");
        jdbcTemplate.execute("DELETE FROM companies");
    }

    private record RecruiterAuth(String token, Long recruiterId, Long userId) {}
    private record CandidateAuth(String token, Long candidateId, Long userId, String fullName) {}

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

    private CandidateAuth registerCandidate(String emailPrefix) throws Exception {
        String email = emailPrefix + "_" + UUID.randomUUID() + "@example.com";
        String fullName = "Candidate " + emailPrefix;
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.CANDIDATE, fullName);
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        String token = root.path("data").path("accessToken").asText();
        Long candidateId = root.path("data").path("user").path("candidateId").asLong();
        Long userId = root.path("data").path("user").path("id").asLong();
        return new CandidateAuth(token, candidateId, userId, fullName);
    }

    private String generateAdminToken() {
        return jwtService.generateToken(new SecurityUser(9999L, "admin.p71@example.com", "HASH", Role.ADMIN, "Admin User", null, null, null, true));
    }

    private Long createCompany(String name) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO companies (name, industry, location, is_active) VALUES (?, 'Tech', 'Bengaluru', TRUE)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name + "_" + UUID.randomUUID());
            return ps;
        }, kh);
        return com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
    }

    private void linkRecruiterToCompany(Long recruiterId, Long companyId) {
        jdbcTemplate.update("UPDATE recruiters SET company_id = ? WHERE id = ?", companyId, recruiterId);
    }

    private Long createJob(Long companyId, String title, JobStatus status) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO jobs (
                        company_id, title, description, job_type, experience_level,
                        work_mode, location, salary_min_lpa, salary_max_lpa, salary_currency,
                        application_deadline, status, created_at, updated_at
                    ) VALUES (?, ?, 'Job Description', 'INTERNSHIP', 'FRESHER', 'HYBRID', 'Bengaluru', 5.0, 10.0, 'INR', ?, ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            ps.setString(2, title);
            ps.setDate(3, Date.valueOf(LocalDate.now().plusMonths(1)));
            ps.setString(4, status.name());
            return ps;
        }, kh);
        return com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
    }

    private Long getOrCreateSkill(String name) {
        String norm = name.trim().toLowerCase();
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM skills WHERE normalized_name = ?",
                (rs, rowNum) -> rs.getLong("id"), norm);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO skills (name, normalized_name, created_at) VALUES (?, ?, CURRENT_TIMESTAMP(3))",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, norm);
            return ps;
        }, kh);
        return com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
    }

    private void addJobSkill(Long jobId, Long skillId) {
        jdbcTemplate.update("INSERT IGNORE INTO job_skills (job_id, skill_id) VALUES (?, ?)", jobId, skillId);
    }

    private void setupCandidateReadiness(CandidateAuth cand, List<String> skills) throws Exception {
        // 1. Profile details
        UpdateProfileRequest profReq = new UpdateProfileRequest(cand.fullName(), "Full-Stack Dev", "Bengaluru", "Passionate developer");
        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profReq)))
                .andExpect(status().isOk());

        // 2. Education
        mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "institution": "National Institute of Tech",
                                  "degree": "B.Tech",
                                  "fieldOfStudy": "Computer Science",
                                  "startYear": 2020,
                                  "endYear": 2024
                                }
                                """))
                .andExpect(status().isCreated());

        // 3. Skills
        for (String skill : skills) {
            mockMvc.perform(post("/api/candidate/skills")
                            .header("Authorization", "Bearer " + cand.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of("name", skill))))
                    .andExpect(status().isCreated());
        }

        // 4. Resume upload
        byte[] pdfBytes = "%PDF-1.4\nTest Resume Content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + cand.token()))
                .andExpect(status().isCreated());
    }

    private Long submitApplication(CandidateAuth cand, Long jobId, String coverLetter) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/jobs/" + jobId + "/applications")
                        .header("Authorization", "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("coverLetter", coverLetter))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("applicationId").asLong();
    }

    // ─── A. Authentication Tests ──────────────────────────────────────────────

    @Test
    @DisplayName("P7.1-01: Unauthenticated request to applicant listing -> 401 UNAUTHENTICATED")
    void getJobApplicants_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/recruiter/jobs/1/applications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("UNAUTHENTICATED")));
    }

    @Test
    @DisplayName("P7.1-02: Unauthenticated request to applicant detail -> 401 UNAUTHENTICATED")
    void getApplicantDetail_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/recruiter/applications/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("UNAUTHENTICATED")));
    }

    // ─── B. Role Authorization Tests ──────────────────────────────────────────

    @Test
    @DisplayName("P7.1-03: Candidate access to applicant listing -> 403 FORBIDDEN")
    void getJobApplicants_candidateRole_returns403() throws Exception {
        CandidateAuth cand = registerCandidate("cand_role_list");

        mockMvc.perform(get("/api/recruiter/jobs/1/applications")
                        .header("Authorization", "Bearer " + cand.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("P7.1-04: Candidate access to applicant detail -> 403 FORBIDDEN")
    void getApplicantDetail_candidateRole_returns403() throws Exception {
        CandidateAuth cand = registerCandidate("cand_role_det");

        mockMvc.perform(get("/api/recruiter/applications/1")
                        .header("Authorization", "Bearer " + cand.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("P7.1-05: Admin access to applicant listing -> 403 FORBIDDEN")
    void getJobApplicants_adminRole_returns403() throws Exception {
        String adminToken = generateAdminToken();

        mockMvc.perform(get("/api/recruiter/jobs/1/applications")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("P7.1-06: Admin access to applicant detail -> 403 FORBIDDEN")
    void getApplicantDetail_adminRole_returns403() throws Exception {
        String adminToken = generateAdminToken();

        mockMvc.perform(get("/api/recruiter/applications/1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    // ─── C. Recruiter Company Requirement Tests ───────────────────────────────

    @Test
    @DisplayName("P7.1-07: Recruiter without company listing applicants -> 422 COMPANY_REQUIRED")
    void getJobApplicants_recruiterWithoutCompany_returns422() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec_no_comp_list");

        mockMvc.perform(get("/api/recruiter/jobs/1/applications")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("COMPANY_REQUIRED")));
    }

    @Test
    @DisplayName("P7.1-08: Recruiter without company detail applicant -> 422 COMPANY_REQUIRED")
    void getApplicantDetail_recruiterWithoutCompany_returns422() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec_no_comp_det");

        mockMvc.perform(get("/api/recruiter/applications/1")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("COMPANY_REQUIRED")));
    }

    // ─── D. Own Company Success & Listing Details ─────────────────────────────

    @Test
    @DisplayName("P7.1-09: Recruiter lists applicants for own company job -> 200 with canonical envelope & match percentage")
    void getJobApplicants_ownCompany_returns200() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec_own_list");
        Long compId = createCompany("Alpha Corp");
        linkRecruiterToCompany(rec.recruiterId(), compId);

        Long jobId = createJob(compId, "Software Engineer Intern", JobStatus.ACTIVE);
        Long javaSkill = getOrCreateSkill("Java");
        Long sqlSkill = getOrCreateSkill("SQL");
        addJobSkill(jobId, javaSkill);
        addJobSkill(jobId, sqlSkill);

        // Candidate with Java and SQL -> 100% match
        CandidateAuth cand1 = registerCandidate("cand_app1");
        setupCandidateReadiness(cand1, List.of("Java", "SQL"));
        Long app1Id = submitApplication(cand1, jobId, "Excited about this opportunity");

        // Candidate with Java only -> 50% match
        CandidateAuth cand2 = registerCandidate("cand_app2");
        setupCandidateReadiness(cand2, List.of("Java"));
        Long app2Id = submitApplication(cand2, jobId, "Strong Java background");

        mockMvc.perform(get("/api/recruiter/jobs/" + jobId + "/applications")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.pagination.totalElements", is(2)))
                .andExpect(jsonPath("$.pagination.page", is(0)))
                .andExpect(jsonPath("$.pagination.size", is(20)))
                // First returned is cand2 (most recently applied)
                .andExpect(jsonPath("$.data[0].applicationId", is(app2Id.intValue())))
                .andExpect(jsonPath("$.data[0].candidateName", is("Candidate cand_app2")))
                .andExpect(jsonPath("$.data[0].candidateHeadline", is("Full-Stack Dev")))
                .andExpect(jsonPath("$.data[0].status", is("APPLIED")))
                .andExpect(jsonPath("$.data[0].matchPercentage", is(50)))
                // Second returned is cand1
                .andExpect(jsonPath("$.data[1].applicationId", is(app1Id.intValue())))
                .andExpect(jsonPath("$.data[1].candidateName", is("Candidate cand_app1")))
                .andExpect(jsonPath("$.data[1].matchPercentage", is(100)));
    }

    // ─── E. Cross-Company Isolation Tests ─────────────────────────────────────

    @Test
    @DisplayName("P7.1-10: Recruiter A cannot list applicants for Company B's job -> 404 JOB_NOT_FOUND")
    void getJobApplicants_foreignCompanyJob_returns404() throws Exception {
        RecruiterAuth recA = registerRecruiter("rec_a_iso");
        Long compA = createCompany("Company A");
        linkRecruiterToCompany(recA.recruiterId(), compA);

        RecruiterAuth recB = registerRecruiter("rec_b_iso");
        Long compB = createCompany("Company B");
        linkRecruiterToCompany(recB.recruiterId(), compB);

        Long jobB = createJob(compB, "Secret Job B", JobStatus.ACTIVE);

        mockMvc.perform(get("/api/recruiter/jobs/" + jobB + "/applications")
                        .header("Authorization", "Bearer " + recA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("JOB_NOT_FOUND")));
    }

    @Test
    @DisplayName("P7.1-11: Recruiter A cannot view applicant detail for Company B's application -> 404 RESOURCE_NOT_FOUND")
    void getApplicantDetail_foreignCompanyApplication_returns404() throws Exception {
        RecruiterAuth recA = registerRecruiter("rec_a_det_iso");
        Long compA = createCompany("Company A Inc");
        linkRecruiterToCompany(recA.recruiterId(), compA);

        RecruiterAuth recB = registerRecruiter("rec_b_det_iso");
        Long compB = createCompany("Company B Inc");
        linkRecruiterToCompany(recB.recruiterId(), compB);

        Long jobB = createJob(compB, "Job B Title", JobStatus.ACTIVE);
        addJobSkill(jobB, getOrCreateSkill("Java"));

        CandidateAuth cand = registerCandidate("cand_foreign_app");
        setupCandidateReadiness(cand, List.of("Java"));
        Long appBId = submitApplication(cand, jobB, "Cover letter for Company B");

        mockMvc.perform(get("/api/recruiter/applications/" + appBId)
                        .header("Authorization", "Bearer " + recA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("P7.1-12: Non-existent job listing -> 404 JOB_NOT_FOUND")
    void getJobApplicants_nonExistentJob_returns404() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec_nonexist");
        Long comp = createCompany("NonExist Corp");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        mockMvc.perform(get("/api/recruiter/jobs/999999/applications")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("JOB_NOT_FOUND")));
    }

    @Test
    @DisplayName("P7.1-13: Soft-deleted job listing -> 404 JOB_NOT_FOUND")
    void getJobApplicants_deletedJob_returns404() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec_del_job");
        Long comp = createCompany("Deleted Job Corp");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long jobId = createJob(comp, "Deleted Title", JobStatus.CLOSED);
        jdbcTemplate.update("UPDATE jobs SET deleted_at = CURRENT_TIMESTAMP(3) WHERE id = ?", jobId);

        mockMvc.perform(get("/api/recruiter/jobs/" + jobId + "/applications")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("JOB_NOT_FOUND")));
    }

    // ─── F. Status Filter Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("P7.1-14: Status filter returns only matching applicants")
    void getJobApplicants_statusFilter_returnsFiltered() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec_filter");
        Long comp = createCompany("Filter Corp");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long jobId = createJob(comp, "Filtered Job", JobStatus.ACTIVE);
        addJobSkill(jobId, getOrCreateSkill("Java"));

        CandidateAuth cand1 = registerCandidate("cand_filt1");
        setupCandidateReadiness(cand1, List.of("Java"));
        Long app1Id = submitApplication(cand1, jobId, "App 1");

        CandidateAuth cand2 = registerCandidate("cand_filt2");
        setupCandidateReadiness(cand2, List.of("Java"));
        Long app2Id = submitApplication(cand2, jobId, "App 2");

        // Manually update app2 in DB to SHORTLISTED to simulate a status transition
        jdbcTemplate.update("UPDATE applications SET status = 'SHORTLISTED' WHERE id = ?", app2Id);

        // Filter by APPLIED -> only app1 returned
        mockMvc.perform(get("/api/recruiter/jobs/" + jobId + "/applications?status=APPLIED")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].applicationId", is(app1Id.intValue())))
                .andExpect(jsonPath("$.pagination.totalElements", is(1)));

        // Filter by SHORTLISTED -> only app2 returned
        mockMvc.perform(get("/api/recruiter/jobs/" + jobId + "/applications?status=SHORTLISTED")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].applicationId", is(app2Id.intValue())));

        // Filter by REJECTED -> 0 returned
        mockMvc.perform(get("/api/recruiter/jobs/" + jobId + "/applications?status=REJECTED")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.pagination.totalElements", is(0)));
    }

    // ─── G. Pagination & Deterministic Ordering ───────────────────────────────

    @Test
    @DisplayName("P7.1-15: Pagination page and size clamping with applied_at DESC, id DESC ordering")
    void getJobApplicants_paginationAndOrdering() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec_page");
        Long comp = createCompany("Page Corp");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long jobId = createJob(comp, "Paginated Job", JobStatus.ACTIVE);
        addJobSkill(jobId, getOrCreateSkill("Java"));

        CandidateAuth cand1 = registerCandidate("cand_page1");
        setupCandidateReadiness(cand1, List.of("Java"));
        Long app1 = submitApplication(cand1, jobId, "Page 1 app");

        CandidateAuth cand2 = registerCandidate("cand_page2");
        setupCandidateReadiness(cand2, List.of("Java"));
        Long app2 = submitApplication(cand2, jobId, "Page 2 app");

        CandidateAuth cand3 = registerCandidate("cand_page3");
        setupCandidateReadiness(cand3, List.of("Java"));
        Long app3 = submitApplication(cand3, jobId, "Page 3 app");

        // Page 0, size 2 -> should return app3 and app2
        mockMvc.perform(get("/api/recruiter/jobs/" + jobId + "/applications?page=0&size=2")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.pagination.totalElements", is(3)))
                .andExpect(jsonPath("$.pagination.totalPages", is(2)))
                .andExpect(jsonPath("$.data[0].applicationId", is(app3.intValue())))
                .andExpect(jsonPath("$.data[1].applicationId", is(app2.intValue())));

        // Page 1, size 2 -> should return app1
        mockMvc.perform(get("/api/recruiter/jobs/" + jobId + "/applications?page=1&size=2")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].applicationId", is(app1.intValue())));
    }

    // ─── H. Applicant Detail & Match Breakdown ────────────────────────────────

    @Test
    @DisplayName("P7.1-16: Recruiter applicant detail returns complete snapshot, status history, and deterministic match")
    void getApplicantDetail_ownCompany_returnsCompleteDetail() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec_det_full");
        Long comp = createCompany("Full Detail Corp");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long jobId = createJob(comp, "Java Microservices Intern", JobStatus.ACTIVE);
        addJobSkill(jobId, getOrCreateSkill("Java"));
        addJobSkill(jobId, getOrCreateSkill("Spring Boot"));
        addJobSkill(jobId, getOrCreateSkill("Docker"));

        CandidateAuth cand = registerCandidate("cand_det_full");
        setupCandidateReadiness(cand, List.of("Java", "Spring Boot", "Git"));
        Long appId = submitApplication(cand, jobId, "I am passionate about backend microservices.");

        mockMvc.perform(get("/api/recruiter/applications/" + appId)
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.applicationId", is(appId.intValue())))
                .andExpect(jsonPath("$.data.jobId", is(jobId.intValue())))
                .andExpect(jsonPath("$.data.jobTitle", is("Java Microservices Intern")))
                .andExpect(jsonPath("$.data.candidateId", is(cand.candidateId().intValue())))
                .andExpect(jsonPath("$.data.status", is("APPLIED")))
                .andExpect(jsonPath("$.data.coverLetter", is("I am passionate about backend microservices.")))
                // Candidate Snapshot
                .andExpect(jsonPath("$.data.candidateSnapshot.fullName", is("Candidate cand_det_full")))
                .andExpect(jsonPath("$.data.candidateSnapshot.headline", is("Full-Stack Dev")))
                .andExpect(jsonPath("$.data.candidateSnapshot.location", is("Bengaluru")))
                .andExpect(jsonPath("$.data.candidateSnapshot.education[0].institution", is("National Institute of Tech")))
                .andExpect(jsonPath("$.data.candidateSnapshot.skills", hasSize(3)))
                // Resume Snapshot
                .andExpect(jsonPath("$.data.resumeSnapshot.originalFilename", is("resume.pdf")))
                .andExpect(jsonPath("$.data.resumeSnapshot.contentType", is("application/pdf")))
                // Status History
                .andExpect(jsonPath("$.data.statusHistory", hasSize(1)))
                .andExpect(jsonPath("$.data.statusHistory[0].toStatus", is("APPLIED")))
                // Match Breakdown: 2 matched out of 3 required (Java, Spring Boot) -> 67%
                .andExpect(jsonPath("$.data.match.percentage", is(67)))
                .andExpect(jsonPath("$.data.match.matchedSkills", hasSize(2)))
                .andExpect(jsonPath("$.data.match.missingSkills", hasSize(1))) // Docker
                .andExpect(jsonPath("$.data.match.additionalSkills", hasSize(1))); // Git
    }

    // ─── I. Snapshot Immutability Verification ─────────────────────────────────

    @Test
    @DisplayName("P7.1-17: Subsequent candidate profile changes do NOT alter recruiter snapshot view")
    void snapshotImmutability_candidateEditsDoNotAlterRecruiterView() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec_snap_imm");
        Long comp = createCompany("Immutability Corp");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long jobId = createJob(comp, "React Intern", JobStatus.ACTIVE);
        addJobSkill(jobId, getOrCreateSkill("React"));

        CandidateAuth cand = registerCandidate("cand_snap_imm");
        setupCandidateReadiness(cand, List.of("React"));
        Long appId = submitApplication(cand, jobId, "Initial application");

        // Now candidate mutates their live profile
        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest(
                                "Renamed Candidate", "Senior Architect", "San Francisco", "Changed summary"))))
                .andExpect(status().isOk());

        // Recruiter queries applicant detail
        mockMvc.perform(get("/api/recruiter/applications/" + appId)
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                // Must retain original snapshot data
                .andExpect(jsonPath("$.data.candidateSnapshot.fullName", is("Candidate cand_snap_imm")))
                .andExpect(jsonPath("$.data.candidateSnapshot.headline", is("Full-Stack Dev")))
                .andExpect(jsonPath("$.data.candidateSnapshot.location", is("Bengaluru")));

        // Recruiter queries applicant listing
        mockMvc.perform(get("/api/recruiter/jobs/" + jobId + "/applications")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].candidateName", is("Candidate cand_snap_imm")))
                .andExpect(jsonPath("$.data[0].candidateHeadline", is("Full-Stack Dev")));
    }

    // ─── J. Stale JWT Handling & Security Tampering ────────────────────────────

    @Test
    @DisplayName("P7.1-18: Stale JWT company removal returns 422 COMPANY_REQUIRED")
    void staleJwt_companyRemovedInDb_returns422() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec_stale_jwt");
        Long comp = createCompany("Stale JWT Corp");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long jobId = createJob(comp, "Stale Job", JobStatus.ACTIVE);

        // Remove company association directly in DB
        jdbcTemplate.update("UPDATE recruiters SET company_id = NULL WHERE id = ?", rec.recruiterId());

        // Reusing original token must be rejected with COMPANY_REQUIRED
        mockMvc.perform(get("/api/recruiter/jobs/" + jobId + "/applications")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("COMPANY_REQUIRED")));
    }

    @Test
    @DisplayName("P7.1-19: Client-supplied companyId query parameter is ignored")
    void clientSuppliedCompanyId_ignored() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec_spoof");
        Long comp = createCompany("Spoof Corp");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long jobId = createJob(comp, "Real Job", JobStatus.ACTIVE);

        // Passing spoofed companyId parameter has no effect
        mockMvc.perform(get("/api/recruiter/jobs/" + jobId + "/applications?companyId=999999")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("P7.1-20: Job with zero applicants returns empty list and totalElements 0")
    void getJobApplicants_zeroApplicants_returnsEmptyList() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec_zero");
        Long comp = createCompany("Zero Corp");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long jobId = createJob(comp, "Zero Applicants Job", JobStatus.ACTIVE);

        mockMvc.perform(get("/api/recruiter/jobs/" + jobId + "/applications")
                        .header("Authorization", "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.pagination.totalElements", is(0)));
    }
}
