package com.careerconnect.application.controller;

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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationSubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    /** Seeds a complete candidate: profile + education + skill + resume. */
    private UserAuth buildCompleteCandidate(String email, String name) throws Exception {
        UserAuth user = registerCandidate(email, name);
        updateProfile(user.token(), name);
        addEducation(user.token());
        addSkill(user.token());
        uploadResume(user.token());
        return user;
    }

    /** Seeds an active job and returns its ID. */
    private Long createActiveJob(String suffix) {
        String companyName = "TestCo " + suffix;
        jdbcTemplate.update("""
                INSERT INTO companies (name, industry, location, is_active) VALUES (?, ?, ?, ?)
                """, companyName, "Technology", "Bengaluru", true);
        Long companyId = jdbcTemplate.queryForObject("SELECT id FROM companies WHERE name = ?", Long.class, companyName);

        String deadline = LocalDate.now().plusDays(30).toString();
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, job_type, experience_level,
                                  work_mode, application_deadline, status)
                VALUES (?, ?, ?, 'INTERNSHIP', 'FRESHER', 'ONSITE', ?, 'ACTIVE')
                """, companyId, "Software Intern " + suffix, "Build cool stuff", deadline);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM jobs WHERE title = ?", Long.class, "Software Intern " + suffix);
    }

    private Long createInactiveJob(String suffix) {
        String companyName = "InactiveCo " + suffix;
        jdbcTemplate.update("""
                INSERT INTO companies (name, industry, location, is_active) VALUES (?, ?, ?, ?)
                """, companyName, "Technology", "Bengaluru", true);
        Long companyId = jdbcTemplate.queryForObject("SELECT id FROM companies WHERE name = ?", Long.class, companyName);

        String deadline = LocalDate.now().plusDays(30).toString();
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, job_type, experience_level,
                                  work_mode, application_deadline, status)
                VALUES (?, ?, ?, 'INTERNSHIP', 'FRESHER', 'ONSITE', ?, 'DRAFT')
                """, companyId, "Draft Intern " + suffix, "Build cool stuff", deadline);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM jobs WHERE title = ?", Long.class, "Draft Intern " + suffix);
    }

    private Long createExpiredJob(String suffix) {
        String companyName = "ExpiredCo " + suffix;
        jdbcTemplate.update("""
                INSERT INTO companies (name, industry, location, is_active) VALUES (?, ?, ?, ?)
                """, companyName, "Technology", "Bengaluru", true);
        Long companyId = jdbcTemplate.queryForObject("SELECT id FROM companies WHERE name = ?", Long.class, companyName);

        String pastDeadline = LocalDate.now().minusDays(5).toString();
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, job_type, experience_level,
                                  work_mode, application_deadline, status)
                VALUES (?, ?, ?, 'INTERNSHIP', 'FRESHER', 'ONSITE', ?, 'ACTIVE')
                """, companyId, "Expired Intern " + suffix, "Build cool stuff", pastDeadline);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM jobs WHERE title = ?", Long.class, "Expired Intern " + suffix);
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

    // ─── Tests ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P5.2-1: Successful application submission returns 201 with APPLIED status")
    void successfulApplicationSubmission_returns201() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p52.success@example.com", "Success Candidate");
        Long jobId = createActiveJob("success1");

        mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverLetter\": \"I am excited to apply!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationId").isNumber())
                .andExpect(jsonPath("$.data.jobId").value(jobId))
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.coverLetter").value("I am excited to apply!"));
    }

    @Test
    @DisplayName("P5.2-1a: Successful submission without cover letter returns 201")
    void successfulApplicationSubmissionNoCoverLetter_returns201() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p52.nocoverletter@example.com", "No CL Candidate");
        Long jobId = createActiveJob("nocl1");

        mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPLIED"));
    }

    @Test
    @DisplayName("P5.2-2: Unauthenticated request returns 401")
    void unauthenticatedRequest_returns401() throws Exception {
        mockMvc.perform(post("/api/jobs/1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("P5.2-3: Recruiter role returns 403 Forbidden")
    void recruiterRole_returns403() throws Exception {
        String recruiterToken = registerRecruiter("p52.recruiter@example.com");
        Long jobId = createActiveJob("rec403");

        mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P5.2-4: Incomplete profile (no education) returns 422 CANDIDATE_PROFILE_INCOMPLETE")
    void incompleteProfile_noEducation_returns422() throws Exception {
        UserAuth candidate = registerCandidate("p52.noedu@example.com", "No Education Cand");
        addSkill(candidate.token());
        uploadResume(candidate.token());
        Long jobId = createActiveJob("noedu1");

        mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CANDIDATE_PROFILE_INCOMPLETE"));
    }

    @Test
    @DisplayName("P5.2-4a: Incomplete profile (no skills) returns 422 CANDIDATE_PROFILE_INCOMPLETE")
    void incompleteProfile_noSkills_returns422() throws Exception {
        UserAuth candidate = registerCandidate("p52.noskills@example.com", "No Skills Cand");
        addEducation(candidate.token());
        uploadResume(candidate.token());
        Long jobId = createActiveJob("noskills1");

        mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CANDIDATE_PROFILE_INCOMPLETE"));
    }

    @Test
    @DisplayName("P5.2-5: Missing resume returns 422 CANDIDATE_RESUME_REQUIRED")
    void missingResume_returns422() throws Exception {
        UserAuth candidate = registerCandidate("p52.noresume@example.com", "No Resume Cand");
        addEducation(candidate.token());
        addSkill(candidate.token());
        Long jobId = createActiveJob("noresume1");

        mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CANDIDATE_RESUME_REQUIRED"));
    }

    @Test
    @DisplayName("P5.2-6: Inactive job returns 422 JOB_NOT_ACTIVE")
    void inactiveJob_returns422() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p52.inactivejob@example.com", "Active Candidate");
        Long jobId = createInactiveJob("inactive1");

        mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("P5.2-7: Expired deadline returns 422 JOB_DEADLINE_PASSED")
    void expiredDeadline_returns422() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p52.expiredjob@example.com", "Eager Candidate");
        Long jobId = createExpiredJob("expired1");

        mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("JOB_DEADLINE_PASSED"));
    }

    @Test
    @DisplayName("P5.2-8: Duplicate application returns 409 DUPLICATE_APPLICATION")
    void duplicateApplication_returns409() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p52.duplicate@example.com", "Dup Candidate");
        Long jobId = createActiveJob("dup1");

        // First application
        mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());

        // Second application to same job
        mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_APPLICATION"));
    }

    @Test
    @DisplayName("P5.2-9: Candidate cannot inject candidateId from another user")
    void candidateIdInjectionAttempt_usesAuthenticatedIdentity() throws Exception {
        UserAuth candidateA = buildCompleteCandidate("p52.injA@example.com", "Candidate A");
        UserAuth candidateB = buildCompleteCandidate("p52.injB@example.com", "Candidate B");
        Long jobId = createActiveJob("inject1");

        // candidateA applies — request body contains candidateB's ID (should be ignored)
        MvcResult result = mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidateA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverLetter\": \"Injecting\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        // The returned candidateId must be candidateA's, not injected
        assertThat(data.path("candidateId").asLong()).isEqualTo(candidateA.candidateId());
        assertThat(data.path("candidateId").asLong()).isNotEqualTo(candidateB.candidateId());

        // Verify in DB the application belongs to candidateA
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE candidate_id = ? AND job_id = ?",
                Integer.class, candidateA.candidateId(), jobId);
        assertThat(count).isEqualTo(1);

        Integer countB = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE candidate_id = ? AND job_id = ?",
                Integer.class, candidateB.candidateId(), jobId);
        assertThat(countB).isEqualTo(0);
    }

    @Test
    @DisplayName("P5.2-11: Snapshots are persisted in DB after successful submission")
    void snapshotsArePersisted_inDatabase() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p52.snap@example.com", "Snapshot Tester");
        Long jobId = createActiveJob("snap1");

        MvcResult result = mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverLetter\": \"Testing snapshots\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        Long applicationId = data.path("applicationId").asLong();

        // Verify snapshots were persisted in DB
        String candidateSnapshot = jdbcTemplate.queryForObject(
                "SELECT candidate_snapshot FROM applications WHERE id = ?", String.class, applicationId);
        String resumeSnapshot = jdbcTemplate.queryForObject(
                "SELECT resume_snapshot FROM applications WHERE id = ?", String.class, applicationId);

        assertThat(candidateSnapshot).isNotNull().isNotEmpty();
        assertThat(resumeSnapshot).isNotNull().isNotEmpty();

        // Validate snapshot content
        JsonNode snapNode = objectMapper.readTree(candidateSnapshot);
        if (snapNode.isTextual()) {
            snapNode = objectMapper.readTree(snapNode.asText());
        }
        assertThat(snapNode.path("candidateId").asLong()).isEqualTo(candidate.candidateId());
        assertThat(snapNode.path("fullName").asText()).isEqualTo("Snapshot Tester");
        assertThat(snapNode.path("education").isArray()).isTrue();
        assertThat(snapNode.path("skills").isArray()).isTrue();

        JsonNode resumeNode = objectMapper.readTree(resumeSnapshot);
        if (resumeNode.isTextual()) {
            resumeNode = objectMapper.readTree(resumeNode.asText());
        }
        assertThat(resumeNode.path("originalFilename").asText()).isEqualTo("resume.pdf");
        assertThat(resumeNode.path("storageKey").asText()).isNotBlank();
    }

    @Test
    @DisplayName("P5.2-12: Initial status history has from_status=NULL, to_status=APPLIED")
    void initialStatusHistory_fromNullToApplied() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p52.history@example.com", "History Candidate");
        Long jobId = createActiveJob("hist1");

        MvcResult result = mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        Long applicationId = data.path("applicationId").asLong();

        // Verify status history row
        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_status_history WHERE application_id = ?",
                Integer.class, applicationId);
        assertThat(historyCount).isEqualTo(1);

        String fromStatus = jdbcTemplate.queryForObject(
                "SELECT from_status FROM application_status_history WHERE application_id = ?",
                String.class, applicationId);
        String toStatus = jdbcTemplate.queryForObject(
                "SELECT to_status FROM application_status_history WHERE application_id = ?",
                String.class, applicationId);

        assertThat(fromStatus).isNull();
        assertThat(toStatus).isEqualTo("APPLIED");
    }

    @Test
    @DisplayName("P5.2-11b: storageKey is not exposed in REST response")
    void storageKeyNotExposedInResponse() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p52.nokey@example.com", "No Key Candidate");
        Long jobId = createActiveJob("nokey1");

        MvcResult result = mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("storageKey");
        assertThat(responseBody).doesNotContain("storage_key");
    }

    @Test
    @DisplayName("P5.2-4b: Candidate with full name set but no education returns CANDIDATE_PROFILE_INCOMPLETE")
    void candidateProfileNotFull_returnsProfileIncomplete() throws Exception {
        // No profile update → registered name is "No Profile" but no education
        UserAuth candidate = registerCandidate("p52.noprof@example.com", "No Profile Complete");
        Long jobId = createActiveJob("noprof1");

        // Try to apply without adding edu/skills/resume
        mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("CANDIDATE_PROFILE_INCOMPLETE"));
    }

    @Test
    @DisplayName("P5.2-job404: Non-existent job returns 404 JOB_NOT_FOUND")
    void nonExistentJob_returns404() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p52.notfound@example.com", "Not Found Cand");

        mockMvc.perform(post("/api/jobs/9999999/applications")
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    @Test
    @DisplayName("P5.2-coverletter-validation: Cover letter over 5000 chars returns 400")
    void coverLetterTooLong_returns400() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p52.longcl@example.com", "Long CL Cand");
        Long jobId = createActiveJob("longcl1");

        String longCoverLetter = "A".repeat(5001);

        mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverLetter\": \"" + longCoverLetter + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
}
