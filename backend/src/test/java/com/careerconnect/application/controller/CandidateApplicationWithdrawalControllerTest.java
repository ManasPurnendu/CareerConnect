package com.careerconnect.application.controller;

import com.careerconnect.application.domain.ApplicationStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CandidateApplicationWithdrawalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Value("${app.resume.upload-dir:./target/test-uploads/resumes}")
    private String uploadDir;

    // ─── Helpers ─────────────────────────────────────────────────────────────

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
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.RECRUITER, "Test Recruiter");
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }

    private void addEducation(String token) throws Exception {
        CreateEducationRequest edu = new CreateEducationRequest(
                "State University", "B.Tech", "Computer Science", 2019, 2023, null, 1);
        mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edu)))
                .andExpect(status().isCreated());
    }

    private void addSkill(String token) throws Exception {
        AssignSkillRequest skill = new AssignSkillRequest("Java");
        mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(skill)))
                .andExpect(status().isCreated());
    }

    private void uploadResume(String token) throws Exception {
        byte[] pdfBytes = "%PDF-1.4 minimal test resume".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private void updateProfile(String token, String fullName) throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest(fullName, "Software Developer", "Bengaluru", null);
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
        addSkill(user.token());
        uploadResume(user.token());
        return user;
    }

    private Long createActiveJob(String suffix) {
        String companyName = "Co " + suffix;
        jdbcTemplate.update("""
                INSERT INTO companies (name, industry, location, is_active) VALUES (?, ?, ?, ?)
                """, companyName, "Technology", "Bengaluru", true);
        Long companyId = jdbcTemplate.queryForObject("SELECT id FROM companies WHERE name = ?", Long.class, companyName);

        String deadline = LocalDate.now().plusDays(30).toString();
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, job_type, experience_level,
                                  work_mode, application_deadline, status)
                VALUES (?, ?, ?, 'INTERNSHIP', 'FRESHER', 'ONSITE', ?, 'ACTIVE')
                """, companyId, "Job " + suffix, "Description " + suffix, deadline);

        return jdbcTemplate.queryForObject("SELECT id FROM jobs WHERE title = ?", Long.class, "Job " + suffix);
    }

    private Long applyToJob(String candidateToken, Long jobId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverLetter\": \"Excited to apply!\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("applicationId").asLong();
    }

    private void setApplicationStatus(Long applicationId, ApplicationStatus status) {
        jdbcTemplate.update("UPDATE applications SET status = ? WHERE id = ?", status.name(), applicationId);
    }

    @AfterEach
    void tearDown() throws IOException {
        jdbcTemplate.execute("DELETE FROM application_status_history");
        jdbcTemplate.execute("DELETE FROM applications");
        jdbcTemplate.execute("DELETE FROM resumes");
        jdbcTemplate.execute("DELETE FROM candidate_skills");
        jdbcTemplate.execute("DELETE FROM education");
        jdbcTemplate.execute("DELETE FROM job_skills");
        jdbcTemplate.execute("DELETE FROM jobs");
        jdbcTemplate.execute("DELETE FROM companies");

        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (Files.exists(dir)) {
            try (Stream<Path> stream = Files.walk(dir)) {
                stream.filter(Files::isRegularFile).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    // ─── D. Candidate Withdrawal from Withdrawable States ──────────────────────

    @Test
    @DisplayName("P5.3-D1: Candidate withdraws from APPLIED -> returns 200 with WITHDRAWN status")
    void withdrawFromApplied_success() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p53.wapplied@example.com", "Withdraw Applied");
        Long jobId = createActiveJob("wapplied1");
        Long appId = applyToJob(candidate.token(), jobId);

        mockMvc.perform(patch("/api/candidate/applications/{id}/withdraw", appId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationId").value(appId))
                .andExpect(jsonPath("$.data.jobId").value(jobId))
                .andExpect(jsonPath("$.data.candidateId").value(candidate.candidateId()))
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.data.coverLetter").value("Excited to apply!"));

        // Verify status in DB
        String dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, appId);
        assertThat(dbStatus).isEqualTo("WITHDRAWN");

        // Verify history rows
        List<ApplicationRepository.ApplicationStatusHistoryEntity> history =
                applicationRepository.findStatusHistoryByApplicationId(appId);
        assertThat(history).hasSize(2);
        // Event 1: Initial submission
        assertThat(history.get(0).fromStatus()).isNull();
        assertThat(history.get(0).toStatus()).isEqualTo(ApplicationStatus.APPLIED);
        // Event 2: Withdrawal
        assertThat(history.get(1).fromStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(history.get(1).toStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
        assertThat(history.get(1).changedByUserId()).isEqualTo(candidate.userId());
        assertThat(history.get(1).note()).isNull();
    }

    @Test
    @DisplayName("P5.3-D2: Candidate withdraws from UNDER_REVIEW -> returns 200 with WITHDRAWN status")
    void withdrawFromUnderReview_success() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p53.wreview@example.com", "Withdraw Review");
        Long jobId = createActiveJob("wreview1");
        Long appId = applyToJob(candidate.token(), jobId);

        setApplicationStatus(appId, ApplicationStatus.UNDER_REVIEW);

        mockMvc.perform(patch("/api/candidate/applications/{id}/withdraw", appId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));

        String dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, appId);
        assertThat(dbStatus).isEqualTo("WITHDRAWN");

        List<ApplicationRepository.ApplicationStatusHistoryEntity> history =
                applicationRepository.findStatusHistoryByApplicationId(appId);
        assertThat(history).hasSize(2);
        assertThat(history.get(1).fromStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        assertThat(history.get(1).toStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
        assertThat(history.get(1).changedByUserId()).isEqualTo(candidate.userId());
    }

    @Test
    @DisplayName("P5.3-D3: Candidate withdraws from SHORTLISTED -> returns 200 with WITHDRAWN status")
    void withdrawFromShortlisted_success() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p53.wshort@example.com", "Withdraw Short");
        Long jobId = createActiveJob("wshort1");
        Long appId = applyToJob(candidate.token(), jobId);

        setApplicationStatus(appId, ApplicationStatus.SHORTLISTED);

        mockMvc.perform(patch("/api/candidate/applications/{id}/withdraw", appId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));

        String dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, appId);
        assertThat(dbStatus).isEqualTo("WITHDRAWN");

        List<ApplicationRepository.ApplicationStatusHistoryEntity> history =
                applicationRepository.findStatusHistoryByApplicationId(appId);
        assertThat(history).hasSize(2);
        assertThat(history.get(1).fromStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
        assertThat(history.get(1).toStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("P5.3-D4: Candidate withdraws from INTERVIEW -> returns 200 with WITHDRAWN status")
    void withdrawFromInterview_success() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p53.winterview@example.com", "Withdraw Interview");
        Long jobId = createActiveJob("winterview1");
        Long appId = applyToJob(candidate.token(), jobId);

        setApplicationStatus(appId, ApplicationStatus.INTERVIEW);

        mockMvc.perform(patch("/api/candidate/applications/{id}/withdraw", appId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));

        String dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, appId);
        assertThat(dbStatus).isEqualTo("WITHDRAWN");

        List<ApplicationRepository.ApplicationStatusHistoryEntity> history =
                applicationRepository.findStatusHistoryByApplicationId(appId);
        assertThat(history).hasSize(2);
        assertThat(history.get(1).fromStatus()).isEqualTo(ApplicationStatus.INTERVIEW);
        assertThat(history.get(1).toStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
    }

    // ─── E. Withdrawal Rejection from Terminal States ─────────────────────────

    @Test
    @DisplayName("P5.3-E1: Candidate attempts withdrawal from SELECTED -> returns 422 INVALID_STATUS_TRANSITION")
    void withdrawFromSelected_rejected() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p53.wsel@example.com", "Withdraw Selected");
        Long jobId = createActiveJob("wsel1");
        Long appId = applyToJob(candidate.token(), jobId);

        setApplicationStatus(appId, ApplicationStatus.SELECTED);

        mockMvc.perform(patch("/api/candidate/applications/{id}/withdraw", appId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // Status must remain unchanged
        String dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, appId);
        assertThat(dbStatus).isEqualTo("SELECTED");
    }

    @Test
    @DisplayName("P5.3-E2: Candidate attempts withdrawal from REJECTED -> returns 422 INVALID_STATUS_TRANSITION")
    void withdrawFromRejected_rejected() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p53.wrej@example.com", "Withdraw Rejected");
        Long jobId = createActiveJob("wrej1");
        Long appId = applyToJob(candidate.token(), jobId);

        setApplicationStatus(appId, ApplicationStatus.REJECTED);

        mockMvc.perform(patch("/api/candidate/applications/{id}/withdraw", appId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        String dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, appId);
        assertThat(dbStatus).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("P5.3-E3: Candidate attempts withdrawal from already WITHDRAWN -> returns 422 INVALID_STATUS_TRANSITION")
    void withdrawFromWithdrawn_rejected() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p53.wwith@example.com", "Withdraw Withdrawn");
        Long jobId = createActiveJob("wwith1");
        Long appId = applyToJob(candidate.token(), jobId);

        // First withdrawal succeeds
        mockMvc.perform(patch("/api/candidate/applications/{id}/withdraw", appId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk());

        // Second withdrawal attempt must be rejected
        mockMvc.perform(patch("/api/candidate/applications/{id}/withdraw", appId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        String dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, appId);
        assertThat(dbStatus).isEqualTo("WITHDRAWN");
    }

    // ─── F. Ownership Security ────────────────────────────────────────────────

    @Test
    @DisplayName("P5.3-F1: Candidate A cannot withdraw Candidate B's application -> returns 404 RESOURCE_NOT_FOUND")
    void ownership_candidateCannotWithdrawAnotherApplication() throws Exception {
        UserAuth candidateA = buildCompleteCandidate("p53.ownA@example.com", "Candidate A");
        UserAuth candidateB = buildCompleteCandidate("p53.ownB@example.com", "Candidate B");
        Long jobId = createActiveJob("own1");

        Long appB = applyToJob(candidateB.token(), jobId);

        // Candidate A attempts to withdraw Candidate B's application
        mockMvc.perform(patch("/api/candidate/applications/{id}/withdraw", appB)
                        .header("Authorization", "Bearer " + candidateA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Candidate B's application status must remain APPLIED
        String dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, appB);
        assertThat(dbStatus).isEqualTo("APPLIED");

        // History count for appB must remain 1 (only initial APPLIED event)
        List<ApplicationRepository.ApplicationStatusHistoryEntity> history =
                applicationRepository.findStatusHistoryByApplicationId(appB);
        assertThat(history).hasSize(1);
    }

    // ─── G. Authentication ────────────────────────────────────────────────────

    @Test
    @DisplayName("P5.3-G1: Unauthenticated withdrawal attempt -> returns 401 UNAUTHENTICATED")
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/candidate/applications/1/withdraw"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    // ─── H. Role Protection ───────────────────────────────────────────────────

    @Test
    @DisplayName("P5.3-H1: Recruiter role cannot call candidate withdrawal -> returns 403 FORBIDDEN")
    void recruiterRole_returns403() throws Exception {
        String recruiterToken = registerRecruiter("p53.recwithdraw@example.com");

        mockMvc.perform(patch("/api/candidate/applications/1/withdraw")
                        .header("Authorization", "Bearer " + recruiterToken))
                .andExpect(status().isForbidden());
    }

    // ─── Non-existent Application ─────────────────────────────────────────────

    @Test
    @DisplayName("P5.3-404: Non-existent application returns 404 RESOURCE_NOT_FOUND")
    void nonExistentApplication_returns404() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p53.notfound@example.com", "Not Found Candidate");

        mockMvc.perform(patch("/api/candidate/applications/9999999/withdraw")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Autowired
    private ApplicationService applicationService;

    // ─── Response Security ────────────────────────────────────────────────────

    @Test
    @DisplayName("P5.3-Response: Withdrawal response does not expose storageKey or filesystem path")
    void responseDoesNotExposeStorageKey() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p53.respsec@example.com", "Resp Sec Candidate");
        Long jobId = createActiveJob("respsec1");
        Long appId = applyToJob(candidate.token(), jobId);

        MvcResult result = mockMvc.perform(patch("/api/candidate/applications/{id}/withdraw", appId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("storageKey");
        assertThat(responseBody).doesNotContain("storage_key");
        assertThat(responseBody).doesNotContain("uploads/resumes");
    }

    // ─── Transaction Rollback ─────────────────────────────────────────────────

    @Test
    @DisplayName("P5.3-Rollback: Status update rolls back if history insertion fails")
    void transactionRollback_onHistoryFailure() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p53.rollback@example.com", "Rollback Candidate");
        Long jobId = createActiveJob("rollback1");
        Long appId = applyToJob(candidate.token(), jobId);

        // Set security context with invalid user ID (9999999L) but valid candidate ID
        com.careerconnect.security.SecurityUser invalidUser = new com.careerconnect.security.SecurityUser(
                9999999L, "p53.rollback@example.com", "password", Role.CANDIDATE, "Rollback Cand", candidate.candidateId(), null, null, true);
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        invalidUser, null, invalidUser.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> applicationService.withdrawApplication(appId))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        // Verify status rolled back to APPLIED
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, appId);
        assertThat(status).isEqualTo("APPLIED");

        // Verify only 1 history row exists (the initial NULL -> APPLIED from submission)
        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_status_history WHERE application_id = ?",
                Integer.class, appId);
        assertThat(historyCount).isEqualTo(1);
    }
}
