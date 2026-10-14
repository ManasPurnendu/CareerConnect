package com.careerconnect.application.controller;

import com.careerconnect.application.dto.ApplicationSubmissionRequest;
import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.AssignSkillRequest;
import com.careerconnect.candidate.dto.CreateEducationRequest;
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
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobMatchControllerTest {

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
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.RECRUITER, "Recruiter Match");
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
                "Match University", "B.Tech", "Computer Science", 2019, 2023, null, 1);
        mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edu)))
                .andExpect(status().isCreated());
    }

    private void uploadResume(String token) throws Exception {
        byte[] pdfBytes = "%PDF-1.4 match test resume".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private void updateProfile(String token, String fullName) throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest(fullName, "Backend Engineer", "Bengaluru", null);
        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    private UserAuth buildCompleteCandidate(String email, String name, String... skills) throws Exception {
        UserAuth user = registerCandidate(email, name);
        updateProfile(user.token(), name);
        addEducation(user.token());
        uploadResume(user.token());
        for (String s : skills) {
            addSkill(user.token(), s);
        }
        return user;
    }

    private Long createJobWithSkills(String suffix, String... skillNames) {
        String companyName = "MatchCo " + suffix;
        jdbcTemplate.update("""
                INSERT INTO companies (name, industry, location, is_active) VALUES (?, ?, ?, ?)
                """, companyName, "Technology", "Bengaluru", true);
        Long companyId = jdbcTemplate.queryForObject("SELECT id FROM companies WHERE name = ?", Long.class, companyName);

        String deadline = LocalDate.now().plusDays(30).toString();
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, job_type, experience_level,
                                  work_mode, application_deadline, status)
                VALUES (?, ?, ?, 'FULL_TIME', 'FRESHER', 'HYBRID', ?, 'ACTIVE')
                """, companyId, "Job " + suffix, "Description for job", deadline);

        Long jobId = jdbcTemplate.queryForObject(
                "SELECT id FROM jobs WHERE title = ?", Long.class, "Job " + suffix);

        for (String skillName : skillNames) {
            Long skillId = ensureSkillExists(skillName);
            jdbcTemplate.update("INSERT INTO job_skills (job_id, skill_id) VALUES (?, ?)", jobId, skillId);
        }

        return jobId;
    }

    private Long ensureSkillExists(String skillName) {
        String normalized = skillName.trim().toLowerCase();
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM skills WHERE normalized_name = ?", Long.class, normalized);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        jdbcTemplate.update("INSERT INTO skills (name, normalized_name) VALUES (?, ?)", skillName.trim(), normalized);
        return jdbcTemplate.queryForObject("SELECT id FROM skills WHERE normalized_name = ?", Long.class, normalized);
    }

    // ─── Tests ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/jobs/{jobId}/match returns 200 with canonical envelope and correct calculations")
    void testGetJobMatchSuccess() throws Exception {
        Long jobId = createJobWithSkills("P55_Match", "Java", "SQL", "Spring Boot");
        UserAuth candidate = registerCandidate("p55.match@example.com", "Skill Candidate");
        addSkill(candidate.token(), "Java");
        addSkill(candidate.token(), "SQL");
        addSkill(candidate.token(), "Python");

        MvcResult result = mockMvc.perform(get("/api/jobs/{jobId}/match", jobId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data.percentage").value(67))
                .andExpect(jsonPath("$.data.matchedSkills[0]").value("Java"))
                .andExpect(jsonPath("$.data.matchedSkills[1]").value("SQL"))
                .andExpect(jsonPath("$.data.missingSkills[0]").value("Spring Boot"))
                .andExpect(jsonPath("$.data.additionalSkills[0]").value("Python"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.path("data").path("matchedSkills")).hasSize(2);
        assertThat(root.path("data").path("missingSkills")).hasSize(1);
        assertThat(root.path("data").path("additionalSkills")).hasSize(1);
    }

    @Test
    @DisplayName("GET /api/jobs/{jobId}/match unauthenticated returns 401 UNAUTHENTICATED")
    void testGetJobMatchUnauthenticated() throws Exception {
        Long jobId = createJobWithSkills("P55_Unauth", "Java");

        mockMvc.perform(get("/api/jobs/{jobId}/match", jobId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("GET /api/jobs/{jobId}/match by Recruiter returns 403 FORBIDDEN")
    void testGetJobMatchByRecruiterForbidden() throws Exception {
        Long jobId = createJobWithSkills("P55_Recruiter", "Java");
        String recruiterToken = registerRecruiter("p55.recruiter@example.com");

        mockMvc.perform(get("/api/jobs/{jobId}/match", jobId)
                        .header("Authorization", "Bearer " + recruiterToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/jobs/{jobId}/match with non-existent job returns 404 JOB_NOT_FOUND")
    void testGetJobMatchJobNotFound() throws Exception {
        UserAuth candidate = registerCandidate("p55.notfound@example.com", "NF Candidate");

        mockMvc.perform(get("/api/jobs/{jobId}/match", 9999999L)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    @Test
    @DisplayName("Candidate identity cannot be injected via query parameter")
    void testCandidateIdentityCannotBeInjected() throws Exception {
        Long jobId = createJobWithSkills("P55_Spoof", "Java", "Docker");

        // Candidate A has Java (50% match)
        UserAuth candidateA = registerCandidate("p55.userA@example.com", "User A");
        addSkill(candidateA.token(), "Java");

        // Candidate B has Java and Docker (100% match)
        UserAuth candidateB = registerCandidate("p55.userB@example.com", "User B");
        addSkill(candidateB.token(), "Java");
        addSkill(candidateB.token(), "Docker");

        // Candidate A calls match endpoint with query parameter candidateId=<candidateB.id>
        mockMvc.perform(get("/api/jobs/{jobId}/match?candidateId=" + candidateB.candidateId(), jobId)
                        .header("Authorization", "Bearer " + candidateA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.percentage").value(50))
                .andExpect(jsonPath("$.data.matchedSkills[0]").value("Java"))
                .andExpect(jsonPath("$.data.missingSkills[0]").value("Docker"));
    }

    @Test
    @DisplayName("Matching does NOT block application submission: candidate with 0% match can apply")
    void testZeroMatchCandidateCanApply() throws Exception {
        // Job requires Java and Go
        Long jobId = createJobWithSkills("P55_ApplyZeroMatch", "Java", "Go");

        // Candidate has Python and Docker (0% match for Java and Go)
        UserAuth candidate = buildCompleteCandidate("p55.applyzero@example.com", "Apply Zero", "Python", "Docker");

        // Preview match -> 0%
        mockMvc.perform(get("/api/jobs/{jobId}/match", jobId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.percentage").value(0))
                .andExpect(jsonPath("$.data.matchedSkills").isEmpty());

        // Now submit application -> MUST SUCCEED (201 Created)
        ApplicationSubmissionRequest submission = new ApplicationSubmissionRequest(
                "I am eager to learn Java and Go on the job.");

        mockMvc.perform(post("/api/jobs/{jobId}/applications", jobId)
                        .header("Authorization", "Bearer " + candidate.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submission)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(jobId))
                .andExpect(jsonPath("$.data.status").value("APPLIED"));
    }

    @Test
    @DisplayName("Zero-skill job returns 100% match preview")
    void testZeroSkillJobReturns100Percent() throws Exception {
        Long jobId = createJobWithSkills("P55_ZeroSkillJob"); // No skills assigned to job
        UserAuth candidate = registerCandidate("p55.zeroskilljob@example.com", "Zero Job Cand");
        addSkill(candidate.token(), "Rust");

        mockMvc.perform(get("/api/jobs/{jobId}/match", jobId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.percentage").value(100))
                .andExpect(jsonPath("$.data.matchedSkills").isEmpty())
                .andExpect(jsonPath("$.data.missingSkills").isEmpty())
                .andExpect(jsonPath("$.data.additionalSkills[0]").value("Rust"));
    }

    @Test
    @DisplayName("Zero-skill candidate matching job with required skills returns 0% match preview")
    void testZeroSkillCandidateReturns0Percent() throws Exception {
        Long jobId = createJobWithSkills("P55_ZeroSkillCand", "Java", "Kotlin");
        UserAuth candidate = registerCandidate("p55.zeroskillcand@example.com", "Zero Cand");
        // No skills added

        mockMvc.perform(get("/api/jobs/{jobId}/match", jobId)
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.percentage").value(0))
                .andExpect(jsonPath("$.data.matchedSkills").isEmpty())
                .andExpect(jsonPath("$.data.missingSkills", hasSize(2)));
    }
}
