package com.careerconnect.application.controller;

import com.careerconnect.application.domain.ApplicationStatus;
import com.careerconnect.application.dto.UpdateApplicationStatusRequest;
import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.UpdateProfileRequest;
import com.careerconnect.common.model.Role;
import com.careerconnect.jobs.dto.RecruiterJobUpdateRequest;
import com.careerconnect.jobs.model.ExperienceLevel;
import com.careerconnect.jobs.model.JobType;
import com.careerconnect.jobs.model.WorkMode;
import com.careerconnect.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Phase7ComprehensiveWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Value("${app.resume.upload-dir:./target/test-uploads/resumes}")
    private String uploadDir;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM notifications");
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
                stream.filter(Files::isRegularFile)
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {}
                        });
            } catch (IOException ignored) {}
        }
    }

    private record RecruiterAuth(String token, Long recruiterId, Long userId) {}
    private record CandidateAuth(String token, Long candidateId, Long userId, String fullName) {}

    private RecruiterAuth registerRecruiter(String email) throws Exception {
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.RECRUITER, "Recruiter Workflow");
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

    private CandidateAuth registerCandidate(String email, String fullName) throws Exception {
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

    private Long createCompany(String name, Long recruiterId) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO companies (name, industry, location, is_active) VALUES (?, 'Fintech', 'Bengaluru', TRUE)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name + "_" + UUID.randomUUID());
            return ps;
        }, kh);
        Long companyId = com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
        jdbcTemplate.update("UPDATE recruiters SET company_id = ? WHERE id = ?", companyId, recruiterId);
        return companyId;
    }

    private Long createJob(Long companyId, String title) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO jobs (
                        company_id, title, description, job_type, experience_level,
                        work_mode, location, salary_min_lpa, salary_max_lpa, salary_currency,
                        application_deadline, status, created_at, updated_at
                    ) VALUES (?, ?, 'Detailed Job Description', 'INTERNSHIP', 'FRESHER', 'HYBRID', 'Bengaluru', 6.0, 12.0, 'INR', ?, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            ps.setString(2, title);
            ps.setDate(3, Date.valueOf(LocalDate.now().plusDays(45)));
            return ps;
        }, kh);
        Long jobId = com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);

        // Add a required skill
        List<Long> skillIds = jdbcTemplate.query("SELECT id FROM skills WHERE normalized_name = 'java'", (rs, rowNum) -> rs.getLong("id"));
        Long skillId;
        if (skillIds.isEmpty()) {
            KeyHolder skh = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> con.prepareStatement(
                    "INSERT INTO skills (name, normalized_name, created_at) VALUES ('Java', 'java', CURRENT_TIMESTAMP(3))",
                    Statement.RETURN_GENERATED_KEYS), skh);
            skillId = com.careerconnect.common.util.JdbcUtils.extractGeneratedId(skh);
        } else {
            skillId = skillIds.get(0);
        }
        jdbcTemplate.update("INSERT INTO job_skills (job_id, skill_id) VALUES (?, ?)", jobId, skillId);
        return jobId;
    }

    private void setupCandidateReadiness(CandidateAuth cand, String resumeContent) throws Exception {
        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest(cand.fullName(), "Backend Developer", "Bengaluru", "Experienced dev"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "institution": "Indian Institute of Technology",
                                  "degree": "B.Tech",
                                  "fieldOfStudy": "Computer Science",
                                  "startYear": 2020,
                                  "endYear": 2024
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "java"))))
                .andExpect(status().isCreated());

        byte[] pdfBytes = ("%PDF-1.4\n" + resumeContent).getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", cand.fullName().toLowerCase().replace(" ", "_") + "_resume.pdf", "application/pdf", pdfBytes);

        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + cand.token()))
                .andExpect(status().isCreated());
    }

    private Long applyToJob(CandidateAuth cand, Long jobId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/jobs/" + jobId + "/applications")
                        .header("Authorization", "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("coverLetter", "Excited to apply to this job!"))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("applicationId").asLong();
    }

    // =========================================================================
    // 1. Candidate -> Recruiter Full Lifecycle End-To-End Workflow
    // =========================================================================
    @Test
    @DisplayName("P7.6 E2E: Candidate applies, recruiter reviews snapshot and downloads resume, steps through status lifecycle, candidate gets notifications and reads them")
    void candidateToRecruiterFullLifecycleEndToEnd() throws Exception {
        // 1. Recruiter registers and sets up company and job
        RecruiterAuth rec = registerRecruiter("rec.p76.e2e_" + UUID.randomUUID() + "@example.com");
        Long companyId = createCompany("Alpha Innovations", rec.recruiterId());
        Long jobId = createJob(companyId, "Senior Java Developer");

        // 2. Candidate registers and prepares profile, education, skills, and resume
        CandidateAuth cand = registerCandidate("cand.p76.e2e_" + UUID.randomUUID() + "@example.com", "Ananya Sharma");
        setupCandidateReadiness(cand, "Ananya Sharma Confidential Resume Content");

        // 3. Candidate verifies profile readiness is complete
        mockMvc.perform(get("/api/candidate/profile/readiness")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(true));

        // 4. Candidate checks match percentage against job
        mockMvc.perform(get("/api/jobs/" + jobId + "/match")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.percentage").value(100));

        // 5. Candidate applies to job
        Long applicationId = applyToJob(cand, jobId);
        assertThat(applicationId).isNotNull();

        // Verify initial state in DB
        String initialStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, applicationId);
        assertThat(initialStatus).isEqualTo("APPLIED");

        // 6. Recruiter lists applicants for the job
        mockMvc.perform(get("/api/recruiter/jobs/" + jobId + "/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].applicationId").value(applicationId))
                .andExpect(jsonPath("$.data[0].candidateName").value("Ananya Sharma"))
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.data[0].matchPercentage").value(100));

        // 7. Recruiter views applicant detail and verifies snapshot immutability
        mockMvc.perform(get("/api/recruiter/applications/" + applicationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value(applicationId))
                .andExpect(jsonPath("$.data.jobTitle").value("Senior Java Developer"))
                .andExpect(jsonPath("$.data.candidateSnapshot.fullName").value("Ananya Sharma"))
                .andExpect(jsonPath("$.data.resumeSnapshot.originalFilename").value("ananya_sharma_resume.pdf"))
                .andExpect(jsonPath("$.data.statusHistory[0].toStatus").value("APPLIED"));

        // 8. Recruiter streams and downloads protected resume
        mockMvc.perform(get("/api/recruiter/applications/" + applicationId + "/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ananya Sharma Confidential Resume Content")));

        // 9. Recruiter steps through lifecycle: APPLIED -> UNDER_REVIEW -> SHORTLISTED -> INTERVIEW -> SELECTED
        ApplicationStatus[] lifecycleSteps = {
                ApplicationStatus.UNDER_REVIEW,
                ApplicationStatus.SHORTLISTED,
                ApplicationStatus.INTERVIEW,
                ApplicationStatus.SELECTED
        };

        for (ApplicationStatus nextStatus : lifecycleSteps) {
            UpdateApplicationStatusRequest statusReq = new UpdateApplicationStatusRequest(nextStatus, "Step transition note to " + nextStatus);
            mockMvc.perform(patch("/api/recruiter/applications/" + applicationId + "/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(nextStatus.name()));

            // Verify DB state
            String dbStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM applications WHERE id = ?", String.class, applicationId);
            assertThat(dbStatus).isEqualTo(nextStatus.name());
        }

        // 10. Candidate checks notifications: must have received 4 status change notifications
        MvcResult notifRes = mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(4))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(4))
                .andReturn();

        JsonNode notifJson = objectMapper.readTree(notifRes.getResponse().getContentAsString());
        Long latestNotifId = notifJson.path("data").path("items").get(0).path("id").asLong();

        // 11. Candidate marks the latest notification read
        mockMvc.perform(patch("/api/notifications/" + latestNotifId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));

        // Candidate checks notifications: unreadCount should now be 3
        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(jsonPath("$.data.unreadCount").value(3));

        // 12. Candidate marks all read
        mockMvc.perform(patch("/api/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.markedReadCount").value(3));

        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        // 13. Candidate views application detail: status is SELECTED
        mockMvc.perform(get("/api/candidate/applications/" + applicationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SELECTED"))
                .andExpect(jsonPath("$.data.statusHistory.length()").value(5)); // APPLIED + 4 transitions

        // 14. Terminal status SELECTED cannot be withdrawn or transitioned
        mockMvc.perform(patch("/api/candidate/applications/" + applicationId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    // =========================================================================
    // 2. P6 Job Closure Triggers Candidate Notifications
    // =========================================================================
    @Test
    @DisplayName("P7.6 Job Closure: Closing an active job notifies all active applicants with JOB_CLOSED notification")
    void p6JobClosureCandidateNotificationFlow() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.p76.close_" + UUID.randomUUID() + "@example.com");
        Long companyId = createCompany("Closure Corp", rec.recruiterId());
        Long jobId = createJob(companyId, "Systems Programmer");

        CandidateAuth cand1 = registerCandidate("cand1.close_" + UUID.randomUUID() + "@example.com", "Applicant One");
        setupCandidateReadiness(cand1, "Resume 1");
        applyToJob(cand1, jobId);

        CandidateAuth cand2 = registerCandidate("cand2.close_" + UUID.randomUUID() + "@example.com", "Applicant Two");
        setupCandidateReadiness(cand2, "Resume 2");
        applyToJob(cand2, jobId);

        // Recruiter closes job
        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // Both candidates receive JOB_CLOSED notification
        for (CandidateAuth cand : List.of(cand1, cand2)) {
            mockMvc.perform(get("/api/notifications")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].type").value("JOB_CLOSED"))
                    .andExpect(jsonPath("$.data.items[0].resourceType").value("JOB"))
                    .andExpect(jsonPath("$.data.items[0].resourceId").value(jobId));
        }

        // Attempting to close an already CLOSED job is rejected
        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    // =========================================================================
    // 3. P6 Job Fundamental Fields Locked When Applications Exist
    // =========================================================================
    @Test
    @DisplayName("P7.6 Cross-Phase Invariant: Recruiter cannot alter fundamental job requirements or delete job once applications exist")
    void p6JobEditFundamentalLockWhenApplicationsExist() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.p76.lock_" + UUID.randomUUID() + "@example.com");
        Long companyId = createCompany("Locked Job Corp", rec.recruiterId());
        Long jobId = createJob(companyId, "Core Architect");

        CandidateAuth cand = registerCandidate("cand.p76.lock_" + UUID.randomUUID() + "@example.com", "Lock Candidate");
        setupCandidateReadiness(cand, "Resume Lock");
        applyToJob(cand, jobId);

        // Recruiter attempts to modify fundamental requirement (salaryMinLpa from 6.0 to 15.0) -> 422 INVALID_STATUS_TRANSITION
        Long javaSkillId = jdbcTemplate.queryForObject("SELECT id FROM skills WHERE normalized_name = 'java'", Long.class);
        RecruiterJobUpdateRequest invalidUpdate = new RecruiterJobUpdateRequest(
                "Core Architect",
                "New description",
                "Updated responsibilities",
                "Updated requirements",
                JobType.INTERNSHIP,
                ExperienceLevel.FRESHER,
                WorkMode.HYBRID,
                "Bengaluru",
                new BigDecimal("15.00"), // Changed fundamental requirement
                new BigDecimal("20.00"),
                "INR",
                LocalDate.now().plusDays(30),
                List.of(javaSkillId)
        );

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUpdate)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // Recruiter attempts to delete job with applications -> 422 INVALID_STATUS_TRANSITION
        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/recruiter/jobs/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    // =========================================================================
    // 4. Candidate Self-Service Withdrawal & Re-Application Prevention
    // =========================================================================
    @Test
    @DisplayName("P7.6 Candidate Withdrawal: Withdrawn application records audit history and prevents duplicate re-application")
    void candidateWithdrawalWorkflow() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.p76.with_" + UUID.randomUUID() + "@example.com");
        Long companyId = createCompany("Withdrawal Corp", rec.recruiterId());
        Long jobId = createJob(companyId, "Frontend Specialist");

        CandidateAuth cand = registerCandidate("cand.p76.with_" + UUID.randomUUID() + "@example.com", "Withdraw Applicant");
        setupCandidateReadiness(cand, "Resume Withdraw");
        Long appId = applyToJob(cand, jobId);

        // Candidate withdraws application
        mockMvc.perform(patch("/api/candidate/applications/" + appId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));

        // Verify withdrawal audit row in application_status_history
        Integer withHistory = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_status_history WHERE application_id = ? AND to_status = 'WITHDRAWN'",
                Integer.class, appId);
        assertThat(withHistory).isEqualTo(1);

        // Candidate attempts to re-apply to the same job -> 409 DUPLICATE_APPLICATION
        mockMvc.perform(post("/api/jobs/" + jobId + "/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("coverLetter", "Trying again!"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_APPLICATION"));

        // Recruiter cannot transition withdrawn application -> 422 INVALID_STATUS_TRANSITION
        UpdateApplicationStatusRequest req = new UpdateApplicationStatusRequest(ApplicationStatus.UNDER_REVIEW, "Attempting update");
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    // =========================================================================
    // 5. Database Integrity & Foreign Key Relational Consistency
    // =========================================================================
    @Test
    @DisplayName("P7.6 Database Integrity: Verifies zero orphaned records across applications, history, notifications, and resumes")
    void databaseIntegrityAndRelationalConsistency() {
        // 1. Verify all applications point to existing jobs and candidates
        Integer orphanApps = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM applications a
                LEFT JOIN jobs j ON a.job_id = j.id
                LEFT JOIN candidates c ON a.candidate_id = c.id
                WHERE j.id IS NULL OR c.id IS NULL
                """, Integer.class);
        assertThat(orphanApps).isEqualTo(0);

        // 2. Verify all status histories point to existing applications
        Integer orphanHistories = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM application_status_history ash
                LEFT JOIN applications a ON ash.application_id = a.id
                WHERE a.id IS NULL
                """, Integer.class);
        assertThat(orphanHistories).isEqualTo(0);

        // 3. Verify all notifications point to existing users
        Integer orphanNotifs = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM notifications n
                LEFT JOIN users u ON n.user_id = u.id
                WHERE u.id IS NULL
                """, Integer.class);
        assertThat(orphanNotifs).isEqualTo(0);

        // 4. Verify all resumes point to existing candidates
        Integer orphanResumes = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM resumes r
                LEFT JOIN candidates c ON r.candidate_id = c.id
                WHERE c.id IS NULL
                """, Integer.class);
        assertThat(orphanResumes).isEqualTo(0);
    }
}
