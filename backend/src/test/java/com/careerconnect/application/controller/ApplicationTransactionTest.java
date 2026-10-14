package com.careerconnect.application.controller;

import com.careerconnect.application.domain.ApplicationStatus;
import com.careerconnect.application.dto.ApplicationSubmissionRequest;
import com.careerconnect.application.repository.ApplicationRepository;
import com.careerconnect.application.service.ApplicationService;
import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.AssignSkillRequest;
import com.careerconnect.candidate.dto.CreateEducationRequest;
import com.careerconnect.candidate.dto.UpdateProfileRequest;
import com.careerconnect.common.model.Role;
import com.careerconnect.security.SecurityUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ApplicationTransactionTest {

    public static class TransactionTestApplicationRepository extends ApplicationRepository {
        public static volatile boolean failStatusHistory = false;

        public TransactionTestApplicationRepository(JdbcTemplate jdbcTemplate) {
            super(jdbcTemplate);
        }

        @Override
        public Long insertStatusHistory(Long applicationId, ApplicationStatus fromStatus,
                                       ApplicationStatus toStatus, Long changedByUserId,
                                       String note, Instant createdAt) {
            if (failStatusHistory) {
                throw new RuntimeException("Simulated status history insertion failure for transaction test");
            }
            return super.insertStatusHistory(applicationId, fromStatus, toStatus, changedByUserId, note, createdAt);
        }
    }

    @TestConfiguration
    static class TestRepoConfig {
        @Bean
        @Primary
        public ApplicationRepository testApplicationRepository(JdbcTemplate jdbcTemplate) {
            return new TransactionTestApplicationRepository(jdbcTemplate);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApplicationRepository applicationRepository;

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

    private void seedCandidateProfile(UserAuth user, String name) throws Exception {
        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateProfileRequest(name, "Developer", "Bengaluru", "Bio"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateEducationRequest("Uni", "B.Tech", "CS", 2018, 2022, "8.5", 1))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignSkillRequest("Java"))))
                .andExpect(status().isCreated());

        byte[] pdfBytes = "%PDF-1.4 sample resume".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isCreated());
    }

    private Long createActiveJob(String suffix) {
        String companyName = "TxTestCo " + suffix;
        jdbcTemplate.update("INSERT INTO companies (name, industry, location, is_active) VALUES (?, ?, ?, ?)",
                companyName, "Tech", "Bengaluru", true);
        Long companyId = jdbcTemplate.queryForObject("SELECT id FROM companies WHERE name = ?", Long.class, companyName);

        String deadline = LocalDate.now().plusDays(30).toString();
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, job_type, experience_level,
                                  work_mode, application_deadline, status)
                VALUES (?, ?, 'Job Desc', 'FULL_TIME', 'FRESHER', 'REMOTE', ?, 'ACTIVE')
                """, companyId, "Tx Job " + suffix, deadline);

        return jdbcTemplate.queryForObject("SELECT id FROM jobs WHERE title = ?", Long.class, "Tx Job " + suffix);
    }

    private void authenticateAs(UserAuth user) {
        SecurityUser secUser = new SecurityUser(
                user.userId(), "tx@example.com", "hash", Role.CANDIDATE, "Tx User",
                user.candidateId(), null, null, true
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(secUser, null, secUser.getAuthorities())
        );
    }

    @org.junit.jupiter.api.BeforeEach
    void cleanDb() {
        jdbcTemplate.execute("DELETE FROM application_status_history");
        jdbcTemplate.execute("DELETE FROM applications");
    }

    @AfterEach
    void tearDown() {
        TransactionTestApplicationRepository.failStatusHistory = false;
        SecurityContextHolder.clearContext();
        cleanDb();
    }

    @Test
    @DisplayName("P5.8-TX1: Application submission transaction rolls back if status history fails")
    void applicationSubmissionRollsBack_whenStatusHistoryFails() throws Exception {
        UserAuth candidate = registerCandidate("tx.sub.fail@example.com", "Tx Sub Fail");
        seedCandidateProfile(candidate, "Tx Sub Fail");
        Long jobId = createActiveJob("subfail");

        authenticateAs(candidate);

        // Turn on simulated failure in status history insertion
        TransactionTestApplicationRepository.failStatusHistory = true;

        ApplicationSubmissionRequest req = new ApplicationSubmissionRequest();
        req.setCoverLetter("Rollback test");

        // The service method is @Transactional, so RuntimeException must cause rollback
        assertThatThrownBy(() -> applicationService.submitApplication(jobId, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated status history insertion failure");

        // Verify that NO application was saved in the database
        Integer appCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE candidate_id = ? AND job_id = ?",
                Integer.class, candidate.candidateId(), jobId);
        assertThat(appCount).isEqualTo(0);

        // Verify that NO status history was saved
        Integer histCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_status_history", Integer.class);
        assertThat(histCount).isEqualTo(0);

        // Turn off failure and retry: submission must succeed cleanly without duplicate application conflict
        TransactionTestApplicationRepository.failStatusHistory = false;
        var result = applicationService.submitApplication(jobId, req);
        assertThat(result).isNotNull();
        assertThat(result.applicationId()).isNotNull();
        assertThat(result.status()).isEqualTo(ApplicationStatus.APPLIED);

        // Now application exists
        Integer finalAppCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM applications WHERE candidate_id = ? AND job_id = ?",
                Integer.class, candidate.candidateId(), jobId);
        assertThat(finalAppCount).isEqualTo(1);
    }

    @Test
    @DisplayName("P5.8-TX2: Application withdrawal transaction rolls back if status history fails")
    void applicationWithdrawalRollsBack_whenStatusHistoryFails() throws Exception {
        UserAuth candidate = registerCandidate("tx.wth.fail@example.com", "Tx Wth Fail");
        seedCandidateProfile(candidate, "Tx Wth Fail");
        Long jobId = createActiveJob("wthfail");

        authenticateAs(candidate);

        // First submit an application normally
        TransactionTestApplicationRepository.failStatusHistory = false;
        ApplicationSubmissionRequest req = new ApplicationSubmissionRequest();
        req.setCoverLetter("Normal application");
        var submitted = applicationService.submitApplication(jobId, req);
        Long appId = submitted.applicationId();

        // Verify status in DB is APPLIED
        String statusBefore = jdbcTemplate.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, appId);
        assertThat(statusBefore).isEqualTo("APPLIED");

        // Turn on simulated failure in status history insertion
        TransactionTestApplicationRepository.failStatusHistory = true;

        // Attempt withdrawal
        assertThatThrownBy(() -> applicationService.withdrawApplication(appId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated status history insertion failure");

        // Verify that application status was ROLLED BACK to APPLIED in the database
        String statusAfter = jdbcTemplate.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, appId);
        assertThat(statusAfter).isEqualTo("APPLIED");

        // Verify no extra status history entry was saved
        Integer histCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_status_history WHERE application_id = ?",
                Integer.class, appId);
        assertThat(histCount).isEqualTo(1); // Only the initial APPLIED history row exists

        // Turn off failure and retry withdrawal: should succeed cleanly
        TransactionTestApplicationRepository.failStatusHistory = false;
        var withdrawn = applicationService.withdrawApplication(appId);
        assertThat(withdrawn.status()).isEqualTo(ApplicationStatus.WITHDRAWN);

        String statusFinal = jdbcTemplate.queryForObject(
                "SELECT status FROM applications WHERE id = ?", String.class, appId);
        assertThat(statusFinal).isEqualTo("WITHDRAWN");

        Integer histCountFinal = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_status_history WHERE application_id = ?",
                Integer.class, appId);
        assertThat(histCountFinal).isEqualTo(2); // APPLIED + WITHDRAWN
    }
}
