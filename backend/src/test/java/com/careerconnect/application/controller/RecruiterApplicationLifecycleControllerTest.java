package com.careerconnect.application.controller;

import com.careerconnect.application.domain.ApplicationStatus;
import com.careerconnect.application.dto.UpdateApplicationStatusRequest;
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

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecruiterApplicationLifecycleControllerTest {

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
        return jwtService.generateToken(new SecurityUser(9999L, "admin.p72@example.com", "HASH", Role.ADMIN, "Admin User", null, null, null, true));
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
        for (String skillName : skills) {
            mockMvc.perform(post("/api/candidate/skills")
                            .header("Authorization", "Bearer " + cand.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new com.careerconnect.candidate.dto.AssignSkillRequest(skillName))))
                    .andExpect(status().isCreated());
        }

        // 4. Resume
        byte[] pdfContent = "%PDF-1.4 mock resume content for application testing".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfContent);
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + cand.token()))
                .andExpect(status().isCreated());
    }

    private Long submitApplication(CandidateAuth cand, Long jobId, String coverLetter) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/jobs/" + jobId + "/applications")
                        .header("Authorization", "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.careerconnect.application.dto.ApplicationSubmissionRequest(coverLetter))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("applicationId").asLong();
    }

    // ─── 1. Authentication & Role Authorization ──────────────────────────────

    @Test
    @DisplayName("P7.2-01: PATCH status without token returns 401 UNAUTHENTICATED")
    void updateStatus_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/recruiter/applications/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "UNDER_REVIEW",
                                  "note": "Reviewing candidate profile"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("UNAUTHENTICATED")));
    }

    @Test
    @DisplayName("P7.2-02: Candidate role accessing recruiter status update returns 403 FORBIDDEN")
    void updateStatus_candidateRole_returns403() throws Exception {
        CandidateAuth cand = registerCandidate("cand_status_role");
        mockMvc.perform(patch("/api/recruiter/applications/1/status")
                        .header("Authorization", "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "UNDER_REVIEW"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("P7.2-03: Admin role accessing recruiter status update returns 403 FORBIDDEN")
    void updateStatus_adminRole_returns403() throws Exception {
        String adminToken = generateAdminToken();
        mockMvc.perform(patch("/api/recruiter/applications/1/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "UNDER_REVIEW"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("P7.2-04: Recruiter without associated company returns 422 COMPANY_REQUIRED")
    void updateStatus_recruiterWithoutCompany_returns422() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec_no_comp_status");
        mockMvc.perform(patch("/api/recruiter/applications/1/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "UNDER_REVIEW"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("COMPANY_REQUIRED")));
    }

    // ─── 2. Company Ownership & Isolation ─────────────────────────────────────

    @Test
    @DisplayName("P7.2-05: Recruiter updating foreign company application returns 404 RESOURCE_NOT_FOUND (zero leakage)")
    void updateStatus_foreignCompanyApplication_returns404() throws Exception {
        Long compA = createCompany("CompA");
        Long compB = createCompany("CompB");

        RecruiterAuth recA = registerRecruiter("rec_a_iso");
        linkRecruiterToCompany(recA.recruiterId(), compA);

        RecruiterAuth recB = registerRecruiter("rec_b_iso");
        linkRecruiterToCompany(recB.recruiterId(), compB);

        Long jobB = createJob(compB, "Job at CompB", JobStatus.ACTIVE);
        addJobSkill(jobB, getOrCreateSkill("Java"));

        CandidateAuth cand = registerCandidate("cand_iso_test");
        setupCandidateReadiness(cand, List.of("Java"));
        Long appBId = submitApplication(cand, jobB, "CompB app");

        // Recruiter A attempts to change status of CompB's application
        mockMvc.perform(patch("/api/recruiter/applications/" + appBId + "/status")
                        .header("Authorization", "Bearer " + recA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "UNDER_REVIEW",
                                  "note": "Malicious foreign status attempt"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("RESOURCE_NOT_FOUND")));

        // Status in DB must remain APPLIED
        String currentStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, appBId);
        assertThat(currentStatus).isEqualTo("APPLIED");

        // Status history must have only 1 row (the original APPLIED)
        Integer histCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_status_history WHERE application_id = ?",
                Integer.class, appBId);
        assertThat(histCount).isEqualTo(1);
    }

    @Test
    @DisplayName("P7.2-06: Nonexistent application returns 404 RESOURCE_NOT_FOUND")
    void updateStatus_nonexistentApplication_returns404() throws Exception {
        Long comp = createCompany("CompNonExistent");
        RecruiterAuth rec = registerRecruiter("rec_nonexistent");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        mockMvc.perform(patch("/api/recruiter/applications/999999/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "UNDER_REVIEW"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("P7.2-07: Application on soft-deleted job cannot be modified and returns 404")
    void updateStatus_deletedJob_returns404() throws Exception {
        Long comp = createCompany("CompDelJob");
        RecruiterAuth rec = registerRecruiter("rec_del_job");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long job = createJob(comp, "Deleted Job", JobStatus.ACTIVE);
        addJobSkill(job, getOrCreateSkill("Java"));

        CandidateAuth cand = registerCandidate("cand_del_job");
        setupCandidateReadiness(cand, List.of("Java"));
        Long appId = submitApplication(cand, job, "App before job deleted");

        // Soft-delete the job directly in DB
        jdbcTemplate.update("UPDATE jobs SET deleted_at = CURRENT_TIMESTAMP(3) WHERE id = ?", job);

        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "UNDER_REVIEW"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("RESOURCE_NOT_FOUND")));
    }

    // ─── 3. Identity Tampering & Stale JWT ─────────────────────────────────────

    @Test
    @DisplayName("P7.2-08: Client-supplied IDs in body are ignored; server authoritative identity controls history")
    void updateStatus_clientSuppliedIds_ignored() throws Exception {
        Long comp = createCompany("CompSpoof");
        RecruiterAuth rec = registerRecruiter("rec_spoof");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long job = createJob(comp, "Engineering Lead", JobStatus.ACTIVE);
        addJobSkill(job, getOrCreateSkill("Java"));

        CandidateAuth cand = registerCandidate("cand_spoof");
        setupCandidateReadiness(cand, List.of("Java"));
        Long appId = submitApplication(cand, job, "Cover letter");

        // Payload contains spoofed recruiterId, companyId, candidateId, userId, jobId
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "UNDER_REVIEW",
                                  "note": "Valid review note",
                                  "recruiterId": 8888,
                                  "companyId": 7777,
                                  "candidateId": 6666,
                                  "userId": 5555,
                                  "jobId": 4444
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("UNDER_REVIEW")));

        // Verify status history row has the real authenticated user ID (rec.userId())
        Long historyActorId = jdbcTemplate.queryForObject(
                "SELECT changed_by_user_id FROM application_status_history WHERE application_id = ? AND to_status = 'UNDER_REVIEW'",
                Long.class, appId);
        assertThat(historyActorId).isEqualTo(rec.userId());
    }

    @Test
    @DisplayName("P7.2-09: Stale JWT - when recruiter company unlinked in DB, returns 422 COMPANY_REQUIRED")
    void updateStatus_staleJwt_companyRemovedInDb_returns422() throws Exception {
        Long comp = createCompany("CompStale");
        RecruiterAuth rec = registerRecruiter("rec_stale");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long job = createJob(comp, "QA Engineer", JobStatus.ACTIVE);
        addJobSkill(job, getOrCreateSkill("Java"));

        CandidateAuth cand = registerCandidate("cand_stale");
        setupCandidateReadiness(cand, List.of("Java"));
        Long appId = submitApplication(cand, job, "Cover letter");

        // DB company association removed
        jdbcTemplate.update("UPDATE recruiters SET company_id = NULL WHERE id = ?", rec.recruiterId());

        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "UNDER_REVIEW"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("COMPANY_REQUIRED")));
    }

    // ─── 4. Exact State Machine Transitions ───────────────────────────────────

    @Test
    @DisplayName("P7.2-10: APPLIED -> UNDER_REVIEW succeeds and records status history")
    void transition_appliedToUnderReview_succeeds() throws Exception {
        Long comp = createCompany("CompTrans1");
        RecruiterAuth rec = registerRecruiter("rec_trans1");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long job = createJob(comp, "Junior Dev", JobStatus.ACTIVE);
        addJobSkill(job, getOrCreateSkill("Java"));

        CandidateAuth cand = registerCandidate("cand_trans1");
        setupCandidateReadiness(cand, List.of("Java"));
        Long appId = submitApplication(cand, job, "Dev cover");

        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "UNDER_REVIEW",
                                  "note": "Profile looks promising"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("UNDER_REVIEW")))
                .andExpect(jsonPath("$.data.statusHistory", hasSize(2)))
                .andExpect(jsonPath("$.data.statusHistory[1].fromStatus", is("APPLIED")))
                .andExpect(jsonPath("$.data.statusHistory[1].toStatus", is("UNDER_REVIEW")))
                .andExpect(jsonPath("$.data.statusHistory[1].note", is("Profile looks promising")))
                .andExpect(jsonPath("$.data.statusHistory[1].changedByUserId", is(rec.userId().intValue())));
    }

    @Test
    @DisplayName("P7.2-11: APPLIED -> SHORTLISTED direct transition succeeds per CC-MASTER-001")
    void transition_appliedToShortlisted_succeeds() throws Exception {
        Long comp = createCompany("CompTrans2");
        RecruiterAuth rec = registerRecruiter("rec_trans2");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long job = createJob(comp, "Mid Dev", JobStatus.ACTIVE);
        addJobSkill(job, getOrCreateSkill("Java"));

        CandidateAuth cand = registerCandidate("cand_trans2");
        setupCandidateReadiness(cand, List.of("Java"));
        Long appId = submitApplication(cand, job, "Mid cover");

        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "SHORTLISTED",
                                  "note": "Fast-tracked candidate"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SHORTLISTED")))
                .andExpect(jsonPath("$.data.statusHistory[1].fromStatus", is("APPLIED")))
                .andExpect(jsonPath("$.data.statusHistory[1].toStatus", is("SHORTLISTED")));
    }

    @Test
    @DisplayName("P7.2-12: APPLIED -> REJECTED direct transition succeeds per CC-MASTER-001")
    void transition_appliedToRejected_succeeds() throws Exception {
        Long comp = createCompany("CompTrans3");
        RecruiterAuth rec = registerRecruiter("rec_trans3");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long job = createJob(comp, "Senior Dev", JobStatus.ACTIVE);
        addJobSkill(job, getOrCreateSkill("Java"));

        CandidateAuth cand = registerCandidate("cand_trans3");
        setupCandidateReadiness(cand, List.of("Java"));
        Long appId = submitApplication(cand, job, "Cover");

        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "REJECTED",
                                  "note": "Does not meet seniority requirement"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("REJECTED")));
    }

    @Test
    @DisplayName("P7.2-13: Full progression: APPLIED -> UNDER_REVIEW -> SHORTLISTED -> INTERVIEW -> SELECTED")
    void fullLifecycleProgression_succeeds() throws Exception {
        Long comp = createCompany("CompProg");
        RecruiterAuth rec = registerRecruiter("rec_prog");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long job = createJob(comp, "Staff Engineer", JobStatus.ACTIVE);
        addJobSkill(job, getOrCreateSkill("Java"));

        CandidateAuth cand = registerCandidate("cand_prog");
        setupCandidateReadiness(cand, List.of("Java"));
        Long appId = submitApplication(cand, job, "Staff cover");

        // 1. APPLIED -> UNDER_REVIEW
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"UNDER_REVIEW\", \"note\": \"Reviewing resume\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("UNDER_REVIEW")));

        // 2. UNDER_REVIEW -> SHORTLISTED
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SHORTLISTED\", \"note\": \"Shortlisted for technical round\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SHORTLISTED")));

        // 3. SHORTLISTED -> INTERVIEW
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"INTERVIEW\", \"note\": \"Interview scheduled on Monday\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("INTERVIEW")));

        // 4. INTERVIEW -> SELECTED
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SELECTED\", \"note\": \"Offer extended and accepted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SELECTED")))
                .andExpect(jsonPath("$.data.statusHistory", hasSize(5))); // Initial APPLIED + 4 transitions
    }

    @Test
    @DisplayName("P7.2-14: UNDER_REVIEW -> REJECTED and SHORTLISTED -> REJECTED and INTERVIEW -> REJECTED")
    void rejectionFromVariousStates_succeeds() throws Exception {
        Long comp = createCompany("CompRejections");
        RecruiterAuth rec = registerRecruiter("rec_rejections");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long job = createJob(comp, "DevOps Engineer", JobStatus.ACTIVE);
        addJobSkill(job, getOrCreateSkill("Java"));

        // Case A: UNDER_REVIEW -> REJECTED
        CandidateAuth candA = registerCandidate("cand_rej_a");
        setupCandidateReadiness(candA, List.of("Java"));
        Long appA = submitApplication(candA, job, "Cover A");
        mockMvc.perform(patch("/api/recruiter/applications/" + appA + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"UNDER_REVIEW\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/recruiter/applications/" + appA + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"REJECTED\", \"note\": \"Skills mismatch after review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("REJECTED")));

        // Case B: SHORTLISTED -> REJECTED
        CandidateAuth candB = registerCandidate("cand_rej_b");
        setupCandidateReadiness(candB, List.of("Java"));
        Long appB = submitApplication(candB, job, "Cover B");
        mockMvc.perform(patch("/api/recruiter/applications/" + appB + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SHORTLISTED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/recruiter/applications/" + appB + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"REJECTED\", \"note\": \"Position cancelled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("REJECTED")));

        // Case C: INTERVIEW -> REJECTED
        CandidateAuth candC = registerCandidate("cand_rej_c");
        setupCandidateReadiness(candC, List.of("Java"));
        Long appC = submitApplication(candC, job, "Cover C");
        mockMvc.perform(patch("/api/recruiter/applications/" + appC + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SHORTLISTED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/recruiter/applications/" + appC + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"INTERVIEW\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/recruiter/applications/" + appC + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"REJECTED\", \"note\": \"Interview did not meet bar\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("REJECTED")));
    }

    // ─── 5. Prohibited Invalid Transitions & Terminal State Enforcements ──────

    @Test
    @DisplayName("P7.2-15: Prohibited direct skips (APPLIED -> SELECTED, APPLIED -> INTERVIEW, UNDER_REVIEW -> SELECTED) return 422")
    void prohibitedSkips_return422() throws Exception {
        Long comp = createCompany("CompSkips");
        RecruiterAuth rec = registerRecruiter("rec_skips");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long job = createJob(comp, "Skips Job", JobStatus.ACTIVE);
        addJobSkill(job, getOrCreateSkill("Java"));

        CandidateAuth cand = registerCandidate("cand_skips");
        setupCandidateReadiness(cand, List.of("Java"));
        Long appId = submitApplication(cand, job, "Cover");

        // 1. APPLIED -> SELECTED is forbidden
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SELECTED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATUS_TRANSITION")));

        // 2. APPLIED -> INTERVIEW is forbidden
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"INTERVIEW\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATUS_TRANSITION")));

        // Move to UNDER_REVIEW
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"UNDER_REVIEW\"}"))
                .andExpect(status().isOk());

        // 3. UNDER_REVIEW -> SELECTED is forbidden
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SELECTED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATUS_TRANSITION")));
    }

    @Test
    @DisplayName("P7.2-16: Same-state transitions return 422 INVALID_STATUS_TRANSITION")
    void sameStateTransitions_return422() throws Exception {
        Long comp = createCompany("CompSameState");
        RecruiterAuth rec = registerRecruiter("rec_same_state");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long job = createJob(comp, "SameState Job", JobStatus.ACTIVE);
        addJobSkill(job, getOrCreateSkill("Java"));

        CandidateAuth cand = registerCandidate("cand_same_state");
        setupCandidateReadiness(cand, List.of("Java"));
        Long appId = submitApplication(cand, job, "Cover");

        // APPLIED -> APPLIED
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"APPLIED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATUS_TRANSITION")));
    }

    @Test
    @DisplayName("P7.2-17: Backward transitions return 422 INVALID_STATUS_TRANSITION")
    void backwardTransitions_return422() throws Exception {
        Long comp = createCompany("CompBackward");
        RecruiterAuth rec = registerRecruiter("rec_backward");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long job = createJob(comp, "Backward Job", JobStatus.ACTIVE);
        addJobSkill(job, getOrCreateSkill("Java"));

        CandidateAuth cand = registerCandidate("cand_backward");
        setupCandidateReadiness(cand, List.of("Java"));
        Long appId = submitApplication(cand, job, "Cover");

        // Move to SHORTLISTED
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SHORTLISTED\"}"))
                .andExpect(status().isOk());

        // SHORTLISTED -> APPLIED
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"APPLIED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATUS_TRANSITION")));

        // SHORTLISTED -> UNDER_REVIEW
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"UNDER_REVIEW\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATUS_TRANSITION")));
    }

    @Test
    @DisplayName("P7.2-18: Terminal states (SELECTED, REJECTED, WITHDRAWN) cannot be transitioned out of")
    void terminalStates_cannotTransition() throws Exception {
        Long comp = createCompany("CompTerminal");
        RecruiterAuth rec = registerRecruiter("rec_terminal");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long job = createJob(comp, "Terminal Job", JobStatus.ACTIVE);
        addJobSkill(job, getOrCreateSkill("Java"));

        // Test REJECTED is terminal
        CandidateAuth candRej = registerCandidate("cand_term_rej");
        setupCandidateReadiness(candRej, List.of("Java"));
        Long appRej = submitApplication(candRej, job, "Cover");
        mockMvc.perform(patch("/api/recruiter/applications/" + appRej + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"REJECTED\"}"))
                .andExpect(status().isOk());

        // Attempt reopening / transitioning REJECTED
        mockMvc.perform(patch("/api/recruiter/applications/" + appRej + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"UNDER_REVIEW\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATUS_TRANSITION")));

        // Test WITHDRAWN is terminal (candidate withdraws)
        CandidateAuth candWth = registerCandidate("cand_term_wth");
        setupCandidateReadiness(candWth, List.of("Java"));
        Long appWth = submitApplication(candWth, job, "Cover");
        mockMvc.perform(patch("/api/candidate/applications/" + appWth + "/withdraw")
                        .header("Authorization", "Bearer " + candWth.token()))
                .andExpect(status().isOk());

        // Recruiter attempts to modify withdrawn application
        mockMvc.perform(patch("/api/recruiter/applications/" + appWth + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"UNDER_REVIEW\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATUS_TRANSITION")));
    }

    @Test
    @DisplayName("P7.2-19: Recruiter cannot set status to WITHDRAWN (candidate withdrawal only)")
    void recruiterCannotWithdrawApplication() throws Exception {
        Long comp = createCompany("CompNoWithdraw");
        RecruiterAuth rec = registerRecruiter("rec_no_withdraw");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long job = createJob(comp, "NoWithdraw Job", JobStatus.ACTIVE);
        addJobSkill(job, getOrCreateSkill("Java"));

        CandidateAuth cand = registerCandidate("cand_no_withdraw");
        setupCandidateReadiness(cand, List.of("Java"));
        Long appId = submitApplication(cand, job, "Cover");

        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"WITHDRAWN\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATUS_TRANSITION")));
    }

    // ─── 6. Note Validation & Integrity ───────────────────────────────────────

    @Test
    @DisplayName("P7.2-20: Note exceeding 500 characters is rejected with 400 VALIDATION_FAILED")
    void overlongNote_rejectedWith400() throws Exception {
        Long comp = createCompany("CompOverlong");
        RecruiterAuth rec = registerRecruiter("rec_overlong");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long job = createJob(comp, "Overlong Job", JobStatus.ACTIVE);
        addJobSkill(job, getOrCreateSkill("Java"));

        CandidateAuth cand = registerCandidate("cand_overlong");
        setupCandidateReadiness(cand, List.of("Java"));
        Long appId = submitApplication(cand, job, "Cover");

        String overlongNote = "A".repeat(501);

        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest(ApplicationStatus.UNDER_REVIEW, overlongNote))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("P7.2-21: Null status in body returns 400 VALIDATION_FAILED")
    void nullStatus_rejectedWith400() throws Exception {
        Long comp = createCompany("CompNullStatus");
        RecruiterAuth rec = registerRecruiter("rec_null_status");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long job = createJob(comp, "NullStatus Job", JobStatus.ACTIVE);
        addJobSkill(job, getOrCreateSkill("Java"));

        CandidateAuth cand = registerCandidate("cand_null_status");
        setupCandidateReadiness(cand, List.of("Java"));
        Long appId = submitApplication(cand, job, "Cover");

        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": null, \"note\": \"Missing status\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("P7.2-22: Exactly 500-character note is accepted and accurately saved in status history")
    void boundaryNote_500Chars_accepted() throws Exception {
        Long comp = createCompany("CompBoundaryNote");
        RecruiterAuth rec = registerRecruiter("rec_boundary_note");
        linkRecruiterToCompany(rec.recruiterId(), comp);

        Long job = createJob(comp, "Boundary Note Job", JobStatus.ACTIVE);
        addJobSkill(job, getOrCreateSkill("Java"));

        CandidateAuth cand = registerCandidate("cand_boundary_note");
        setupCandidateReadiness(cand, List.of("Java"));
        Long appId = submitApplication(cand, job, "Cover");

        String exact500Note = "N".repeat(500);

        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest(ApplicationStatus.UNDER_REVIEW, exact500Note))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("UNDER_REVIEW")))
                .andExpect(jsonPath("$.data.statusHistory[1].note", is(exact500Note)));
    }
}
