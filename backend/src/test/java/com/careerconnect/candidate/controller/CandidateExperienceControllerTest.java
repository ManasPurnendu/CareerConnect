package com.careerconnect.candidate.controller;

import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.CreateExperienceRequest;
import com.careerconnect.candidate.dto.UpdateExperienceRequest;
import com.careerconnect.candidate.repository.CandidateExperienceRepository;
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

import java.time.LocalDate;

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
class CandidateExperienceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CandidateExperienceRepository candidateExperienceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM experience");
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

    private Long createExperienceRecord(String token, CreateExperienceRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/candidate/experience")
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
    @DisplayName("P4.6-1 & P4.6-2: Candidate can create experience and it persists in database")
    void shouldCreateExperienceSuccessfully() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.exp1@example.com", Role.CANDIDATE, "Dev One");

        CreateExperienceRequest request = new CreateExperienceRequest(
                "Acme Corp",
                "Software Engineer",
                LocalDate.of(2022, 1, 15),
                LocalDate.of(2023, 6, 30),
                false,
                "Worked on distributed backend microservices.",
                1
        );

        MvcResult result = mockMvc.perform(post("/api/candidate/experience")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.candidateId").value(user.candidateId()))
                .andExpect(jsonPath("$.data.companyName").value("Acme Corp"))
                .andExpect(jsonPath("$.data.roleTitle").value("Software Engineer"))
                .andExpect(jsonPath("$.data.startDate").value("2022-01-15"))
                .andExpect(jsonPath("$.data.endDate").value("2023-06-30"))
                .andExpect(jsonPath("$.data.isCurrent").value(false))
                .andExpect(jsonPath("$.data.description").value("Worked on distributed backend microservices."))
                .andExpect(jsonPath("$.data.sortOrder").value(1))
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        Long createdId = node.path("data").path("id").asLong();

        // Direct DB verification
        var found = candidateExperienceRepository.findByIdAndCandidateId(createdId, user.candidateId());
        assertThat(found).isPresent();
        assertThat(found.get().companyName()).isEqualTo("Acme Corp");
    }

    @Test
    @DisplayName("P4.6-3 & P4.6-4: Candidate can update own experience and changes persist correctly")
    void shouldUpdateOwnExperienceSuccessfully() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.exp2@example.com", Role.CANDIDATE, "Dev Two");

        CreateExperienceRequest initial = new CreateExperienceRequest(
                "Old Company",
                "Junior Dev",
                LocalDate.of(2021, 6, 1),
                LocalDate.of(2022, 5, 31),
                false,
                "Initial description",
                0
        );
        Long expId = createExperienceRecord(user.token(), initial);

        UpdateExperienceRequest updateRequest = new UpdateExperienceRequest(
                "New Enterprise Corp",
                "Senior Dev",
                LocalDate.of(2021, 6, 1),
                null,
                true,
                "Promoted to Senior Dev in current role",
                2
        );

        mockMvc.perform(put("/api/candidate/experience/" + expId)
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(expId))
                .andExpect(jsonPath("$.data.companyName").value("New Enterprise Corp"))
                .andExpect(jsonPath("$.data.roleTitle").value("Senior Dev"))
                .andExpect(jsonPath("$.data.isCurrent").value(true))
                .andExpect(jsonPath("$.data.endDate").doesNotExist())
                .andExpect(jsonPath("$.data.sortOrder").value(2));

        // Direct DB verification
        var found = candidateExperienceRepository.findByIdAndCandidateId(expId, user.candidateId());
        assertThat(found).isPresent();
        assertThat(found.get().companyName()).isEqualTo("New Enterprise Corp");
        assertThat(found.get().isCurrent()).isTrue();
    }

    @Test
    @DisplayName("P4.6-5 & P4.6-6: Candidate can delete own experience and it is no longer available")
    void shouldDeleteOwnExperienceSuccessfully() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.exp3@example.com", Role.CANDIDATE, "Dev Three");

        CreateExperienceRequest initial = new CreateExperienceRequest(
                "Company to Delete",
                "Intern",
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2020, 6, 1),
                false,
                null,
                0
        );
        Long expId = createExperienceRecord(user.token(), initial);

        // Delete experience
        mockMvc.perform(delete("/api/candidate/experience/" + expId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNoContent());

        // Verify it is no longer in repository
        var found = candidateExperienceRepository.findByIdAndCandidateId(expId, user.candidateId());
        assertThat(found).isEmpty();

        // Subsequent delete returns 404
        mockMvc.perform(delete("/api/candidate/experience/" + expId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P4.6-7 & P4.6-8: Candidate A cannot update or delete Candidate B's experience (returns 404)")
    void shouldNotAllowCandidateToMutateAnotherCandidatesExperience() throws Exception {
        UserAuth userA = registerUserAndGetAuth("candidate.expA@example.com", Role.CANDIDATE, "User A");
        UserAuth userB = registerUserAndGetAuth("candidate.expB@example.com", Role.CANDIDATE, "User B");

        CreateExperienceRequest initial = new CreateExperienceRequest(
                "User A Company",
                "Role A",
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2023, 1, 1),
                false,
                null,
                0
        );
        Long expIdA = createExperienceRecord(userA.token(), initial);

        UpdateExperienceRequest maliciousUpdate = new UpdateExperienceRequest(
                "Hacked Company",
                "Hacked Role",
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2023, 1, 1),
                false,
                null,
                0
        );

        // Candidate B tries to update Candidate A's experience -> 404
        mockMvc.perform(put("/api/candidate/experience/" + expIdA)
                        .header("Authorization", "Bearer " + userB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousUpdate)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Candidate B tries to delete Candidate A's experience -> 404
        mockMvc.perform(delete("/api/candidate/experience/" + expIdA)
                        .header("Authorization", "Bearer " + userB.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Verify Candidate A's record remains untouched
        var found = candidateExperienceRepository.findByIdAndCandidateId(expIdA, userA.candidateId());
        assertThat(found).isPresent();
        assertThat(found.get().companyName()).isEqualTo("User A Company");
    }

    @Test
    @DisplayName("P4.6-9: Nonexistent experience returns 404")
    void shouldReturn404ForNonexistentExperience() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.exp404@example.com", Role.CANDIDATE, "Test User");

        UpdateExperienceRequest updateRequest = new UpdateExperienceRequest(
                "Company",
                "Role",
                LocalDate.of(2022, 1, 1),
                null,
                true,
                null,
                0
        );

        mockMvc.perform(put("/api/candidate/experience/999999")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(delete("/api/candidate/experience/999999")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P4.6-10, P4.6-11, P4.6-12: Invalid companyName, roleTitle, and missing startDate return 400")
    void shouldReturn400WhenBasicFieldsAreInvalid() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.expinv@example.com", Role.CANDIDATE, "Student Invalid");

        // Blank companyName
        CreateExperienceRequest blankCompany = new CreateExperienceRequest(
                "  ", "Dev", LocalDate.of(2022, 1, 1), null, true, null, 0
        );
        mockMvc.perform(post("/api/candidate/experience")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankCompany)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Company name cannot be blank")));

        // Blank roleTitle
        CreateExperienceRequest blankRole = new CreateExperienceRequest(
                "Acme", "   ", LocalDate.of(2022, 1, 1), null, true, null, 0
        );
        mockMvc.perform(post("/api/candidate/experience")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankRole)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Role title cannot be blank")));

        // Missing startDate
        CreateExperienceRequest missingStart = new CreateExperienceRequest(
                "Acme", "Dev", null, null, true, null, 0
        );
        mockMvc.perform(post("/api/candidate/experience")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingStart)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Start date is required")));
    }

    @Test
    @DisplayName("P4.6-13 & P4.6-14: Current vs non-current and endDate before startDate return 400")
    void shouldValidateDatesAccordingToCurrentState() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.expdates@example.com", Role.CANDIDATE, "Date Tester");

        // 1. Past experience missing endDate -> 400
        CreateExperienceRequest pastMissingEnd = new CreateExperienceRequest(
                "Acme", "Dev", LocalDate.of(2022, 1, 1), null, false, null, 0
        );
        mockMvc.perform(post("/api/candidate/experience")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pastMissingEnd)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("End date is required for past experience")));

        // 2. endDate before startDate -> 400
        CreateExperienceRequest endBeforeStart = new CreateExperienceRequest(
                "Acme", "Dev", LocalDate.of(2023, 1, 1), LocalDate.of(2022, 1, 1), false, null, 0
        );
        mockMvc.perform(post("/api/candidate/experience")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(endBeforeStart)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("End date cannot be before start date")));

        // 3. Current experience without endDate -> 201 Created
        CreateExperienceRequest currentValid = new CreateExperienceRequest(
                "Acme Current", "Tech Lead", LocalDate.of(2023, 1, 1), null, true, "Ongoing lead role", 0
        );
        mockMvc.perform(post("/api/candidate/experience")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(currentValid)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isCurrent").value(true))
                .andExpect(jsonPath("$.data.endDate").doesNotExist());
    }

    @Test
    @DisplayName("P4.6-15: Unauthenticated experience requests return 401")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        CreateExperienceRequest request = new CreateExperienceRequest("Acme", "Dev", LocalDate.of(2022, 1, 1), null, true, null, 0);

        mockMvc.perform(post("/api/candidate/experience")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(put("/api/candidate/experience/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(delete("/api/candidate/experience/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("P4.6-16: Recruiter accessing experience endpoints returns 403")
    void shouldReturn403WhenRecruiterAccessesExperience() throws Exception {
        UserAuth recruiter = registerUserAndGetAuth("recruiter.exp@example.com", Role.RECRUITER, "Recruiter Name");
        CreateExperienceRequest request = new CreateExperienceRequest("Acme", "Dev", LocalDate.of(2022, 1, 1), null, true, null, 0);

        mockMvc.perform(post("/api/candidate/experience")
                        .header("Authorization", "Bearer " + recruiter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mockMvc.perform(put("/api/candidate/experience/1")
                        .header("Authorization", "Bearer " + recruiter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/candidate/experience/1")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("P4.5-13: Authenticated candidate can GET own experience list")
    void shouldGetOwnExperienceListSuccessfully() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.explist@example.com", Role.CANDIDATE, "Exp User");

        CreateExperienceRequest exp1 = new CreateExperienceRequest("Company 1", "Dev", LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 1), false, "Desc 1", 1);
        CreateExperienceRequest exp2 = new CreateExperienceRequest("Company 2", "Senior Dev", LocalDate.of(2022, 2, 1), null, true, "Desc 2", 2);
        createExperienceRecord(user.token(), exp1);
        createExperienceRecord(user.token(), exp2);

        mockMvc.perform(get("/api/candidate/experience")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].companyName").value("Company 1"))
                .andExpect(jsonPath("$.data[1].companyName").value("Company 2"));
    }
}
