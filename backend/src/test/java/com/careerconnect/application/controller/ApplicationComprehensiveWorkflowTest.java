package com.careerconnect.application.controller;

import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.AssignSkillRequest;
import com.careerconnect.candidate.dto.CreateEducationRequest;
import com.careerconnect.candidate.dto.UpdateProfileRequest;
import com.careerconnect.common.model.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ApplicationComprehensiveWorkflowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.resume.upload-dir:./target/test-uploads/resumes}")
    private String uploadDir;

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
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.RECRUITER, "Recruiter User");
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }

    private void seedCandidateProfile(UserAuth user, String name, String skill) throws Exception {
        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateProfileRequest(name, "Software Engineer", "Bengaluru", "Passionate engineer"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateEducationRequest("Top Tech University", "B.Tech", "Computer Science", 2018, 2022, "9.0", 1))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignSkillRequest(skill))))
                .andExpect(status().isCreated());

        byte[] pdfBytes = "%PDF-1.4 original resume content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "original_resume.pdf", "application/pdf", pdfBytes);
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isCreated());
    }

    private Long createJob(String titleSuffix, String status, LocalDate deadline, String... requiredSkills) {
        String companyName = "WorkflowCo " + titleSuffix;
        jdbcTemplate.update("INSERT INTO companies (name, industry, location, is_active) VALUES (?, ?, ?, ?)",
                companyName, "Technology", "Bengaluru", true);
        Long companyId = jdbcTemplate.queryForObject("SELECT id FROM companies WHERE name = ?", Long.class, companyName);

        String deadlineStr = deadline != null ? deadline.toString() : LocalDate.now().plusDays(30).toString();
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, job_type, experience_level,
                                  work_mode, application_deadline, status)
                VALUES (?, ?, 'Job description for testing', 'FULL_TIME', 'FRESHER', 'HYBRID', ?, ?)
                """, companyId, "Position " + titleSuffix, deadlineStr, status);

        Long jobId = jdbcTemplate.queryForObject("SELECT id FROM jobs WHERE title = ?", Long.class, "Position " + titleSuffix);

        for (String skillName : requiredSkills) {
            String norm = skillName.trim().toLowerCase();
            jdbcTemplate.update("INSERT INTO skills (name, normalized_name) VALUES (?, ?) ON DUPLICATE KEY UPDATE name = name", skillName, norm);
            Long skillId = jdbcTemplate.queryForObject("SELECT id FROM skills WHERE normalized_name = ?", Long.class, norm);
            jdbcTemplate.update("INSERT INTO job_skills (job_id, skill_id) VALUES (?, ?)", jobId, skillId);
        }

        return jobId;
    }

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM application_status_history");
        jdbcTemplate.execute("DELETE FROM applications");
        jdbcTemplate.execute("DELETE FROM resumes");
        jdbcTemplate.execute("DELETE FROM candidate_skills");
        jdbcTemplate.execute("DELETE FROM education");
        jdbcTemplate.execute("DELETE FROM job_skills");
        jdbcTemplate.execute("DELETE FROM jobs");
        jdbcTemplate.execute("DELETE FROM companies");

        try {
            Path dir = Paths.get(uploadDir);
            if (Files.exists(dir)) {
                try (Stream<Path> s = Files.walk(dir)) {
                    s.filter(Files::isRegularFile).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
                }
            }
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // 1. Comprehensive Candidate Application Lifecycle Workflow
    // =========================================================================

    @Test
    @DisplayName("P5.8-Workflow: End-to-end multi-step candidate application lifecycle with snapshot immutability")
    void completeApplicationLifecycleWorkflow() throws Exception {
        // Step 1: Candidate A registers and completes profile with skill "Python"
        UserAuth candidateA = registerCandidate("flow.cand.a@example.com", "Alice Walker");
        seedCandidateProfile(candidateA, "Alice Walker", "Python");

        // Job requires "Java" and "Spring Boot" -> Candidate A has 0% match
        Long jobId = createJob("DevOps", "ACTIVE", LocalDate.now().plusDays(15), "Java", "Spring Boot");

        // Step 2: Deterministic Match Preview -> 0% match
        mockMvc.perform(get("/api/jobs/{id}/match", jobId)
                        .header("Authorization", "Bearer " + candidateA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.percentage").value(0))
                .andExpect(jsonPath("$.data.matchedSkills", hasSize(0)))
                .andExpect(jsonPath("$.data.missingSkills", containsInAnyOrder("Java", "Spring Boot")))
                .andExpect(jsonPath("$.data.additionalSkills", containsInAnyOrder("Python")));

        // Step 3: Application Submission: 0% match does NOT block application submission (201 Created)
        MvcResult submitResult = mockMvc.perform(post("/api/jobs/{id}/applications", jobId)
                        .header("Authorization", "Bearer " + candidateA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverLetter\": \"Excited to apply despite Python background!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.coverLetter").value("Excited to apply despite Python background!"))
                .andReturn();

        JsonNode submitNode = objectMapper.readTree(submitResult.getResponse().getContentAsString()).path("data");
        Long applicationId = submitNode.path("applicationId").asLong();
        assertThat(applicationId).isNotNull().isPositive();

        // Step 4: Duplicate application attempt while active -> 409 DUPLICATE_APPLICATION
        mockMvc.perform(post("/api/jobs/{id}/applications", jobId)
                        .header("Authorization", "Bearer " + candidateA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverLetter\": \"Trying again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_APPLICATION"));

        // Step 5: Candidate lists own applications -> returns 1 item, canonical envelope
        mockMvc.perform(get("/api/candidate/applications")
                        .header("Authorization", "Bearer " + candidateA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].applicationId").value(applicationId))
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.data[0].jobTitle").value("Position DevOps"))
                .andExpect(jsonPath("$.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.pagination.totalPages").value(1));

        // Step 6: Candidate gets application detail: snapshot, metadata, status history
        MvcResult detailBeforeMutate = mockMvc.perform(get("/api/candidate/applications/{id}", applicationId)
                        .header("Authorization", "Bearer " + candidateA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.candidateSnapshot.fullName").value("Alice Walker"))
                .andExpect(jsonPath("$.data.candidateSnapshot.skills", contains("Python")))
                .andExpect(jsonPath("$.data.resumeSnapshot.originalFilename").value("original_resume.pdf"))
                .andExpect(jsonPath("$.data.statusHistory", hasSize(1)))
                .andExpect(jsonPath("$.data.statusHistory[0].toStatus").value("APPLIED"))
                .andReturn();

        // Response sanitization: Ensure absolute filesystem paths and secrets are NOT exposed
        String detailBody = detailBeforeMutate.getResponse().getContentAsString();
        assertThat(detailBody).doesNotContain(uploadDir);
        assertThat(detailBody).doesNotContain("test-uploads");
        assertThat(detailBody).doesNotContain("passwordHash");

        // Step 7: Candidate mutates profile, education, skills, and replaces resume
        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + candidateA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateProfileRequest("Alice M. Walker (Updated)", "Staff Engineer", "Remote", "Updated bio"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + candidateA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignSkillRequest("Rust"))))
                .andExpect(status().isCreated());

        byte[] newPdf = "%PDF-1.4 brand new resume".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile newFile = new MockMultipartFile("file", "replacement_resume.pdf", "application/pdf", newPdf);
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(newFile)
                        .header("Authorization", "Bearer " + candidateA.token()))
                .andExpect(status().isCreated());

        // Step 8: Detail application check — Snapshot Immutability (P5.4)
        // Historical candidate snapshot and resume snapshot MUST remain completely unchanged
        mockMvc.perform(get("/api/candidate/applications/{id}", applicationId)
                        .header("Authorization", "Bearer " + candidateA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidateSnapshot.fullName").value("Alice Walker")) // Unchanged!
                .andExpect(jsonPath("$.data.candidateSnapshot.skills", contains("Python"))) // Still just Python!
                .andExpect(jsonPath("$.data.candidateSnapshot.headline").value("Software Engineer")) // Unchanged!
                .andExpect(jsonPath("$.data.resumeSnapshot.originalFilename").value("original_resume.pdf")); // Unchanged!

        // Step 9: Candidate withdraws application -> 200 OK with WITHDRAWN
        mockMvc.perform(patch("/api/candidate/applications/{id}/withdraw", applicationId)
                        .header("Authorization", "Bearer " + candidateA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));

        // Status history now contains 2 chronological entries
        mockMvc.perform(get("/api/candidate/applications/{id}", applicationId)
                        .header("Authorization", "Bearer " + candidateA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.data.statusHistory", hasSize(2)))
                .andExpect(jsonPath("$.data.statusHistory[0].toStatus").value("APPLIED"))
                .andExpect(jsonPath("$.data.statusHistory[1].fromStatus").value("APPLIED"))
                .andExpect(jsonPath("$.data.statusHistory[1].toStatus").value("WITHDRAWN"));

        // Step 10: Candidate attempts to re-apply after withdrawal -> MUST remain 409 DUPLICATE_APPLICATION
        mockMvc.perform(post("/api/jobs/{id}/applications", jobId)
                        .header("Authorization", "Bearer " + candidateA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverLetter\": \"Re-applying after withdrawal\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_APPLICATION"));

        // Step 11: Candidate attempts to withdraw an already withdrawn application -> 422 INVALID_STATUS_TRANSITION
        mockMvc.perform(patch("/api/candidate/applications/{id}/withdraw", applicationId)
                        .header("Authorization", "Bearer " + candidateA.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // Step 12: Cross-candidate isolation: Candidate B cannot see or withdraw Candidate A's application
        UserAuth candidateB = registerCandidate("flow.cand.b@example.com", "Bob Builder");
        seedCandidateProfile(candidateB, "Bob Builder", "Java");

        mockMvc.perform(get("/api/candidate/applications/{id}", applicationId)
                        .header("Authorization", "Bearer " + candidateB.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(patch("/api/candidate/applications/{id}/withdraw", applicationId)
                        .header("Authorization", "Bearer " + candidateB.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Candidate B has 0 applications in their list
        mockMvc.perform(get("/api/candidate/applications")
                        .header("Authorization", "Bearer " + candidateB.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.pagination.totalElements").value(0));
    }

    // =========================================================================
    // 2. Pagination & Sorting Edge Cases on GET /api/candidate/applications
    // =========================================================================

    @Test
    @DisplayName("P5.8-Pagination: Pagination boundary checks and clamping")
    void paginationBoundaryChecks() throws Exception {
        UserAuth candidate = registerCandidate("flow.page@example.com", "Page Tester");
        seedCandidateProfile(candidate, "Page Tester", "Java");

        Long job1 = createJob("P1", "ACTIVE", LocalDate.now().plusDays(20));
        Long job2 = createJob("P2", "ACTIVE", LocalDate.now().plusDays(20));
        Long job3 = createJob("P3", "ACTIVE", LocalDate.now().plusDays(20));

        // Submit 3 applications
        mockMvc.perform(post("/api/jobs/{id}/applications", job1)
                .header("Authorization", "Bearer " + candidate.token())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/jobs/{id}/applications", job2)
                .header("Authorization", "Bearer " + candidate.token())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/jobs/{id}/applications", job3)
                .header("Authorization", "Bearer " + candidate.token())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isCreated());

        // 1. Explicit size 2, page 0 -> returns first 2
        mockMvc.perform(get("/api/candidate/applications?page=0&size=2")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(2))
                .andExpect(jsonPath("$.pagination.totalElements").value(3))
                .andExpect(jsonPath("$.pagination.totalPages").value(2));

        // 2. Page 1, size 2 -> returns remaining 1
        mockMvc.perform(get("/api/candidate/applications?page=1&size=2")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.pagination.page").value(1));

        // 3. Page beyond available data (page 5) -> returns empty array with correct totalElements
        mockMvc.perform(get("/api/candidate/applications?page=5&size=2")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.pagination.totalElements").value(3))
                .andExpect(jsonPath("$.pagination.totalPages").value(2));

        // 4. Negative page number -> clamped/defaults to 0
        mockMvc.perform(get("/api/candidate/applications?page=-1&size=10")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.data", hasSize(3)));

        // 5. Size exceeding max 100 (e.g. 150) -> clamped to 100
        mockMvc.perform(get("/api/candidate/applications?size=150")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.size").value(100));
    }

    // =========================================================================
    // 3. Error Contract & Canonical Envelope Verification
    // =========================================================================

    @Test
    @DisplayName("P5.8-ErrorContracts: Comprehensive canonical error envelope across all error cases")
    void canonicalErrorContractVerification() throws Exception {
        UserAuth candidate = registerCandidate("flow.errors@example.com", "Error Cand");
        seedCandidateProfile(candidate, "Error Cand", "Java");

        // 1. UNAUTHENTICATED (401)
        mockMvc.perform(get("/api/candidate/applications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").isString());

        // 2. FORBIDDEN (403)
        String recruiterToken = registerRecruiter("flow.rec.err@example.com");
        mockMvc.perform(get("/api/candidate/applications")
                        .header("Authorization", "Bearer " + recruiterToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        // 3. RESOURCE_NOT_FOUND (404)
        mockMvc.perform(get("/api/candidate/applications/888888")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // 4. JOB_NOT_ACTIVE (422)
        Long draftJob = createJob("DraftJob", "DRAFT", LocalDate.now().plusDays(30));
        mockMvc.perform(post("/api/jobs/{id}/applications", draftJob)
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_ACTIVE"));

        // 5. JOB_DEADLINE_PASSED (422)
        Long expiredJob = createJob("ExpiredJob", "ACTIVE", LocalDate.now().minusDays(5));
        mockMvc.perform(post("/api/jobs/{id}/applications", expiredJob)
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("JOB_DEADLINE_PASSED"));

        // 6. CANDIDATE_PROFILE_INCOMPLETE (422)
        UserAuth noEduCand = registerCandidate("flow.noedu@example.com", "No Edu");
        // No education added
        Long activeJob = createJob("ValidJob", "ACTIVE", LocalDate.now().plusDays(30));
        mockMvc.perform(post("/api/jobs/{id}/applications", activeJob)
                        .header("Authorization", "Bearer " + noEduCand.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("CANDIDATE_PROFILE_INCOMPLETE"));
    }

    // =========================================================================
    // 4. Candidate Identity Spoofing Protection Across All Endpoints
    // =========================================================================

    @Test
    @DisplayName("P5.8-IdentitySpoofing: Client cannot override candidate identity via body, params, or headers")
    void candidateIdentitySpoofingIsStrictlyPreventedAcrossAllEndpoints() throws Exception {
        UserAuth candA = registerCandidate("spoof.cand.a@example.com", "Candidate Real A");
        seedCandidateProfile(candA, "Candidate Real A", "Java");

        UserAuth candB = registerCandidate("spoof.cand.b@example.com", "Candidate Real B");
        seedCandidateProfile(candB, "Candidate Real B", "Python");

        Long job = createJob("SpoofJob", "ACTIVE", LocalDate.now().plusDays(30), "Java");

        // 1. Submit with candidateId: candB in JSON body
        MvcResult res = mockMvc.perform(post("/api/jobs/{id}/applications", job)
                        .header("Authorization", "Bearer " + candA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\": " + candB.candidateId() + ", \"coverLetter\": \"Spoof attempt\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.candidateId").value(candA.candidateId()))
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        Long appId = root.path("data").path("applicationId").asLong();

        // 2. List with candidateId: candB in query param
        mockMvc.perform(get("/api/candidate/applications?candidateId=" + candB.candidateId())
                        .header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].applicationId").value(appId))
                .andExpect(jsonPath("$.data[0].candidateId").value(candA.candidateId()));

        // 3. Detail with candidateId: candB in query param
        mockMvc.perform(get("/api/candidate/applications/{id}?candidateId=" + candB.candidateId(), appId)
                        .header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value(appId))
                .andExpect(jsonPath("$.data.candidateId").value(candA.candidateId()));

        // 4. Match preview with candidateId: candB in query param (candA has Java -> 100%, candB has Python -> 0%)
        mockMvc.perform(get("/api/jobs/{id}/match?candidateId=" + candB.candidateId(), job)
                        .header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.percentage").value(100)); // Evaluated for candA!

        // 5. Withdrawal with candidateId: candB in query param
        mockMvc.perform(patch("/api/candidate/applications/{id}/withdraw?candidateId=" + candB.candidateId(), appId)
                        .header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
    }
}
