package com.careerconnect.jobs.controller;

import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.common.model.Role;
import com.careerconnect.auth.service.AuthService;
import com.careerconnect.jobs.dto.RecruiterJobUpdateRequest;
import com.careerconnect.jobs.model.ExperienceLevel;
import com.careerconnect.jobs.model.JobStatus;
import com.careerconnect.jobs.model.JobType;
import com.careerconnect.jobs.model.WorkMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecruiterJobMutationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.careerconnect.security.JwtService jwtService;

    // Helper: Register Recruiter and return JWT token
    private String registerRecruiterAndGetToken(String emailPrefix) {
        String email = emailPrefix + "_" + UUID.randomUUID() + "@example.com";
        var res = authService.register(new RegisterRequest(email, "Password@123", Role.RECRUITER, "Test Recruiter"));
        return res.getAccessToken();
    }

    // Helper: Register Candidate and return JWT token
    private String registerCandidateAndGetToken(String emailPrefix) {
        String email = emailPrefix + "_" + UUID.randomUUID() + "@example.com";
        var res = authService.register(new RegisterRequest(email, "Password@123", Role.CANDIDATE, "Test Candidate"));
        return res.getAccessToken();
    }

    // Helper: Admin JWT token
    private String getAdminToken() {
        com.careerconnect.security.SecurityUser adminUser = new com.careerconnect.security.SecurityUser(
                1L, "admin.test@example.com", "hash", Role.ADMIN, "System Admin",
                null, null, null, true
        );
        return jwtService.generateToken(adminUser);
    }

    // Helper: extract recruiter ID from token
    private Long getRecruiterIdFromToken(String token) {
        return jdbcTemplate.queryForObject(
                "SELECT r.id FROM recruiters r JOIN users u ON r.user_id = u.id WHERE u.email = ?",
                Long.class,
                extractEmailFromToken(token)
        );
    }

    // Helper: extract candidate ID from token
    private Long getCandidateIdFromToken(String token) {
        return jdbcTemplate.queryForObject(
                "SELECT c.id FROM candidates c JOIN users u ON c.user_id = u.id WHERE u.email = ?",
                Long.class,
                extractEmailFromToken(token)
        );
    }

    private String extractEmailFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            JsonNode node = objectMapper.readTree(payload);
            return node.get("sub").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode token", e);
        }
    }

    // Helper: Create company directly in DB
    private Long createCompany(String name) {
        KeyHolder kh = new GeneratedKeyHolder();
        String sql = "INSERT INTO companies (name, industry, location, website, company_size, is_active) VALUES (?, 'IT', 'Bengaluru', 'https://example.com', '51-200', true)";
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name + "_" + UUID.randomUUID());
            return ps;
        }, kh);
        return com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
    }

    private void associateRecruiterWithCompany(Long recruiterId, Long companyId) {
        jdbcTemplate.update("UPDATE recruiters SET company_id = ? WHERE id = ?", companyId, recruiterId);
    }

    private Long getOrCreateSkill(String name) {
        String norm = name.trim().toLowerCase();
        List<Long> ids = jdbcTemplate.query("SELECT id FROM skills WHERE normalized_name = ?", (rs, rowNum) -> rs.getLong("id"), norm);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO skills (name, normalized_name) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, norm);
            return ps;
        }, kh);
        return com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
    }

    private Long createJob(Long companyId, String title, String description, JobStatus status,
                           LocalDate deadline, String workMode, String location,
                           BigDecimal minSalary, BigDecimal maxSalary, List<Long> skillIds) {
        KeyHolder kh = new GeneratedKeyHolder();
        String sql = """
                INSERT INTO jobs (company_id, title, description, responsibilities, requirements,
                                  job_type, experience_level, work_mode, location, salary_min_lpa,
                                  salary_max_lpa, salary_currency, application_deadline, status)
                VALUES (?, ?, ?, 'Core responsibilities', 'Core requirements', 'FULL_TIME', 'FRESHER', ?, ?, ?, ?, 'INR', ?, ?)
                """;
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            ps.setString(2, title);
            ps.setString(3, description);
            ps.setString(4, workMode);
            ps.setString(5, location);
            ps.setBigDecimal(6, minSalary);
            ps.setBigDecimal(7, maxSalary);
            ps.setDate(8, deadline != null ? Date.valueOf(deadline) : null);
            ps.setString(9, status.name());
            return ps;
        }, kh);

        Long jobId = com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);

        if (skillIds != null) {
            for (Long sId : skillIds) {
                jdbcTemplate.update("INSERT INTO job_skills (job_id, skill_id) VALUES (?, ?)", jobId, sId);
            }
        }
        return jobId;
    }

    private Long createApplication(Long jobId, Long candidateId) {
        KeyHolder kh = new GeneratedKeyHolder();
        String sql = """
                INSERT INTO applications (job_id, candidate_id, cover_letter, status, candidate_snapshot, resume_snapshot)
                VALUES (?, ?, 'Cover letter', 'APPLIED', '{}', '{}')
                """;
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, jobId);
            ps.setLong(2, candidateId);
            return ps;
        }, kh);
        return com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
    }

    private RecruiterJobUpdateRequest createSampleUpdateRequest(List<Long> skillIds) {
        return new RecruiterJobUpdateRequest(
                "Updated Backend Engineer",
                "Updated detailed description of job",
                "Updated responsibilities",
                "Core requirements",
                JobType.FULL_TIME,
                ExperienceLevel.FRESHER,
                WorkMode.HYBRID,
                "Bengaluru, India",
                new BigDecimal("8.00"),
                new BigDecimal("15.00"),
                "INR",
                LocalDate.now().plusMonths(2),
                skillIds
        );
    }

    // =========================================================================
    // 1. AUTHENTICATION TESTS
    // =========================================================================

    @Test
    @DisplayName("1. Unauthenticated update -> 401")
    void unauthenticatedUpdate_Returns401() throws Exception {
        RecruiterJobUpdateRequest req = createSampleUpdateRequest(List.of(1L));
        mockMvc.perform(put("/api/recruiter/jobs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("2. Unauthenticated delete -> 401")
    void unauthenticatedDelete_Returns401() throws Exception {
        mockMvc.perform(delete("/api/recruiter/jobs/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    // =========================================================================
    // 2. ROLE TESTS
    // =========================================================================

    @Test
    @DisplayName("3. Candidate update -> 403")
    void candidateUpdate_Returns403() throws Exception {
        String token = registerCandidateAndGetToken("cand.update");
        RecruiterJobUpdateRequest req = createSampleUpdateRequest(List.of(1L));
        mockMvc.perform(put("/api/recruiter/jobs/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("4. Candidate delete -> 403")
    void candidateDelete_Returns403() throws Exception {
        String token = registerCandidateAndGetToken("cand.del");
        mockMvc.perform(delete("/api/recruiter/jobs/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("5. Admin update -> 403")
    void adminUpdate_Returns403() throws Exception {
        String token = getAdminToken();
        RecruiterJobUpdateRequest req = createSampleUpdateRequest(List.of(1L));
        mockMvc.perform(put("/api/recruiter/jobs/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("6. Admin delete -> 403")
    void adminDelete_Returns403() throws Exception {
        String token = getAdminToken();
        mockMvc.perform(delete("/api/recruiter/jobs/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    // =========================================================================
    // 3. COMPANY TESTS
    // =========================================================================

    @Test
    @DisplayName("7. Recruiter without company update -> 422 COMPANY_REQUIRED")
    void recruiterWithoutCompanyUpdate_Returns422() throws Exception {
        String token = registerRecruiterAndGetToken("rec.nocomp.upd");
        Long skillId = getOrCreateSkill("Java");
        RecruiterJobUpdateRequest req = createSampleUpdateRequest(List.of(skillId));

        mockMvc.perform(put("/api/recruiter/jobs/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("COMPANY_REQUIRED"));
    }

    @Test
    @DisplayName("8. Recruiter without company delete -> 422 COMPANY_REQUIRED")
    void recruiterWithoutCompanyDelete_Returns422() throws Exception {
        String token = registerRecruiterAndGetToken("rec.nocomp.del");

        mockMvc.perform(delete("/api/recruiter/jobs/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("COMPANY_REQUIRED"));
    }

    // =========================================================================
    // 4. OWNERSHIP TESTS
    // =========================================================================

    @Test
    @DisplayName("9. Recruiter updates own company's job successfully")
    void recruiterUpdatesOwnCompanyJob_Success() throws Exception {
        String token = registerRecruiterAndGetToken("rec.owner.upd");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Owner Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("Python");
        Long jobId = createJob(compId, "Old Python Dev", "Old Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "ONSITE", "Pune",
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        Long skillId2 = getOrCreateSkill("Django");
        RecruiterJobUpdateRequest req = new RecruiterJobUpdateRequest(
                "New Python Lead",
                "New comprehensive description",
                "Lead the backend team",
                "Django expertise",
                JobType.FULL_TIME,
                ExperienceLevel.FRESHER,
                WorkMode.REMOTE,
                null,
                new BigDecimal("12.00"),
                new BigDecimal("20.00"),
                "INR",
                LocalDate.now().plusMonths(3),
                List.of(skillId, skillId2)
        );

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("New Python Lead"))
                .andExpect(jsonPath("$.data.description").value("New comprehensive description"))
                .andExpect(jsonPath("$.data.workMode").value("REMOTE"))
                .andExpect(jsonPath("$.data.salaryMinLpa").value(12.00))
                .andExpect(jsonPath("$.data.salaryMaxLpa").value(20.00))
                .andExpect(jsonPath("$.data.skills.length()").value(2));
    }

    @Test
    @DisplayName("10. Recruiter cannot update another company's job -> 404 JOB_NOT_FOUND")
    void recruiterCannotUpdateOtherCompanyJob_Returns404() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.cross.a");
        Long recIdA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("Co A");
        associateRecruiterWithCompany(recIdA, compA);

        Long compB = createCompany("Co B");
        Long skillId = getOrCreateSkill("Ruby");
        Long jobB = createJob(compB, "Ruby Dev", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        RecruiterJobUpdateRequest req = createSampleUpdateRequest(List.of(skillId));

        mockMvc.perform(put("/api/recruiter/jobs/" + jobB)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    @Test
    @DisplayName("11. Recruiter cannot delete another company's job -> 404 JOB_NOT_FOUND")
    void recruiterCannotDeleteOtherCompanyJob_Returns404() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.cross.del");
        Long recIdA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("Co Del A");
        associateRecruiterWithCompany(recIdA, compA);

        Long compB = createCompany("Co Del B");
        Long skillId = getOrCreateSkill("Go");
        Long jobB = createJob(compB, "Go Dev", "Desc", JobStatus.CLOSED,
                LocalDate.now().minusDays(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        mockMvc.perform(delete("/api/recruiter/jobs/" + jobB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    @Test
    @DisplayName("12. Cross-company access returns 404 without leaking metadata")
    void crossCompanyAccessReturns404_NoLeak() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.leak.check");
        Long recIdA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("Co Leak A");
        associateRecruiterWithCompany(recIdA, compA);

        Long compB = createCompany("Co Leak Secret");
        Long skillId = getOrCreateSkill("SecretSkill");
        Long jobB = createJob(compB, "Top Secret Job", "Classified", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("50.00"), new BigDecimal("100.00"), List.of(skillId));

        RecruiterJobUpdateRequest req = createSampleUpdateRequest(List.of(skillId));

        mockMvc.perform(put("/api/recruiter/jobs/" + jobB)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("13. Client companyId in query or payload cannot override ownership")
    void clientCompanyIdCannotOverrideOwnership() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.spoof.comp");
        Long recIdA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("Co Spoof A");
        associateRecruiterWithCompany(recIdA, compA);

        Long compB = createCompany("Co Spoof B");
        Long skillId = getOrCreateSkill("Rust");
        Long jobB = createJob(compB, "Rust Core Dev", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("10.00"), new BigDecimal("20.00"), List.of(skillId));

        String maliciousBody = """
                {
                  "companyId": %d,
                  "title": "Hacked Title",
                  "description": "Hacked Desc",
                  "jobType": "FULL_TIME",
                  "experienceLevel": "FRESHER",
                  "workMode": "REMOTE",
                  "salaryMinLpa": 10.00,
                  "salaryMaxLpa": 20.00,
                  "applicationDeadline": "%s",
                  "requiredSkillIds": [%d]
                }
                """.formatted(compB, LocalDate.now().plusMonths(1), skillId);

        mockMvc.perform(put("/api/recruiter/jobs/" + jobB + "?companyId=" + compB)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    // =========================================================================
    // 5. VALIDATION TESTS
    // =========================================================================

    @Test
    @DisplayName("14. Missing title rejected -> 400 Bad Request")
    void missingTitle_Returns400() throws Exception {
        String token = registerRecruiterAndGetToken("rec.val.notitle");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Val Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("Kotlin");
        Long jobId = createJob(compId, "Old Title", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        String body = """
                {
                  "title": "   ",
                  "description": "Valid desc",
                  "jobType": "FULL_TIME",
                  "experienceLevel": "FRESHER",
                  "workMode": "REMOTE",
                  "applicationDeadline": "%s",
                  "requiredSkillIds": [%d]
                }
                """.formatted(LocalDate.now().plusMonths(1), skillId);

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("15. Missing required skill rejected -> 400/422")
    void missingRequiredSkill_ReturnsError() throws Exception {
        String token = registerRecruiterAndGetToken("rec.val.noskill");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Val Skill Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("C++");
        Long jobId = createJob(compId, "C++ Dev", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        String body = """
                {
                  "title": "Valid Title",
                  "description": "Valid desc",
                  "jobType": "FULL_TIME",
                  "experienceLevel": "FRESHER",
                  "workMode": "REMOTE",
                  "applicationDeadline": "%s",
                  "requiredSkillIds": []
                }
                """.formatted(LocalDate.now().plusMonths(1));

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("16. Invalid salary rejected (negative min salary) -> 400/422")
    void negativeMinSalary_ReturnsError() throws Exception {
        String token = registerRecruiterAndGetToken("rec.val.negmin");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Val NegSal Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("Scala");
        Long jobId = createJob(compId, "Scala Dev", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        String body = """
                {
                  "title": "Valid Title",
                  "description": "Valid desc",
                  "jobType": "FULL_TIME",
                  "experienceLevel": "FRESHER",
                  "workMode": "REMOTE",
                  "salaryMinLpa": -5.00,
                  "applicationDeadline": "%s",
                  "requiredSkillIds": [%d]
                }
                """.formatted(LocalDate.now().plusMonths(1), skillId);

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("17. Max salary below min salary rejected -> 422")
    void maxSalaryBelowMin_Returns422() throws Exception {
        String token = registerRecruiterAndGetToken("rec.val.salbounds");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Val SalBounds Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("Perl");
        Long jobId = createJob(compId, "Perl Dev", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        RecruiterJobUpdateRequest req = new RecruiterJobUpdateRequest(
                "Perl Dev",
                "Valid Desc",
                null,
                null,
                JobType.FULL_TIME,
                ExperienceLevel.FRESHER,
                WorkMode.REMOTE,
                null,
                new BigDecimal("15.00"),
                new BigDecimal("10.00"),
                "INR",
                LocalDate.now().plusMonths(1),
                List.of(skillId)
        );

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("18. Non-remote job without location rejected -> 422")
    void nonRemoteJobWithoutLocation_Returns422() throws Exception {
        String token = registerRecruiterAndGetToken("rec.val.nolocation");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Val NoLoc Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("C#");
        Long jobId = createJob(compId, "C# Dev", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        RecruiterJobUpdateRequest req = new RecruiterJobUpdateRequest(
                "C# Dev",
                "Valid Desc",
                null,
                null,
                JobType.FULL_TIME,
                ExperienceLevel.FRESHER,
                WorkMode.ONSITE,
                "   ",
                new BigDecimal("5.00"),
                new BigDecimal("10.00"),
                "INR",
                LocalDate.now().plusMonths(1),
                List.of(skillId)
        );

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("19. Past deadline rejected -> 400/422")
    void pastDeadline_ReturnsError() throws Exception {
        String token = registerRecruiterAndGetToken("rec.val.pastdeadline");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Val PastDeadline Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("Swift");
        Long jobId = createJob(compId, "Swift Dev", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        String body = """
                {
                  "title": "Swift Dev",
                  "description": "Valid Desc",
                  "jobType": "FULL_TIME",
                  "experienceLevel": "FRESHER",
                  "workMode": "REMOTE",
                  "applicationDeadline": "%s",
                  "requiredSkillIds": [%d]
                }
                """.formatted(LocalDate.now().minusDays(2), skillId);

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("20. Invalid enum value rejected -> 400 Bad Request")
    void invalidEnum_Returns400() throws Exception {
        String token = registerRecruiterAndGetToken("rec.val.invalsenum");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Val Enum Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("SQL");
        Long jobId = createJob(compId, "DBA", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        String body = """
                {
                  "title": "DBA",
                  "description": "Valid Desc",
                  "jobType": "SUPER_JOB_TYPE",
                  "experienceLevel": "FRESHER",
                  "workMode": "REMOTE",
                  "applicationDeadline": "%s",
                  "requiredSkillIds": [%d]
                }
                """.formatted(LocalDate.now().plusMonths(1), skillId);

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    // =========================================================================
    // 6. UPDATE TESTS
    // =========================================================================

    @Test
    @DisplayName("21. DRAFT job can be edited")
    void draftJobCanBeEdited() throws Exception {
        String token = registerRecruiterAndGetToken("rec.upd.draft");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Draft Edit Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("Elixir");
        Long jobId = createJob(compId, "Elixir Junior", "Desc Initial", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("6.00"), new BigDecimal("10.00"), List.of(skillId));

        RecruiterJobUpdateRequest req = new RecruiterJobUpdateRequest(
                "Elixir Intermediate",
                "Updated Description for Draft",
                "New Responsibilities",
                "New Requirements",
                JobType.FULL_TIME,
                ExperienceLevel.ZERO_TO_ONE_YEARS,
                WorkMode.REMOTE,
                null,
                new BigDecimal("8.00"),
                new BigDecimal("14.00"),
                "INR",
                LocalDate.now().plusMonths(2),
                List.of(skillId)
        );

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Elixir Intermediate"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("22. ACTIVE job can be edited when permitted (zero applications)")
    void activeJobWithZeroApplications_CanBeEdited() throws Exception {
        String token = registerRecruiterAndGetToken("rec.upd.actzero");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Active Zero Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("TypeScript");
        Long jobId = createJob(compId, "TS Dev Active", "Desc Initial", JobStatus.ACTIVE,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("7.00"), new BigDecimal("12.00"), List.of(skillId));

        RecruiterJobUpdateRequest req = new RecruiterJobUpdateRequest(
                "TS Senior Dev Active",
                "New Active Desc",
                "New Tasks",
                "TypeScript expert",
                JobType.FULL_TIME,
                ExperienceLevel.ONE_TO_TWO_YEARS,
                WorkMode.REMOTE,
                null,
                new BigDecimal("10.00"),
                new BigDecimal("18.00"),
                "INR",
                LocalDate.now().plusMonths(2),
                List.of(skillId)
        );

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("TS Senior Dev Active"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("23. Correct fields are persisted in MySQL")
    void correctFieldsArePersisted() throws Exception {
        String token = registerRecruiterAndGetToken("rec.persist.chk");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Persist Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("PostgreSQL");
        Long jobId = createJob(compId, "DB Admin", "Initial Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "ONSITE", "Mumbai",
                new BigDecimal("6.00"), new BigDecimal("9.00"), List.of(skillId));

        LocalDate newDeadline = LocalDate.now().plusDays(45);
        RecruiterJobUpdateRequest req = new RecruiterJobUpdateRequest(
                "Lead DB Administrator",
                "Deep knowledge of query optimization",
                "Manage database replication",
                "5+ years PostgreSQL experience",
                JobType.FULL_TIME,
                ExperienceLevel.ONE_TO_TWO_YEARS,
                WorkMode.HYBRID,
                "Navi Mumbai",
                new BigDecimal("12.50"),
                new BigDecimal("22.00"),
                "INR",
                newDeadline,
                List.of(skillId)
        );

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Verify database fields directly
        var row = jdbcTemplate.queryForMap("SELECT * FROM jobs WHERE id = ?", jobId);
        assertThat(row.get("title")).isEqualTo("Lead DB Administrator");
        assertThat(row.get("description")).isEqualTo("Deep knowledge of query optimization");
        assertThat(row.get("responsibilities")).isEqualTo("Manage database replication");
        assertThat(row.get("requirements")).isEqualTo("5+ years PostgreSQL experience");
        assertThat(row.get("job_type")).isEqualTo("FULL_TIME");
        assertThat(row.get("experience_level")).isEqualTo("ONE_TO_TWO_YEARS");
        assertThat(row.get("work_mode")).isEqualTo("HYBRID");
        assertThat(row.get("location")).isEqualTo("Navi Mumbai");
        assertThat(new BigDecimal(row.get("salary_min_lpa").toString())).isEqualByComparingTo("12.50");
        assertThat(new BigDecimal(row.get("salary_max_lpa").toString())).isEqualByComparingTo("22.00");
    }

    @Test
    @DisplayName("24. Required skills are correctly replaced/updated")
    void requiredSkillsAreCorrectlyReplaced() throws Exception {
        String token = registerRecruiterAndGetToken("rec.skills.repl");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Skills Replace Co");
        associateRecruiterWithCompany(recId, compId);

        Long skill1 = getOrCreateSkill("SkillA");
        Long skill2 = getOrCreateSkill("SkillB");
        Long skill3 = getOrCreateSkill("SkillC");

        Long jobId = createJob(compId, "Multi Skill Job", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skill1, skill2));

        // Replace with skill2 and skill3
        RecruiterJobUpdateRequest req = new RecruiterJobUpdateRequest(
                "Multi Skill Job",
                "Desc",
                null,
                null,
                JobType.FULL_TIME,
                ExperienceLevel.FRESHER,
                WorkMode.REMOTE,
                null,
                new BigDecimal("5.00"),
                new BigDecimal("10.00"),
                "INR",
                LocalDate.now().plusMonths(1),
                List.of(skill2, skill3)
        );

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        List<Long> actualSkills = jdbcTemplate.queryForList(
                "SELECT skill_id FROM job_skills WHERE job_id = ? ORDER BY skill_id ASC",
                Long.class,
                jobId
        );
        assertThat(actualSkills).containsExactlyInAnyOrder(skill2, skill3);
    }

    @Test
    @DisplayName("25. Transaction rolls back when skill validation fails")
    void transactionRollsBackOnInvalidSkill() throws Exception {
        String token = registerRecruiterAndGetToken("rec.rollback.test");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Rollback Co");
        associateRecruiterWithCompany(recId, compId);

        Long validSkill = getOrCreateSkill("ValidSkill");
        Long jobId = createJob(compId, "Original Title", "Original Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(validSkill));

        // Pass a non-existent skill ID
        RecruiterJobUpdateRequest req = new RecruiterJobUpdateRequest(
                "Changed Title Attempt",
                "Changed Desc Attempt",
                null,
                null,
                JobType.FULL_TIME,
                ExperienceLevel.FRESHER,
                WorkMode.REMOTE,
                null,
                new BigDecimal("5.00"),
                new BigDecimal("10.00"),
                "INR",
                LocalDate.now().plusMonths(1),
                List.of(validSkill, 99999999L)
        );

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        // Verify title was not changed in DB
        String title = jdbcTemplate.queryForObject("SELECT title FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(title).isEqualTo("Original Title");
    }

    @Test
    @DisplayName("26. Job detail reflects updated values")
    void jobDetailReflectsUpdatedValues() throws Exception {
        String token = registerRecruiterAndGetToken("rec.detail.reflect");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Detail Reflect Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("Vue");
        Long jobId = createJob(compId, "Vue Dev", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("6.00"), new BigDecimal("10.00"), List.of(skillId));

        RecruiterJobUpdateRequest req = new RecruiterJobUpdateRequest(
                "Vue 3 Lead Architect",
                "New description for Vue",
                null,
                null,
                JobType.FULL_TIME,
                ExperienceLevel.FRESHER,
                WorkMode.REMOTE,
                null,
                new BigDecimal("15.00"),
                new BigDecimal("25.00"),
                "INR",
                LocalDate.now().plusMonths(2),
                List.of(skillId)
        );

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Call detail endpoint
        mockMvc.perform(get("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Vue 3 Lead Architect"))
                .andExpect(jsonPath("$.data.salaryMinLpa").value(15.00))
                .andExpect(jsonPath("$.data.salaryMaxLpa").value(25.00));
    }

    @Test
    @DisplayName("27. Job listing reflects updated values")
    void jobListingReflectsUpdatedValues() throws Exception {
        String token = registerRecruiterAndGetToken("rec.list.reflect");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("List Reflect Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("Angular");
        Long jobId = createJob(compId, "Angular Dev", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        RecruiterJobUpdateRequest req = new RecruiterJobUpdateRequest(
                "Angular 18 Expert",
                "Updated desc",
                null,
                null,
                JobType.FULL_TIME,
                ExperienceLevel.FRESHER,
                WorkMode.REMOTE,
                null,
                new BigDecimal("8.00"),
                new BigDecimal("16.00"),
                "INR",
                LocalDate.now().plusMonths(2),
                List.of(skillId)
        );

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Call list endpoint
        mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + jobId + ")].title").value("Angular 18 Expert"));
    }

    // =========================================================================
    // 7. APPLICATION RESTRICTIONS TESTS
    // =========================================================================

    @Test
    @DisplayName("28. Job with applications cannot change fundamental requirements")
    void jobWithApplications_FundamentalChangeRejected() throws Exception {
        String token = registerRecruiterAndGetToken("rec.app.fundlock");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("App Lock Co");
        associateRecruiterWithCompany(recId, compId);

        String candToken = registerCandidateAndGetToken("cand.app.fundlock");
        Long candId = getCandidateIdFromToken(candToken);

        Long skill1 = getOrCreateSkill("JavaLock");
        Long jobId = createJob(compId, "Java Locked Role", "Original Desc", JobStatus.ACTIVE,
                LocalDate.now().plusMonths(1), "ONSITE", "Delhi",
                new BigDecimal("6.00"), new BigDecimal("12.00"), List.of(skill1));

        // Candidate submits application
        createApplication(jobId, candId);

        // Attempt to change title
        RecruiterJobUpdateRequest reqTitleChange = new RecruiterJobUpdateRequest(
                "Java Changed Role",
                "Original Desc",
                "Core responsibilities",
                "Core requirements",
                JobType.FULL_TIME,
                ExperienceLevel.FRESHER,
                WorkMode.ONSITE,
                "Delhi",
                new BigDecimal("6.00"),
                new BigDecimal("12.00"),
                "INR",
                LocalDate.now().plusMonths(1),
                List.of(skill1)
        );

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqTitleChange)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // Attempt to change required skills
        Long skill2 = getOrCreateSkill("GoLock");
        RecruiterJobUpdateRequest reqSkillChange = new RecruiterJobUpdateRequest(
                "Java Locked Role",
                "Original Desc",
                "Core responsibilities",
                "Core requirements",
                JobType.FULL_TIME,
                ExperienceLevel.FRESHER,
                WorkMode.ONSITE,
                "Delhi",
                new BigDecimal("6.00"),
                new BigDecimal("12.00"),
                "INR",
                LocalDate.now().plusMonths(1),
                List.of(skill2)
        );

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqSkillChange)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // Attempt to change work mode
        RecruiterJobUpdateRequest reqModeChange = new RecruiterJobUpdateRequest(
                "Java Locked Role",
                "Original Desc",
                "Core responsibilities",
                "Core requirements",
                JobType.FULL_TIME,
                ExperienceLevel.FRESHER,
                WorkMode.REMOTE,
                null,
                new BigDecimal("6.00"),
                new BigDecimal("12.00"),
                "INR",
                LocalDate.now().plusMonths(1),
                List.of(skill1)
        );

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqModeChange)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("29. Job with applications can change permitted non-fundamental fields")
    void jobWithApplications_CanChangeNonFundamentalFields() throws Exception {
        String token = registerRecruiterAndGetToken("rec.app.nonfund");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("NonFund Co");
        associateRecruiterWithCompany(recId, compId);

        String candToken = registerCandidateAndGetToken("cand.app.nonfund");
        Long candId = getCandidateIdFromToken(candToken);

        Long skillId = getOrCreateSkill("SecurityDev");
        LocalDate originalDeadline = LocalDate.now().plusDays(20);
        Long jobId = createJob(compId, "SecOps Engineer", "Original Description", JobStatus.ACTIVE,
                originalDeadline, "ONSITE", "Hyderabad",
                new BigDecimal("10.00"), new BigDecimal("15.00"), List.of(skillId));

        createApplication(jobId, candId);

        // Update description, responsibilities, and extend deadline
        LocalDate extendedDeadline = LocalDate.now().plusDays(40);
        RecruiterJobUpdateRequest req = new RecruiterJobUpdateRequest(
                "SecOps Engineer", // Unchanged
                "Updated elaborate description of the SecOps role with company culture notes",
                "Updated responsibilities: Monitor SIEM alerts and incident response",
                "Core requirements", // Unchanged
                JobType.FULL_TIME, // Unchanged
                ExperienceLevel.FRESHER, // Unchanged
                WorkMode.ONSITE, // Unchanged
                "Hyderabad - Hitech City Office", // Location updated detail
                new BigDecimal("10.00"), // Unchanged
                new BigDecimal("15.00"), // Unchanged
                "INR", // Unchanged
                extendedDeadline,
                List.of(skillId) // Unchanged
        );

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Updated elaborate description of the SecOps role with company culture notes"))
                .andExpect(jsonPath("$.data.location").value("Hyderabad - Hitech City Office"))
                .andExpect(jsonPath("$.data.applicationDeadline").value(extendedDeadline.toString()));
    }

    @Test
    @DisplayName("30. Rejected fundamental update makes NO partial database changes")
    void rejectedFundamentalUpdate_NoPartialChanges() throws Exception {
        String token = registerRecruiterAndGetToken("rec.app.nopartial");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("NoPartial Co");
        associateRecruiterWithCompany(recId, compId);

        String candToken = registerCandidateAndGetToken("cand.app.nopartial");
        Long candId = getCandidateIdFromToken(candToken);

        Long skillId = getOrCreateSkill("IntegritySkill");
        Long jobId = createJob(compId, "Original Immutable Title", "Original Desc", JobStatus.ACTIVE,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("8.00"), new BigDecimal("12.00"), List.of(skillId));

        createApplication(jobId, candId);

        // Request changes both non-fundamental (description) and fundamental (title)
        RecruiterJobUpdateRequest req = new RecruiterJobUpdateRequest(
                "Changed Title Attempt",
                "Changed Description Attempt",
                null,
                "Core requirements",
                JobType.FULL_TIME,
                ExperienceLevel.FRESHER,
                WorkMode.REMOTE,
                null,
                new BigDecimal("8.00"),
                new BigDecimal("12.00"),
                "INR",
                LocalDate.now().plusMonths(1),
                List.of(skillId)
        );

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // Verify description was NOT partially updated
        var row = jdbcTemplate.queryForMap("SELECT title, description FROM jobs WHERE id = ?", jobId);
        assertThat(row.get("title")).isEqualTo("Original Immutable Title");
        assertThat(row.get("description")).isEqualTo("Original Desc");
    }

    // =========================================================================
    // 8. DELETE TESTS
    // =========================================================================

    @Test
    @DisplayName("31. Job with zero applications can be soft-deleted")
    void jobWithZeroApplications_CanBeSoftDeleted() throws Exception {
        String token = registerRecruiterAndGetToken("rec.del.zero");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Del Zero Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("DelSkill");
        Long jobId = createJob(compId, "Job To Delete", "Desc", JobStatus.CLOSED,
                LocalDate.now().minusDays(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        mockMvc.perform(delete("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Verify soft-deleted in DB: deleted_at IS NOT NULL
        var deletedAt = jdbcTemplate.queryForObject("SELECT deleted_at FROM jobs WHERE id = ?", java.time.Instant.class, jobId);
        assertThat(deletedAt).isNotNull();
    }

    @Test
    @DisplayName("32. Deleted job no longer appears in recruiter listing")
    void deletedJob_NotInRecruiterListing() throws Exception {
        String token = registerRecruiterAndGetToken("rec.del.listing");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Del Listing Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("ListDelSkill");
        Long jobId = createJob(compId, "Job Listing Del", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        // Delete job
        mockMvc.perform(delete("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Query listing
        mockMvc.perform(get("/api/recruiter/jobs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + jobId + ")]").doesNotExist());
    }

    @Test
    @DisplayName("33. Deleted job no longer returns from recruiter detail -> 404")
    void deletedJob_NotInRecruiterDetail() throws Exception {
        String token = registerRecruiterAndGetToken("rec.del.detail");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Del Detail Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("DetailDelSkill");
        Long jobId = createJob(compId, "Job Detail Del", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        mockMvc.perform(delete("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    @Test
    @DisplayName("34. Job with applications cannot be deleted -> 422")
    void jobWithApplications_CannotBeDeleted() throws Exception {
        String token = registerRecruiterAndGetToken("rec.del.withapp");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Del App Co");
        associateRecruiterWithCompany(recId, compId);

        String candToken = registerCandidateAndGetToken("cand.del.withapp");
        Long candId = getCandidateIdFromToken(candToken);

        Long skillId = getOrCreateSkill("AppSkill");
        Long jobId = createJob(compId, "Job With App", "Desc", JobStatus.CLOSED,
                LocalDate.now().minusDays(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        createApplication(jobId, candId);

        mockMvc.perform(delete("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // Confirm not deleted in DB
        var deletedAt = jdbcTemplate.queryForObject("SELECT deleted_at FROM jobs WHERE id = ?", java.time.Instant.class, jobId);
        assertThat(deletedAt).isNull();
    }

    @Test
    @DisplayName("35. Application records remain intact after blocked deletion")
    void applicationRecordsRemainIntact() throws Exception {
        String token = registerRecruiterAndGetToken("rec.intact.app");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Intact App Co");
        associateRecruiterWithCompany(recId, compId);

        String candToken = registerCandidateAndGetToken("cand.intact.app");
        Long candId = getCandidateIdFromToken(candToken);

        Long skillId = getOrCreateSkill("IntactSkill");
        Long jobId = createJob(compId, "Job Intact", "Desc", JobStatus.CLOSED,
                LocalDate.now().minusDays(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        Long appId = createApplication(jobId, candId);

        mockMvc.perform(delete("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());

        // Confirm application still exists and unchanged
        var appRow = jdbcTemplate.queryForMap("SELECT * FROM applications WHERE id = ?", appId);
        assertThat(appRow.get("job_id")).isEqualTo(jobId);
        assertThat(appRow.get("candidate_id")).isEqualTo(candId);
        assertThat(appRow.get("status")).isEqualTo("APPLIED");
    }

    @Test
    @DisplayName("36. Cross-company deletion is rejected -> 404 JOB_NOT_FOUND")
    void crossCompanyDeletionRejected() throws Exception {
        String tokenA = registerRecruiterAndGetToken("rec.cross.delchk");
        Long recIdA = getRecruiterIdFromToken(tokenA);
        Long compA = createCompany("Co A Cross");
        associateRecruiterWithCompany(recIdA, compA);

        Long compB = createCompany("Co B Cross");
        Long skillId = getOrCreateSkill("SkillX");
        Long jobB = createJob(compB, "Job B Zero App", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        mockMvc.perform(delete("/api/recruiter/jobs/" + jobB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        // Job in Co B remains not deleted
        var deletedAt = jdbcTemplate.queryForObject("SELECT deleted_at FROM jobs WHERE id = ?", java.time.Instant.class, jobB);
        assertThat(deletedAt).isNull();
    }

    // =========================================================================
    // 9. STATUS CONSTRAINTS TESTS
    // =========================================================================

    @Test
    @DisplayName("37. Generic edit endpoint cannot publish (status remains DRAFT)")
    void genericEditCannotPublish() throws Exception {
        String token = registerRecruiterAndGetToken("rec.stat.publish");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Status Pub Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("StatusSkill");
        Long jobId = createJob(compId, "Draft State Job", "Desc", JobStatus.DRAFT,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        String bodyWithStatus = """
                {
                  "title": "Updated Draft Title",
                  "description": "Desc",
                  "jobType": "FULL_TIME",
                  "experienceLevel": "FRESHER",
                  "workMode": "REMOTE",
                  "status": "ACTIVE",
                  "applicationDeadline": "%s",
                  "requiredSkillIds": [%d]
                }
                """.formatted(LocalDate.now().plusMonths(1), skillId);

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithStatus))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        // Confirm database still has status = DRAFT
        String statusInDb = jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(statusInDb).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("38. Generic edit endpoint cannot close (status remains ACTIVE)")
    void genericEditCannotClose() throws Exception {
        String token = registerRecruiterAndGetToken("rec.stat.close");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Status Close Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("CloseSkill");
        Long jobId = createJob(compId, "Active Job", "Desc", JobStatus.ACTIVE,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        String bodyWithStatus = """
                {
                  "title": "Active Job",
                  "description": "Desc Updated",
                  "jobType": "FULL_TIME",
                  "experienceLevel": "FRESHER",
                  "workMode": "REMOTE",
                  "status": "CLOSED",
                  "applicationDeadline": "%s",
                  "requiredSkillIds": [%d]
                }
                """.formatted(LocalDate.now().plusMonths(1), skillId);

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithStatus))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        String statusInDb = jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(statusInDb).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("39. Generic edit endpoint cannot archive")
    void genericEditCannotArchive() throws Exception {
        String token = registerRecruiterAndGetToken("rec.stat.archive");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Status Archive Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("ArchiveSkill");
        Long jobId = createJob(compId, "Active Job 2", "Desc", JobStatus.ACTIVE,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        String bodyWithStatus = """
                {
                  "title": "Active Job 2",
                  "description": "Desc Updated",
                  "jobType": "FULL_TIME",
                  "experienceLevel": "FRESHER",
                  "workMode": "REMOTE",
                  "status": "ARCHIVED",
                  "applicationDeadline": "%s",
                  "requiredSkillIds": [%d]
                }
                """.formatted(LocalDate.now().plusMonths(1), skillId);

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithStatus))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        String statusInDb = jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, jobId);
        assertThat(statusInDb).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("40. Archived jobs cannot be modified or deleted by recruiter -> 422")
    void archivedJobCannotBeModifiedOrDeleted() throws Exception {
        String token = registerRecruiterAndGetToken("rec.arch.lock");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Archived Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("ArchivedSkill");
        Long jobId = createJob(compId, "Archived Job", "Desc", JobStatus.ARCHIVED,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        RecruiterJobUpdateRequest req = createSampleUpdateRequest(List.of(skillId));

        // Attempt update
        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // Attempt delete
        mockMvc.perform(delete("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("40b. Closed jobs cannot be modified by recruiter generic edit -> 422")
    void closedJobCannotBeModified() throws Exception {
        String token = registerRecruiterAndGetToken("rec.closed.nomod");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Closed NoMod Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("ClosedSkill");
        Long jobId = createJob(compId, "Closed Job", "Desc", JobStatus.CLOSED,
                LocalDate.now().minusDays(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        RecruiterJobUpdateRequest req = createSampleUpdateRequest(List.of(skillId));

        mockMvc.perform(put("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("40c. Active jobs cannot be deleted directly without closing -> 422")
    void activeJobCannotBeDeletedDirectly() throws Exception {
        String token = registerRecruiterAndGetToken("rec.act.nodel");
        Long recId = getRecruiterIdFromToken(token);
        Long compId = createCompany("Active NoDel Co");
        associateRecruiterWithCompany(recId, compId);

        Long skillId = getOrCreateSkill("ActiveDelSkill");
        Long jobId = createJob(compId, "Active Zero Job", "Desc", JobStatus.ACTIVE,
                LocalDate.now().plusMonths(1), "REMOTE", null,
                new BigDecimal("5.00"), new BigDecimal("10.00"), List.of(skillId));

        mockMvc.perform(delete("/api/recruiter/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // Job in DB remains not deleted
        var deletedAt = jdbcTemplate.queryForObject("SELECT deleted_at FROM jobs WHERE id = ?", java.time.Instant.class, jobId);
        assertThat(deletedAt).isNull();
    }
}
