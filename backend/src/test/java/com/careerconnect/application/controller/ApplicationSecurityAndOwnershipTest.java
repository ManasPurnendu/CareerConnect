package com.careerconnect.application.controller;

import com.careerconnect.application.domain.ApplicationStatus;
import com.careerconnect.application.dto.CandidateSnapshot;
import com.careerconnect.application.repository.ApplicationRepository;
import com.careerconnect.application.service.ApplicationService;
import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.AssignSkillRequest;
import com.careerconnect.candidate.dto.CreateEducationRequest;
import com.careerconnect.candidate.dto.UpdateProfileRequest;
import com.careerconnect.common.model.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
class ApplicationSecurityAndOwnershipTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationService applicationService;

    @Value("${app.resume.upload-dir:./target/test-uploads/resumes}")
    private String uploadDir;

    @AfterEach
    void cleanUploads() {
        try {
            Path dir = Paths.get(uploadDir);
            if (Files.exists(dir)) {
                try (Stream<Path> s = Files.walk(dir)) {
                    s.filter(Files::isRegularFile).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {}
                    });
                }
            }
        } catch (Exception ignored) {}
    }

    private record UserAuth(String token, Long candidateId, Long userId) {}

    private UserAuth registerCandidate(String email, String name) throws Exception {
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.CANDIDATE, name);
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        String token = root.path("data").path("accessToken").asText();
        Long candidateId = root.path("data").path("user").path("candidateId").asLong();
        Long userId = root.path("data").path("user").path("id").asLong();
        return new UserAuth(token, candidateId, userId);
    }

    private String registerRecruiter(String email) throws Exception {
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.RECRUITER, "Recruiter Sec");
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }

    private void addSkill(String token, String skillName) throws Exception {
        AssignSkillRequest skill = new AssignSkillRequest(skillName);
        mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(skill)))
                .andExpect(status().isCreated());
    }

    private void addEducation(String token) throws Exception {
        CreateEducationRequest edu = new CreateEducationRequest(
                "Sec University", "B.Tech", "Computer Science", 2019, 2023, null, 1);
        mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edu)))
                .andExpect(status().isCreated());
    }

    private void uploadResume(String token, String filename) throws Exception {
        byte[] pdfBytes = "%PDF-1.4 security test resume".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/pdf", pdfBytes);
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private void updateProfile(String token, String fullName) throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest(fullName, "Backend Engineer", "Bengaluru", "Security summary");
        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    private UserAuth buildCompleteCandidate(String email, String name) throws Exception {
        UserAuth user = registerCandidate(email, name);
        updateProfile(user.token(), name);
        addEducation(user.token());
        addSkill(user.token(), "Java");
        uploadResume(user.token(), "sec_resume.pdf");
        return user;
    }

    private Long createActiveJob(String suffix) {
        String companyName = "SecCo " + suffix;
        jdbcTemplate.update("""
                INSERT INTO companies (name, industry, location, is_active) VALUES (?, ?, ?, ?)
                """, companyName, "Technology", "Bengaluru", true);
        Long companyId = jdbcTemplate.queryForObject("SELECT id FROM companies WHERE name = ?", Long.class, companyName);

        String deadline = LocalDate.now().plusDays(30).toString();
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, job_type, experience_level,
                                  work_mode, application_deadline, status)
                VALUES (?, ?, ?, 'FULL_TIME', 'FRESHER', 'HYBRID', ?, 'ACTIVE')
                """, companyId, "Job " + suffix, "Description for job " + suffix, deadline);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM jobs WHERE title = ?", Long.class, "Job " + suffix);
    }

    private Long applyToJob(String candidateToken, Long jobId, String coverLetter) throws Exception {
        String payload = String.format("{\"coverLetter\": \"%s\"}", coverLetter != null ? coverLetter : "Applying");
        MvcResult res = mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("applicationId").asLong();
    }

    // =========================================================================
    // 1. Authentication Boundary (Missing, Invalid, Malformed Tokens)
    // =========================================================================

    @Test
    @DisplayName("P5.7-1: Missing token on candidate application list -> 401 UNAUTHENTICATED")
    void missingToken_applicationList_returns401() throws Exception {
        mockMvc.perform(get("/api/candidate/applications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("P5.7-2: Missing token on application detail -> 401 UNAUTHENTICATED")
    void missingToken_applicationDetail_returns401() throws Exception {
        mockMvc.perform(get("/api/candidate/applications/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("P5.7-3: Missing token on application withdrawal -> 401 UNAUTHENTICATED")
    void missingToken_withdrawal_returns401() throws Exception {
        mockMvc.perform(patch("/api/candidate/applications/1/withdraw"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("P5.7-4: Missing token on job match preview -> 401 UNAUTHENTICATED")
    void missingToken_jobMatch_returns401() throws Exception {
        mockMvc.perform(get("/api/jobs/1/match"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("P5.7-5: Missing token on application submission -> 401 UNAUTHENTICATED")
    void missingToken_applicationSubmission_returns401() throws Exception {
        mockMvc.perform(post("/api/jobs/1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverLetter\": \"hello\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("P5.7-6: Invalid/malformed JWT token -> 401 UNAUTHENTICATED")
    void invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/candidate/applications")
                        .header("Authorization", "Bearer invalid.jwt.token.here"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/api/candidate/applications/1")
                        .header("Authorization", "Bearer not_even_a_jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    // =========================================================================
    // 2. Role Isolation (Recruiter Access Denied to Candidate Endpoints)
    // =========================================================================

    @Test
    @DisplayName("P5.7-7: Recruiter cannot access candidate application list -> 403 FORBIDDEN")
    void recruiter_applicationList_returns403() throws Exception {
        String recToken = registerRecruiter("p57.rec.list@example.com");

        mockMvc.perform(get("/api/candidate/applications")
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("P5.7-8: Recruiter cannot access candidate application detail -> 403 FORBIDDEN")
    void recruiter_applicationDetail_returns403() throws Exception {
        String recToken = registerRecruiter("p57.rec.detail@example.com");

        mockMvc.perform(get("/api/candidate/applications/1")
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("P5.7-9: Recruiter cannot withdraw candidate application -> 403 FORBIDDEN")
    void recruiter_withdrawal_returns403() throws Exception {
        String recToken = registerRecruiter("p57.rec.withdraw@example.com");

        mockMvc.perform(patch("/api/candidate/applications/1/withdraw")
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("P5.7-10: Recruiter cannot access candidate match preview -> 403 FORBIDDEN")
    void recruiter_match_returns403() throws Exception {
        String recToken = registerRecruiter("p57.rec.match@example.com");

        mockMvc.perform(get("/api/jobs/1/match")
                        .header("Authorization", "Bearer " + recToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("P5.7-11: Recruiter cannot submit candidate application -> 403 FORBIDDEN")
    void recruiter_applicationSubmission_returns403() throws Exception {
        String recToken = registerRecruiter("p57.rec.apply@example.com");

        mockMvc.perform(post("/api/jobs/1/applications")
                        .header("Authorization", "Bearer " + recToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverLetter\": \"hello\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    // =========================================================================
    // 3. Cross-Candidate Ownership Isolation & Resource Enumeration Resistance
    // =========================================================================

    @Test
    @DisplayName("P5.7-12: Candidate A cannot view Candidate B application detail -> 404 RESOURCE_NOT_FOUND")
    void crossCandidate_viewDetail_returns404() throws Exception {
        UserAuth candA = buildCompleteCandidate("p57.crossA@example.com", "Alice Cross");
        UserAuth candB = buildCompleteCandidate("p57.crossB@example.com", "Bob Cross");
        Long job = createActiveJob("CROSS1");

        Long appB = applyToJob(candB.token(), job, "Bob Cover Letter");

        mockMvc.perform(get("/api/candidate/applications/{id}", appB)
                        .header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P5.7-13: Candidate A cannot withdraw Candidate B application -> 404 RESOURCE_NOT_FOUND; B unaffected")
    void crossCandidate_withdrawal_returns404_B_unaffected() throws Exception {
        UserAuth candA = buildCompleteCandidate("p57.wdA@example.com", "Alice Withdraw");
        UserAuth candB = buildCompleteCandidate("p57.wdB@example.com", "Bob Withdraw");
        Long job = createActiveJob("CROSS2");

        Long appB = applyToJob(candB.token(), job, "Bob Application");

        // Alice tries to withdraw Bob's application
        mockMvc.perform(patch("/api/candidate/applications/{id}/withdraw", appB)
                        .header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Bob's application status remains APPLIED
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, appB);
        assertThat(status).isEqualTo("APPLIED");

        // History count remains 1 (initial APPLIED event)
        List<ApplicationRepository.ApplicationStatusHistoryEntity> history =
                applicationRepository.findStatusHistoryByApplicationId(appB);
        assertThat(history).hasSize(1);
    }

    @Test
    @DisplayName("P5.7-14: Candidate A cannot access Candidate B snapshot data through any application endpoint")
    void crossCandidate_snapshotIsolation() throws Exception {
        UserAuth candA = buildCompleteCandidate("p57.snapA@example.com", "Alice Snapshot");
        UserAuth candB = buildCompleteCandidate("p57.snapB@example.com", "Bob Confidential Profile");
        Long job = createActiveJob("CROSS3");

        Long appB = applyToJob(candB.token(), job, "Bob Secret Cover");

        // Detail attempt by Alice
        MvcResult res = mockMvc.perform(get("/api/candidate/applications/{id}", appB)
                        .header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isNotFound())
                .andReturn();

        // Response must NOT contain Bob's confidential name, email, or cover letter
        String responseBody = res.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("Bob Confidential Profile");
        assertThat(responseBody).doesNotContain("p57.snapB@example.com");
        assertThat(responseBody).doesNotContain("Bob Secret Cover");
    }

    @Test
    @DisplayName("P5.7-15: Candidate A list pagination never returns Candidate B applications")
    void crossCandidate_listPaginationIsolation() throws Exception {
        UserAuth candA = buildCompleteCandidate("p57.pagA@example.com", "Alice Pag");
        UserAuth candB = buildCompleteCandidate("p57.pagB@example.com", "Bob Pag");

        Long job1 = createActiveJob("PAG1");
        Long job2 = createActiveJob("PAG2");

        applyToJob(candA.token(), job1, "Alice App 1");
        applyToJob(candB.token(), job2, "Bob App 1");

        // Alice lists her applications
        mockMvc.perform(get("/api/candidate/applications?page=0&size=10")
                        .header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].candidateId").value(candA.candidateId()))
                .andExpect(jsonPath("$.data[0].jobId").value(job1))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));

        // Bob lists his applications
        mockMvc.perform(get("/api/candidate/applications?page=0&size=10")
                        .header("Authorization", "Bearer " + candB.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].candidateId").value(candB.candidateId()))
                .andExpect(jsonPath("$.data[0].jobId").value(job2))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
    }

    // =========================================================================
    // 4. Candidate ID Spoofing Resistance
    // =========================================================================

    @Test
    @DisplayName("P5.7-16: candidateId query param spoofing ignored on application list")
    void spoofing_applicationList_ignored() throws Exception {
        UserAuth candA = buildCompleteCandidate("p57.sp.listA@example.com", "Alice Sp List");
        UserAuth candB = buildCompleteCandidate("p57.sp.listB@example.com", "Bob Sp List");
        Long job = createActiveJob("SPLIST1");
        applyToJob(candB.token(), job, "Bob Application");

        // Alice attempts to spoof identity via ?candidateId=<Bob's ID>
        mockMvc.perform(get("/api/candidate/applications?candidateId=" + candB.candidateId())
                        .header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.pagination.totalElements").value(0));
    }

    @Test
    @DisplayName("P5.7-17: candidateId query param spoofing ignored on application detail")
    void spoofing_applicationDetail_ignored() throws Exception {
        UserAuth candA = buildCompleteCandidate("p57.sp.detA@example.com", "Alice Sp Det");
        UserAuth candB = buildCompleteCandidate("p57.sp.detB@example.com", "Bob Sp Det");
        Long job = createActiveJob("SPDET1");
        Long appB = applyToJob(candB.token(), job, "Bob Application");

        mockMvc.perform(get("/api/candidate/applications/{id}?candidateId=" + candB.candidateId(), appB)
                        .header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P5.7-18: candidateId query param spoofing ignored on withdrawal")
    void spoofing_withdrawal_ignored() throws Exception {
        UserAuth candA = buildCompleteCandidate("p57.sp.wdA@example.com", "Alice Sp Wd");
        UserAuth candB = buildCompleteCandidate("p57.sp.wdB@example.com", "Bob Sp Wd");
        Long job = createActiveJob("SPWD1");
        Long appB = applyToJob(candB.token(), job, "Bob Application");

        mockMvc.perform(patch("/api/candidate/applications/{id}/withdraw?candidateId=" + candB.candidateId(), appB)
                        .header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P5.7-19: candidateId query param spoofing ignored on match preview")
    void spoofing_match_ignored() throws Exception {
        UserAuth candA = buildCompleteCandidate("p57.sp.mtA@example.com", "Alice Sp Mt");
        UserAuth candB = buildCompleteCandidate("p57.sp.mtB@example.com", "Bob Sp Mt");
        Long job = createActiveJob("SPMT1");

        // Alice has Java, Bob has Java
        mockMvc.perform(get("/api/jobs/{id}/match?candidateId=" + candB.candidateId(), job)
                        .header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("P5.7-20: Injected candidateId in submission JSON body cannot change ownership")
    void spoofing_submissionBody_ignored() throws Exception {
        UserAuth candA = buildCompleteCandidate("p57.sp.subA@example.com", "Alice Sp Sub");
        UserAuth candB = buildCompleteCandidate("p57.sp.subB@example.com", "Bob Sp Sub");
        Long job = createActiveJob("SPSUB1");

        // Alice injects Bob's candidateId in JSON body
        String maliciousPayload = String.format("""
                {
                    "candidateId": %d,
                    "coverLetter": "Spoofed attempt"
                }
                """, candB.candidateId());

        MvcResult res = mockMvc.perform(post("/api/jobs/{id}/applications", job)
                        .header("Authorization", "Bearer " + candA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousPayload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        Long createdAppId = root.path("data").path("applicationId").asLong();
        Long assignedCandidateId = root.path("data").path("candidateId").asLong();

        // Must belong to Alice, NOT Bob
        assertThat(assignedCandidateId).isEqualTo(candA.candidateId());

        Long dbCandidateId = jdbcTemplate.queryForObject(
                "SELECT candidate_id FROM applications WHERE id = ?", Long.class, createdAppId);
        assertThat(dbCandidateId).isEqualTo(candA.candidateId());
    }

    // =========================================================================
    // 5. Database Query Scoping Verification
    // =========================================================================

    @Test
    @DisplayName("P5.7-21: Repository queries strictly enforce candidateId in WHERE clauses")
    void databaseQueries_enforceCandidateIdScoping() throws Exception {
        UserAuth cand = buildCompleteCandidate("p57.dbscope@example.com", "DB Scope Candidate");
        Long job = createActiveJob("DBSCOPE1");
        Long appId = applyToJob(cand.token(), job, "DB Scope Cover");

        // findByCandidateId with candidate's own ID returns the application
        var listOwn = applicationRepository.findByCandidateId(cand.candidateId(), 10, 0);
        assertThat(listOwn).hasSize(1);
        assertThat(listOwn.get(0).id()).isEqualTo(appId);

        // findByCandidateId with arbitrary/foreign ID returns empty list
        var listForeign = applicationRepository.findByCandidateId(999999L, 10, 0);
        assertThat(listForeign).isEmpty();

        // findDetailByIdAndCandidateId with correct pair returns entity
        var detailOwn = applicationRepository.findDetailByIdAndCandidateId(appId, cand.candidateId());
        assertThat(detailOwn).isPresent();

        // findDetailByIdAndCandidateId with wrong candidateId returns empty Optional
        var detailWrongCand = applicationRepository.findDetailByIdAndCandidateId(appId, 999999L);
        assertThat(detailWrongCand).isEmpty();

        // updateStatusForCandidate with wrong candidateId updates 0 rows
        int rows = applicationRepository.updateStatusForCandidate(appId, 999999L, ApplicationStatus.WITHDRAWN, null);
        assertThat(rows).isEqualTo(0);
    }

    // =========================================================================
    // 6. Regression Safety (Submission, Detail, Withdrawal, Matching Still Work)
    // =========================================================================

    @Test
    @DisplayName("P5.7-22: Full lifecycle for authorized candidate functions correctly")
    void fullAuthorizedLifecycle_regressionSafety() throws Exception {
        UserAuth cand = buildCompleteCandidate("p57.regress@example.com", "Regression Candidate");
        Long job = createActiveJob("REGRESS1");

        // 1. Match preview
        mockMvc.perform(get("/api/jobs/{id}/match", job)
                        .header("Authorization", "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 2. Submission
        Long appId = applyToJob(cand.token(), job, "Regression Application");
        assertThat(appId).isNotNull();

        // 3. Detail
        mockMvc.perform(get("/api/candidate/applications/{id}", appId)
                        .header("Authorization", "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"));

        // 4. Withdrawal
        mockMvc.perform(patch("/api/candidate/applications/{id}/withdraw", appId)
                        .header("Authorization", "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));

        // 5. Updated detail reflects WITHDRAWN
        mockMvc.perform(get("/api/candidate/applications/{id}", appId)
                        .header("Authorization", "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
    }
}
