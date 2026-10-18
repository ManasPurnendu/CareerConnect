package com.careerconnect.notification.controller;

import com.careerconnect.application.domain.ApplicationStatus;
import com.careerconnect.application.dto.ApplicationSubmissionRequest;
import com.careerconnect.application.dto.UpdateApplicationStatusRequest;
import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.AssignSkillRequest;
import com.careerconnect.candidate.dto.CreateEducationRequest;
import com.careerconnect.candidate.dto.UpdateProfileRequest;
import com.careerconnect.common.model.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

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
public class NotificationEngineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private record UserAuth(String token, Long candidateId, Long recruiterId, Long userId) {}

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
        return new UserAuth(token, candidateId, null, userId);
    }

    private UserAuth registerRecruiter(String email, String name) throws Exception {
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.RECRUITER, name);
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        String token = root.path("data").path("accessToken").asText();
        Long recruiterId = root.path("data").path("user").path("recruiterId").asLong();
        Long userId = root.path("data").path("user").path("id").asLong();
        return new UserAuth(token, null, recruiterId, userId);
    }

    private void seedCandidateProfile(UserAuth user, String name) throws Exception {
        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateProfileRequest(name, "Software Engineer", "Bengaluru", "Passionate engineer."))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateEducationRequest("National University", "B.Tech", "Computer Science", 2018, 2022, "9.0", 1))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignSkillRequest("Java"))))
                .andExpect(status().isCreated());

        byte[] pdfBytes = "%PDF-1.4 mock resume content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isCreated());
    }

    private Long setupCompany(UserAuth recruiter, String companyName) {
        jdbcTemplate.update("INSERT INTO companies (name, industry, location, is_active) VALUES (?, 'Tech', 'Bengaluru', true)",
                companyName);
        Long companyId = jdbcTemplate.queryForObject("SELECT id FROM companies WHERE name = ?", Long.class, companyName);
        jdbcTemplate.update("UPDATE recruiters SET company_id = ? WHERE id = ?", companyId, recruiter.recruiterId());
        return companyId;
    }

    private Long createActiveJob(Long companyId, String title) {
        String deadline = LocalDate.now().plusDays(30).toString();
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, job_type, experience_level,
                                  work_mode, application_deadline, status)
                VALUES (?, ?, 'Job Description', 'FULL_TIME', 'FRESHER', 'REMOTE', ?, 'ACTIVE')
                """, companyId, title, deadline);
        return jdbcTemplate.queryForObject("SELECT id FROM jobs WHERE title = ? AND company_id = ?", Long.class, title, companyId);
    }

    private Long submitApplication(UserAuth candidate, Long jobId) throws Exception {
        ApplicationSubmissionRequest req = new ApplicationSubmissionRequest();
        req.setCoverLetter("I am excited to apply for this position.");
        MvcResult res = mockMvc.perform(post("/api/jobs/" + jobId + "/applications")
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("applicationId").asLong();
    }

    // ─── 1. Authentication & Security Access Boundaries ────────────────────────

    @Test
    @DisplayName("P7.3-01: GET /api/notifications unauthenticated returns 401 UNAUTHENTICATED")
    void getNotifications_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("P7.3-02: PATCH /api/notifications/1/read unauthenticated returns 401 UNAUTHENTICATED")
    void markRead_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/notifications/1/read"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("P7.3-03: PATCH /api/notifications/read-all unauthenticated returns 401 UNAUTHENTICATED")
    void markAllRead_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/notifications/read-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("P7.3-04: Authenticated candidate can access own notifications")
    void getNotifications_candidate_success() throws Exception {
        UserAuth candidate = registerCandidate("cand.notif.get_" + UUID.randomUUID() + "@example.com", "Candidate Get");
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.unreadCount").isNumber());
    }

    @Test
    @DisplayName("P7.3-05: Authenticated recruiter can access own notifications")
    void getNotifications_recruiter_success() throws Exception {
        UserAuth recruiter = registerRecruiter("rec.notif.get_" + UUID.randomUUID() + "@example.com", "Recruiter Get");
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.unreadCount").isNumber());
    }

    @Test
    @DisplayName("P7.3-06: User isolation: Candidate cannot see or mark other user's notification")
    void userIsolation_candidateCannotAccessOtherUser() throws Exception {
        UserAuth userA = registerCandidate("userA_" + UUID.randomUUID() + "@example.com", "User A");
        UserAuth userB = registerCandidate("userB_" + UUID.randomUUID() + "@example.com", "User B");

        // Insert notification directly for User B
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, body, resource_type, resource_id, read_at, created_at)
                VALUES (?, 'APPLICATION_STATUS_CHANGED', 'Secret Title', 'Secret Body', 'APPLICATION', 999, NULL, CURRENT_TIMESTAMP(3))
                """, userB.userId());
        Long notifId = jdbcTemplate.queryForObject("SELECT id FROM notifications WHERE user_id = ?", Long.class, userB.userId());

        // User A fetches notifications -> User B's notification is NOT visible
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + userA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        // User A attempts to mark User B's notification as read -> generic 404 RESOURCE_NOT_FOUND
        mockMvc.perform(patch("/api/notifications/" + notifId + "/read")
                        .header("Authorization", "Bearer " + userA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ─── 2. Query, Pagination, and Unread Count ────────────────────────────────

    @Test
    @DisplayName("P7.3-07: Query pagination and unread filter behavior")
    void queryPaginationAndUnreadFilter() throws Exception {
        UserAuth user = registerCandidate("user_filter_" + UUID.randomUUID() + "@example.com", "User Filter");

        // Insert 3 notifications: 2 unread, 1 read
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, body, resource_type, resource_id, read_at, created_at)
                VALUES (?, 'APPLICATION_STATUS_CHANGED', 'Title 1', 'Body 1', 'APPLICATION', 1, NULL, CURRENT_TIMESTAMP(3))
                """, user.userId());
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, body, resource_type, resource_id, read_at, created_at)
                VALUES (?, 'APPLICATION_STATUS_CHANGED', 'Title 2', 'Body 2', 'APPLICATION', 2, NULL, CURRENT_TIMESTAMP(3))
                """, user.userId());
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, body, resource_type, resource_id, read_at, created_at)
                VALUES (?, 'JOB_CLOSED', 'Title 3', 'Body 3', 'JOB', 3, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """, user.userId());

        // 1. Get all notifications (total 3, unreadCount 2)
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.unreadCount").value(2))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(3));

        // 2. Filter unreadOnly = true (total 2)
        mockMvc.perform(get("/api/notifications?unread=true")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.unreadCount").value(2))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2));

        // 3. Pagination size = 1
        mockMvc.perform(get("/api/notifications?page=0&size=1")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(3))
                .andExpect(jsonPath("$.data.pagination.totalPages").value(3))
                .andExpect(jsonPath("$.data.pagination.page").value(0))
                .andExpect(jsonPath("$.data.pagination.size").value(1));
    }

    // ─── 3. Mark As Read Operations ────────────────────────────────────────────

    @Test
    @DisplayName("P7.3-08: PATCH /api/notifications/{id}/read marks single notification as read")
    void markSingleAsRead_success() throws Exception {
        UserAuth user = registerCandidate("user_mark_" + UUID.randomUUID() + "@example.com", "User Mark");

        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, body, resource_type, resource_id, read_at, created_at)
                VALUES (?, 'APPLICATION_STATUS_CHANGED', 'Pending Review', 'Your status changed.', 'APPLICATION', 10, NULL, CURRENT_TIMESTAMP(3))
                """, user.userId());
        Long notifId = jdbcTemplate.queryForObject("SELECT id FROM notifications WHERE user_id = ?", Long.class, user.userId());

        // Mark as read
        mockMvc.perform(patch("/api/notifications/" + notifId + "/read")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(notifId))
                .andExpect(jsonPath("$.data.read").value(true))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());

        // Verify unread count becomes 0
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        // Mark read again (idempotent)
        mockMvc.perform(patch("/api/notifications/" + notifId + "/read")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));
    }

    @Test
    @DisplayName("P7.3-09: PATCH /api/notifications/read-all marks all unread notifications for user")
    void markAllAsRead_success() throws Exception {
        UserAuth userA = registerCandidate("user_all_a_" + UUID.randomUUID() + "@example.com", "User All A");
        UserAuth userB = registerCandidate("user_all_b_" + UUID.randomUUID() + "@example.com", "User All B");

        // Insert 2 unread for User A, 1 unread for User B
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, body, resource_type, resource_id, read_at, created_at)
                VALUES (?, 'APPLICATION_STATUS_CHANGED', 'A1', 'Body A1', 'APPLICATION', 1, NULL, CURRENT_TIMESTAMP(3))
                """, userA.userId());
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, body, resource_type, resource_id, read_at, created_at)
                VALUES (?, 'APPLICATION_STATUS_CHANGED', 'A2', 'Body A2', 'APPLICATION', 2, NULL, CURRENT_TIMESTAMP(3))
                """, userA.userId());
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, body, resource_type, resource_id, read_at, created_at)
                VALUES (?, 'APPLICATION_STATUS_CHANGED', 'B1', 'Body B1', 'APPLICATION', 3, NULL, CURRENT_TIMESTAMP(3))
                """, userB.userId());

        // User A marks all as read
        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + userA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.markedReadCount").value(2));

        // User A unread count is 0
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + userA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        // User B's unread count remains 1!
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + userB.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    // ─── 4. Application Status Transition Notifications ────────────────────────

    @Test
    @DisplayName("P7.3-10: Status transition generates in-app notification for candidate")
    void statusTransition_createsCandidateNotification() throws Exception {
        UserAuth recruiter = registerRecruiter("rec_notif_status_" + UUID.randomUUID() + "@example.com", "Rec Status");
        Long companyId = setupCompany(recruiter, "Status Co " + UUID.randomUUID());
        Long jobId = createActiveJob(companyId, "Fullstack Architect " + UUID.randomUUID());

        UserAuth candidate = registerCandidate("cand_notif_status_" + UUID.randomUUID() + "@example.com", "Cand Status");
        seedCandidateProfile(candidate, "Cand Status");

        Long appId = submitApplication(candidate, jobId);

        // Recruiter changes status to UNDER_REVIEW
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + recruiter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest(ApplicationStatus.UNDER_REVIEW, "Review note"))))
                .andExpect(status().isOk());

        // Verify notification created for Candidate
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("APPLICATION_STATUS_CHANGED"))
                .andExpect(jsonPath("$.data.items[0].resourceType").value("APPLICATION"))
                .andExpect(jsonPath("$.data.items[0].resourceId").value(appId))
                .andExpect(jsonPath("$.data.items[0].body").value(org.hamcrest.Matchers.containsString("UNDER_REVIEW")))
                .andExpect(jsonPath("$.data.items[0].read").value(false))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        // Verify recruiter has 0 notifications
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    @DisplayName("P7.3-11: Invalid status transition creates NO notification")
    void invalidStatusTransition_createsNoNotification() throws Exception {
        UserAuth recruiter = registerRecruiter("rec_notif_inv_" + UUID.randomUUID() + "@example.com", "Rec Inv");
        Long companyId = setupCompany(recruiter, "Inv Co " + UUID.randomUUID());
        Long jobId = createActiveJob(companyId, "DevOps " + UUID.randomUUID());

        UserAuth candidate = registerCandidate("cand_notif_inv_" + UUID.randomUUID() + "@example.com", "Cand Inv");
        seedCandidateProfile(candidate, "Cand Inv");

        Long appId = submitApplication(candidate, jobId);

        // Attempt illegal skip: APPLIED -> SELECTED
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + recruiter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest(ApplicationStatus.SELECTED, "Direct hire"))))
                .andExpect(status().isUnprocessableEntity());

        // Candidate has 0 notifications
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    @DisplayName("P7.3-12: Candidate withdrawal does NOT create notification for candidate or recruiter")
    void candidateWithdrawal_createsNoNotification() throws Exception {
        UserAuth recruiter = registerRecruiter("rec_notif_wth_" + UUID.randomUUID() + "@example.com", "Rec Wth");
        Long companyId = setupCompany(recruiter, "Wth Co " + UUID.randomUUID());
        Long jobId = createActiveJob(companyId, "Frontend Lead " + UUID.randomUUID());

        UserAuth candidate = registerCandidate("cand_notif_wth_" + UUID.randomUUID() + "@example.com", "Cand Wth");
        seedCandidateProfile(candidate, "Cand Wth");

        Long appId = submitApplication(candidate, jobId);

        // Candidate withdraws application
        mockMvc.perform(patch("/api/candidate/applications/" + appId + "/withdraw")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk());

        // Candidate has 0 notifications
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));

        // Recruiter has 0 notifications
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    // ─── 5. Job Closure Notifications ──────────────────────────────────────────

    @Test
    @DisplayName("P7.3-13: Closing active job creates in-app notifications for affected applicants only")
    void closeJob_notifiesAffectedApplicants() throws Exception {
        UserAuth recruiter = registerRecruiter("rec_notif_close_" + UUID.randomUUID() + "@example.com", "Rec Close");
        Long companyId = setupCompany(recruiter, "Close Co " + UUID.randomUUID());
        Long jobId = createActiveJob(companyId, "Principal Architect " + UUID.randomUUID());

        // Candidate 1 applies
        UserAuth candidate1 = registerCandidate("cand1_close_" + UUID.randomUUID() + "@example.com", "Cand 1");
        seedCandidateProfile(candidate1, "Cand 1");
        submitApplication(candidate1, jobId);

        // Candidate 2 applies
        UserAuth candidate2 = registerCandidate("cand2_close_" + UUID.randomUUID() + "@example.com", "Cand 2");
        seedCandidateProfile(candidate2, "Cand 2");
        submitApplication(candidate2, jobId);

        // Candidate 3 does NOT apply
        UserAuth candidate3 = registerCandidate("cand3_unrelated_" + UUID.randomUUID() + "@example.com", "Cand 3");
        seedCandidateProfile(candidate3, "Cand 3");

        // Recruiter closes the job: PUT /api/recruiter/jobs/{jobId}/close (or via lifecycle endpoint)
        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/close")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isOk());

        // Candidate 1 received JOB_CLOSED notification
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + candidate1.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("JOB_CLOSED"))
                .andExpect(jsonPath("$.data.items[0].resourceType").value("JOB"))
                .andExpect(jsonPath("$.data.items[0].resourceId").value(jobId))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        // Candidate 2 received JOB_CLOSED notification
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + candidate2.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("JOB_CLOSED"))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        // Candidate 3 received 0 notifications!
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + candidate3.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    @DisplayName("P7.3-14: Closing a job with zero applicants creates zero notifications")
    void closeZeroApplicantJob_createsZeroNotifications() throws Exception {
        UserAuth recruiter = registerRecruiter("rec_notif_zero_" + UUID.randomUUID() + "@example.com", "Rec Zero");
        Long companyId = setupCompany(recruiter, "Zero Co " + UUID.randomUUID());
        Long jobId = createActiveJob(companyId, "Zero App Job " + UUID.randomUUID());

        // Close zero applicant job
        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/close")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isOk());

        // Verify total notifications in DB has no notification for this job
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE resource_type = 'JOB' AND resource_id = ?",
                Integer.class, jobId);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("P7.3-15: Closing an already CLOSED job fails and does not duplicate notifications")
    void closeAlreadyClosedJob_failsAndDoesNotDuplicate() throws Exception {
        UserAuth recruiter = registerRecruiter("rec_notif_dup_" + UUID.randomUUID() + "@example.com", "Rec Dup");
        Long companyId = setupCompany(recruiter, "Dup Co " + UUID.randomUUID());
        Long jobId = createActiveJob(companyId, "Dup Job " + UUID.randomUUID());

        UserAuth candidate = registerCandidate("cand_dup_" + UUID.randomUUID() + "@example.com", "Cand Dup");
        seedCandidateProfile(candidate, "Cand Dup");
        submitApplication(candidate, jobId);

        // First close succeeds
        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/close")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isOk());

        // Second close fails with 422 INVALID_STATUS_TRANSITION
        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/close")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isUnprocessableEntity());

        // Candidate still has exactly 1 notification (not 2)
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    @DisplayName("P7.3-16: Foreign recruiter attempting to close another company's job creates 0 notifications")
    void foreignRecruiterClose_createsNoNotifications() throws Exception {
        UserAuth recA = registerRecruiter("rec_a_close_" + UUID.randomUUID() + "@example.com", "Rec A");
        Long compA = setupCompany(recA, "Comp A " + UUID.randomUUID());
        Long jobId = createActiveJob(compA, "Job A " + UUID.randomUUID());

        UserAuth candidate = registerCandidate("cand_foreign_" + UUID.randomUUID() + "@example.com", "Cand Foreign");
        seedCandidateProfile(candidate, "Cand Foreign");
        submitApplication(candidate, jobId);

        UserAuth recB = registerRecruiter("rec_b_close_" + UUID.randomUUID() + "@example.com", "Rec B");
        setupCompany(recB, "Comp B " + UUID.randomUUID());

        // Recruiter B attempts to close Job A -> 404 JOB_NOT_FOUND
        mockMvc.perform(patch("/api/recruiter/jobs/" + jobId + "/close")
                        .header("Authorization", "Bearer " + recB.token()))
                .andExpect(status().isNotFound());

        // Candidate received 0 notifications
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    @DisplayName("P7.3-17: Foreign recruiter attempting to change application status creates 0 notifications")
    void foreignRecruiterStatusChange_createsNoNotifications() throws Exception {
        UserAuth recA = registerRecruiter("rec_a_stat_" + UUID.randomUUID() + "@example.com", "Rec A");
        Long compA = setupCompany(recA, "Comp A " + UUID.randomUUID());
        Long jobId = createActiveJob(compA, "Job A " + UUID.randomUUID());

        UserAuth candidate = registerCandidate("cand_foreign_stat_" + UUID.randomUUID() + "@example.com", "Cand Foreign");
        seedCandidateProfile(candidate, "Cand Foreign");
        Long appId = submitApplication(candidate, jobId);

        UserAuth recB = registerRecruiter("rec_b_stat_" + UUID.randomUUID() + "@example.com", "Rec B");
        setupCompany(recB, "Comp B " + UUID.randomUUID());

        // Recruiter B attempts to mutate application A -> 404 RESOURCE_NOT_FOUND
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + recB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest(ApplicationStatus.UNDER_REVIEW, "Unauthorized note"))))
                .andExpect(status().isNotFound());

        // Candidate received 0 notifications
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    @DisplayName("P7.3-18: Terminal state mutation creates NO notification")
    void terminalStateMutation_createsNoNotification() throws Exception {
        UserAuth rec = registerRecruiter("rec_term_notif_" + UUID.randomUUID() + "@example.com", "Rec Term");
        Long compId = setupCompany(rec, "Term Co " + UUID.randomUUID());
        Long jobId = createActiveJob(compId, "Term Job " + UUID.randomUUID());

        UserAuth candidate = registerCandidate("cand_term_notif_" + UUID.randomUUID() + "@example.com", "Cand Term");
        seedCandidateProfile(candidate, "Cand Term");
        Long appId = submitApplication(candidate, jobId);

        // Transition APPLIED -> REJECTED (valid)
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest(ApplicationStatus.REJECTED, "Rejection note"))))
                .andExpect(status().isOk());

        // Candidate has 1 notification (from the valid transition to REJECTED)
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));

        // Attempt invalid mutation from terminal state: REJECTED -> UNDER_REVIEW (422)
        mockMvc.perform(patch("/api/recruiter/applications/" + appId + "/status")
                        .header("Authorization", "Bearer " + rec.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest(ApplicationStatus.UNDER_REVIEW, "Reopening note"))))
                .andExpect(status().isUnprocessableEntity());

        // Candidate STILL has exactly 1 notification (no second notification created)
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    @DisplayName("P7.3-19: PATCH /api/notifications/{id} marks notification as read")
    void patchNotificationDirectly_marksAsRead() throws Exception {
        UserAuth user = registerCandidate("user_patch_dir_" + UUID.randomUUID() + "@example.com", "User Patch Dir");

        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, body, resource_type, resource_id, read_at, created_at)
                VALUES (?, 'APPLICATION_STATUS_CHANGED', 'Direct Patch', 'Direct test body', 'APPLICATION', 22, NULL, CURRENT_TIMESTAMP(3))
                """, user.userId());
        Long notifId = jdbcTemplate.queryForObject("SELECT id FROM notifications WHERE user_id = ?", Long.class, user.userId());

        // Call PATCH /api/notifications/{id} with body
        mockMvc.perform(patch("/api/notifications/" + notifId)
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"read\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(notifId))
                .andExpect(jsonPath("$.data.read").value(true));
    }

    @Test
    @DisplayName("P7.3-20: Spoofed userId in query parameter is ignored; server identity wins")
    void spoofedUserId_isIgnored() throws Exception {
        UserAuth userA = registerCandidate("user_spoof_a_" + UUID.randomUUID() + "@example.com", "User Spoof A");
        UserAuth userB = registerCandidate("user_spoof_b_" + UUID.randomUUID() + "@example.com", "User Spoof B");

        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, body, resource_type, resource_id, read_at, created_at)
                VALUES (?, 'JOB_CLOSED', 'B Title', 'B Body', 'JOB', 1, NULL, CURRENT_TIMESTAMP(3))
                """, userB.userId());

        // User A supplies ?userId={userB.userId()}
        mockMvc.perform(get("/api/notifications?userId=" + userB.userId())
                        .header("Authorization", "Bearer " + userA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }
}
