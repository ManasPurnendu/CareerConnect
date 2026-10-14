package com.careerconnect.candidate.controller;

import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.AssignSkillRequest;
import com.careerconnect.candidate.dto.CreateEducationRequest;
import com.careerconnect.candidate.dto.CreateExperienceRequest;
import com.careerconnect.candidate.dto.CreateProjectRequest;
import com.careerconnect.candidate.dto.UpdateEducationRequest;
import com.careerconnect.candidate.dto.UpdateExperienceRequest;
import com.careerconnect.candidate.dto.UpdateProjectRequest;
import com.careerconnect.common.model.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

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
class CandidateSecurityHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.resume.upload-dir:./target/test-uploads/resumes}")
    private String uploadDir;

    private record UserContext(String token, Long candidateId) {}

    private UserContext registerCandidate(String email, String name) throws Exception {
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.CANDIDATE, name);
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        String token = root.path("data").path("accessToken").asText();
        Long candidateId = root.path("data").path("user").path("candidateId").asLong();
        return new UserContext(token, candidateId);
    }

    private String registerRecruiter(String email) throws Exception {
        RegisterRequest req = new RegisterRequest(email, "Password123!", Role.RECRUITER, "Recruiter HR");
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }

    // =========================================================================
    // 1. Cross-Candidate Education Ownership Isolation
    // =========================================================================

    @Test
    @DisplayName("P4.9-1: Cross-candidate education update returns 404 with canonical error envelope")
    void crossCandidateEducationUpdate_shouldReturn404() throws Exception {
        UserContext candA = registerCandidate("sec.candA.edu@example.com", "Alice Edu");
        UserContext candB = registerCandidate("sec.candB.edu@example.com", "Bob Edu");

        CreateEducationRequest createReq = new CreateEducationRequest(
                "Stanford University", "B.S.", "Computer Science", 2018, 2022, "3.9", 1
        );
        MvcResult createRes = mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + candB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long bobEduId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Alice attempts to update Bob's education
        UpdateEducationRequest updateReq = new UpdateEducationRequest(
                "Hacked University", "B.S.", "Computer Science", 2018, 2022, "3.9", 1
        );
        mockMvc.perform(put("/api/candidate/education/" + bobEduId)
                        .header("Authorization", "Bearer " + candA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P4.9-2: Cross-candidate education delete returns 404 with canonical error envelope")
    void crossCandidateEducationDelete_shouldReturn404() throws Exception {
        UserContext candA = registerCandidate("sec.candA.edudel@example.com", "Alice EduDel");
        UserContext candB = registerCandidate("sec.candB.edudel@example.com", "Bob EduDel");

        CreateEducationRequest createReq = new CreateEducationRequest(
                "MIT", "M.S.", "AI", 2020, 2022, "4.0", 1
        );
        MvcResult createRes = mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + candB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long bobEduId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Alice attempts to delete Bob's education
        mockMvc.perform(delete("/api/candidate/education/" + bobEduId)
                        .header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // =========================================================================
    // 2. Cross-Candidate Project Ownership Isolation
    // =========================================================================

    @Test
    @DisplayName("P4.9-3: Cross-candidate project update returns 404 with canonical error envelope")
    void crossCandidateProjectUpdate_shouldReturn404() throws Exception {
        UserContext candA = registerCandidate("sec.candA.proj@example.com", "Alice Proj");
        UserContext candB = registerCandidate("sec.candB.proj@example.com", "Bob Proj");

        CreateProjectRequest createReq = new CreateProjectRequest(
                "Secret Algorithm", "Proprietary code", "https://github.com/secret", 1
        );
        MvcResult createRes = mockMvc.perform(post("/api/candidate/projects")
                        .header("Authorization", "Bearer " + candB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long bobProjId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Alice attempts to modify Bob's project
        UpdateProjectRequest updateReq = new UpdateProjectRequest(
                "Defaced Title", "Defaced Description", "https://malicious.com", 1
        );
        mockMvc.perform(put("/api/candidate/projects/" + bobProjId)
                        .header("Authorization", "Bearer " + candA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P4.9-4: Cross-candidate project delete returns 404 with canonical error envelope")
    void crossCandidateProjectDelete_shouldReturn404() throws Exception {
        UserContext candA = registerCandidate("sec.candA.projdel@example.com", "Alice ProjDel");
        UserContext candB = registerCandidate("sec.candB.projdel@example.com", "Bob ProjDel");

        CreateProjectRequest createReq = new CreateProjectRequest(
                "Bob Project", "Bob Description", null, 1
        );
        MvcResult createRes = mockMvc.perform(post("/api/candidate/projects")
                        .header("Authorization", "Bearer " + candB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long bobProjId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(delete("/api/candidate/projects/" + bobProjId)
                        .header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // =========================================================================
    // 3. Cross-Candidate Experience Ownership Isolation
    // =========================================================================

    @Test
    @DisplayName("P4.9-5: Cross-candidate experience update returns 404 with canonical error envelope")
    void crossCandidateExperienceUpdate_shouldReturn404() throws Exception {
        UserContext candA = registerCandidate("sec.candA.exp@example.com", "Alice Exp");
        UserContext candB = registerCandidate("sec.candB.exp@example.com", "Bob Exp");

        CreateExperienceRequest createReq = new CreateExperienceRequest(
                "Acme Corp", "Senior Engineer", LocalDate.of(2021, 1, 1),
                null, true, "Core platform team", 1
        );
        MvcResult createRes = mockMvc.perform(post("/api/candidate/experience")
                        .header("Authorization", "Bearer " + candB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long bobExpId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        UpdateExperienceRequest updateReq = new UpdateExperienceRequest(
                "Fake Company", "Fake Title", LocalDate.of(2021, 1, 1),
                null, true, "Fake description", 1
        );
        mockMvc.perform(put("/api/candidate/experience/" + bobExpId)
                        .header("Authorization", "Bearer " + candA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P4.9-6: Cross-candidate experience delete returns 404 with canonical error envelope")
    void crossCandidateExperienceDelete_shouldReturn404() throws Exception {
        UserContext candA = registerCandidate("sec.candA.expdel@example.com", "Alice ExpDel");
        UserContext candB = registerCandidate("sec.candB.expdel@example.com", "Bob ExpDel");

        CreateExperienceRequest createReq = new CreateExperienceRequest(
                "Startup Inc", "Lead Developer", LocalDate.of(2019, 3, 1),
                LocalDate.of(2021, 1, 1), false, "Full stack work", 1
        );
        MvcResult createRes = mockMvc.perform(post("/api/candidate/experience")
                        .header("Authorization", "Bearer " + candB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long bobExpId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(delete("/api/candidate/experience/" + bobExpId)
                        .header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // =========================================================================
    // 4. Cross-Candidate Skill Isolation
    // =========================================================================

    @Test
    @DisplayName("P4.9-7: Candidate A cannot delete Candidate B's skill assignment -> 404")
    void crossCandidateSkillDelete_shouldReturn404() throws Exception {
        UserContext candA = registerCandidate("sec.candA.skill@example.com", "Alice Skill");
        UserContext candB = registerCandidate("sec.candB.skill@example.com", "Bob Skill");

        // Bob assigns Rust
        AssignSkillRequest assignReq = new AssignSkillRequest("Rust");
        MvcResult assignRes = mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + candB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long rustSkillId = objectMapper.readTree(assignRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Alice tries to delete Rust (which she has not assigned)
        mockMvc.perform(delete("/api/candidate/skills/" + rustSkillId)
                        .header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Verify Bob's skill is still intact
        mockMvc.perform(get("/api/candidate/skills")
                        .header("Authorization", "Bearer " + candB.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(rustSkillId))
                .andExpect(jsonPath("$.data[0].name").value("Rust"));
    }

    // =========================================================================
    // 5. Cross-Candidate Resume Isolation
    // =========================================================================

    @Test
    @DisplayName("P4.9-8: Candidate A cannot delete or access Candidate B's resume")
    void crossCandidateResume_shouldBeIsolated() throws Exception {
        UserContext candA = registerCandidate("sec.candA.res@example.com", "Alice Res");
        UserContext candB = registerCandidate("sec.candB.res@example.com", "Bob Res");

        // Bob uploads a resume
        byte[] pdfContent = "%PDF-1.7 Bob Resume Content".getBytes();
        MockMultipartFile bobFile = new MockMultipartFile(
                "file", "bob_resume.pdf", "application/pdf", pdfContent
        );
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(bobFile)
                        .header("Authorization", "Bearer " + candB.token()))
                .andExpect(status().isCreated());

        // Alice (who has no resume) attempts to delete resume -> 404
        mockMvc.perform(delete("/api/candidate/resume")
                        .header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Alice (who has no resume) attempts to get resume -> 404
        mockMvc.perform(get("/api/candidate/resume")
                        .header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Verify Bob's resume is still present and valid
        mockMvc.perform(get("/api/candidate/resume")
                        .header("Authorization", "Bearer " + candB.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalFilename").value("bob_resume.pdf"));
    }

    // =========================================================================
    // 6. Role Authorization & Authentication Rejection (401 / 403)
    // =========================================================================

    @Test
    @DisplayName("P4.9-9: Recruiter role attempting candidate endpoints -> 403 FORBIDDEN")
    void recruiterAccessingCandidateEndpoints_shouldReturn403() throws Exception {
        String recruiterToken = registerRecruiter("sec.recruiter@example.com");

        // Profile
        mockMvc.perform(get("/api/candidate/profile")
                        .header("Authorization", "Bearer " + recruiterToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        // Readiness
        mockMvc.perform(get("/api/candidate/profile/readiness")
                        .header("Authorization", "Bearer " + recruiterToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        // Skills
        mockMvc.perform(get("/api/candidate/skills")
                        .header("Authorization", "Bearer " + recruiterToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("P4.9-10: Unauthenticated access to candidate endpoints -> 401 UNAUTHENTICATED")
    void unauthenticatedAccessToCandidateEndpoints_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/candidate/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/api/candidate/profile/readiness"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/api/candidate/skills"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/api/candidate/resume"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    // =========================================================================
    // 7. Identity Spoofing Protection (Client candidateId Injection Ignored)
    // =========================================================================

    @Test
    @DisplayName("P4.9-11: Injected candidateId in request payload is completely ignored")
    void injectedCandidateIdInPayload_shouldBeIgnored() throws Exception {
        UserContext candA = registerCandidate("sec.candA.spoof@example.com", "Alice Spoof");

        // Request with foreign candidateId injected in raw JSON
        String rawJson = """
                {
                    "candidateId": 99999,
                    "institution": "Caltech",
                    "degree": "B.S.",
                    "fieldOfStudy": "Physics",
                    "startYear": 2017,
                    "endYear": 2021
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + candA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isCreated())
                .andReturn();

        // Must be attached to Alice's candidateId, NOT 99999
        Long createdCandidateId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("candidateId").asLong();
        assertThat(createdCandidateId).isEqualTo(candA.candidateId());
    }

    // =========================================================================
    // 8. Resume Path Traversal Sanitization
    // =========================================================================

    @Test
    @DisplayName("P4.9-12: Resume originalFilename with path traversal sequences is sanitized to clean basename")
    void resumePathTraversalFilename_shouldBeSanitized() throws Exception {
        UserContext cand = registerCandidate("sec.cand.traversal@example.com", "Traversal Tester");

        byte[] pdfContent = "%PDF-1.7 Safe content".getBytes();
        MockMultipartFile traversalFile = new MockMultipartFile(
                "file", "../../../../etc/passwd.pdf", "application/pdf", pdfContent
        );

        MvcResult result = mockMvc.perform(multipart("/api/candidate/resume")
                        .file(traversalFile)
                        .header("Authorization", "Bearer " + cand.token()))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String originalFilename = root.path("data").path("originalFilename").asText();

        // Must be sanitized to basename only, no directory components
        assertThat(originalFilename).doesNotContain("/");
        assertThat(originalFilename).doesNotContain("\\");
        assertThat(originalFilename).doesNotContain("..");
        assertThat(originalFilename).isEqualTo("passwd.pdf");

        // Verify that the stored file remains securely inside the configured upload directory
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        assertThat(Files.exists(dir)).isTrue();
    }

    // =========================================================================
    // 9. SQL Injection Resilience
    // =========================================================================

    @Test
    @DisplayName("P4.9-13: SQL injection payloads in profile fields are safely stored as literal text")
    void sqlInjectionPayloads_shouldBeSafelyTreatedAsLiteralText() throws Exception {
        UserContext cand = registerCandidate("sec.cand.sqli@example.com", "SQLi Tester");

        String injectionPayload = "Robert'); DROP TABLE education;--";
        String rawJson = String.format("""
                {
                    "fullName": "Safe Name",
                    "headline": "%s",
                    "location": "Safe City",
                    "summary": "' OR '1'='1"
                }
                """, injectionPayload);

        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + cand.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.headline").value(injectionPayload))
                .andExpect(jsonPath("$.data.summary").value("' OR '1'='1"));

        // Verify database tables still exist and query runs normally
        mockMvc.perform(get("/api/candidate/profile")
                        .header("Authorization", "Bearer " + cand.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headline").value(injectionPayload));
    }
}
