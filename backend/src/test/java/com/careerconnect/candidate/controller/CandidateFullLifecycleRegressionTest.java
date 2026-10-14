package com.careerconnect.candidate.controller;

import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.AssignSkillRequest;
import com.careerconnect.candidate.dto.CreateEducationRequest;
import com.careerconnect.candidate.dto.CreateExperienceRequest;
import com.careerconnect.candidate.dto.CreateProjectRequest;
import com.careerconnect.candidate.dto.UpdateEducationRequest;
import com.careerconnect.candidate.dto.UpdateExperienceRequest;
import com.careerconnect.candidate.dto.UpdateProfileRequest;
import com.careerconnect.candidate.dto.UpdateProjectRequest;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CandidateFullLifecycleRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.resume.upload-dir:./target/test-uploads/resumes}")
    private String uploadDir;

    @AfterEach
    void tearDown() throws Exception {
        jdbcTemplate.execute("DELETE FROM resumes");
        jdbcTemplate.execute("DELETE FROM candidate_skills");
        jdbcTemplate.execute("DELETE FROM education");
        jdbcTemplate.execute("DELETE FROM projects");
        jdbcTemplate.execute("DELETE FROM experience");

        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (Files.exists(dir)) {
            try (Stream<Path> stream = Files.walk(dir)) {
                stream.filter(Files::isRegularFile).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    private record CandidateAuth(String token, Long candidateId) {}

    private CandidateAuth registerCandidate(String email, String name) throws Exception {
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.CANDIDATE, name);
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        String token = root.path("data").path("accessToken").asText();
        Long candidateId = root.path("data").path("user").path("candidateId").asLong();
        return new CandidateAuth(token, candidateId);
    }

    @Test
    @DisplayName("P4.10: End-to-end progressive candidate profile lifecycle & readiness verification")
    void fullCandidateLifecycleAndReadinessRegression() throws Exception {
        // 1. Candidate Registration
        CandidateAuth auth = registerCandidate("e2e.candidate@example.com", "Jane Doe");
        String token = auth.token();
        Long candidateId = auth.candidateId();

        // 2. Initial Readiness Check: profileComplete=true (from registration), others false
        mockMvc.perform(get("/api/candidate/profile/readiness")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.readyToApply").value(false))
                .andExpect(jsonPath("$.data.profileComplete").value(true))
                .andExpect(jsonPath("$.data.hasEducation").value(false))
                .andExpect(jsonPath("$.data.hasSkills").value(false))
                .andExpect(jsonPath("$.data.hasResume").value(false))
                .andExpect(jsonPath("$.data.missingRequirements[0]").value("EDUCATION"))
                .andExpect(jsonPath("$.data.missingRequirements[1]").value("SKILLS"))
                .andExpect(jsonPath("$.data.missingRequirements[2]").value("RESUME"));

        // 3. Update Profile
        UpdateProfileRequest profileReq = new UpdateProfileRequest(
                "Jane Doe", "Principal Cloud Architect", "Seattle, WA", "Expert in high throughput systems"
        );
        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.headline").value("Principal Cloud Architect"))
                .andExpect(jsonPath("$.data.location").value("Seattle, WA"));

        // 4. Add Education
        CreateEducationRequest eduReq = new CreateEducationRequest(
                "UC Berkeley", "B.S.", "Electrical Engineering", 2016, 2020, "3.85", 1
        );
        MvcResult eduRes = mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eduReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        Long educationId = objectMapper.readTree(eduRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Readiness after education
        mockMvc.perform(get("/api/candidate/profile/readiness")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(false))
                .andExpect(jsonPath("$.data.hasEducation").value(true))
                .andExpect(jsonPath("$.data.hasSkills").value(false))
                .andExpect(jsonPath("$.data.hasResume").value(false));

        // 5. Update Education
        UpdateEducationRequest eduUpdateReq = new UpdateEducationRequest(
                "UC Berkeley", "B.S. Honors", "EECS", 2016, 2020, "3.90", 1
        );
        mockMvc.perform(put("/api/candidate/education/" + educationId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eduUpdateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.degree").value("B.S. Honors"));

        // 6. Assign Skill
        AssignSkillRequest skillReq = new AssignSkillRequest("Kubernetes");
        MvcResult skillRes = mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(skillReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Kubernetes"))
                .andReturn();
        Long skillId = objectMapper.readTree(skillRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Readiness after skill
        mockMvc.perform(get("/api/candidate/profile/readiness")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(false))
                .andExpect(jsonPath("$.data.hasEducation").value(true))
                .andExpect(jsonPath("$.data.hasSkills").value(true))
                .andExpect(jsonPath("$.data.hasResume").value(false))
                .andExpect(jsonPath("$.data.missingRequirements[0]").value("RESUME"));

        // 7. Add Project (projects do not affect readiness, but must work seamlessly)
        CreateProjectRequest projReq = new CreateProjectRequest(
                "Service Mesh Controller", "High performance Go controller", "https://github.com/janedoe/mesh", 1
        );
        MvcResult projRes = mockMvc.perform(post("/api/candidate/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projReq)))
                .andExpect(status().isCreated())
                .andReturn();
        Long projectId = objectMapper.readTree(projRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 8. Update Project
        UpdateProjectRequest projUpdate = new UpdateProjectRequest(
                "Service Mesh Controller v2", "Updated Go controller", "https://github.com/janedoe/mesh-v2", 1
        );
        mockMvc.perform(put("/api/candidate/projects/" + projectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Service Mesh Controller v2"));

        // 9. Add Experience
        CreateExperienceRequest expReq = new CreateExperienceRequest(
                "Cloud Solutions LLC", "Lead Systems Engineer", LocalDate.of(2020, 7, 1),
                null, true, "Managed global clusters", 1
        );
        MvcResult expRes = mockMvc.perform(post("/api/candidate/experience")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expReq)))
                .andExpect(status().isCreated())
                .andReturn();
        Long expId = objectMapper.readTree(expRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 10. Update Experience
        UpdateExperienceRequest expUpdate = new UpdateExperienceRequest(
                "Cloud Solutions Global", "Principal Architect", LocalDate.of(2020, 7, 1),
                null, true, "Overseeing infrastructure roadmap", 1
        );
        mockMvc.perform(put("/api/candidate/experience/" + expId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("Cloud Solutions Global"));

        // 11. Upload Resume -> Should flip readiness to TRUE
        byte[] pdfBytes = "%PDF-1.7 Lifecycle Resume Payload".getBytes();
        MockMultipartFile resumeFile = new MockMultipartFile(
                "file", "jane_resume.pdf", "application/pdf", pdfBytes
        );
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(resumeFile)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isCurrent").value(true));

        // Readiness after resume upload -> readyToApply = TRUE!
        mockMvc.perform(get("/api/candidate/profile/readiness")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(true))
                .andExpect(jsonPath("$.data.profileComplete").value(true))
                .andExpect(jsonPath("$.data.hasEducation").value(true))
                .andExpect(jsonPath("$.data.hasSkills").value(true))
                .andExpect(jsonPath("$.data.hasResume").value(true))
                .andExpect(jsonPath("$.data.missingRequirements").isEmpty());

        // 12. Replace Resume -> Still readyToApply = true
        byte[] pdfBytesV2 = "%PDF-1.7 Lifecycle Resume Payload V2".getBytes();
        MockMultipartFile resumeFileV2 = new MockMultipartFile(
                "file", "jane_resume_v2.pdf", "application/pdf", pdfBytesV2
        );
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(resumeFileV2)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/candidate/profile/readiness")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(true));

        // 13. Remove Skill -> readyToApply flips back to FALSE
        mockMvc.perform(delete("/api/candidate/skills/" + skillId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/candidate/profile/readiness")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(false))
                .andExpect(jsonPath("$.data.hasSkills").value(false))
                .andExpect(jsonPath("$.data.missingRequirements[0]").value("SKILLS"));

        // Re-assign skill -> readyToApply returns to TRUE
        mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignSkillRequest("Kubernetes"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/candidate/profile/readiness")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(true));

        // 14. Delete Resume -> readyToApply flips back to FALSE
        mockMvc.perform(delete("/api/candidate/resume")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/candidate/profile/readiness")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(false))
                .andExpect(jsonPath("$.data.hasResume").value(false))
                .andExpect(jsonPath("$.data.missingRequirements[0]").value("RESUME"));

        // 15. Delete Education -> hasEducation becomes false
        mockMvc.perform(delete("/api/candidate/education/" + educationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/candidate/profile/readiness")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(false))
                .andExpect(jsonPath("$.data.hasEducation").value(false))
                .andExpect(jsonPath("$.data.hasResume").value(false))
                .andExpect(jsonPath("$.data.missingRequirements[0]").value("EDUCATION"))
                .andExpect(jsonPath("$.data.missingRequirements[1]").value("RESUME"));

        // 16. Verify Database Integrity
        Integer orphanSkills = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM candidate_skills WHERE candidate_id NOT IN (SELECT id FROM candidates)", Integer.class);
        assertThat(orphanSkills).isEqualTo(0);

        Integer orphanResumes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM resumes WHERE candidate_id NOT IN (SELECT id FROM candidates)", Integer.class);
        assertThat(orphanResumes).isEqualTo(0);

        Integer candidateRecordCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM candidates WHERE id = ?", Integer.class, candidateId);
        assertThat(candidateRecordCount).isEqualTo(1);
    }
}
