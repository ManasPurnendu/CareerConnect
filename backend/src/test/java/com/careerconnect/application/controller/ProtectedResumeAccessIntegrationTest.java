package com.careerconnect.application.controller;

import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.UpdateProfileRequest;
import com.careerconnect.common.model.Role;
import com.careerconnect.jobs.model.JobStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProtectedResumeAccessIntegrationTest {

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

    private RecruiterAuth registerRecruiter(String prefix) throws Exception {
        String email = prefix + "_" + UUID.randomUUID() + "@example.com";
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.RECRUITER, "Recruiter " + prefix);
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

    private CandidateAuth registerCandidate(String prefix) throws Exception {
        String email = prefix + "_" + UUID.randomUUID() + "@example.com";
        String fullName = "Candidate " + prefix;
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
        return jwtService.generateToken(new SecurityUser(9999L, "admin.p74@example.com", "HASH", Role.ADMIN, "Admin User", null, null, null, true));
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

    private Long createJob(Long companyId, String title, JobStatus status) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO jobs (
                        company_id, title, description, job_type, experience_level,
                        work_mode, location, salary_min_lpa, salary_max_lpa, salary_currency,
                        application_deadline, status, created_at, updated_at
                    ) VALUES (?, ?, 'Job Description', 'INTERNSHIP', 'FRESHER', 'HYBRID', 'Bengaluru', 5.0, 10.0, 'INR', ?, ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            ps.setString(2, title);
            ps.setDate(3, Date.valueOf(LocalDate.now().plusDays(30)));
            ps.setString(4, status.name());
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
        MockMultipartFile file = new MockMultipartFile("file", cand.fullName() + "_resume.pdf", "application/pdf", pdfBytes);

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

    @Test
    @DisplayName("P7.4-1: Recruiter can access resume for applicant belonging to their company")
    void recruiterCanAccessOwnCompanyApplicantResume() throws Exception {
        RecruiterAuth recruiter = registerRecruiter("p74.rec.own");
        Long companyId = createCompany("Alpha Corp");
        linkRecruiterToCompany(recruiter.recruiterId(), companyId);
        Long jobId = createJob(companyId, "Backend Engineer", JobStatus.ACTIVE);

        CandidateAuth candidate = registerCandidate("p74.cand.own");
        setupCandidateReadiness(candidate, "Alice Resume Content Here");
        Long applicationId = applyToJob(candidate, jobId);

        // 1. Stream file (default)
        MvcResult result = mockMvc.perform(get("/api/recruiter/applications/" + applicationId + "/resume")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"Candidate p74.cand.own_resume.pdf\""))
                .andReturn();

        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(body).contains("%PDF-1.4").contains("Alice Resume Content Here");

        // 2. Stream file via /resume/download alias
        mockMvc.perform(get("/api/recruiter/applications/" + applicationId + "/resume/download")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"Candidate p74.cand.own_resume.pdf\""));

        // 3. Query metadata via ?metadata=true
        mockMvc.perform(get("/api/recruiter/applications/" + applicationId + "/resume?metadata=true")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalFilename").value("Candidate p74.cand.own_resume.pdf"))
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.data.isCurrent").value(true))
                .andExpect(jsonPath("$.data.storageKey").doesNotExist());
    }

    @Test
    @DisplayName("P7.4-2: Recruiter cannot access resume for foreign company applicant (returns generic 404)")
    void recruiterCannotAccessForeignCompanyApplicantResume() throws Exception {
        RecruiterAuth recAlpha = registerRecruiter("p74.rec.alpha");
        Long compAlpha = createCompany("Alpha Corp");
        linkRecruiterToCompany(recAlpha.recruiterId(), compAlpha);
        Long jobAlpha = createJob(compAlpha, "Alpha Engineer", JobStatus.ACTIVE);

        RecruiterAuth recBeta = registerRecruiter("p74.rec.beta");
        Long compBeta = createCompany("Beta Corp");
        linkRecruiterToCompany(recBeta.recruiterId(), compBeta);

        CandidateAuth candidate = registerCandidate("p74.cand.cross");
        setupCandidateReadiness(candidate, "Confidential Resume");
        Long applicationId = applyToJob(candidate, jobAlpha);

        // Recruiter Beta attempts to access application from Company Alpha
        mockMvc.perform(get("/api/recruiter/applications/" + applicationId + "/resume")
                        .header("Authorization", "Bearer " + recBeta.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Application with ID " + applicationId + " not found."));
    }

    @Test
    @DisplayName("P7.4-3: Guest cannot access recruiter resume endpoint -> 401")
    void guestCannotAccessRecruiterResume() throws Exception {
        mockMvc.perform(get("/api/recruiter/applications/1/resume"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("P7.4-4: Candidate cannot access recruiter resume endpoint -> 403")
    void candidateCannotAccessRecruiterResume() throws Exception {
        CandidateAuth cand = registerCandidate("p74.cand.forbidden");
        mockMvc.perform(get("/api/recruiter/applications/1/resume")
                        .header("Authorization", "Bearer " + cand.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("P7.4-5: Candidate can access own resume (metadata and download)")
    void candidateCanAccessOwnResume() throws Exception {
        CandidateAuth candidate = registerCandidate("p74.cand.ownres");
        setupCandidateReadiness(candidate, "Candidate Own Resume Body");

        // 1. Metadata via GET /api/candidate/resume
        mockMvc.perform(get("/api/candidate/resume")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalFilename").value("Candidate p74.cand.ownres_resume.pdf"))
                .andExpect(jsonPath("$.data.isCurrent").value(true));

        // 2. Download via GET /api/candidate/resume/download
        MvcResult dlResult = mockMvc.perform(get("/api/candidate/resume/download")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"Candidate p74.cand.ownres_resume.pdf\""))
                .andReturn();

        String content = new String(dlResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(content).contains("Candidate Own Resume Body");

        // 3. Download via ?download=true
        mockMvc.perform(get("/api/candidate/resume?download=true")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"));
    }

    @Test
    @DisplayName("P7.4-6: Candidate cannot access another candidate's resume")
    void candidateCannotAccessAnotherCandidatesResume() throws Exception {
        CandidateAuth candA = registerCandidate("p74.cand.a");
        setupCandidateReadiness(candA, "Candidate A Resume");

        CandidateAuth candB = registerCandidate("p74.cand.b");

        // Candidate B requests resume (has none uploaded yet)
        mockMvc.perform(get("/api/candidate/resume")
                        .header("Authorization", "Bearer " + candB.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Candidate B attempts parameter tampering: ?candidateId=A or ?userId=A
        mockMvc.perform(get("/api/candidate/resume?candidateId=" + candA.candidateId() + "&userId=" + candA.userId())
                        .header("Authorization", "Bearer " + candB.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/candidate/resume/download?candidateId=" + candA.candidateId())
                        .header("Authorization", "Bearer " + candB.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P7.4-7: Nonexistent application returns generic 404")
    void nonexistentApplication_returns404() throws Exception {
        RecruiterAuth recruiter = registerRecruiter("p74.rec.nonexistent");
        Long companyId = createCompany("Nonexistent Corp");
        linkRecruiterToCompany(recruiter.recruiterId(), companyId);

        mockMvc.perform(get("/api/recruiter/applications/999999/resume")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Application with ID 999999 not found."));
    }

    @Test
    @DisplayName("P7.4-8: Missing resume returns expected 404 error")
    void missingResume_returnsExpectedError() throws Exception {
        RecruiterAuth recruiter = registerRecruiter("p74.rec.missing");
        Long companyId = createCompany("Missing Resume Corp");
        linkRecruiterToCompany(recruiter.recruiterId(), companyId);
        Long jobId = createJob(companyId, "Lead Architect", JobStatus.ACTIVE);

        CandidateAuth candidate = registerCandidate("p74.cand.missing");
        setupCandidateReadiness(candidate, "Will be deleted");
        Long applicationId = applyToJob(candidate, jobId);

        // Candidate subsequently deletes their resume
        jdbcTemplate.update("DELETE FROM resumes WHERE candidate_id = ?", candidate.candidateId());

        mockMvc.perform(get("/api/recruiter/applications/" + applicationId + "/resume")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resume not found for applicant in application " + applicationId + "."));
    }

    @Test
    @DisplayName("P7.4-9: Soft-deleted job resume access returns generic 404")
    void softDeletedJob_resumeAccess_returns404() throws Exception {
        RecruiterAuth recruiter = registerRecruiter("p74.rec.softdel");
        Long companyId = createCompany("SoftDel Corp");
        linkRecruiterToCompany(recruiter.recruiterId(), companyId);
        Long jobId = createJob(companyId, "DevOps Engineer", JobStatus.ACTIVE);

        CandidateAuth candidate = registerCandidate("p74.cand.softdel");
        setupCandidateReadiness(candidate, "DevOps Resume");
        Long applicationId = applyToJob(candidate, jobId);

        // Soft-delete the job
        jdbcTemplate.update("UPDATE jobs SET deleted_at = CURRENT_TIMESTAMP(3) WHERE id = ?", jobId);

        mockMvc.perform(get("/api/recruiter/applications/" + applicationId + "/resume")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Application with ID " + applicationId + " not found."));
    }

    @Test
    @DisplayName("P7.4-10: Client-supplied candidateId cannot bypass ownership")
    void clientSuppliedCandidateIdCannotBypassOwnership() throws Exception {
        RecruiterAuth recAlpha = registerRecruiter("p74.rec.tamper.cand");
        Long compAlpha = createCompany("Alpha Tamper Corp");
        linkRecruiterToCompany(recAlpha.recruiterId(), compAlpha);
        Long jobAlpha = createJob(compAlpha, "Alpha Position", JobStatus.ACTIVE);

        RecruiterAuth recForeign = registerRecruiter("p74.rec.foreign.cand");
        Long compForeign = createCompany("Foreign Tamper Corp");
        linkRecruiterToCompany(recForeign.recruiterId(), compForeign);

        CandidateAuth candidate = registerCandidate("p74.cand.target");
        setupCandidateReadiness(candidate, "Target Resume");
        Long applicationId = applyToJob(candidate, jobAlpha);

        // Foreign recruiter passes ?candidateId=target
        mockMvc.perform(get("/api/recruiter/applications/" + applicationId + "/resume?candidateId=" + candidate.candidateId())
                        .header("Authorization", "Bearer " + recForeign.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P7.4-11: Client-supplied userId cannot bypass ownership")
    void clientSuppliedUserIdCannotBypassOwnership() throws Exception {
        RecruiterAuth recAlpha = registerRecruiter("p74.rec.tamper.usr");
        Long compAlpha = createCompany("Alpha Usr Corp");
        linkRecruiterToCompany(recAlpha.recruiterId(), compAlpha);
        Long jobAlpha = createJob(compAlpha, "Alpha Position", JobStatus.ACTIVE);

        RecruiterAuth recForeign = registerRecruiter("p74.rec.foreign.usr");
        Long compForeign = createCompany("Foreign Usr Corp");
        linkRecruiterToCompany(recForeign.recruiterId(), compForeign);

        CandidateAuth candidate = registerCandidate("p74.cand.usr.target");
        setupCandidateReadiness(candidate, "User Target Resume");
        Long applicationId = applyToJob(candidate, jobAlpha);

        // Foreign recruiter passes ?userId=target
        mockMvc.perform(get("/api/recruiter/applications/" + applicationId + "/resume?userId=" + candidate.userId())
                        .header("Authorization", "Bearer " + recForeign.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P7.4-12: Client-supplied companyId cannot bypass ownership")
    void clientSuppliedCompanyIdCannotBypassOwnership() throws Exception {
        RecruiterAuth recAlpha = registerRecruiter("p74.rec.tamper.cmp");
        Long compAlpha = createCompany("Alpha Cmp Corp");
        linkRecruiterToCompany(recAlpha.recruiterId(), compAlpha);
        Long jobAlpha = createJob(compAlpha, "Alpha Position", JobStatus.ACTIVE);

        RecruiterAuth recForeign = registerRecruiter("p74.rec.foreign.cmp");
        Long compForeign = createCompany("Foreign Cmp Corp");
        linkRecruiterToCompany(recForeign.recruiterId(), compForeign);

        CandidateAuth candidate = registerCandidate("p74.cand.cmp.target");
        setupCandidateReadiness(candidate, "Cmp Target Resume");
        Long applicationId = applyToJob(candidate, jobAlpha);

        // Foreign recruiter passes ?companyId=compAlpha
        mockMvc.perform(get("/api/recruiter/applications/" + applicationId + "/resume?companyId=" + compAlpha)
                        .header("Authorization", "Bearer " + recForeign.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P7.4-13: Client-supplied recruiterId cannot bypass ownership")
    void clientSuppliedRecruiterIdCannotBypassOwnership() throws Exception {
        RecruiterAuth recAlpha = registerRecruiter("p74.rec.tamper.rec");
        Long compAlpha = createCompany("Alpha Rec Corp");
        linkRecruiterToCompany(recAlpha.recruiterId(), compAlpha);
        Long jobAlpha = createJob(compAlpha, "Alpha Position", JobStatus.ACTIVE);

        RecruiterAuth recForeign = registerRecruiter("p74.rec.foreign.rec");
        Long compForeign = createCompany("Foreign Rec Corp");
        linkRecruiterToCompany(recForeign.recruiterId(), compForeign);

        CandidateAuth candidate = registerCandidate("p74.cand.rec.target");
        setupCandidateReadiness(candidate, "Rec Target Resume");
        Long applicationId = applyToJob(candidate, jobAlpha);

        // Foreign recruiter passes ?recruiterId=recAlpha
        mockMvc.perform(get("/api/recruiter/applications/" + applicationId + "/resume?recruiterId=" + recAlpha.recruiterId())
                        .header("Authorization", "Bearer " + recForeign.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P7.4-14: ResumeId tampering cannot bypass ownership")
    void resumeIdTamperingCannotBypassOwnership() throws Exception {
        RecruiterAuth recAlpha = registerRecruiter("p74.rec.tamper.res");
        Long compAlpha = createCompany("Alpha Res Corp");
        linkRecruiterToCompany(recAlpha.recruiterId(), compAlpha);
        Long jobAlpha = createJob(compAlpha, "Alpha Position", JobStatus.ACTIVE);

        RecruiterAuth recForeign = registerRecruiter("p74.rec.foreign.res");
        Long compForeign = createCompany("Foreign Res Corp");
        linkRecruiterToCompany(recForeign.recruiterId(), compForeign);

        CandidateAuth candidate = registerCandidate("p74.cand.res.target");
        setupCandidateReadiness(candidate, "Res Target Resume");
        Long applicationId = applyToJob(candidate, jobAlpha);

        // Foreign recruiter attempts to query with arbitrary ?resumeId=1
        mockMvc.perform(get("/api/recruiter/applications/" + applicationId + "/resume?resumeId=1")
                        .header("Authorization", "Bearer " + recForeign.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P7.4-15: Stale JWT company reassignment is respected in database")
    void staleJwtCompanyReassignment_isRespected() throws Exception {
        RecruiterAuth recruiter = registerRecruiter("p74.rec.stalejwt");
        Long compAlpha = createCompany("Alpha Initial Corp");
        linkRecruiterToCompany(recruiter.recruiterId(), compAlpha);
        Long jobAlpha = createJob(compAlpha, "Alpha Role", JobStatus.ACTIVE);

        Long compBeta = createCompany("Beta Future Corp");
        Long jobBeta = createJob(compBeta, "Beta Role", JobStatus.ACTIVE);

        CandidateAuth candAlpha = registerCandidate("p74.cand.stale.alpha");
        setupCandidateReadiness(candAlpha, "Alpha Applicant");
        Long appAlpha = applyToJob(candAlpha, jobAlpha);

        CandidateAuth candBeta = registerCandidate("p74.cand.stale.beta");
        setupCandidateReadiness(candBeta, "Beta Applicant");
        Long appBeta = applyToJob(candBeta, jobBeta);

        // Initially recruiter has access to appAlpha
        mockMvc.perform(get("/api/recruiter/applications/" + appAlpha + "/resume")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isOk());

        // Now reassign recruiter to Company Beta directly in MySQL
        jdbcTemplate.update("UPDATE recruiters SET company_id = ? WHERE id = ?", compBeta, recruiter.recruiterId());

        // Using the same original JWT token:
        // 1. Recruiter can NO LONGER access appAlpha -> 404
        mockMvc.perform(get("/api/recruiter/applications/" + appAlpha + "/resume")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // 2. Recruiter CAN NOW access appBeta -> 200 OK
        mockMvc.perform(get("/api/recruiter/applications/" + appBeta + "/resume")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"));
    }

    @Test
    @DisplayName("P7.4-16: Foreign company attempt does not leak resume metadata")
    void foreignCompanyDoesNotLeakResumeMetadata() throws Exception {
        RecruiterAuth recAlpha = registerRecruiter("p74.rec.alpha.leak");
        Long compAlpha = createCompany("Alpha Leak Corp");
        linkRecruiterToCompany(recAlpha.recruiterId(), compAlpha);
        Long jobAlpha = createJob(compAlpha, "Secret Engineer", JobStatus.ACTIVE);

        RecruiterAuth recBeta = registerRecruiter("p74.rec.beta.leak");
        Long compBeta = createCompany("Beta Leak Corp");
        linkRecruiterToCompany(recBeta.recruiterId(), compBeta);

        CandidateAuth candidate = registerCandidate("p74.cand.leak");
        setupCandidateReadiness(candidate, "Super Confidential Resume Data");
        Long applicationId = applyToJob(candidate, jobAlpha);

        // Recruiter Beta sends request for metadata: ?metadata=true
        MvcResult res = mockMvc.perform(get("/api/recruiter/applications/" + applicationId + "/resume?metadata=true")
                        .header("Authorization", "Bearer " + recBeta.token()))
                .andExpect(status().isNotFound())
                .andReturn();

        String responseContent = res.getResponse().getContentAsString();
        assertThat(responseContent).doesNotContain("Super Confidential");
        assertThat(responseContent).doesNotContain("Candidate p74.cand.leak");
        assertThat(responseContent).doesNotContain("resume.pdf");
        assertThat(responseContent).doesNotContain("storageKey");
        assertThat(responseContent).contains("RESOURCE_NOT_FOUND");
    }

    @Test
    @DisplayName("P7.4-17: Recruiter without company returns 422 COMPANY_REQUIRED")
    void recruiterWithoutCompany_returns422CompanyRequired() throws Exception {
        RecruiterAuth recruiter = registerRecruiter("p74.rec.nocomp");
        // No company linked (recruiters.company_id is null)

        mockMvc.perform(get("/api/recruiter/applications/1/resume")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("COMPANY_REQUIRED"));
    }

    @Test
    @DisplayName("P7.4-18: Admin cannot access recruiter resume endpoint -> 403")
    void adminCannotAccessRecruiterResume() throws Exception {
        String adminToken = generateAdminToken();
        mockMvc.perform(get("/api/recruiter/applications/1/resume")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("P7.4-19: Physical file missing from storage returns 404")
    void physicalResumeFileMissing_returns404() throws Exception {
        RecruiterAuth recruiter = registerRecruiter("p74.rec.diskmiss");
        Long compId = createCompany("Disk Missing Corp");
        linkRecruiterToCompany(recruiter.recruiterId(), compId);
        Long jobId = createJob(compId, "Systems Engineer", JobStatus.ACTIVE);

        CandidateAuth candidate = registerCandidate("p74.cand.diskmiss");
        setupCandidateReadiness(candidate, "Disk test file");
        Long applicationId = applyToJob(candidate, jobId);

        // Delete physical files from test directory
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (Files.exists(dir)) {
            try (Stream<Path> stream = Files.walk(dir)) {
                stream.filter(Files::isRegularFile)
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {}
                        });
            }
        }

        mockMvc.perform(get("/api/recruiter/applications/" + applicationId + "/resume")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
