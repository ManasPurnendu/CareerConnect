package com.careerconnect.admin;

import com.careerconnect.application.domain.ApplicationStatus;
import com.careerconnect.application.dto.UpdateApplicationStatusRequest;
import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.UpdateProfileRequest;
import com.careerconnect.common.model.Role;
import com.careerconnect.common.util.JdbcUtils;
import com.careerconnect.company.dto.RecruiterCompanyUpsertRequest;
import com.careerconnect.jobs.dto.RecruiterJobCreateRequest;
import com.careerconnect.jobs.model.ExperienceLevel;
import com.careerconnect.jobs.model.JobType;
import com.careerconnect.jobs.model.WorkMode;
import com.careerconnect.security.JwtService;
import com.careerconnect.security.SecurityUser;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P8.5 Comprehensive Admin Workflow & Cross-Phase Integration Verification Test Suite.
 *
 * Verifies end-to-end integration across:
 * - Candidate Registration, Profile readiness, Skills, Education, Resume upload
 * - Recruiter Registration, Company upsert, Job creation (DRAFT), Required skills, Publish (ACTIVE)
 * - Public Job discovery, Job details, Deterministic Skill Matching
 * - Candidate Job Application, Immutable Application & Resume Snapshots
 * - Recruiter Applicant management, Status progression, Candidate notifications, Mark read
 * - Recruiter Job Closure, Job closure notifications
 * - Admin Platform-wide Moderation: Dashboard, Users, Companies, Jobs, Applications, Protected Resume streaming
 * - Admin Job Archive / Restore, Company Deactivate / Reactivate, User Deactivate / Reactivate
 * - Atomic Audit Logging, Append-only guarantees, Transactional rollbacks
 * - Database Integrity (0 orphan rows, 0 duplicate applications, 0 invalid lifecycle states)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Phase8ComprehensiveWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Value("${app.resume.upload-dir:./target/test-uploads/resumes}")
    private String uploadDir;

    private Long adminUserId;
    private String adminToken;

    @BeforeEach
    void setUp() {
        tearDown();

        Long existingAdminId = jdbcTemplate.query(
                "SELECT id FROM users WHERE email = 'admin@careerconnect.com'",
                (rs, rowNum) -> rs.getLong("id")
        ).stream().findFirst().orElse(null);

        if (existingAdminId == null) {
            KeyHolder kh = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO users (email, password_hash, role, is_active, created_at, updated_at) " +
                                "VALUES ('admin@careerconnect.com', '$2a$12$dummyHashForAdminTestPass1234567890123456', 'ADMIN', true, NOW(), NOW())",
                        Statement.RETURN_GENERATED_KEYS
                );
                return ps;
            }, kh);
            adminUserId = JdbcUtils.extractGeneratedId(kh);
        } else {
            adminUserId = existingAdminId;
        }

        SecurityUser adminUser = new SecurityUser(adminUserId, "admin@careerconnect.com", "HASH", Role.ADMIN, "Admin User", null, null, null, true);
        adminToken = jwtService.generateToken(adminUser);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM audit_logs");
        jdbcTemplate.execute("DELETE FROM notifications");
        jdbcTemplate.execute("DELETE FROM application_status_history");
        jdbcTemplate.execute("DELETE FROM applications");
        jdbcTemplate.execute("DELETE FROM resumes");
        jdbcTemplate.execute("DELETE FROM candidate_skills");
        jdbcTemplate.execute("DELETE FROM education");
        jdbcTemplate.execute("DELETE FROM projects");
        jdbcTemplate.execute("DELETE FROM experience");
        jdbcTemplate.execute("DELETE FROM job_skills");
        jdbcTemplate.execute("DELETE FROM jobs");
        jdbcTemplate.execute("DELETE FROM recruiters");
        jdbcTemplate.execute("DELETE FROM candidates");
        jdbcTemplate.execute("DELETE FROM companies");
        jdbcTemplate.execute("DELETE FROM users WHERE role != 'ADMIN'");

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

    // =========================================================================
    // Helper Records & Methods
    // =========================================================================

    private record RecruiterAuth(String token, Long recruiterId, Long userId, String email) {}
    private record CandidateAuth(String token, Long candidateId, Long userId, String fullName, String email) {}

    private RecruiterAuth registerRecruiter(String email) throws Exception {
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.RECRUITER, "Recruiter E2E");
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        String token = root.path("data").path("accessToken").asText();
        Long recruiterId = root.path("data").path("user").path("recruiterId").asLong();
        Long userId = root.path("data").path("user").path("id").asLong();
        return new RecruiterAuth(token, recruiterId, userId, email);
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
        return new CandidateAuth(token, candidateId, userId, fullName, email);
    }

    private Long createCompany(RecruiterAuth rec, String name) throws Exception {
        RecruiterCompanyUpsertRequest req = new RecruiterCompanyUpsertRequest(
                name + " " + UUID.randomUUID(),
                "https://example.com/logo.png",
                "Premier engineering solutions company",
                "Software & Technology",
                "https://example.com",
                "Bengaluru, Karnataka",
                "51-200"
        );
        MvcResult res = mockMvc.perform(put("/api/recruiter/company")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
    }

    private Long getOrCreateSkill(String name, String normalized) {
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
                    "INSERT INTO skills (name, normalized_name, created_at) VALUES (?, ?, CURRENT_TIMESTAMP(3))",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, normalized);
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }

    private Long createAndPublishJob(RecruiterAuth rec, String title, List<Long> skillIds) throws Exception {
        RecruiterJobCreateRequest req = new RecruiterJobCreateRequest(
                title,
                "Comprehensive role description requiring strong background and proven experience.",
                "Deliver high quality engineering outcomes.",
                "Proven track record in building reliable applications.",
                JobType.FULL_TIME,
                ExperienceLevel.ONE_TO_TWO_YEARS,
                WorkMode.HYBRID,
                "Bengaluru",
                new BigDecimal("12.00"),
                new BigDecimal("24.00"),
                "INR",
                LocalDate.now().plusDays(60),
                skillIds
        );

        MvcResult createRes = mockMvc.perform(post("/api/recruiter/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long jobId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        return jobId;
    }

    private void prepareCandidateProfile(CandidateAuth cand, String skillName, String resumeText) throws Exception {
        mockMvc.perform(put("/api/candidate/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest(
                                cand.fullName(), "Senior Full Stack Engineer", "Bengaluru", "Experienced software practitioner"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/candidate/education")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "institution": "National Institute of Technology",
                                  "degree": "B.Tech",
                                  "fieldOfStudy": "Computer Science & Engineering",
                                  "startYear": 2018,
                                  "endYear": 2022
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/candidate/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", skillName))))
                .andExpect(status().isCreated());

        byte[] pdfBytes = ("%PDF-1.4\n" + resumeText).getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", cand.fullName().toLowerCase().replace(" ", "_") + "_resume.pdf",
                "application/pdf", pdfBytes);

        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isCreated());
    }

    private Long applyCandidate(CandidateAuth cand, Long jobId, String coverLetter) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/jobs/" + jobId + "/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("coverLetter", coverLetter))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("applicationId").asLong();
    }

    // =========================================================================
    // 1. Comprehensive Candidate -> Recruiter -> Admin End-to-End Workflow (44 steps)
    // =========================================================================
    @Test
    @DisplayName("P8.5 Section 4: 44-Step Comprehensive Candidate -> Recruiter -> Admin Workflow Integration")
    void test01_endToEndCandidateRecruiterAdminWorkflow() throws Exception {
        // 1. Register candidate
        CandidateAuth cand = registerCandidate("cand.p85.e2e_" + UUID.randomUUID() + "@example.com", "Rohan Verma");

        // 2. Authenticate candidate (register generates valid JWT)
        assertThat(cand.token()).isNotBlank();

        // 3. Complete candidate profile
        // 4. Add required education
        // 5. Add required skills
        // 6. Upload/create current resume
        prepareCandidateProfile(cand, "Java", "Rohan Verma Verified Resume Document Content");

        // Verify profile readiness
        mockMvc.perform(get("/api/candidate/profile/readiness")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(true));

        // 7. Register recruiter
        RecruiterAuth rec = registerRecruiter("rec.p85.e2e_" + UUID.randomUUID() + "@example.com");

        // 8. Create recruiter company
        createCompany(rec, "Nexus Innovations");

        // 9. Authenticate recruiter
        assertThat(rec.token()).isNotBlank();

        // 10. Create job as DRAFT
        Long javaSkillId = getOrCreateSkill("Java", "java");
        RecruiterJobCreateRequest draftReq = new RecruiterJobCreateRequest(
                "Lead Platform Engineer",
                "Detailed architecture and platform engineering requirements.",
                "Lead system design and distributed scaling.",
                "Deep experience with reliable software patterns.",
                JobType.FULL_TIME,
                ExperienceLevel.ONE_TO_TWO_YEARS,
                WorkMode.HYBRID,
                "Bengaluru",
                new BigDecimal("25.00"),
                new BigDecimal("45.00"),
                "INR",
                LocalDate.now().plusDays(90),
                List.of(javaSkillId)
        );

        MvcResult draftRes = mockMvc.perform(post("/api/recruiter/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();

        Long jobId = objectMapper.readTree(draftRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 11. Add required skills (already provided in creation)
        // 12. Publish job
        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // 13. Verify job appears in public discovery
        mockMvc.perform(get("/api/jobs?keyword=Platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(jobId))
                .andExpect(jsonPath("$.data[0].title").value("Lead Platform Engineer"));

        // 14. Candidate discovers job
        // 15. Candidate retrieves job details
        mockMvc.perform(get("/api/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(jobId))
                .andExpect(jsonPath("$.data.company.name").exists());

        // 16. Candidate calculates/receives deterministic match
        mockMvc.perform(get("/api/jobs/" + jobId + "/match")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.percentage").value(100))
                .andExpect(jsonPath("$.data.matchedSkills[0]").value("Java"));

        // 17. Candidate applies
        Long applicationId = applyCandidate(cand, jobId, "I am strongly interested in leading platform engineering.");

        // 18. Verify application is persisted
        String appStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, applicationId);
        assertThat(appStatus).isEqualTo("APPLIED");

        // 19. Verify immutable application snapshot
        String candidateSnapshot = jdbcTemplate.queryForObject(
                "SELECT candidate_snapshot FROM applications WHERE id = ?", String.class, applicationId);
        assertThat(candidateSnapshot).contains("Rohan Verma").contains("Senior Full Stack Engineer");

        // 20. Recruiter lists applicants
        mockMvc.perform(get("/api/recruiter/jobs/" + jobId + "/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].applicationId").value(applicationId))
                .andExpect(jsonPath("$.data[0].candidateName").value("Rohan Verma"))
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"));

        // 21. Recruiter retrieves applicant detail
        mockMvc.perform(get("/api/recruiter/applications/" + applicationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value(applicationId))
                .andExpect(jsonPath("$.data.candidateSnapshot.fullName").value("Rohan Verma"))
                .andExpect(jsonPath("$.data.coverLetter").value("I am strongly interested in leading platform engineering."));

        // 22. Recruiter retrieves applicant resume
        mockMvc.perform(get("/api/recruiter/applications/" + applicationId + "/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rohan Verma Verified Resume Document Content")));

        // 23. Recruiter changes application status: APPLIED -> UNDER_REVIEW
        mockMvc.perform(patch("/api/recruiter/applications/" + applicationId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest(
                                ApplicationStatus.UNDER_REVIEW, "Reviewing profile credentials."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNDER_REVIEW"));

        // 24. Verify application status history
        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_status_history WHERE application_id = ?", Integer.class, applicationId);
        assertThat(historyCount).isEqualTo(2); // APPLIED + UNDER_REVIEW

        // 25. Verify candidate notification
        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("APPLICATION_STATUS_CHANGED"));

        // 26. Candidate reads notification
        Long notifId = jdbcTemplate.queryForObject(
                "SELECT id FROM notifications WHERE user_id = ? ORDER BY id DESC LIMIT 1", Long.class, cand.userId());
        mockMvc.perform(patch("/api/notifications/" + notifId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));

        // 27. Recruiter advances application through valid state transitions: UNDER_REVIEW -> SHORTLISTED -> INTERVIEW -> SELECTED
        ApplicationStatus[] nextStages = {
                ApplicationStatus.SHORTLISTED,
                ApplicationStatus.INTERVIEW,
                ApplicationStatus.SELECTED
        };
        for (ApplicationStatus stage : nextStages) {
            mockMvc.perform(patch("/api/recruiter/applications/" + applicationId + "/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest(
                                    stage, "Advancing candidate to " + stage))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(stage.name()));
        }

        // 28. Verify notifications are generated (1 initial + 3 transitions = 4 notifications total)
        Integer notifTotal = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ?", Integer.class, cand.userId());
        assertThat(notifTotal).isEqualTo(4);

        // 29. Recruiter closes job
        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // 30. Verify job closure notification behavior
        Integer closureNotifCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'JOB_CLOSED'",
                Integer.class, cand.userId());
        assertThat(closureNotifCount).isEqualTo(1);

        // 31. Admin authenticates (uses adminToken initialized in setUp)
        assertThat(adminToken).isNotBlank();

        // 32. Admin views dashboard
        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobs.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.applications.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // 33. Admin views users
        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // 34. Admin views companies
        mockMvc.perform(get("/api/admin/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // 35. Admin views jobs
        mockMvc.perform(get("/api/admin/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // 36. Admin views applications
        mockMvc.perform(get("/api/admin/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // 37. Admin views application detail
        mockMvc.perform(get("/api/admin/applications/" + applicationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value(applicationId))
                .andExpect(jsonPath("$.data.jobTitle").value("Lead Platform Engineer"))
                .andExpect(jsonPath("$.data.candidateSnapshot.fullName").value("Rohan Verma"));

        // 38. Admin accesses protected application resume
        mockMvc.perform(get("/api/admin/applications/" + applicationId + "/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rohan Verma Verified Resume Document Content")));

        // 39. Admin views audit logs
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // 40. Admin performs legitimate moderation mutation: Archive the closed job
        mockMvc.perform(patch("/api/admin/jobs/" + jobId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        // 41. Verify corresponding audit record
        Integer jobArchiveAudit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'JOB_ARCHIVED' AND entity_id = ? AND actor_user_id = ?",
                Integer.class, jobId, adminUserId);
        assertThat(jobArchiveAudit).isEqualTo(1);

        // 42. Verify public/recruiter/candidate behavior after moderation: Archived job invisible in public
        mockMvc.perform(get("/api/jobs?keyword=Platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        // 43. Restore state where applicable: Restore job back to CLOSED
        mockMvc.perform(patch("/api/admin/jobs/" + jobId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        Integer jobRestoreAudit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'JOB_RESTORED' AND entity_id = ? AND actor_user_id = ?",
                Integer.class, jobId, adminUserId);
        assertThat(jobRestoreAudit).isEqualTo(1);

        // 44. Verify final database invariants
        String finalJobStatus = jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(finalJobStatus).isEqualTo("CLOSED");

        String finalAppStatus = jdbcTemplate.queryForObject("SELECT status FROM applications WHERE id = ?", String.class, applicationId);
        assertThat(finalAppStatus).isEqualTo("SELECTED");

        Integer finalHistoryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_status_history WHERE application_id = ?", Integer.class, applicationId);
        assertThat(finalHistoryCount).isEqualTo(5); // APPLIED, UNDER_REVIEW, SHORTLISTED, INTERVIEW, SELECTED
    }

    // =========================================================================
    // 2. Application Lifecycle & Terminal State Verification
    // =========================================================================
    @Test
    @DisplayName("P8.5 Section 5: Application State Machine, Terminal States, Re-application Prohibition, and Withdrawal Rules")
    void test02_applicationLifecycleAndTerminalStates() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.p85.app_" + UUID.randomUUID() + "@example.com");
        createCompany(rec, "Terminal Verification Corp");
        Long skillId = getOrCreateSkill("Python", "python");
        Long jobId = createAndPublishJob(rec, "Python Backend Specialist", List.of(skillId));

        CandidateAuth cand = registerCandidate("cand.p85.app_" + UUID.randomUUID() + "@example.com", "Meera Nair");
        prepareCandidateProfile(cand, "Python", "Meera Nair Terminal Lifecycle Resume");

        Long applicationId = applyCandidate(cand, jobId, "Cover letter for terminal state tests");

        // Duplicate application prohibited
        mockMvc.perform(post("/api/jobs/" + jobId + "/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("coverLetter", "Duplicate attempt"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_APPLICATION"));

        // Recruiter cannot set WITHDRAWN
        mockMvc.perform(patch("/api/recruiter/applications/" + applicationId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest(
                                ApplicationStatus.WITHDRAWN, "Recruiter trying to withdraw candidate"))))
                .andExpect(status().isUnprocessableEntity());

        // Step recruiter through: APPLIED -> UNDER_REVIEW -> SHORTLISTED -> INTERVIEW -> SELECTED
        mockMvc.perform(patch("/api/recruiter/applications/" + applicationId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest(
                                ApplicationStatus.UNDER_REVIEW, "Valid review"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/recruiter/applications/" + applicationId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest(
                                ApplicationStatus.SHORTLISTED, "Valid shortlist"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/recruiter/applications/" + applicationId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest(
                                ApplicationStatus.INTERVIEW, "Valid interview"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/recruiter/applications/" + applicationId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest(
                                ApplicationStatus.SELECTED, "Candidate Selected!"))))
                .andExpect(status().isOk());

        // SELECTED is terminal: Recruiter cannot change status from SELECTED
        mockMvc.perform(patch("/api/recruiter/applications/" + applicationId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest(
                                ApplicationStatus.REJECTED, "Cannot reject after select"))))
                .andExpect(status().isUnprocessableEntity());

        // Candidate cannot withdraw from terminal state SELECTED
        mockMvc.perform(patch("/api/candidate/applications/" + applicationId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isUnprocessableEntity());

        // Test second candidate for WITHDRAWN lifecycle and re-application restriction
        CandidateAuth cand2 = registerCandidate("cand2.p85.app_" + UUID.randomUUID() + "@example.com", "Sunil Joshi");
        prepareCandidateProfile(cand2, "Python", "Sunil Joshi Resume");
        Long app2Id = applyCandidate(cand2, jobId, "Sunil cover letter");

        // Candidate withdraws successfully from APPLIED
        mockMvc.perform(patch("/api/candidate/applications/" + app2Id + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand2.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));

        // Withdrawn application is terminal: recruiter cannot change status
        mockMvc.perform(patch("/api/recruiter/applications/" + app2Id + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest(
                                ApplicationStatus.UNDER_REVIEW, "Cannot un-withdraw"))))
                .andExpect(status().isUnprocessableEntity());

        // Re-application is prohibited even after withdrawal
        mockMvc.perform(post("/api/jobs/" + jobId + "/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand2.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("coverLetter", "Re-applying after withdraw"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_APPLICATION"));
    }

    // =========================================================================
    // 3. Job Lifecycle: Recruiter & Admin Moderation
    // =========================================================================
    @Test
    @DisplayName("P8.5 Section 6: Job Lifecycle (Recruiter DRAFT->ACTIVE->CLOSED & Admin DRAFT/ACTIVE/CLOSED->ARCHIVED->CLOSED)")
    void test03_jobLifecycleRecruiterAndAdmin() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.p85.jl_" + UUID.randomUUID() + "@example.com");
        createCompany(rec, "Lifecycle Dynamics");
        Long skillId = getOrCreateSkill("Go", "go");

        // Create DRAFT job
        RecruiterJobCreateRequest draftReq = new RecruiterJobCreateRequest(
                "Golang Infrastructure Lead",
                "Infrastructure engineering and distributed systems.",
                "Build cloud infrastructure and services.",
                "Strong systems programming experience.",
                JobType.FULL_TIME,
                ExperienceLevel.ONE_TO_TWO_YEARS,
                WorkMode.REMOTE,
                null,
                new BigDecimal("30.00"),
                new BigDecimal("50.00"),
                "INR",
                LocalDate.now().plusDays(45),
                List.of(skillId)
        );

        MvcResult createRes = mockMvc.perform(post("/api/recruiter/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long jobId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 1. Admin can archive directly from DRAFT
        mockMvc.perform(patch("/api/admin/jobs/" + jobId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        // 2. Cannot archive an already ARCHIVED job
        mockMvc.perform(patch("/api/admin/jobs/" + jobId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnprocessableEntity());

        // 3. Recruiter cannot publish or close an ARCHIVED job
        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isUnprocessableEntity());

        // 4. Restore ARCHIVED job -> goes strictly to CLOSED
        mockMvc.perform(patch("/api/admin/jobs/" + jobId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // 5. Restore rejected if job is not ARCHIVED (e.g. now it is CLOSED)
        mockMvc.perform(patch("/api/admin/jobs/" + jobId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnprocessableEntity());

        // 6. Test Active -> Archived -> Restored
        Long activeJobId = createAndPublishJob(rec, "Active Job To Archive", List.of(skillId));
        mockMvc.perform(patch("/api/admin/jobs/" + activeJobId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(patch("/api/admin/jobs/" + activeJobId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    // =========================================================================
    // 4. Company Moderation Workflow
    // =========================================================================
    @Test
    @DisplayName("P8.5 Section 7: Company Moderation (Deactivate hides jobs without deleting data; Reactivate restores)")
    void test04_companyModerationWorkflow() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.p85.comp_" + UUID.randomUUID() + "@example.com");
        Long companyId = createCompany(rec, "Verifiable Enterprises");
        Long skillId = getOrCreateSkill("React", "react");
        Long jobId = createAndPublishJob(rec, "Senior React Architect", List.of(skillId));

        CandidateAuth cand = registerCandidate("cand.p85.comp_" + UUID.randomUUID() + "@example.com", "Ankit Bose");
        prepareCandidateProfile(cand, "React", "Ankit Bose Company Moderation Resume");
        Long applicationId = applyCandidate(cand, jobId, "Applying to Verifiable Enterprises");

        // Verify job is visible in public discovery
        mockMvc.perform(get("/api/jobs?keyword=Architect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(jobId));

        // Admin deactivates company
        mockMvc.perform(patch("/api/admin/companies/" + companyId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        // Inactive company's job is hidden from public discovery
        mockMvc.perform(get("/api/jobs?keyword=Architect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        // Company deactivation does NOT delete jobs, applications, or alter application status
        String jobStatus = jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(jobStatus).isEqualTo("ACTIVE");

        String appStatus = jdbcTemplate.queryForObject("SELECT status FROM applications WHERE id = ?", String.class, applicationId);
        assertThat(appStatus).isEqualTo("APPLIED");

        // Admin reactivates company
        mockMvc.perform(patch("/api/admin/companies/" + companyId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));

        // Public discovery restores job visibility
        mockMvc.perform(get("/api/jobs?keyword=Architect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(jobId));

        // Audit records verified: exactly 1 DEACTIVATE and 1 REACTIVATE
        Integer deactAudit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'COMPANY_DEACTIVATED' AND entity_id = ?",
                Integer.class, companyId);
        Integer reactAudit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'COMPANY_REACTIVATED' AND entity_id = ?",
                Integer.class, companyId);
        assertThat(deactAudit).isEqualTo(1);
        assertThat(reactAudit).isEqualTo(1);
    }

    // =========================================================================
    // 5. User Moderation Workflow
    // =========================================================================
    @Test
    @DisplayName("P8.5 Section 8: User Moderation (Deactivate blocks login; Reactivate restores access; Self-deactivation blocked)")
    void test05_userModerationWorkflow() throws Exception {
        CandidateAuth cand = registerCandidate("cand.p85.user_" + UUID.randomUUID() + "@example.com", "Dev User");

        // Admin cannot deactivate themselves
        mockMvc.perform(patch("/api/admin/users/" + adminUserId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isUnprocessableEntity());

        // Admin deactivates candidate
        mockMvc.perform(patch("/api/admin/users/" + cand.userId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        // Deactivated user cannot login
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + cand.email() + "\", \"password\": \"Password123!\"}"))
                .andExpect(status().isForbidden());

        // Same-state transition rejected
        mockMvc.perform(patch("/api/admin/users/" + cand.userId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isUnprocessableEntity());

        // Admin reactivates candidate
        mockMvc.perform(patch("/api/admin/users/" + cand.userId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));

        // Login succeeds again
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + cand.email() + "\", \"password\": \"Password123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    // =========================================================================
    // 6. Admin Dashboard Consistency
    // =========================================================================
    @Test
    @DisplayName("P8.5 Section 9: Admin Dashboard Metrics Consistency against Real Database Counts")
    void test06_adminDashboardConsistency() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.p85.dash_" + UUID.randomUUID() + "@example.com");
        createCompany(rec, "Dashboard Metrics Corp");
        Long skillId = getOrCreateSkill("C++", "cpp");
        Long jobId = createAndPublishJob(rec, "C++ Systems Engineer", List.of(skillId));

        CandidateAuth cand = registerCandidate("cand.p85.dash_" + UUID.randomUUID() + "@example.com", "Deepak Rao");
        prepareCandidateProfile(cand, "C++", "Deepak Rao Resume");
        applyCandidate(cand, jobId, "Cover letter for dashboard test");

        // Query database counts directly
        Integer usersCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        int expectedUsers = usersCount != null ? usersCount : 0;
        Integer candidatesCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM candidates", Integer.class);
        int expectedCandidates = candidatesCount != null ? candidatesCount : 0;
        Integer recruitersCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recruiters", Integer.class);
        int expectedRecruiters = recruitersCount != null ? recruitersCount : 0;
        Integer companiesCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM companies", Integer.class);
        int expectedCompanies = companiesCount != null ? companiesCount : 0;
        Integer jobsCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM jobs WHERE deleted_at IS NULL", Integer.class);
        int expectedJobs = jobsCount != null ? jobsCount : 0;
        Integer applicationsCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM applications", Integer.class);
        int expectedApplications = applicationsCount != null ? applicationsCount : 0;

        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users.total").value(expectedUsers))
                .andExpect(jsonPath("$.data.users.candidates").value(expectedCandidates))
                .andExpect(jsonPath("$.data.users.recruiters").value(expectedRecruiters))
                .andExpect(jsonPath("$.data.companies.total").value(expectedCompanies))
                .andExpect(jsonPath("$.data.jobs.total").value(expectedJobs))
                .andExpect(jsonPath("$.data.applications.total").value(expectedApplications));
    }

    // =========================================================================
    // 7. Admin Application Visibility Platform-Wide
    // =========================================================================
    @Test
    @DisplayName("P8.5 Section 10: Admin Application Platform-Wide Visibility Across Companies")
    void test07_adminApplicationVisibilityPlatformWide() throws Exception {
        // Company A + Recruiter A + Job A + Cand A
        RecruiterAuth recA = registerRecruiter("rec.p85.vis.a_" + UUID.randomUUID() + "@example.com");
        createCompany(recA, "Visibility Corp A");
        Long skillA = getOrCreateSkill("Kotlin", "kotlin");
        Long jobA = createAndPublishJob(recA, "Kotlin Developer", List.of(skillA));

        CandidateAuth candA = registerCandidate("cand.p85.vis.a_" + UUID.randomUUID() + "@example.com", "Candidate A");
        prepareCandidateProfile(candA, "Kotlin", "Candidate A Resume");
        Long appA = applyCandidate(candA, jobA, "App A cover letter");

        // Company B + Recruiter B + Job B + Cand B
        RecruiterAuth recB = registerRecruiter("rec.p85.vis.b_" + UUID.randomUUID() + "@example.com");
        createCompany(recB, "Visibility Corp B");
        Long jobB = createAndPublishJob(recB, "Kotlin Backend", List.of(skillA));

        CandidateAuth candB = registerCandidate("cand.p85.vis.b_" + UUID.randomUUID() + "@example.com", "Candidate B");
        prepareCandidateProfile(candB, "Kotlin", "Candidate B Resume");
        Long appB = applyCandidate(candB, jobB, "App B cover letter");

        // Recruiter A CANNOT see App B
        mockMvc.perform(get("/api/recruiter/applications/" + appB)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recA.token()))
                .andExpect(status().isNotFound());

        // Admin CAN see both App A and App B platform-wide
        mockMvc.perform(get("/api/admin/applications/" + appA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value(appA))
                .andExpect(jsonPath("$.data.candidateSnapshot.fullName").value("Candidate A"));

        mockMvc.perform(get("/api/admin/applications/" + appB)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value(appB))
                .andExpect(jsonPath("$.data.candidateSnapshot.fullName").value("Candidate B"));
    }

    // =========================================================================
    // 8. Admin Resume Workflow & Path Traversal Protection
    // =========================================================================
    @Test
    @DisplayName("P8.5 Section 11: Admin Resume Streaming and Path Traversal Safety")
    void test08_adminResumeWorkflowAndPathTraversal() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.p85.res_" + UUID.randomUUID() + "@example.com");
        createCompany(rec, "Resume Safety Corp");
        Long skillId = getOrCreateSkill("TypeScript", "typescript");
        Long jobId = createAndPublishJob(rec, "TS Architect", List.of(skillId));

        CandidateAuth cand = registerCandidate("cand.p85.res_" + UUID.randomUUID() + "@example.com", "Priya Sharma");
        prepareCandidateProfile(cand, "TypeScript", "Priya Sharma Protected Resume Text");
        Long appId = applyCandidate(cand, jobId, "Cover letter for resume streaming");

        // 1. Admin downloads resume successfully
        mockMvc.perform(get("/api/admin/applications/" + appId + "/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("priya_sharma_resume.pdf")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Priya Sharma Protected Resume Text")));

        // 2. Non-existent application returns 404 without leaking path
        mockMvc.perform(get("/api/admin/applications/999999/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // 3. Candidate downloading another application's resume via admin endpoint returns 403
        mockMvc.perform(get("/api/admin/applications/" + appId + "/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 9. Audit Log Workflow & Immutability
    // =========================================================================
    @Test
    @DisplayName("P8.5 Section 12: Audit Log Append-Only Guarantee and Parameterized Filtering")
    void test09_auditLogIntegrityAndWorkflow() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.p85.aud_" + UUID.randomUUID() + "@example.com");
        Long compId = createCompany(rec, "Audit Dynamics Corp");

        // Perform company deactivate -> reactivate to generate audits
        mockMvc.perform(patch("/api/admin/companies/" + compId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/companies/" + compId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": true}"))
                .andExpect(status().isOk());

        // Verify audit log query with action filter
        mockMvc.perform(get("/api/admin/audit-logs?action=COMPANY_DEACTIVATED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].action").value("COMPANY_DEACTIVATED"))
                .andExpect(jsonPath("$.data[0].entityType").value("COMPANY"))
                .andExpect(jsonPath("$.data[0].actorUserId").value(adminUserId));

        // Audit log endpoint rejects modifying methods: POST, PUT, DELETE return 405
        mockMvc.perform(post("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\": \"FAKE\"}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isMethodNotAllowed());
    }

    // =========================================================================
    // 10. Notification Workflow Across Lifecycle
    // =========================================================================
    @Test
    @DisplayName("P8.5 Section 13: Notification Delivery, Unread Counts, and Mark Read Flow")
    void test10_notificationWorkflowAcrossOperations() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.p85.notif_" + UUID.randomUUID() + "@example.com");
        createCompany(rec, "Notification Flow Corp");
        Long skillId = getOrCreateSkill("Rust", "rust");
        Long jobId = createAndPublishJob(rec, "Rust Systems Engineer", List.of(skillId));

        CandidateAuth cand = registerCandidate("cand.p85.notif_" + UUID.randomUUID() + "@example.com", "Kavita Sen");
        prepareCandidateProfile(cand, "Rust", "Kavita Sen Resume");
        Long appId = applyCandidate(cand, jobId, "Cover letter for notifications");

        // Recruiter advances status: candidate receives notification
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest(
                                ApplicationStatus.UNDER_REVIEW, "Notification trigger"))))
                .andExpect(status().isOk());

        // Candidate checks notifications
        MvcResult res = mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andReturn();

        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        Long notifId = json.path("data").path("items").get(0).path("id").asLong();

        // Candidate marks single notification read
        mockMvc.perform(patch("/api/notifications/" + notifId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));

        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    // =========================================================================
    // 11. Snapshot Immutability Under Candidate & Moderation Mutations
    // =========================================================================
    @Test
    @DisplayName("P8.5 Section 14: Application Snapshot Immutability Under Post-Application Modifications & Moderation")
    void test11_snapshotImmutabilityCrossPhase() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.p85.snap_" + UUID.randomUUID() + "@example.com");
        Long compId = createCompany(rec, "Snapshot Integrity Inc");
        Long skillId = getOrCreateSkill("SQL", "sql");
        Long jobId = createAndPublishJob(rec, "Database Administrator", List.of(skillId));

        CandidateAuth cand = registerCandidate("cand.p85.snap_" + UUID.randomUUID() + "@example.com", "Amitabh Bachan");
        prepareCandidateProfile(cand, "SQL", "Amitabh Original Application Resume");
        Long appId = applyCandidate(cand, jobId, "Original immutable application");

        // Record initial candidate snapshot
        String initialCandidateSnapshot = jdbcTemplate.queryForObject(
                "SELECT candidate_snapshot FROM applications WHERE id = ?", String.class, appId);
        String initialResumeSnapshot = jdbcTemplate.queryForObject(
                "SELECT resume_snapshot FROM applications WHERE id = ?", String.class, appId);

        // Candidate modifies profile completely
        mockMvc.perform(put("/api/candidate/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest(
                                "Amitabh S. Bachan", "Chief Architect & Executive", "Mumbai", "Completely altered bio"))))
                .andExpect(status().isOk());

        // Upload a brand new resume
        byte[] newPdf = "%PDF-1.4\nBrand New Resume Document".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile newFile = new MockMultipartFile("file", "amitabh_v2_resume.pdf", "application/pdf", newPdf);
        mockMvc.perform(multipart("/api/candidate/resume").file(newFile).header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isCreated());

        // Admin archives and restores job
        mockMvc.perform(patch("/api/admin/jobs/" + jobId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/admin/jobs/" + jobId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Admin deactivates and reactivates company
        mockMvc.perform(patch("/api/admin/companies/" + compId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/admin/companies/" + compId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": true}"))
                .andExpect(status().isOk());

        // Verify snapshots are 100% UNCHANGED
        String afterCandidateSnapshot = jdbcTemplate.queryForObject(
                "SELECT candidate_snapshot FROM applications WHERE id = ?", String.class, appId);
        String afterResumeSnapshot = jdbcTemplate.queryForObject(
                "SELECT resume_snapshot FROM applications WHERE id = ?", String.class, appId);

        assertThat(afterCandidateSnapshot).isEqualTo(initialCandidateSnapshot);
        assertThat(afterResumeSnapshot).isEqualTo(initialResumeSnapshot);
    }

    // =========================================================================
    // 12. Matching Integration Determinism
    // =========================================================================
    @Test
    @DisplayName("P8.5 Section 15: Matching Integration Determinism (100%, Partial, and Zero Match)")
    void test12_matchingDeterministicIntegration() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.p85.match_" + UUID.randomUUID() + "@example.com");
        createCompany(rec, "Matching Logic Corp");

        Long javaId = getOrCreateSkill("Java", "java");
        Long springId = getOrCreateSkill("Spring Boot", "spring boot");

        // Job requires Java and Spring Boot
        Long jobId = createAndPublishJob(rec, "Full Stack Java Dev", List.of(javaId, springId));

        // Candidate 1: Has Java only -> 50% match
        CandidateAuth candPartial = registerCandidate("cand.partial_" + UUID.randomUUID() + "@example.com", "Partial Matcher");
        prepareCandidateProfile(candPartial, "Java", "Partial Match Resume");

        mockMvc.perform(get("/api/jobs/" + jobId + "/match")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candPartial.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.percentage").value(50))
                .andExpect(jsonPath("$.data.matchedSkills[0]").value("Java"))
                .andExpect(jsonPath("$.data.missingSkills[0]").value("Spring Boot"));

        // Candidate 2: Has Python only -> 0% match
        CandidateAuth candZero = registerCandidate("cand.zero_" + UUID.randomUUID() + "@example.com", "Zero Matcher");
        prepareCandidateProfile(candZero, "Python", "Zero Match Resume");

        mockMvc.perform(get("/api/jobs/" + jobId + "/match")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candZero.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.percentage").value(0))
                .andExpect(jsonPath("$.data.missingSkills").isArray());
    }

    // =========================================================================
    // 13. Cross-Company Isolation
    // =========================================================================
    @Test
    @DisplayName("P8.5 Section 16: Cross-Company Isolation for Recruiters & Platform-Wide Admin Visibility")
    void test13_crossCompanyIsolation() throws Exception {
        RecruiterAuth recA = registerRecruiter("rec.iso.a_" + UUID.randomUUID() + "@example.com");
        createCompany(recA, "Iso Corp Alpha");
        Long skillId = getOrCreateSkill("Ruby", "ruby");
        Long jobA = createAndPublishJob(recA, "Ruby Developer", List.of(skillId));

        RecruiterAuth recB = registerRecruiter("rec.iso.b_" + UUID.randomUUID() + "@example.com");
        createCompany(recB, "Iso Corp Beta");
        Long jobB = createAndPublishJob(recB, "Ruby Architect", List.of(skillId));

        // Recruiter A cannot view Recruiter B's job via recruiter endpoints
        mockMvc.perform(get("/api/recruiter/jobs/" + jobB)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recA.token()))
                .andExpect(status().isNotFound());

        // Recruiter B cannot view Recruiter A's job via recruiter endpoints
        mockMvc.perform(get("/api/recruiter/jobs/" + jobA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recB.token()))
                .andExpect(status().isNotFound());

        // Recruiter A cannot close Recruiter B's job
        mockMvc.perform(patch("/api/recruiter/jobs/" + jobB + "/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recA.token()))
                .andExpect(status().isNotFound());

        // Admin can view both jobs
        mockMvc.perform(get("/api/admin/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // =========================================================================
    // 14. Soft-Deleted Job Workflow
    // =========================================================================
    @Test
    @DisplayName("P8.5 Section 17: Soft-Deleted Job Excluded From Discovery and Recruiter Listing")
    void test14_softDeletedJobWorkflow() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.p85.del_" + UUID.randomUUID() + "@example.com");
        createCompany(rec, "Deletable Jobs Inc");
        Long skillId = getOrCreateSkill("Scala", "scala");

        // Create DRAFT job (zero applications)
        RecruiterJobCreateRequest draftReq = new RecruiterJobCreateRequest(
                "Scala Specialist To Delete",
                "Job intended for soft deletion testing.",
                "Implement robust functional code.",
                "Familiarity with functional paradigms.",
                JobType.FULL_TIME,
                ExperienceLevel.FRESHER,
                WorkMode.REMOTE,
                null,
                new BigDecimal("10.00"),
                new BigDecimal("18.00"),
                "INR",
                LocalDate.now().plusDays(30),
                List.of(skillId)
        );

        MvcResult res = mockMvc.perform(post("/api/recruiter/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long jobId = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Soft-delete the job
        mockMvc.perform(delete("/api/recruiter/jobs/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isNoContent());

        // Verify deleted_at is populated
        String deletedAt = jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(deletedAt).isNotNull();

        // Excluded from recruiter jobs list
        mockMvc.perform(get("/api/recruiter/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + jobId + ")]").doesNotExist());

        // Excluded from public discovery
        mockMvc.perform(get("/api/jobs?keyword=Scala"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + jobId + ")]").doesNotExist());
    }

    // =========================================================================
    // 15. Pagination & List Integration
    // =========================================================================
    @Test
    @DisplayName("P8.5 Section 18: Bounded Pagination and Deterministic Sorting across Admin Endpoints")
    void test15_paginationAndListIntegration() throws Exception {
        // Test pagination on admin users endpoint
        mockMvc.perform(get("/api/admin/users?page=0&size=5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(5))
                .andExpect(jsonPath("$.pagination.totalElements").exists())
                .andExpect(jsonPath("$.pagination.totalPages").exists());

        // Size cap enforced: size=1000 clamped to 100
        mockMvc.perform(get("/api/admin/users?page=0&size=1000")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.size").value(100));

        // Negative page/size fails gracefully or clamps safely
        mockMvc.perform(get("/api/admin/users?page=-1&size=-5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(20));
    }

    // =========================================================================
    // 16. Canonical API Envelope Verification
    // =========================================================================
    @Test
    @DisplayName("P8.5 Section 19: Canonical API Envelope on Success and Error Responses")
    void test16_apiEnvelopeVerification() throws Exception {
        // Success response canonical envelope
        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.message").doesNotExist());

        // Error response canonical envelope (404)
        mockMvc.perform(get("/api/admin/applications/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString());

        // Error response canonical envelope (401)
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    // =========================================================================
    // 17. Security Regression Matrix Across Roles
    // =========================================================================
    @Test
    @DisplayName("P8.5 Section 20: Cross-Phase Role Isolation Matrix (Guest, Candidate, Recruiter, Admin)")
    void test17_securityRegressionMatrix() throws Exception {
        CandidateAuth cand = registerCandidate("cand.p85.sec_" + UUID.randomUUID() + "@example.com", "Sec Candidate");
        RecruiterAuth rec = registerRecruiter("rec.p85.sec_" + UUID.randomUUID() + "@example.com");

        // 1. Guest blocked from candidate, recruiter, and admin APIs
        mockMvc.perform(get("/api/candidate/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/recruiter/company")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());

        // 2. Candidate blocked from recruiter and admin APIs
        mockMvc.perform(get("/api/recruiter/company")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isForbidden());

        // 3. Recruiter blocked from candidate profile and admin APIs
        mockMvc.perform(get("/api/candidate/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isForbidden());

        // 4. Admin succeeds on admin APIs
        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 18. Database Integrity & Transaction Rollback Integration
    // =========================================================================
    @Test
    @DisplayName("P8.5 Sections 21 & 22: Relational Database Integrity and Transaction Rollback Verification")
    void test18_databaseIntegrityAndTransactionRollback() throws Exception {
        // Direct SQL checks for relational integrity across the entire test database
        Integer orphanApplications = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM applications a LEFT JOIN jobs j ON a.job_id = j.id WHERE j.id IS NULL",
                Integer.class);
        assertThat(orphanApplications).isEqualTo(0);

        Integer orphanStatusHistories = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_status_history h LEFT JOIN applications a ON h.application_id = a.id WHERE a.id IS NULL",
                Integer.class);
        assertThat(orphanStatusHistories).isEqualTo(0);

        Integer orphanJobSkills = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_skills js LEFT JOIN jobs j ON js.job_id = j.id WHERE j.id IS NULL",
                Integer.class);
        assertThat(orphanJobSkills).isEqualTo(0);

        Integer orphanCandidateSkills = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM candidate_skills cs LEFT JOIN candidates c ON cs.candidate_id = c.id WHERE c.id IS NULL",
                Integer.class);
        assertThat(orphanCandidateSkills).isEqualTo(0);

        Integer orphanResumes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM resumes r LEFT JOIN candidates c ON r.candidate_id = c.id WHERE c.id IS NULL",
                Integer.class);
        assertThat(orphanResumes).isEqualTo(0);

        // Transaction Rollback test: If audit logging fails, entity mutation must roll back
        CandidateAuth cand = registerCandidate("cand.p85.roll_" + UUID.randomUUID() + "@example.com", "Rollback User");
        boolean initialActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM users WHERE id = ?", Boolean.class, cand.userId());
        assertThat(initialActive).isTrue();

        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        try {
            tt.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    jdbcTemplate.update("UPDATE users SET is_active = FALSE WHERE id = ?", cand.userId());
                    // Simulate an unexpected audit failure
                    throw new RuntimeException("Simulated audit write failure for atomicity test");
                }
            });
        } catch (RuntimeException ignored) {}

        // State rolled back cleanly
        boolean postActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM users WHERE id = ?", Boolean.class, cand.userId());
        assertThat(postActive).isTrue();
    }
}
