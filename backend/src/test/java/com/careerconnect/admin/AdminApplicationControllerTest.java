package com.careerconnect.admin;

import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.common.model.Role;
import com.careerconnect.common.util.JdbcUtils;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApplicationControllerTest {

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
        jdbcTemplate.execute("DELETE FROM notifications");
        jdbcTemplate.execute("DELETE FROM application_status_history");
        jdbcTemplate.execute("DELETE FROM applications");
        jdbcTemplate.execute("DELETE FROM resumes");
        jdbcTemplate.execute("DELETE FROM candidate_skills");
        jdbcTemplate.execute("DELETE FROM education");
        jdbcTemplate.execute("DELETE FROM job_skills");
        jdbcTemplate.execute("DELETE FROM jobs");
        jdbcTemplate.execute("DELETE FROM recruiters");
        jdbcTemplate.execute("DELETE FROM candidates");
        jdbcTemplate.execute("DELETE FROM companies");
        jdbcTemplate.execute("DELETE FROM users WHERE email != 'admin@careerconnect.com'");

        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (Files.exists(dir)) {
            try (Stream<Path> stream = Files.walk(dir)) {
                stream.filter(Files::isRegularFile).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {}
                });
            } catch (IOException ignored) {}
        }
    }

    private record CandidateAuth(String token, Long candidateId, Long userId, String fullName) {}
    private record RecruiterAuth(String token, Long recruiterId, Long userId) {}

    private CandidateAuth registerCandidate(String prefix, String fullName) throws Exception {
        String email = prefix + "_" + UUID.randomUUID() + "@example.com";
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

    private Long createCompany(String name) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO companies (name, is_active, created_at, updated_at) VALUES (?, TRUE, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name + "_" + UUID.randomUUID());
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }

    private Long createJob(Long companyId, String title) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO jobs (
                        company_id, title, description, job_type, experience_level,
                        work_mode, location, salary_min_lpa, salary_max_lpa,
                        application_deadline, status, created_at, updated_at
                    ) VALUES (?, ?, 'Job Desc', 'FULL_TIME', 'FRESHER', 'REMOTE', 'Bengaluru', 10.0, 20.0, ?, 'ACTIVE', NOW(), NOW())
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            ps.setString(2, title);
            ps.setDate(3, Date.valueOf(LocalDate.now().plusMonths(1)));
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }

    private void prepareCandidateProfile(CandidateAuth cand, String resumeContent) throws Exception {
        mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "institution": "Stanford University",
                                  "degree": "B.S.",
                                  "fieldOfStudy": "Computer Science",
                                  "startYear": 2018,
                                  "endYear": 2022
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Java"))))
                .andExpect(status().isCreated());

        byte[] pdfBytes = ("%PDF-1.4\n" + resumeContent).getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);

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
    @DisplayName("Admin can list applications platform-wide across multiple companies")
    void adminCanListApplications_platformWide() throws Exception {
        Long compA = createCompany("Company Alpha");
        Long compB = createCompany("Company Beta");

        Long jobA = createJob(compA, "Backend Engineer");
        Long jobB = createJob(compB, "Frontend Engineer");

        CandidateAuth cand1 = registerCandidate("cand1", "Alice Smith");
        prepareCandidateProfile(cand1, "Alice Resume Body");
        applyToJob(cand1, jobA);

        CandidateAuth cand2 = registerCandidate("cand2", "Bob Jones");
        prepareCandidateProfile(cand2, "Bob Resume Body");
        applyToJob(cand2, jobB);

        mockMvc.perform(get("/api/admin/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.pagination.totalElements").value(2));
    }

    @Test
    @DisplayName("Admin can filter applications by companyId, jobId, and status")
    void adminCanFilterApplications() throws Exception {
        Long compA = createCompany("Alpha Corp");
        Long jobA = createJob(compA, "Lead Engineer");

        CandidateAuth cand1 = registerCandidate("cand1", "Alice");
        prepareCandidateProfile(cand1, "Alice Resume");
        Long app1 = applyToJob(cand1, jobA);

        // Filter by companyId
        mockMvc.perform(get("/api/admin/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("companyId", String.valueOf(compA))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(app1));

        // Filter by status = APPLIED
        mockMvc.perform(get("/api/admin/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("status", "APPLIED")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        // Filter by status = REJECTED (should be 0)
        mockMvc.perform(get("/api/admin/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("status", "REJECTED")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("Admin can view application detail with immutable snapshots and history")
    void adminCanViewApplicationDetail() throws Exception {
        Long comp = createCompany("Delta Tech");
        Long job = createJob(comp, "Full Stack Lead");

        CandidateAuth cand = registerCandidate("candDetail", "Carol Danvers");
        prepareCandidateProfile(cand, "Carol Resume Secret");
        Long appId = applyToJob(cand, job);

        mockMvc.perform(get("/api/admin/applications/" + appId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationId").value(appId))
                .andExpect(jsonPath("$.data.jobTitle").value("Full Stack Lead"))
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.coverLetter").value("Excited to apply!"))
                .andExpect(jsonPath("$.data.candidateSnapshot.fullName").value("Carol Danvers"))
                .andExpect(jsonPath("$.data.resumeSnapshot.originalFilename").value("resume.pdf"))
                .andExpect(jsonPath("$.data.statusHistory", hasSize(1)))
                .andExpect(jsonPath("$.data.match", notNullValue()));
    }

    @Test
    @DisplayName("Admin can download protected resume and view resume metadata")
    void adminCanAccessProtectedResume() throws Exception {
        Long comp = createCompany("Resume Co");
        Long job = createJob(comp, "Security Specialist");

        CandidateAuth cand = registerCandidate("candResume", "Diana Prince");
        prepareCandidateProfile(cand, "Diana Secure Resume Content");
        Long appId = applyToJob(cand, job);

        // 1. Download binary via /resume
        mockMvc.perform(get("/api/admin/applications/" + appId + "/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(content().bytes(("%PDF-1.4\nDiana Secure Resume Content").getBytes(StandardCharsets.UTF_8)));

        // 2. Download binary via /resume/download
        mockMvc.perform(get("/api/admin/applications/" + appId + "/resume/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"));

        // 3. Metadata query via ?metadata=true
        mockMvc.perform(get("/api/admin/applications/" + appId + "/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("metadata", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalFilename").value("resume.pdf"))
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"));
    }

    @Test
    @DisplayName("Non-admin roles are rejected from admin application endpoints")
    void nonAdmin_rejectedFromApplicationEndpoints() throws Exception {
        CandidateAuth cand = registerCandidate("candSec", "Regular User");
        RecruiterAuth rec = registerRecruiter("recSec");

        // Guest -> 401
        mockMvc.perform(get("/api/admin/applications"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/applications/1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/applications/1/resume"))
                .andExpect(status().isUnauthorized());

        // Candidate -> 403
        mockMvc.perform(get("/api/admin/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/applications/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/applications/1/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cand.token()))
                .andExpect(status().isForbidden());

        // Recruiter -> 403
        mockMvc.perform(get("/api/admin/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/applications/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/applications/1/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rec.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Missing application or missing resume returns 404 RESOURCE_NOT_FOUND")
    void missingApplicationOrResume_returns404() throws Exception {
        // Missing application
        mockMvc.perform(get("/api/admin/applications/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/admin/applications/999999/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
