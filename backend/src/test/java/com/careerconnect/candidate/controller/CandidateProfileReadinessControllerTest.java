package com.careerconnect.candidate.controller;

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
import java.util.stream.Stream;

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
class CandidateProfileReadinessControllerTest {

    private static final String READINESS_URL = "/api/candidate/profile/readiness";

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
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (Files.exists(dir)) {
            try (Stream<Path> stream = Files.walk(dir)) {
                stream.filter(Files::isRegularFile).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private record UserAuth(String token, Long candidateId) {}

    private UserAuth registerUserAndGetAuth(String email, Role role, String fullName) throws Exception {
        RegisterRequest req = new RegisterRequest(email, "Password123!", role, fullName);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = root.path("data").path("accessToken").asText();
        Long candidateId = root.path("data").path("user").path("candidateId").asLong();
        return new UserAuth(token, candidateId);
    }

    private void addEducation(String token) throws Exception {
        CreateEducationRequest edu = new CreateEducationRequest(
                "Test University", "BSc", "Computer Science", 2019, 2023, null, 1);
        mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edu)))
                .andExpect(status().isCreated());
    }

    private void addSkill(String token) throws Exception {
        AssignSkillRequest skill = new AssignSkillRequest("Java");
        mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(skill)))
                .andExpect(status().isCreated());
    }

    private void uploadResume(String token) throws Exception {
        byte[] pdfBytes = "%PDF-1.4 minimal".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        mockMvc.perform(multipart("/api/candidate/resume")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private void updateProfile(String token, String fullName) throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest(fullName, null, null, null);
        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ─── tests ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P4.8-1: Complete candidate -> readyToApply=true, all flags true, missingRequirements empty")
    void completeCandidate_shouldBeReadyToApply() throws Exception {
        UserAuth user = registerUserAndGetAuth("ready.full@example.com", Role.CANDIDATE, "Full Ready");
        addEducation(user.token());
        addSkill(user.token());
        uploadResume(user.token());

        mockMvc.perform(get(READINESS_URL).header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.readyToApply").value(true))
                .andExpect(jsonPath("$.data.profileComplete").value(true))
                .andExpect(jsonPath("$.data.hasEducation").value(true))
                .andExpect(jsonPath("$.data.hasSkills").value(true))
                .andExpect(jsonPath("$.data.hasResume").value(true))
                .andExpect(jsonPath("$.data.missingRequirements").isEmpty());
    }

    @Test
    @DisplayName("P4.8-2: Candidate with no education -> readyToApply=false, EDUCATION in missingRequirements")
    void candidateWithNoEducation_shouldNotBeReady() throws Exception {
        UserAuth user = registerUserAndGetAuth("ready.noedu@example.com", Role.CANDIDATE, "No Edu");
        addSkill(user.token());
        uploadResume(user.token());

        mockMvc.perform(get(READINESS_URL).header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(false))
                .andExpect(jsonPath("$.data.profileComplete").value(true))
                .andExpect(jsonPath("$.data.hasEducation").value(false))
                .andExpect(jsonPath("$.data.hasSkills").value(true))
                .andExpect(jsonPath("$.data.hasResume").value(true))
                .andExpect(jsonPath("$.data.missingRequirements").isArray())
                .andExpect(jsonPath("$.data.missingRequirements[0]").value("EDUCATION"))
                .andExpect(jsonPath("$.data.missingRequirements.length()").value(1));
    }

    @Test
    @DisplayName("P4.8-3: Candidate with no skills -> readyToApply=false, SKILLS in missingRequirements")
    void candidateWithNoSkills_shouldNotBeReady() throws Exception {
        UserAuth user = registerUserAndGetAuth("ready.noskill@example.com", Role.CANDIDATE, "No Skills");
        addEducation(user.token());
        uploadResume(user.token());

        mockMvc.perform(get(READINESS_URL).header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(false))
                .andExpect(jsonPath("$.data.profileComplete").value(true))
                .andExpect(jsonPath("$.data.hasEducation").value(true))
                .andExpect(jsonPath("$.data.hasSkills").value(false))
                .andExpect(jsonPath("$.data.hasResume").value(true))
                .andExpect(jsonPath("$.data.missingRequirements[0]").value("SKILLS"))
                .andExpect(jsonPath("$.data.missingRequirements.length()").value(1));
    }

    @Test
    @DisplayName("P4.8-4: Candidate with no resume -> readyToApply=false, RESUME in missingRequirements")
    void candidateWithNoResume_shouldNotBeReady() throws Exception {
        UserAuth user = registerUserAndGetAuth("ready.noresume@example.com", Role.CANDIDATE, "No Resume");
        addEducation(user.token());
        addSkill(user.token());

        mockMvc.perform(get(READINESS_URL).header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(false))
                .andExpect(jsonPath("$.data.profileComplete").value(true))
                .andExpect(jsonPath("$.data.hasEducation").value(true))
                .andExpect(jsonPath("$.data.hasSkills").value(true))
                .andExpect(jsonPath("$.data.hasResume").value(false))
                .andExpect(jsonPath("$.data.missingRequirements[0]").value("RESUME"))
                .andExpect(jsonPath("$.data.missingRequirements.length()").value(1));
    }

    @Test
    @DisplayName("P4.8-5: Missing education + resume -> both in missingRequirements in order [EDUCATION, RESUME]")
    void candidateMissingMultiple_shouldListAllInOrder() throws Exception {
        UserAuth user = registerUserAndGetAuth("ready.multi@example.com", Role.CANDIDATE, "Multi Miss");
        addSkill(user.token());

        mockMvc.perform(get(READINESS_URL).header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(false))
                .andExpect(jsonPath("$.data.missingRequirements.length()").value(2))
                .andExpect(jsonPath("$.data.missingRequirements[0]").value("EDUCATION"))
                .andExpect(jsonPath("$.data.missingRequirements[1]").value("RESUME"));
    }

    @Test
    @DisplayName("P4.8-6: Missing profile fullName -> profileComplete=false, PROFILE in missingRequirements")
    void candidateWithBlankFullName_shouldHaveProfileIncomplete() throws Exception {
        // Register with a name, then overwrite the full_name to blank to simulate missing name
        UserAuth user = registerUserAndGetAuth("ready.noprofile@example.com", Role.CANDIDATE, "Temp Name");
        // Set full_name to a single space (blank but satisfies NOT NULL constraint)
        jdbcTemplate.update("UPDATE candidates SET full_name = ' ' WHERE id = ?", user.candidateId());
        addEducation(user.token());
        addSkill(user.token());
        uploadResume(user.token());

        mockMvc.perform(get(READINESS_URL).header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(false))
                .andExpect(jsonPath("$.data.profileComplete").value(false))
                .andExpect(jsonPath("$.data.hasEducation").value(true))
                .andExpect(jsonPath("$.data.hasSkills").value(true))
                .andExpect(jsonPath("$.data.hasResume").value(true))
                .andExpect(jsonPath("$.data.missingRequirements[0]").value("PROFILE"))
                .andExpect(jsonPath("$.data.missingRequirements.length()").value(1));
    }

    @Test
    @DisplayName("P4.8-7: Non-current (historical) resume does NOT satisfy readiness -> hasResume=false")
    void nonCurrentResumeDoesNotSatisfyReadiness() throws Exception {
        UserAuth user = registerUserAndGetAuth("ready.oldresume@example.com", Role.CANDIDATE, "Old Resume");
        addEducation(user.token());
        addSkill(user.token());
        uploadResume(user.token());

        // Mark the resume as non-current directly
        jdbcTemplate.update("UPDATE resumes SET is_current = FALSE WHERE candidate_id = ?", user.candidateId());

        mockMvc.perform(get(READINESS_URL).header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(false))
                .andExpect(jsonPath("$.data.hasResume").value(false))
                .andExpect(jsonPath("$.data.missingRequirements[0]").value("RESUME"));
    }

    @Test
    @DisplayName("P4.8-8: Adding missing requirement transitions readyToApply from false to true")
    void addingMissingSkill_shouldTransitionToReady() throws Exception {
        UserAuth user = registerUserAndGetAuth("ready.transition@example.com", Role.CANDIDATE, "Transition Dev");
        addEducation(user.token());
        uploadResume(user.token());

        // Initially not ready — missing skills
        mockMvc.perform(get(READINESS_URL).header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(false))
                .andExpect(jsonPath("$.data.hasSkills").value(false));

        // Add skills
        addSkill(user.token());

        // Now ready
        mockMvc.perform(get(READINESS_URL).header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(true))
                .andExpect(jsonPath("$.data.hasSkills").value(true))
                .andExpect(jsonPath("$.data.missingRequirements").isEmpty());
    }

    @Test
    @DisplayName("P4.8-9: Candidate A's readiness cannot see Candidate B's data (tenant isolation)")
    void tenantIsolation_candidateAReadinessDoesNotSeeCandidateBData() throws Exception {
        UserAuth candA = registerUserAndGetAuth("isolate.a@example.com", Role.CANDIDATE, "Candidate A");
        UserAuth candB = registerUserAndGetAuth("isolate.b@example.com", Role.CANDIDATE, "Candidate B");

        // Give Candidate A all requirements
        addEducation(candA.token());
        addSkill(candA.token());
        uploadResume(candA.token());

        // Candidate B has nothing — should see their own (empty) readiness
        mockMvc.perform(get(READINESS_URL).header("Authorization", "Bearer " + candB.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(false))
                .andExpect(jsonPath("$.data.hasEducation").value(false))
                .andExpect(jsonPath("$.data.hasSkills").value(false))
                .andExpect(jsonPath("$.data.hasResume").value(false));

        // Candidate A still sees their own full readiness
        mockMvc.perform(get(READINESS_URL).header("Authorization", "Bearer " + candA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(true));
    }

    @Test
    @DisplayName("P4.8-10: Unauthenticated request -> 401 UNAUTHENTICATED")
    void unauthenticatedRequest_shouldReturn401() throws Exception {
        mockMvc.perform(get(READINESS_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("P4.8-11: Recruiter request -> 403 FORBIDDEN")
    void recruiterRequest_shouldReturn403() throws Exception {
        UserAuth recruiter = registerUserAndGetAuth("recruiter.readiness@example.com", Role.RECRUITER, "Recruiter");

        mockMvc.perform(get(READINESS_URL).header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("P4.8-12: Response is wrapped in canonical ApiResponse envelope")
    void responseShouldUseApiResponseEnvelope() throws Exception {
        UserAuth user = registerUserAndGetAuth("envelope.check@example.com", Role.CANDIDATE, "Envelope User");

        mockMvc.perform(get(READINESS_URL).header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.readyToApply").isBoolean())
                .andExpect(jsonPath("$.data.profileComplete").isBoolean())
                .andExpect(jsonPath("$.data.hasEducation").isBoolean())
                .andExpect(jsonPath("$.data.hasSkills").isBoolean())
                .andExpect(jsonPath("$.data.hasResume").isBoolean())
                .andExpect(jsonPath("$.data.missingRequirements").isArray());
    }

    @Test
    @DisplayName("P4.8-13: Brand-new candidate (fullName from registration) has profileComplete=true but missing edu/skills/resume")
    void brandNewCandidate_shouldHaveProfileCompleteButMissingOthers() throws Exception {
        UserAuth user = registerUserAndGetAuth("newbie.ready@example.com", Role.CANDIDATE, "Newbie Dev");

        mockMvc.perform(get(READINESS_URL).header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(false))
                .andExpect(jsonPath("$.data.profileComplete").value(true))
                .andExpect(jsonPath("$.data.hasEducation").value(false))
                .andExpect(jsonPath("$.data.hasSkills").value(false))
                .andExpect(jsonPath("$.data.hasResume").value(false))
                .andExpect(jsonPath("$.data.missingRequirements.length()").value(3))
                .andExpect(jsonPath("$.data.missingRequirements[0]").value("EDUCATION"))
                .andExpect(jsonPath("$.data.missingRequirements[1]").value("SKILLS"))
                .andExpect(jsonPath("$.data.missingRequirements[2]").value("RESUME"));
    }

    @Test
    @DisplayName("P4.8-14: All four requirements missing -> order is PROFILE, EDUCATION, SKILLS, RESUME")
    void allRequirementsMissing_shouldListInDeterministicOrder() throws Exception {
        UserAuth user = registerUserAndGetAuth("allbad@example.com", Role.CANDIDATE, "Temp");
        // Set full_name to blank (whitespace) — DB allows non-null blank strings
        jdbcTemplate.update("UPDATE candidates SET full_name = '   ' WHERE id = ?", user.candidateId());

        mockMvc.perform(get(READINESS_URL).header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyToApply").value(false))
                .andExpect(jsonPath("$.data.missingRequirements.length()").value(4))
                .andExpect(jsonPath("$.data.missingRequirements[0]").value("PROFILE"))
                .andExpect(jsonPath("$.data.missingRequirements[1]").value("EDUCATION"))
                .andExpect(jsonPath("$.data.missingRequirements[2]").value("SKILLS"))
                .andExpect(jsonPath("$.data.missingRequirements[3]").value("RESUME"));
    }

    @Test
    @DisplayName("P4.8-15: Readiness endpoint does not modify any database records")
    void readinessEndpointDoesNotModifyDatabase() throws Exception {
        UserAuth user = registerUserAndGetAuth("readonly.check@example.com", Role.CANDIDATE, "Read Only");

        int educationCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM education WHERE candidate_id = ?", Integer.class, user.candidateId());
        int skillCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM candidate_skills WHERE candidate_id = ?", Integer.class, user.candidateId());
        int resumeCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM resumes WHERE candidate_id = ?", Integer.class, user.candidateId());

        mockMvc.perform(get(READINESS_URL).header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk());

        int educationCountAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM education WHERE candidate_id = ?", Integer.class, user.candidateId());
        int skillCountAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM candidate_skills WHERE candidate_id = ?", Integer.class, user.candidateId());
        int resumeCountAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM resumes WHERE candidate_id = ?", Integer.class, user.candidateId());

        assert educationCountBefore == educationCountAfter;
        assert skillCountBefore == skillCountAfter;
        assert resumeCountBefore == resumeCountAfter;
    }

    @Test
    @DisplayName("P4.8-16: Updating profile fullName fixes PROFILE requirement dynamically")
    void updatingFullName_fixesProfileRequirement() throws Exception {
        UserAuth user = registerUserAndGetAuth("fix.profile@example.com", Role.CANDIDATE, "Temp");
        // Set blank full_name to trigger profileComplete=false
        jdbcTemplate.update("UPDATE candidates SET full_name = '   ' WHERE id = ?", user.candidateId());

        // Confirm PROFILE is missing
        mockMvc.perform(get(READINESS_URL).header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileComplete").value(false));

        // Fix it via the profile PUT endpoint
        updateProfile(user.token(), "Fixed Name");

        // Confirm PROFILE is now satisfied
        mockMvc.perform(get(READINESS_URL).header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileComplete").value(true));
    }
}
