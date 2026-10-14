package com.careerconnect.candidate.controller;

import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.AssignSkillRequest;
import com.careerconnect.candidate.repository.CandidateSkillRepository;
import com.careerconnect.common.model.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CandidateSkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CandidateSkillRepository candidateSkillRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM candidate_skills");
    }

    private record UserAuth(String token, Long candidateId) {}

    private UserAuth registerUserAndGetAuth(String email, Role role, String fullName) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(email, "Password123!", role, fullName);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = root.path("data").path("accessToken").asText();
        Long candidateId = root.path("data").path("user").path("candidateId").asLong();
        return new UserAuth(token, candidateId);
    }

    private Long assignSkill(String token, String skillName) throws Exception {
        AssignSkillRequest request = new AssignSkillRequest(skillName);
        MvcResult result = mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
    }

    @Test
    @DisplayName("P4.4-1 & P4.4-4: Candidate can assign skills, changes persist, and candidate can retrieve own skills")
    void shouldAssignSkillsAndRetrieveOwnSkills() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.skill1@example.com", Role.CANDIDATE, "Dev One");

        Long javaId = assignSkill(user.token(), "Java");
        Long springId = assignSkill(user.token(), "Spring Boot");

        // Verify retrieval via GET
        mockMvc.perform(get("/api/candidate/skills")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.name == 'Java')].id").value(javaId.intValue()))
                .andExpect(jsonPath("$.data[?(@.name == 'Spring Boot')].id").value(springId.intValue()));

        // Direct DB verification
        var list = candidateSkillRepository.findByCandidateId(user.candidateId());
        assertThat(list).hasSize(2);
    }

    @Test
    @DisplayName("P4.4-3 & P4.4-7: Skill names with different casing/whitespace resolve to same normalized skill")
    void shouldReuseExistingNormalizedSkillDefinition() throws Exception {
        UserAuth user1 = registerUserAndGetAuth("candidate.norm1@example.com", Role.CANDIDATE, "Norm One");
        UserAuth user2 = registerUserAndGetAuth("candidate.norm2@example.com", Role.CANDIDATE, "Norm Two");

        Long skillId1 = assignSkill(user1.token(), "PostgreSQL");
        Long skillId2 = assignSkill(user2.token(), "  postgresql   ");

        // Both candidates must reference the exact same skill row in `skills`
        assertThat(skillId1).isEqualTo(skillId2);
    }

    @Test
    @DisplayName("P4.4-6: Duplicate skill assignment returns 409 Conflict and creates no duplicate rows")
    void shouldReturn409OnDuplicateSkillAssignment() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.dupskill@example.com", Role.CANDIDATE, "Dup Tester");

        assignSkill(user.token(), "Docker");

        // Try assigning same normalized skill with leading/trailing spaces and lower case
        AssignSkillRequest duplicateRequest = new AssignSkillRequest("  docker  ");
        mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Skill is already assigned to this candidate")));

        // Verify DB only has 1 row
        var skills = candidateSkillRepository.findByCandidateId(user.candidateId());
        assertThat(skills).hasSize(1);
    }

    @Test
    @DisplayName("P4.4-5: Candidate can delete an assigned skill")
    void shouldDeleteAssignedSkillSuccessfully() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.delskill@example.com", Role.CANDIDATE, "Delete Tester");

        Long skillId = assignSkill(user.token(), "Kubernetes");

        // Delete skill
        mockMvc.perform(delete("/api/candidate/skills/" + skillId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNoContent());

        // Verify it is no longer assigned in DB
        var skills = candidateSkillRepository.findByCandidateId(user.candidateId());
        assertThat(skills).isEmpty();

        // Second deletion attempt returns 404
        mockMvc.perform(delete("/api/candidate/skills/" + skillId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P4.4-8: Candidate A cannot delete Candidate B's skill assignment (returns 404)")
    void shouldNotAllowCandidateToDeleteAnotherCandidatesSkill() throws Exception {
        UserAuth userA = registerUserAndGetAuth("candidate.skillA@example.com", Role.CANDIDATE, "User A");
        UserAuth userB = registerUserAndGetAuth("candidate.skillB@example.com", Role.CANDIDATE, "User B");

        Long skillIdA = assignSkill(userA.token(), "Rust");

        // Candidate B (who has NOT assigned Rust) attempts to delete skillIdA
        mockMvc.perform(delete("/api/candidate/skills/" + skillIdA)
                        .header("Authorization", "Bearer " + userB.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Verify Candidate A still has Rust
        var skillsA = candidateSkillRepository.findByCandidateId(userA.candidateId());
        assertThat(skillsA).hasSize(1);
        assertThat(skillsA.getFirst().id()).isEqualTo(skillIdA);
    }

    @Test
    @DisplayName("P4.4-9: Nonexistent skill assignment deletion returns 404")
    void shouldReturn404WhenDeletingNonexistentSkill() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.noskill@example.com", Role.CANDIDATE, "No Skill");

        mockMvc.perform(delete("/api/candidate/skills/999999")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P4.4-10: Blank skill name returns 400 VALIDATION_FAILED")
    void shouldReturn400WhenSkillNameIsBlank() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.blank@example.com", Role.CANDIDATE, "Blank Skill");

        AssignSkillRequest blankRequest = new AssignSkillRequest("   ");
        mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Skill name cannot be blank")));
    }

    @Test
    @DisplayName("P4.4-11: Unauthenticated request to candidate skills returns 401")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/candidate/skills"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        AssignSkillRequest request = new AssignSkillRequest("Java");
        mockMvc.perform(post("/api/candidate/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(delete("/api/candidate/skills/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("P4.4-12: Recruiter accessing candidate skills returns 403")
    void shouldReturn403WhenRecruiterAccessesSkills() throws Exception {
        UserAuth recruiter = registerUserAndGetAuth("recruiter.skills@example.com", Role.RECRUITER, "Recruiter Joe");

        mockMvc.perform(get("/api/candidate/skills")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        AssignSkillRequest request = new AssignSkillRequest("Java");
        mockMvc.perform(post("/api/candidate/skills")
                        .header("Authorization", "Bearer " + recruiter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/candidate/skills/1")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }
}
