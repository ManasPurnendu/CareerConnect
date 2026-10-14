package com.careerconnect.application.controller;

import com.careerconnect.application.domain.ApplicationStatus;
import com.careerconnect.application.dto.ApplicationSubmissionRequest;
import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.AssignSkillRequest;
import com.careerconnect.candidate.dto.CreateEducationRequest;
import com.careerconnect.candidate.dto.CreateExperienceRequest;
import com.careerconnect.candidate.dto.CreateProjectRequest;
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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
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
class CandidateApplicationQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.RECRUITER, "Recruiter Query");
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
                "Query University", "B.Tech", "Computer Science", 2019, 2023, null, 1);
        mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edu)))
                .andExpect(status().isCreated());
    }

    private void addProject(String token) throws Exception {
        CreateProjectRequest project = new CreateProjectRequest(
                "Distributed Engine", "Built a custom engine", "https://github.com/engine", 1);
        mockMvc.perform(post("/api/candidate/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(project)))
                .andExpect(status().isCreated());
    }

    private void addExperience(String token) throws Exception {
        CreateExperienceRequest exp = new CreateExperienceRequest(
                "Startup Inc", "Intern", LocalDate.of(2022, 1, 1), LocalDate.of(2022, 6, 1), false, "Did things", 1);
        mockMvc.perform(post("/api/candidate/experience")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exp)))
                .andExpect(status().isCreated());
    }

    private void uploadResume(String token, String filename) throws Exception {
        byte[] pdfBytes = "%PDF-1.4 query test resume".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/pdf", pdfBytes);
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private void updateProfile(String token, String fullName) throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest(fullName, "Backend Engineer", "Bengaluru", "Excited coder");
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
        addProject(user.token());
        addExperience(user.token());
        uploadResume(user.token(), "query_resume.pdf");
        return user;
    }

    private Long createActiveJob(String suffix) {
        String companyName = "QueryCo " + suffix;
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
        ApplicationSubmissionRequest req = new ApplicationSubmissionRequest(coverLetter);
        MvcResult res = mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("applicationId").asLong();
    }

    // ─── CANDIDATE LIST TESTS ─────────────────────────────────────────────────

    @Test
    @DisplayName("1. Authenticated candidate receives only own applications")
    void testCandidateReceivesOwnApplications() throws Exception {
        UserAuth candidateA = buildCompleteCandidate("p56.own.a@example.com", "Candidate A");
        UserAuth candidateB = buildCompleteCandidate("p56.own.b@example.com", "Candidate B");

        Long job1 = createActiveJob("P56_1");
        Long job2 = createActiveJob("P56_2");

        applyToJob(candidateA.token(), job1, "App A1");
        applyToJob(candidateB.token(), job2, "App B1");

        mockMvc.perform(get("/api/candidate/applications")
                        .header("Authorization", "Bearer " + candidateA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].candidateId").value(candidateA.candidateId()))
                .andExpect(jsonPath("$.data[0].jobId").value(job1))
                .andExpect(jsonPath("$.data[0].jobTitle").value("Job P56_1"))
                .andExpect(jsonPath("$.data[0].companyName").value("QueryCo P56_1"))
                .andExpect(jsonPath("$.data[0].coverLetter").value("App A1"))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
    }

    @Test
    @DisplayName("2. Empty application list returns canonical envelope with empty data array")
    void testEmptyApplicationList() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p56.empty@example.com", "Empty Candidate");

        mockMvc.perform(get("/api/candidate/applications")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", empty()))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(20))
                .andExpect(jsonPath("$.pagination.totalElements").value(0))
                .andExpect(jsonPath("$.pagination.totalPages").value(0));
    }

    @Test
    @DisplayName("3-6. Multiple applications and pagination across pages")
    void testMultipleApplicationsPagination() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p56.multi@example.com", "Multi Candidate");

        Long job1 = createActiveJob("P56_M1");
        Long job2 = createActiveJob("P56_M2");
        Long job3 = createActiveJob("P56_M3");

        applyToJob(candidate.token(), job1, "App 1");
        applyToJob(candidate.token(), job2, "App 2");
        applyToJob(candidate.token(), job3, "App 3");

        // Page 0 with size 2: elements 2
        mockMvc.perform(get("/api/candidate/applications?page=0&size=2")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(2))
                .andExpect(jsonPath("$.pagination.totalElements").value(3))
                .andExpect(jsonPath("$.pagination.totalPages").value(2));

        // Page 1 with size 2: elements 1
        mockMvc.perform(get("/api/candidate/applications?page=1&size=2")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.size").value(2))
                .andExpect(jsonPath("$.pagination.totalElements").value(3))
                .andExpect(jsonPath("$.pagination.totalPages").value(2));

        // Page beyond available results (page 10): empty data, valid pagination metadata
        mockMvc.perform(get("/api/candidate/applications?page=10&size=2")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", empty()))
                .andExpect(jsonPath("$.pagination.page").value(10))
                .andExpect(jsonPath("$.pagination.size").value(2))
                .andExpect(jsonPath("$.pagination.totalElements").value(3))
                .andExpect(jsonPath("$.pagination.totalPages").value(2));
    }

    @Test
    @DisplayName("7-8. Default page size (20) and maximum page size (100)")
    void testDefaultAndMaxPageSize() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p56.sizes@example.com", "Sizes Candidate");
        Long job = createActiveJob("P56_SZ");
        applyToJob(candidate.token(), job, "App SZ");

        // No size passed -> defaults to 20
        mockMvc.perform(get("/api/candidate/applications")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.size").value(20));

        // Explicit size 100 -> maximum allowed size
        mockMvc.perform(get("/api/candidate/applications?size=100")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.size").value(100));
    }

    @Test
    @DisplayName("9-11. Invalid negative page, invalid size, and size above maximum")
    void testInvalidPaginationInputsHandledGracefully() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p56.invalidpag@example.com", "Invalid Pag");
        Long job = createActiveJob("P56_INV");
        applyToJob(candidate.token(), job, "App INV");

        // Negative page normalizes to 0
        mockMvc.perform(get("/api/candidate/applications?page=-1")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.data", hasSize(1)));

        // Invalid size (<= 0) normalizes to default 20
        mockMvc.perform(get("/api/candidate/applications?size=-5")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.size").value(20))
                .andExpect(jsonPath("$.data", hasSize(1)));

        // Size above maximum (e.g. 500) capped to 100
        mockMvc.perform(get("/api/candidate/applications?size=500")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.size").value(100))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("12. Deterministic ordering: applications returned newest-first (applied_at DESC, id DESC)")
    void testDeterministicOrdering() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p56.order@example.com", "Order Candidate");

        Long job1 = createActiveJob("P56_ORD1");
        Long job2 = createActiveJob("P56_ORD2");
        Long job3 = createActiveJob("P56_ORD3");

        Long app1 = applyToJob(candidate.token(), job1, "App 1");
        Long app2 = applyToJob(candidate.token(), job2, "App 2");
        Long app3 = applyToJob(candidate.token(), job3, "App 3");

        // Set timestamps strictly
        jdbcTemplate.update("UPDATE applications SET applied_at = TIMESTAMP '2026-10-15 10:00:00' WHERE id = ?", app1);
        jdbcTemplate.update("UPDATE applications SET applied_at = TIMESTAMP '2026-10-15 11:00:00' WHERE id = ?", app2);
        jdbcTemplate.update("UPDATE applications SET applied_at = TIMESTAMP '2026-10-15 12:00:00' WHERE id = ?", app3);

        MvcResult res = mockMvc.perform(get("/api/candidate/applications")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].applicationId").value(app3))
                .andExpect(jsonPath("$.data[1].applicationId").value(app2))
                .andExpect(jsonPath("$.data[2].applicationId").value(app1))
                .andReturn();

        // Also test tie breaker: when applied_at identical, id DESC dictates order
        jdbcTemplate.update("UPDATE applications SET applied_at = TIMESTAMP '2026-10-15 10:00:00' WHERE id IN (?, ?, ?)", app1, app2, app3);
        mockMvc.perform(get("/api/candidate/applications")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].applicationId").value(app3))
                .andExpect(jsonPath("$.data[1].applicationId").value(app2))
                .andExpect(jsonPath("$.data[2].applicationId").value(app1));
    }

    // ─── CANDIDATE DETAIL TESTS ───────────────────────────────────────────────

    @Test
    @DisplayName("13-22. Candidate retrieves own application detail with full snapshots and status history")
    void testCandidateRetrievesOwnApplicationDetail() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p56.detail@example.com", "Detail Candidate");
        Long job = createActiveJob("P56_DET");

        Long appId = applyToJob(candidate.token(), job, "Detailed cover letter text");

        // Withdraw application to create a second status history entry
        mockMvc.perform(patch("/api/candidate/applications/{id}/withdraw", appId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/candidate/applications/{id}", appId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationId").value(appId))
                .andExpect(jsonPath("$.data.jobId").value(job))
                .andExpect(jsonPath("$.data.candidateId").value(candidate.candidateId()))
                .andExpect(jsonPath("$.data.jobTitle").value("Job P56_DET"))
                .andExpect(jsonPath("$.data.companyName").value("QueryCo P56_DET"))
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.data.coverLetter").value("Detailed cover letter text"))
                .andExpect(jsonPath("$.data.appliedAt").isNotEmpty())
                // Snapshot assertions
                .andExpect(jsonPath("$.data.candidateSnapshot.fullName").value("Detail Candidate"))
                .andExpect(jsonPath("$.data.candidateSnapshot.email").value("p56.detail@example.com"))
                .andExpect(jsonPath("$.data.candidateSnapshot.skills[0]").value("Java"))
                .andExpect(jsonPath("$.data.candidateSnapshot.education[0].institution").value("Query University"))
                .andExpect(jsonPath("$.data.candidateSnapshot.projects[0].title").value("Distributed Engine"))
                .andExpect(jsonPath("$.data.candidateSnapshot.experience[0].companyName").value("Startup Inc"))
                .andExpect(jsonPath("$.data.resumeSnapshot.originalFilename").value("query_resume.pdf"))
                .andExpect(jsonPath("$.data.resumeSnapshot.contentType").value("application/pdf"))
                // Status history assertions
                .andExpect(jsonPath("$.data.statusHistory", hasSize(2)))
                .andExpect(jsonPath("$.data.statusHistory[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$.data.statusHistory[0].toStatus").value("APPLIED"))
                .andExpect(jsonPath("$.data.statusHistory[0].changedByUserId").value(candidate.userId()))
                .andExpect(jsonPath("$.data.statusHistory[1].fromStatus").value("APPLIED"))
                .andExpect(jsonPath("$.data.statusHistory[1].toStatus").value("WITHDRAWN"))
                .andExpect(jsonPath("$.data.statusHistory[1].changedByUserId").value(candidate.userId()))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.path("data").path("candidateSnapshot").isMissingNode()).isFalse();
        assertThat(root.path("data").path("resumeSnapshot").isMissingNode()).isFalse();
    }

    @Test
    @DisplayName("14. Non-existent application returns 404 RESOURCE_NOT_FOUND")
    void testApplicationNotFoundReturns404() throws Exception {
        UserAuth candidate = buildCompleteCandidate("p56.nf@example.com", "NF Candidate");

        mockMvc.perform(get("/api/candidate/applications/{id}", 9999999L)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("15. Cross-candidate application access denied/not exposed -> returns 404 RESOURCE_NOT_FOUND")
    void testCrossCandidateAccessDenied() throws Exception {
        UserAuth candidateA = buildCompleteCandidate("p56.cross.a@example.com", "Candidate A");
        UserAuth candidateB = buildCompleteCandidate("p56.cross.b@example.com", "Candidate B");
        Long job = createActiveJob("P56_CROSS");

        Long appB = applyToJob(candidateB.token(), job, "App Candidate B");

        // Candidate A tries to access Candidate B's application detail -> 404
        mockMvc.perform(get("/api/candidate/applications/{id}", appB)
                        .header("Authorization", "Bearer " + candidateA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ─── SECURITY TESTS ───────────────────────────────────────────────────────

    @Test
    @DisplayName("23. Unauthenticated list request returns 401 UNAUTHENTICATED")
    void testUnauthenticatedListReturns401() throws Exception {
        mockMvc.perform(get("/api/candidate/applications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("24. Unauthenticated detail request returns 401 UNAUTHENTICATED")
    void testUnauthenticatedDetailReturns401() throws Exception {
        mockMvc.perform(get("/api/candidate/applications/{id}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("25. Recruiter list request returns 403 FORBIDDEN")
    void testRecruiterListReturns403() throws Exception {
        String recruiterToken = registerRecruiter("p56.rec.list@example.com");

        mockMvc.perform(get("/api/candidate/applications")
                        .header("Authorization", "Bearer " + recruiterToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("26. Recruiter detail request returns 403 FORBIDDEN")
    void testRecruiterDetailReturns403() throws Exception {
        String recruiterToken = registerRecruiter("p56.rec.detail@example.com");

        mockMvc.perform(get("/api/candidate/applications/{id}", 1L)
                        .header("Authorization", "Bearer " + recruiterToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("27-28. Candidate ID query param spoofing does not alter identity; server-side ownership enforced")
    void testCandidateIdSpoofingIgnored() throws Exception {
        UserAuth candidateA = buildCompleteCandidate("p56.spoof.a@example.com", "Spoof A");
        UserAuth candidateB = buildCompleteCandidate("p56.spoof.b@example.com", "Spoof B");

        Long jobA = createActiveJob("P56_SPA");
        Long jobB = createActiveJob("P56_SPB");

        applyToJob(candidateA.token(), jobA, "App A");
        Long appB = applyToJob(candidateB.token(), jobB, "App B");

        // Candidate A queries list with ?candidateId=<B's ID>
        mockMvc.perform(get("/api/candidate/applications?candidateId=" + candidateB.candidateId())
                        .header("Authorization", "Bearer " + candidateA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].candidateId").value(candidateA.candidateId()))
                .andExpect(jsonPath("$.data[0].jobId").value(jobA));

        // Candidate A queries detail with ?candidateId=<B's ID>
        mockMvc.perform(get("/api/candidate/applications/{id}?candidateId=" + candidateB.candidateId(), appB)
                        .header("Authorization", "Bearer " + candidateA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
