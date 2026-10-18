package com.careerconnect.candidate.controller;

import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.CreateProjectRequest;
import com.careerconnect.candidate.dto.UpdateProjectRequest;
import com.careerconnect.candidate.repository.CandidateProjectRepository;
import com.careerconnect.common.model.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CandidateProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CandidateProjectRepository candidateProjectRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM projects");
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

    private Long createProjectRecord(String token, CreateProjectRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/candidate/projects")
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
    @DisplayName("P4.5-1 & P4.5-2: Candidate can create project and it persists in database")
    void shouldCreateProjectSuccessfully() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.proj1@example.com", Role.CANDIDATE, "Dev One");

        CreateProjectRequest request = new CreateProjectRequest(
                "Distributed Cache System",
                "High performance in-memory key-value store with Raft consensus.",
                "https://github.com/example/cache",
                1
        );

        MvcResult result = mockMvc.perform(post("/api/candidate/projects")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.candidateId").value(user.candidateId()))
                .andExpect(jsonPath("$.data.title").value("Distributed Cache System"))
                .andExpect(jsonPath("$.data.description").value("High performance in-memory key-value store with Raft consensus."))
                .andExpect(jsonPath("$.data.projectUrl").value("https://github.com/example/cache"))
                .andExpect(jsonPath("$.data.sortOrder").value(1))
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        Long createdId = node.path("data").path("id").asLong();

        // Direct DB verification
        var found = candidateProjectRepository.findByIdAndCandidateId(createdId, user.candidateId());
        assertThat(found).isPresent();
        assertThat(found.get().title()).isEqualTo("Distributed Cache System");
    }

    @Test
    @DisplayName("P4.5-3 & P4.5-4: Candidate can update own project and changes persist correctly")
    void shouldUpdateOwnProjectSuccessfully() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.proj2@example.com", Role.CANDIDATE, "Dev Two");

        CreateProjectRequest initial = new CreateProjectRequest(
                "Initial Title",
                "Initial Description",
                null,
                0
        );
        Long projectId = createProjectRecord(user.token(), initial);

        UpdateProjectRequest updateRequest = new UpdateProjectRequest(
                "Updated Title",
                "Updated Description with new features",
                "https://github.com/example/updated",
                5
        );

        mockMvc.perform(put("/api/candidate/projects/" + projectId)
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(projectId))
                .andExpect(jsonPath("$.data.title").value("Updated Title"))
                .andExpect(jsonPath("$.data.description").value("Updated Description with new features"))
                .andExpect(jsonPath("$.data.projectUrl").value("https://github.com/example/updated"))
                .andExpect(jsonPath("$.data.sortOrder").value(5));

        // Direct DB verification
        var found = candidateProjectRepository.findByIdAndCandidateId(projectId, user.candidateId());
        assertThat(found).isPresent();
        assertThat(found.get().title()).isEqualTo("Updated Title");
        assertThat(found.get().projectUrl()).isEqualTo("https://github.com/example/updated");
    }

    @Test
    @DisplayName("P4.5-5 & P4.5-6: Candidate can delete own project and it is no longer available")
    void shouldDeleteOwnProjectSuccessfully() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.proj3@example.com", Role.CANDIDATE, "Dev Three");

        CreateProjectRequest initial = new CreateProjectRequest(
                "Delete Me Project",
                "Description",
                null,
                0
        );
        Long projectId = createProjectRecord(user.token(), initial);

        // Delete project
        mockMvc.perform(delete("/api/candidate/projects/" + projectId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNoContent());

        // Verify it is no longer in repository
        var found = candidateProjectRepository.findByIdAndCandidateId(projectId, user.candidateId());
        assertThat(found).isEmpty();

        // Subsequent delete returns 404
        mockMvc.perform(delete("/api/candidate/projects/" + projectId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P4.5-7 & P4.5-13: Candidate A cannot update Candidate B's project (returns 404)")
    void shouldNotAllowCandidateToUpdateAnotherCandidatesProject() throws Exception {
        UserAuth userA = registerUserAndGetAuth("candidate.projA@example.com", Role.CANDIDATE, "User A");
        UserAuth userB = registerUserAndGetAuth("candidate.projB@example.com", Role.CANDIDATE, "User B");

        CreateProjectRequest initial = new CreateProjectRequest(
                "User A Project",
                "User A Description",
                null,
                0
        );
        Long projIdA = createProjectRecord(userA.token(), initial);

        UpdateProjectRequest maliciousUpdate = new UpdateProjectRequest(
                "Hacked Title",
                "Hacked Description",
                null,
                0
        );

        // Candidate B tries to update Candidate A's project
        mockMvc.perform(put("/api/candidate/projects/" + projIdA)
                        .header("Authorization", "Bearer " + userB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousUpdate)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Verify Candidate A's project was untouched
        var found = candidateProjectRepository.findByIdAndCandidateId(projIdA, userA.candidateId());
        assertThat(found).isPresent();
        assertThat(found.get().title()).isEqualTo("User A Project");
    }

    @Test
    @DisplayName("P4.5-8 & P4.5-13: Candidate A cannot delete Candidate B's project (returns 404)")
    void shouldNotAllowCandidateToDeleteAnotherCandidatesProject() throws Exception {
        UserAuth userA = registerUserAndGetAuth("candidate.projA2@example.com", Role.CANDIDATE, "User A2");
        UserAuth userB = registerUserAndGetAuth("candidate.projB2@example.com", Role.CANDIDATE, "User B2");

        CreateProjectRequest initial = new CreateProjectRequest(
                "User A Project",
                "User A Description",
                null,
                0
        );
        Long projIdA = createProjectRecord(userA.token(), initial);

        // Candidate B tries to delete Candidate A's project
        mockMvc.perform(delete("/api/candidate/projects/" + projIdA)
                        .header("Authorization", "Bearer " + userB.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Verify Candidate A's project still exists
        var found = candidateProjectRepository.findByIdAndCandidateId(projIdA, userA.candidateId());
        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("P4.5-9: Nonexistent project returns 404")
    void shouldReturn404ForNonexistentProject() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.proj404@example.com", Role.CANDIDATE, "Test User");

        UpdateProjectRequest updateRequest = new UpdateProjectRequest("Title", "Desc", null, 0);

        mockMvc.perform(put("/api/candidate/projects/999999")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(delete("/api/candidate/projects/999999")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P4.5-10: Invalid project request returns 400 VALIDATION_FAILED")
    void shouldReturn400WhenProjectRequestIsInvalid() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.projinv@example.com", Role.CANDIDATE, "Student Invalid");

        // 1. Blank title
        CreateProjectRequest blankTitle = new CreateProjectRequest("  ", "Valid Description", null, 0);
        mockMvc.perform(post("/api/candidate/projects")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankTitle)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Title cannot be blank")));

        // 2. Blank description
        CreateProjectRequest blankDesc = new CreateProjectRequest("Valid Title", "   ", null, 0);
        mockMvc.perform(post("/api/candidate/projects")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankDesc)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Description cannot be blank")));

        // 3. Oversized title (> 150 chars)
        CreateProjectRequest longTitle = new CreateProjectRequest("T".repeat(151), "Description", null, 0);
        mockMvc.perform(post("/api/candidate/projects")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longTitle)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Title cannot exceed 150 characters")));

        // 4. Oversized projectUrl (> 255 chars)
        CreateProjectRequest longUrl = new CreateProjectRequest("Valid Title", "Description", "https://example.com/" + "u".repeat(250), 0);
        mockMvc.perform(post("/api/candidate/projects")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longUrl)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Project URL cannot exceed 255 characters")));
    }

    @Test
    @DisplayName("P4.5-11: Unauthenticated project requests return 401")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        CreateProjectRequest request = new CreateProjectRequest("Title", "Desc", null, 0);

        mockMvc.perform(post("/api/candidate/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(put("/api/candidate/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(delete("/api/candidate/projects/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("P4.5-12: Recruiter accessing project endpoints returns 403")
    void shouldReturn403WhenRecruiterAccessesProjects() throws Exception {
        UserAuth recruiter = registerUserAndGetAuth("recruiter.proj@example.com", Role.RECRUITER, "Recruiter Name");
        CreateProjectRequest request = new CreateProjectRequest("Title", "Desc", null, 0);

        mockMvc.perform(post("/api/candidate/projects")
                        .header("Authorization", "Bearer " + recruiter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mockMvc.perform(put("/api/candidate/projects/1")
                        .header("Authorization", "Bearer " + recruiter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/candidate/projects/1")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("P4.6-13: Authenticated candidate can GET own project list")
    void shouldGetOwnProjectListSuccessfully() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.projlist@example.com", Role.CANDIDATE, "Proj User");

        CreateProjectRequest proj1 = new CreateProjectRequest("Project Alpha", "First project", "https://example.com/alpha", 1);
        CreateProjectRequest proj2 = new CreateProjectRequest("Project Beta", "Second project", "https://example.com/beta", 2);
        createProjectRecord(user.token(), proj1);
        createProjectRecord(user.token(), proj2);

        mockMvc.perform(get("/api/candidate/projects")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].title").value("Project Alpha"))
                .andExpect(jsonPath("$.data[1].title").value("Project Beta"));
    }
}
