package com.careerconnect.application.controller;

import com.careerconnect.application.domain.ApplicationStatus;
import com.careerconnect.application.dto.UpdateApplicationStatusRequest;
import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.UpdateProfileRequest;
import com.careerconnect.common.model.Role;
import com.careerconnect.security.JwtService;
import com.careerconnect.security.SecurityUser;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
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
public class Phase7SecurityHardeningAuditTest {

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
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.RECRUITER, "Recruiter User");
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

    private String generateAdminToken() {
        return jwtService.generateToken(new SecurityUser(9999L, "admin.p75@example.com", "HASH", Role.ADMIN, "Admin User", null, null, null, true));
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

    private Long createJob(Long companyId, String title) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO jobs (
                        company_id, title, description, job_type, experience_level,
                        work_mode, location, salary_min_lpa, salary_max_lpa, salary_currency,
                        application_deadline, status, created_at, updated_at
                    ) VALUES (?, ?, 'Job Description', 'INTERNSHIP', 'FRESHER', 'HYBRID', 'Bengaluru', 5.0, 10.0, 'INR', ?, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            ps.setString(2, title);
            ps.setDate(3, Date.valueOf(LocalDate.now().plusDays(30)));
            return ps;
        }, kh);
        return com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
    }

    private void setupCandidateReadiness(CandidateAuth cand, String resumeContent) throws Exception {
        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest(cand.fullName(), "Full-Stack Dev", "Bengaluru", "Bio"))))
                .andExpect(status().isOk());

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

        mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Java"))))
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
                        .content(objectMapper.writeValueAsString(Map.of("coverLetter", "Excited to apply!"))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("applicationId").asLong();
    }

    private void softDeleteJob(Long jobId) {
        jdbcTemplate.update("UPDATE jobs SET deleted_at = CURRENT_TIMESTAMP(3) WHERE id = ?", jobId);
    }

    // =========================================================================
    // 1. recruiterCrossCompanyApplicantList_returns404
    // =========================================================================
    @Test
    @DisplayName("1. recruiterCrossCompanyApplicantList_returns404: Recruiter A cannot query applicants of Job B")
    void recruiterCrossCompanyApplicantList_returns404() throws Exception {
        RecruiterAuth recA = registerRecruiter("rec.audit.a1_" + UUID.randomUUID() + "@example.com");
        Long companyAId = createCompany("Company Alpha");
        linkRecruiterToCompany(recA.recruiterId(), companyAId);

        RecruiterAuth recB = registerRecruiter("rec.audit.b1_" + UUID.randomUUID() + "@example.com");
        Long companyBId = createCompany("Company Beta");
        linkRecruiterToCompany(recB.recruiterId(), companyBId);
        Long jobBId = createJob(companyBId, "Backend Engineer B");

        mockMvc.perform(get("/api/recruiter/jobs/" + jobBId + "/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    // =========================================================================
    // 2. recruiterCrossCompanyApplicantDetail_returns404
    // =========================================================================
    @Test
    @DisplayName("2. recruiterCrossCompanyApplicantDetail_returns404: Recruiter A cannot view applicant detail of Company B")
    void recruiterCrossCompanyApplicantDetail_returns404() throws Exception {
        RecruiterAuth recA = registerRecruiter("rec.audit.a2_" + UUID.randomUUID() + "@example.com");
        Long companyAId = createCompany("Company Alpha");
        linkRecruiterToCompany(recA.recruiterId(), companyAId);

        RecruiterAuth recB = registerRecruiter("rec.audit.b2_" + UUID.randomUUID() + "@example.com");
        Long companyBId = createCompany("Company Beta");
        linkRecruiterToCompany(recB.recruiterId(), companyBId);
        Long jobBId = createJob(companyBId, "DevOps Engineer B");

        CandidateAuth cand = registerCandidate("cand.audit.b2_" + UUID.randomUUID() + "@example.com", "Bob Applicant");
        setupCandidateReadiness(cand, "Bob Resume Content");
        Long appBId = applyToJob(cand, jobBId);

        mockMvc.perform(get("/api/recruiter/applications/" + appBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // =========================================================================
    // 3. recruiterCrossCompanyStatusUpdate_returns404
    // =========================================================================
    @Test
    @DisplayName("3. recruiterCrossCompanyStatusUpdate_returns404: Recruiter A cannot update status on Company B application")
    void recruiterCrossCompanyStatusUpdate_returns404() throws Exception {
        RecruiterAuth recA = registerRecruiter("rec.audit.a3_" + UUID.randomUUID() + "@example.com");
        Long companyAId = createCompany("Company Alpha");
        linkRecruiterToCompany(recA.recruiterId(), companyAId);

        RecruiterAuth recB = registerRecruiter("rec.audit.b3_" + UUID.randomUUID() + "@example.com");
        Long companyBId = createCompany("Company Beta");
        linkRecruiterToCompany(recB.recruiterId(), companyBId);
        Long jobBId = createJob(companyBId, "Security Engineer B");

        CandidateAuth cand = registerCandidate("cand.audit.b3_" + UUID.randomUUID() + "@example.com", "Charlie Applicant");
        setupCandidateReadiness(cand, "Charlie Resume Content");
        Long appBId = applyToJob(cand, jobBId);

        UpdateApplicationStatusRequest req = new UpdateApplicationStatusRequest(ApplicationStatus.UNDER_REVIEW, "Tamper attempt");

        mockMvc.perform(patch("/api/recruiter/applications/" + appBId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // =========================================================================
    // 4. recruiterCrossCompanyResume_returns404
    // =========================================================================
    @Test
    @DisplayName("4. recruiterCrossCompanyResume_returns404: Recruiter A cannot view resume for Company B application")
    void recruiterCrossCompanyResume_returns404() throws Exception {
        RecruiterAuth recA = registerRecruiter("rec.audit.a4_" + UUID.randomUUID() + "@example.com");
        Long companyAId = createCompany("Company Alpha");
        linkRecruiterToCompany(recA.recruiterId(), companyAId);

        RecruiterAuth recB = registerRecruiter("rec.audit.b4_" + UUID.randomUUID() + "@example.com");
        Long companyBId = createCompany("Company Beta");
        linkRecruiterToCompany(recB.recruiterId(), companyBId);
        Long jobBId = createJob(companyBId, "Frontend Engineer B");

        CandidateAuth cand = registerCandidate("cand.audit.b4_" + UUID.randomUUID() + "@example.com", "David Applicant");
        setupCandidateReadiness(cand, "David Resume Content");
        Long appBId = applyToJob(cand, jobBId);

        mockMvc.perform(get("/api/recruiter/applications/" + appBId + "/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // =========================================================================
    // 5. recruiterCrossCompanyResumeDownload_returns404
    // =========================================================================
    @Test
    @DisplayName("5. recruiterCrossCompanyResumeDownload_returns404: Recruiter A cannot download resume for Company B application")
    void recruiterCrossCompanyResumeDownload_returns404() throws Exception {
        RecruiterAuth recA = registerRecruiter("rec.audit.a5_" + UUID.randomUUID() + "@example.com");
        Long companyAId = createCompany("Company Alpha");
        linkRecruiterToCompany(recA.recruiterId(), companyAId);

        RecruiterAuth recB = registerRecruiter("rec.audit.b5_" + UUID.randomUUID() + "@example.com");
        Long companyBId = createCompany("Company Beta");
        linkRecruiterToCompany(recB.recruiterId(), companyBId);
        Long jobBId = createJob(companyBId, "Data Analyst B");

        CandidateAuth cand = registerCandidate("cand.audit.b5_" + UUID.randomUUID() + "@example.com", "Eve Applicant");
        setupCandidateReadiness(cand, "Eve Resume Content");
        Long appBId = applyToJob(cand, jobBId);

        mockMvc.perform(get("/api/recruiter/applications/" + appBId + "/resume/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // =========================================================================
    // 6. candidateCannotAccessRecruiterApplicantEndpoint
    // =========================================================================
    @Test
    @DisplayName("6. candidateCannotAccessRecruiterApplicantEndpoint: Candidate receives 403 on all recruiter endpoints")
    void candidateCannotAccessRecruiterApplicantEndpoint() throws Exception {
        CandidateAuth cand = registerCandidate("cand.audit.role_" + UUID.randomUUID() + "@example.com", "Role Tester");

        mockMvc.perform(get("/api/recruiter/jobs/1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/recruiter/applications/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/recruiter/applications/1/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHORTLISTED\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/recruiter/applications/1/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/recruiter/applications/1/resume/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 7. candidateCannotAccessAnotherCandidatesProtectedData
    // =========================================================================
    @Test
    @DisplayName("7. candidateCannotAccessAnotherCandidatesProtectedData: Candidate A cannot view or withdraw Candidate B's application")
    void candidateCannotAccessAnotherCandidatesProtectedData() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.audit.candown_" + UUID.randomUUID() + "@example.com");
        Long companyId = createCompany("Ownership Corp");
        linkRecruiterToCompany(rec.recruiterId(), companyId);
        Long jobId = createJob(companyId, "Platform Engineer");

        CandidateAuth candA = registerCandidate("cand.audit.a7_" + UUID.randomUUID() + "@example.com", "Alice Cand");
        CandidateAuth candB = registerCandidate("cand.audit.b7_" + UUID.randomUUID() + "@example.com", "Bob Cand");

        setupCandidateReadiness(candB, "Bob Private Resume");
        Long appBId = applyToJob(candB, jobId);

        // Candidate A attempts to view Candidate B's application detail -> 404
        mockMvc.perform(get("/api/candidate/applications/" + appBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        // Candidate A attempts to withdraw Candidate B's application -> 404
        mockMvc.perform(patch("/api/candidate/applications/" + appBId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // =========================================================================
    // 8. notificationUserIsolation
    // =========================================================================
    @Test
    @DisplayName("8. notificationUserIsolation: User A notifications are never visible to User B")
    void notificationUserIsolation() throws Exception {
        CandidateAuth candA = registerCandidate("cand.audit.notif.a_" + UUID.randomUUID() + "@example.com", "Notif Alice");
        CandidateAuth candB = registerCandidate("cand.audit.notif.b_" + UUID.randomUUID() + "@example.com", "Notif Bob");

        // Insert notification directly for User A
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, body, resource_type, resource_id, read_at, created_at)
                VALUES (?, 'APPLICATION_STATUS_CHANGED', 'Status Update', 'Updated to REVIEW', 'APPLICATION', 101, NULL, CURRENT_TIMESTAMP(3))
                """, candA.userId());

        // User B queries notifications -> empty list, unread count = 0
        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candB.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.unreadCount").value(0))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(0));
    }

    // =========================================================================
    // 9. notificationReadForeignUser_returns404
    // =========================================================================
    @Test
    @DisplayName("9. notificationReadForeignUser_returns404: User B cannot mark User A's notification as read")
    void notificationReadForeignUser_returns404() throws Exception {
        CandidateAuth candA = registerCandidate("cand.audit.notif.a9_" + UUID.randomUUID() + "@example.com", "Alice Notif");
        CandidateAuth candB = registerCandidate("cand.audit.notif.b9_" + UUID.randomUUID() + "@example.com", "Bob Notif");

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO notifications (user_id, type, title, body, resource_type, resource_id, read_at, created_at)
                    VALUES (?, 'APPLICATION_STATUS_CHANGED', 'Secret Title', 'Secret Body', 'APPLICATION', 102, NULL, CURRENT_TIMESTAMP(3))
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, candA.userId());
            return ps;
        }, keyHolder);
        Long notifAId = com.careerconnect.common.util.JdbcUtils.extractGeneratedId(keyHolder);

        // User B attempts to mark User A's notification as read via /read
        mockMvc.perform(patch("/api/notifications/" + notifAId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candB.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // User B attempts to mark via generic PATCH /{id}
        mockMvc.perform(patch("/api/notifications/" + notifAId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candB.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Verify notification for User A remains unread
        Integer unreadCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE id = ? AND read_at IS NULL", Integer.class, notifAId);
        assertThat(unreadCount).isEqualTo(1);
    }

    // =========================================================================
    // 10. notificationReadAllOnlyAffectsCurrentUser
    // =========================================================================
    @Test
    @DisplayName("10. notificationReadAllOnlyAffectsCurrentUser: markAllAsRead only modifies caller's notifications")
    void notificationReadAllOnlyAffectsCurrentUser() throws Exception {
        CandidateAuth candA = registerCandidate("cand.audit.readall.a_" + UUID.randomUUID() + "@example.com", "Alice All");
        CandidateAuth candB = registerCandidate("cand.audit.readall.b_" + UUID.randomUUID() + "@example.com", "Bob All");

        // Insert 2 notifications for A and 2 notifications for B
        for (int i = 1; i <= 2; i++) {
            jdbcTemplate.update("INSERT INTO notifications (user_id, type, title, body, created_at) VALUES (?, 'JOB_CLOSED', 'A" + i + "', 'B" + i + "', CURRENT_TIMESTAMP(3))", candA.userId());
            jdbcTemplate.update("INSERT INTO notifications (user_id, type, title, body, created_at) VALUES (?, 'JOB_CLOSED', 'B" + i + "', 'B" + i + "', CURRENT_TIMESTAMP(3))", candB.userId());
        }

        // User A marks all as read
        mockMvc.perform(patch("/api/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.markedReadCount").value(2));

        // User A unread count is now 0
        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candA.token()))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        // User B unread count remains 2
        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candB.token()))
                .andExpect(jsonPath("$.data.unreadCount").value(2));
    }

    // =========================================================================
    // 11. clientUserIdTamperingIgnored
    // =========================================================================
    @Test
    @DisplayName("11. clientUserIdTamperingIgnored: Injected ?userId=999999 or body userId cannot alter execution context")
    void clientUserIdTamperingIgnored() throws Exception {
        CandidateAuth cand = registerCandidate("cand.audit.userid_" + UUID.randomUUID() + "@example.com", "User Spoof");

        // Tamper via query param on notifications
        mockMvc.perform(get("/api/notifications?userId=999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Tamper via JSON body on status update
        RecruiterAuth rec = registerRecruiter("rec.audit.useridt_" + UUID.randomUUID() + "@example.com");
        Long companyId = createCompany("Tamper Corp");
        linkRecruiterToCompany(rec.recruiterId(), companyId);
        Long jobId = createJob(companyId, "Security Architect");

        setupCandidateReadiness(cand, "Spoof Resume Content");
        Long appId = applyToJob(cand, jobId);

        String tamperedBody = "{\"status\":\"SHORTLISTED\",\"note\":\"valid note\",\"userId\":999999}";
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tamperedBody))
                .andExpect(status().isOk());

        // Verify status history recorded recruiter's actual user ID, not 999999
        Long recordedUserId = jdbcTemplate.queryForObject(
                "SELECT changed_by_user_id FROM application_status_history WHERE application_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, appId);
        assertThat(recordedUserId).isEqualTo(rec.userId());
        assertThat(recordedUserId).isNotEqualTo(999999L);
    }

    // =========================================================================
    // 12. clientCandidateIdTamperingIgnored
    // =========================================================================
    @Test
    @DisplayName("12. clientCandidateIdTamperingIgnored: ?candidateId=999999 cannot bypass candidate identity")
    void clientCandidateIdTamperingIgnored() throws Exception {
        CandidateAuth cand = registerCandidate("cand.audit.candid_" + UUID.randomUUID() + "@example.com", "Cand Tamper");
        setupCandidateReadiness(cand, "Candidate Content");

        // Candidate requests resume with injected candidateId=999999 -> returns caller's resume
        mockMvc.perform(get("/api/candidate/resume?candidateId=999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalFilename").value("cand_tamper_resume.pdf"));
    }

    // =========================================================================
    // 13. clientRecruiterIdTamperingIgnored
    // =========================================================================
    @Test
    @DisplayName("13. clientRecruiterIdTamperingIgnored: ?recruiterId=999999 cannot override authenticated recruiter")
    void clientRecruiterIdTamperingIgnored() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.audit.recid_" + UUID.randomUUID() + "@example.com");
        Long companyId = createCompany("Recruiter Tamper Corp");
        linkRecruiterToCompany(rec.recruiterId(), companyId);
        Long jobId = createJob(companyId, "Cloud Engineer");

        mockMvc.perform(get("/api/recruiter/jobs/" + jobId + "/applications?recruiterId=999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // =========================================================================
    // 14. clientCompanyIdTamperingIgnored
    // =========================================================================
    @Test
    @DisplayName("14. clientCompanyIdTamperingIgnored: ?companyId=999999 or body companyId cannot bypass company resolution")
    void clientCompanyIdTamperingIgnored() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.audit.compid_" + UUID.randomUUID() + "@example.com");
        Long companyId = createCompany("Company ID Tamper Corp");
        linkRecruiterToCompany(rec.recruiterId(), companyId);
        Long jobId = createJob(companyId, "Site Reliability Engineer");

        mockMvc.perform(get("/api/recruiter/jobs/" + jobId + "/applications?companyId=999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // =========================================================================
    // 15. clientResumeIdTamperingIgnored
    // =========================================================================
    @Test
    @DisplayName("15. clientResumeIdTamperingIgnored: ?resumeId=999999 cannot fetch arbitrary resume")
    void clientResumeIdTamperingIgnored() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.audit.resumet_" + UUID.randomUUID() + "@example.com");
        Long companyId = createCompany("Resume Corp");
        linkRecruiterToCompany(rec.recruiterId(), companyId);
        Long jobId = createJob(companyId, "Firmware Engineer");

        CandidateAuth cand = registerCandidate("cand.audit.resumet_" + UUID.randomUUID() + "@example.com", "Resume Applicant");
        setupCandidateReadiness(cand, "Firmware Resume Content");
        Long appId = applyToJob(cand, jobId);

        // Recruiter asks with resumeId=999999 -> returns legitimate resume for applicant
        mockMvc.perform(get("/api/recruiter/applications/" + appId + "/resume?resumeId=999999&metadata=true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalFilename").value("resume_applicant_resume.pdf"));
    }

    // =========================================================================
    // 16. softDeletedJobCannotExposeApplication
    // =========================================================================
    @Test
    @DisplayName("16. softDeletedJobCannotExposeApplication: Soft-deleted jobs hide applications and details with 404")
    void softDeletedJobCannotExposeApplication() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.audit.softjob_" + UUID.randomUUID() + "@example.com");
        Long companyId = createCompany("Soft Delete Corp");
        linkRecruiterToCompany(rec.recruiterId(), companyId);
        Long jobId = createJob(companyId, "Archived Role");

        CandidateAuth cand = registerCandidate("cand.audit.softjob_" + UUID.randomUUID() + "@example.com", "Soft Candidate");
        setupCandidateReadiness(cand, "Soft Resume Content");
        Long appId = applyToJob(cand, jobId);

        // Soft delete the job
        softDeleteJob(jobId);

        // 1. Applicant list -> 404 JOB_NOT_FOUND
        mockMvc.perform(get("/api/recruiter/jobs/" + jobId + "/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        // 2. Applicant detail -> 404 RESOURCE_NOT_FOUND
        mockMvc.perform(get("/api/recruiter/applications/" + appId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // 3. Status mutation -> 404 RESOURCE_NOT_FOUND
        UpdateApplicationStatusRequest req = new UpdateApplicationStatusRequest(ApplicationStatus.UNDER_REVIEW, "Attempt");
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // =========================================================================
    // 17. softDeletedJobCannotExposeResume
    // =========================================================================
    @Test
    @DisplayName("17. softDeletedJobCannotExposeResume: Soft-deleted jobs hide resume access and downloads with 404")
    void softDeletedJobCannotExposeResume() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.audit.softres_" + UUID.randomUUID() + "@example.com");
        Long companyId = createCompany("Resume Soft Corp");
        linkRecruiterToCompany(rec.recruiterId(), companyId);
        Long jobId = createJob(companyId, "Deleted Job Resume");

        CandidateAuth cand = registerCandidate("cand.audit.softres_" + UUID.randomUUID() + "@example.com", "Resume Soft Cand");
        setupCandidateReadiness(cand, "Private Soft Content");
        Long appId = applyToJob(cand, jobId);

        // Soft delete the job
        softDeleteJob(jobId);

        // Recruiter resume endpoint -> 404
        mockMvc.perform(get("/api/recruiter/applications/" + appId + "/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Recruiter resume download -> 404
        mockMvc.perform(get("/api/recruiter/applications/" + appId + "/resume/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // =========================================================================
    // 18. staleJwtCompanyReassignment
    // =========================================================================
    @Test
    @DisplayName("18. staleJwtCompanyReassignment: Reassigning recruiters.company_id in MySQL dynamically updates authorization")
    void staleJwtCompanyReassignment() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.audit.stalejwt_" + UUID.randomUUID() + "@example.com");
        Long companyAId = createCompany("Original Company A");
        linkRecruiterToCompany(rec.recruiterId(), companyAId);
        Long jobAId = createJob(companyAId, "Company A Role");

        CandidateAuth candA = registerCandidate("cand.audit.stale.a_" + UUID.randomUUID() + "@example.com", "Applicant A");
        setupCandidateReadiness(candA, "A Content");
        Long appAId = applyToJob(candA, jobAId);

        // Create Company B with another recruiter, job, and applicant
        RecruiterAuth recB = registerRecruiter("rec.audit.stale.b_" + UUID.randomUUID() + "@example.com");
        Long companyBId = createCompany("Reassigned Company B");
        linkRecruiterToCompany(recB.recruiterId(), companyBId);
        Long jobBId = createJob(companyBId, "Company B Role");

        CandidateAuth candB = registerCandidate("cand.audit.stale.b_" + UUID.randomUUID() + "@example.com", "Applicant B");
        setupCandidateReadiness(candB, "B Content");
        Long appBId = applyToJob(candB, jobBId);

        // Verify rec initially accesses Company A and cannot access Company B
        mockMvc.perform(get("/api/recruiter/applications/" + appAId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/recruiter/applications/" + appBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isNotFound());

        // Reassign recruiter's company_id directly in database to Company B
        jdbcTemplate.update("UPDATE recruiters SET company_id = ? WHERE id = ?", companyBId, rec.recruiterId());

        // Using ORIGINAL token: Company A is now denied (404), Company B is now allowed (200)
        mockMvc.perform(get("/api/recruiter/applications/" + appAId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/recruiter/applications/" + appBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/recruiter/applications/" + appBId + "/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 19. terminalApplicationStatusCannotBeChanged
    // =========================================================================
    @Test
    @DisplayName("19. terminalApplicationStatusCannotBeChanged: Terminal status cannot be transitioned to any other status")
    void terminalApplicationStatusCannotBeChanged() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.audit.terminal_" + UUID.randomUUID() + "@example.com");
        Long companyId = createCompany("Terminal State Corp");
        linkRecruiterToCompany(rec.recruiterId(), companyId);
        Long jobId = createJob(companyId, "Principal Architect");

        CandidateAuth cand = registerCandidate("cand.audit.terminal_" + UUID.randomUUID() + "@example.com", "Terminal Applicant");
        setupCandidateReadiness(cand, "Term Content");
        Long appId = applyToJob(cand, jobId);

        // Transition: APPLIED -> REJECTED (terminal)
        UpdateApplicationStatusRequest rejectReq = new UpdateApplicationStatusRequest(ApplicationStatus.REJECTED, "Not a match");
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        // Attempt transition from REJECTED to SELECTED -> 422 INVALID_STATUS_TRANSITION
        UpdateApplicationStatusRequest selectReq = new UpdateApplicationStatusRequest(ApplicationStatus.SELECTED, "Reopening");
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selectReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // Candidate attempt to withdraw from REJECTED -> 422 INVALID_STATUS_TRANSITION
        mockMvc.perform(patch("/api/candidate/applications/" + appId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    // =========================================================================
    // 20. recruiterCannotSetWithdrawn
    // =========================================================================
    @Test
    @DisplayName("20. recruiterCannotSetWithdrawn: Recruiters are forbidden from setting WITHDRAWN status")
    void recruiterCannotSetWithdrawn() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.audit.withdraw_" + UUID.randomUUID() + "@example.com");
        Long companyId = createCompany("Withdrawn Corp");
        linkRecruiterToCompany(rec.recruiterId(), companyId);
        Long jobId = createJob(companyId, "Tech Lead");

        CandidateAuth cand = registerCandidate("cand.audit.withdraw_" + UUID.randomUUID() + "@example.com", "Withdraw Candidate");
        setupCandidateReadiness(cand, "With Content");
        Long appId = applyToJob(cand, jobId);

        // Recruiter attempts to set status = WITHDRAWN
        UpdateApplicationStatusRequest withdrawReq = new UpdateApplicationStatusRequest(ApplicationStatus.WITHDRAWN, "Recruiter withdrawing");
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withdrawReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    // =========================================================================
    // 21. statusHistoryAndNotificationRemainAtomic
    // =========================================================================
    @Test
    @DisplayName("21. statusHistoryAndNotificationRemainAtomic: Status change, history log, and candidate notification execute atomically")
    void statusHistoryAndNotificationRemainAtomic() throws Exception {
        RecruiterAuth rec = registerRecruiter("rec.audit.atomic_" + UUID.randomUUID() + "@example.com");
        Long companyId = createCompany("Atomic Corp");
        linkRecruiterToCompany(rec.recruiterId(), companyId);
        Long jobId = createJob(companyId, "Full Stack Engineer");

        CandidateAuth cand = registerCandidate("cand.audit.atomic_" + UUID.randomUUID() + "@example.com", "Atomic Candidate");
        setupCandidateReadiness(cand, "Atomic Content");
        Long appId = applyToJob(cand, jobId);

        UpdateApplicationStatusRequest req = new UpdateApplicationStatusRequest(ApplicationStatus.SHORTLISTED, "Impressive profile");
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // 1. Verify application status in DB
        String currentStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, appId);
        assertThat(currentStatus).isEqualTo("SHORTLISTED");

        // 2. Verify status history audit row
        Integer historyRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_status_history WHERE application_id = ? AND to_status = 'SHORTLISTED'",
                Integer.class, appId);
        assertThat(historyRows).isEqualTo(1);

        // 3. Verify notification created for candidate user
        Integer notifRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND resource_id = ? AND type = 'APPLICATION_STATUS_CHANGED'",
                Integer.class, cand.userId(), appId);
        assertThat(notifRows).isEqualTo(1);
    }

    // =========================================================================
    // 22. noSensitiveMetadataLeakage
    // =========================================================================
    @Test
    @DisplayName("22. noSensitiveMetadataLeakage: Error responses contain no candidate, company, resume, or internal path details")
    void noSensitiveMetadataLeakage() throws Exception {
        RecruiterAuth recA = registerRecruiter("rec.audit.leak.a_" + UUID.randomUUID() + "@example.com");
        Long companyAId = createCompany("Company Alpha Leak");
        linkRecruiterToCompany(recA.recruiterId(), companyAId);

        RecruiterAuth recB = registerRecruiter("rec.audit.leak.b_" + UUID.randomUUID() + "@example.com");
        Long companyBId = createCompany("Confidential Defense Corp");
        linkRecruiterToCompany(recB.recruiterId(), companyBId);
        Long jobBId = createJob(companyBId, "Secret Agent Position");

        CandidateAuth candB = registerCandidate("topsecret.candidate@defense.gov", "Secret Agent Candidate");
        setupCandidateReadiness(candB, "TopSecretContent");
        Long appBId = applyToJob(candB, jobBId);

        MvcResult result = mockMvc.perform(get("/api/recruiter/applications/" + appBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recA.token()))
                .andExpect(status().isNotFound())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        // Must not contain confidential names, emails, job titles, or file details
        assertThat(responseBody).doesNotContain("topsecret.candidate@defense.gov");
        assertThat(responseBody).doesNotContain("Secret Agent Candidate");
        assertThat(responseBody).doesNotContain("Confidential Defense Corp");
        assertThat(responseBody).doesNotContain("Secret Agent Position");
        assertThat(responseBody).doesNotContain("classified_resume.pdf");
        assertThat(responseBody).doesNotContain("TopSecretContent");
        assertThat(responseBody).doesNotContain("SQLException");
        assertThat(responseBody).doesNotContain("upload-dir");
        assertThat(responseBody).contains("RESOURCE_NOT_FOUND");
    }

    // =========================================================================
    // 23. roleMatrixVerification: Guest, Candidate, Recruiter, Admin
    // =========================================================================
    @Test
    @DisplayName("23. roleMatrixVerification: Enforces 401 for Guest and 403 for unauthorized roles")
    void roleMatrixVerification() throws Exception {
        String adminToken = generateAdminToken();

        // 1. Guest access to recruiter endpoint -> 401
        mockMvc.perform(get("/api/recruiter/jobs/1/applications"))
                .andExpect(status().isUnauthorized());

        // 2. Guest access to candidate endpoint -> 401
        mockMvc.perform(get("/api/candidate/resume"))
                .andExpect(status().isUnauthorized());

        // 3. Guest access to notifications -> 401
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());

        // 4. Admin access to recruiter endpoint -> 403
        mockMvc.perform(get("/api/recruiter/jobs/1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        // 5. Admin access to candidate resume -> 403
        mockMvc.perform(get("/api/candidate/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        // 6. Admin access to notifications -> 200 (any authenticated identity can access notifications)
        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 24. resumePathTraversalPrevention
    // =========================================================================
    @Test
    @DisplayName("24. resumePathTraversalPrevention: Injected directory traversal keys cannot escape upload directory")
    void resumePathTraversalPrevention() throws Exception {
        CandidateAuth cand = registerCandidate("cand.audit.traversal_" + UUID.randomUUID() + "@example.com", "Traversal Tester");

        // Insert corrupted resume with path traversal storage key
        jdbcTemplate.update("""
                INSERT INTO resumes (candidate_id, original_filename, storage_key, content_type, size_bytes, is_current, created_at, updated_at)
                VALUES (?, 'passwd.pdf', '../../etc/passwd', 'application/pdf', 1024, TRUE, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """, cand.candidateId());

        // Downloading resume must return 404 and safely reject the path traversal
        mockMvc.perform(get("/api/candidate/resume/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
