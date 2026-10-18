package com.careerconnect.candidate.controller;

import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.CreateEducationRequest;
import com.careerconnect.candidate.dto.UpdateEducationRequest;
import com.careerconnect.candidate.repository.CandidateEducationRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CandidateEducationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CandidateEducationRepository candidateEducationRepository;

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

    private Long createEducationRecord(String token, CreateEducationRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/candidate/education")
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
    @DisplayName("P4.3-1 & P4.3-10: Candidate can create education and record persists in database")
    void shouldCreateEducationSuccessfully() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.edu1@example.com", Role.CANDIDATE, "Student One");

        CreateEducationRequest request = new CreateEducationRequest(
                "National Institute of Technology",
                "Bachelor of Technology",
                "Computer Science and Engineering",
                2020,
                2024,
                "8.8 CGPA",
                1
        );

        MvcResult result = mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.candidateId").value(user.candidateId()))
                .andExpect(jsonPath("$.data.institution").value("National Institute of Technology"))
                .andExpect(jsonPath("$.data.degree").value("Bachelor of Technology"))
                .andExpect(jsonPath("$.data.fieldOfStudy").value("Computer Science and Engineering"))
                .andExpect(jsonPath("$.data.startYear").value(2020))
                .andExpect(jsonPath("$.data.endYear").value(2024))
                .andExpect(jsonPath("$.data.grade").value("8.8 CGPA"))
                .andExpect(jsonPath("$.data.sortOrder").value(1))
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        Long createdId = node.path("data").path("id").asLong();

        // Direct DB verification
        var found = candidateEducationRepository.findByIdAndCandidateId(createdId, user.candidateId());
        assertThat(found).isPresent();
        assertThat(found.get().institution()).isEqualTo("National Institute of Technology");
    }

    @Test
    @DisplayName("P4.3-2 & P4.3-11: Candidate can update own education and changes persist correctly")
    void shouldUpdateOwnEducationSuccessfully() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.edu2@example.com", Role.CANDIDATE, "Student Two");

        CreateEducationRequest initial = new CreateEducationRequest(
                "State University",
                "BSc",
                "Information Technology",
                2019,
                2022,
                "First Class",
                0
        );
        Long eduId = createEducationRecord(user.token(), initial);

        UpdateEducationRequest updateRequest = new UpdateEducationRequest(
                "State University of Technology",
                "Bachelor of Science",
                "Information Technology",
                2019,
                2023,
                "Distinction",
                2
        );

        mockMvc.perform(put("/api/candidate/education/" + eduId)
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(eduId))
                .andExpect(jsonPath("$.data.institution").value("State University of Technology"))
                .andExpect(jsonPath("$.data.degree").value("Bachelor of Science"))
                .andExpect(jsonPath("$.data.endYear").value(2023))
                .andExpect(jsonPath("$.data.grade").value("Distinction"))
                .andExpect(jsonPath("$.data.sortOrder").value(2));

        // Direct DB verification
        var found = candidateEducationRepository.findByIdAndCandidateId(eduId, user.candidateId());
        assertThat(found).isPresent();
        assertThat(found.get().institution()).isEqualTo("State University of Technology");
        assertThat(found.get().grade()).isEqualTo("Distinction");
    }

    @Test
    @DisplayName("P4.3-3 & P4.3-12: Candidate can delete own education and record is no longer retrievable")
    void shouldDeleteOwnEducationSuccessfully() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.edu3@example.com", Role.CANDIDATE, "Student Three");

        CreateEducationRequest initial = new CreateEducationRequest(
                "Old College",
                "Diploma",
                "Computer Engineering",
                2018,
                2020,
                null,
                0
        );
        Long eduId = createEducationRecord(user.token(), initial);

        // Delete education
        mockMvc.perform(delete("/api/candidate/education/" + eduId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNoContent());

        // Verify it is no longer in repository
        var found = candidateEducationRepository.findByIdAndCandidateId(eduId, user.candidateId());
        assertThat(found).isEmpty();

        // Attempting to delete again returns 404
        mockMvc.perform(delete("/api/candidate/education/" + eduId)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P4.3-4: Candidate A cannot update Candidate B's education (returns 404)")
    void shouldNotAllowCandidateToUpdateAnotherCandidatesEducation() throws Exception {
        UserAuth userA = registerUserAndGetAuth("candidate.userA@example.com", Role.CANDIDATE, "User A");
        UserAuth userB = registerUserAndGetAuth("candidate.userB@example.com", Role.CANDIDATE, "User B");

        CreateEducationRequest initial = new CreateEducationRequest(
                "User A University",
                "BTech",
                "CSE",
                2020,
                2024,
                "8.0",
                0
        );
        Long eduIdA = createEducationRecord(userA.token(), initial);

        UpdateEducationRequest maliciousUpdate = new UpdateEducationRequest(
                "Hacked University",
                "Hacked Degree",
                "Hacked Field",
                2020,
                2024,
                "10.0",
                0
        );

        // Candidate B tries to update Candidate A's education
        mockMvc.perform(put("/api/candidate/education/" + eduIdA)
                        .header("Authorization", "Bearer " + userB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousUpdate)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Verify Candidate A's record was untouched
        var found = candidateEducationRepository.findByIdAndCandidateId(eduIdA, userA.candidateId());
        assertThat(found).isPresent();
        assertThat(found.get().institution()).isEqualTo("User A University");
    }

    @Test
    @DisplayName("P4.3-5: Candidate A cannot delete Candidate B's education (returns 404)")
    void shouldNotAllowCandidateToDeleteAnotherCandidatesEducation() throws Exception {
        UserAuth userA = registerUserAndGetAuth("candidate.userA2@example.com", Role.CANDIDATE, "User A2");
        UserAuth userB = registerUserAndGetAuth("candidate.userB2@example.com", Role.CANDIDATE, "User B2");

        CreateEducationRequest initial = new CreateEducationRequest(
                "User A University",
                "BTech",
                "CSE",
                2020,
                2024,
                "8.0",
                0
        );
        Long eduIdA = createEducationRecord(userA.token(), initial);

        // Candidate B tries to delete Candidate A's education
        mockMvc.perform(delete("/api/candidate/education/" + eduIdA)
                        .header("Authorization", "Bearer " + userB.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        // Verify Candidate A's record still exists
        var found = candidateEducationRepository.findByIdAndCandidateId(eduIdA, userA.candidateId());
        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("P4.3-6: Nonexistent education returns 404")
    void shouldReturn404ForNonexistentEducation() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.edu404@example.com", Role.CANDIDATE, "Test User");

        UpdateEducationRequest updateRequest = new UpdateEducationRequest(
                "College",
                "Degree",
                "Field",
                2020,
                2024,
                null,
                0
        );

        mockMvc.perform(put("/api/candidate/education/999999")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(delete("/api/candidate/education/999999")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("P4.3-7: Unauthenticated education requests return 401")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        CreateEducationRequest request = new CreateEducationRequest("Inst", "Deg", "Field", 2020, 2024, null, 0);

        mockMvc.perform(post("/api/candidate/education")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(put("/api/candidate/education/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(delete("/api/candidate/education/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("P4.3-8: Recruiter accessing education endpoints returns 403")
    void shouldReturn403WhenRecruiterAccessesEducation() throws Exception {
        UserAuth recruiter = registerUserAndGetAuth("recruiter.edu@example.com", Role.RECRUITER, "Recruiter Name");
        CreateEducationRequest request = new CreateEducationRequest("Inst", "Deg", "Field", 2020, 2024, null, 0);

        mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + recruiter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mockMvc.perform(put("/api/candidate/education/1")
                        .header("Authorization", "Bearer " + recruiter.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/candidate/education/1")
                        .header("Authorization", "Bearer " + recruiter.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("P4.3-9: Invalid education request returns 400 VALIDATION_FAILED")
    void shouldReturn400WhenEducationRequestIsInvalid() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.invalid.edu@example.com", Role.CANDIDATE, "Student Invalid");

        // 1. Blank institution
        CreateEducationRequest blankInst = new CreateEducationRequest("  ", "BTech", "CSE", 2020, 2024, null, 0);
        mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankInst)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Institution cannot be blank")));

        // 2. Blank degree
        CreateEducationRequest blankDegree = new CreateEducationRequest("IIT Bombay", "", "CSE", 2020, 2024, null, 0);
        mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankDegree)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Degree cannot be blank")));

        // 3. Blank fieldOfStudy
        CreateEducationRequest blankField = new CreateEducationRequest("IIT Bombay", "BTech", "   ", 2020, 2024, null, 0);
        mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankField)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Field of study cannot be blank")));

        // 4. Missing startYear
        CreateEducationRequest missingYear = new CreateEducationRequest("IIT Bombay", "BTech", "CSE", null, 2024, null, 0);
        mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingYear)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Start year is required")));

        // 5. endYear < startYear
        CreateEducationRequest invalidYearRange = new CreateEducationRequest("IIT Bombay", "BTech", "CSE", 2024, 2020, null, 0);
        mockMvc.perform(post("/api/candidate/education")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidYearRange)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("End year cannot be before start year")));
    }

    @Test
    @DisplayName("P4.3-13: Authenticated candidate can GET own education list")
    void shouldGetOwnEducationListSuccessfully() throws Exception {
        UserAuth user = registerUserAndGetAuth("candidate.edulist@example.com", Role.CANDIDATE, "Student List");

        CreateEducationRequest edu1 = new CreateEducationRequest("Uni A", "BSc", "CS", 2020, 2024, "8.0", 1);
        CreateEducationRequest edu2 = new CreateEducationRequest("Uni B", "MSc", "CS", 2024, 2026, "9.0", 2);
        createEducationRecord(user.token(), edu1);
        createEducationRecord(user.token(), edu2);

        mockMvc.perform(get("/api/candidate/education")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].institution").value("Uni A"))
                .andExpect(jsonPath("$.data[1].institution").value("Uni B"));
    }
}
