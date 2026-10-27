package com.careerconnect.candidate.controller;

import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.candidate.dto.UpdateProfileRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CandidateProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerUserAndGetToken(String email, Role role, String fullName) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(email, "Password123!", role, fullName);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("P4.2-1: Authenticated candidate can GET own profile")
    void shouldGetOwnCandidateProfile() throws Exception {
        String token = registerUserAndGetToken("candidate.get@example.com", Role.CANDIDATE, "Alice Bob");

        mockMvc.perform(get("/api/candidate/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.fullName").value("Alice Bob"))
                .andExpect(jsonPath("$.data.headline").doesNotExist())
                .andExpect(jsonPath("$.data.location").doesNotExist())
                .andExpect(jsonPath("$.data.summary").doesNotExist());
    }

    @Test
    @DisplayName("P4.2-2 & P4.2-6: Authenticated candidate can PUT own profile and changes persist to DB")
    void shouldUpdateOwnCandidateProfileAndPersist() throws Exception {
        String token = registerUserAndGetToken("candidate.put@example.com", Role.CANDIDATE, "Charlie Delta");

        UpdateProfileRequest updateRequest = new UpdateProfileRequest(
                "Charlie Delta Updated",
                "Backend Software Engineer",
                "Bengaluru, India",
                "Passionate about distributed systems and Spring Boot."
        );

        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Charlie Delta Updated"))
                .andExpect(jsonPath("$.data.headline").value("Backend Software Engineer"))
                .andExpect(jsonPath("$.data.location").value("Bengaluru, India"))
                .andExpect(jsonPath("$.data.summary").value("Passionate about distributed systems and Spring Boot."));

        // Verify persistence with a subsequent GET
        mockMvc.perform(get("/api/candidate/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Charlie Delta Updated"))
                .andExpect(jsonPath("$.data.headline").value("Backend Software Engineer"))
                .andExpect(jsonPath("$.data.location").value("Bengaluru, India"))
                .andExpect(jsonPath("$.data.summary").value("Passionate about distributed systems and Spring Boot."));
    }

    @Test
    @DisplayName("P4.2-3: Unauthenticated request to candidate profile returns 401")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/candidate/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        UpdateProfileRequest updateRequest = new UpdateProfileRequest("Hacker", "Title", "Loc", "Summary");
        mockMvc.perform(put("/api/candidate/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("P4.2-4: Recruiter accessing candidate profile returns 403")
    void shouldReturn403WhenRecruiterAccessesCandidateProfile() throws Exception {
        String recruiterToken = registerUserAndGetToken("recruiter.probe@example.com", Role.RECRUITER, "Recruiter Bob");

        mockMvc.perform(get("/api/candidate/profile")
                        .header("Authorization", "Bearer " + recruiterToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        UpdateProfileRequest updateRequest = new UpdateProfileRequest("Recruiter Hack", "Title", "Loc", "Summary");
        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("P4.2-5: Invalid fullName validation returns 400 VALIDATION_FAILED")
    void shouldReturn400WhenFullNameIsInvalid() throws Exception {
        String token = registerUserAndGetToken("candidate.invalid@example.com", Role.CANDIDATE, "Valid Name");

        // Blank fullName
        UpdateProfileRequest blankNameRequest = new UpdateProfileRequest(
                "   ",
                "Headline",
                "Location",
                "Summary"
        );

        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankNameRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Full name cannot be blank")));

        // Oversized fullName (> 150 chars)
        UpdateProfileRequest oversizedNameRequest = new UpdateProfileRequest(
                "A".repeat(151),
                "Headline",
                "Location",
                "Summary"
        );

        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oversizedNameRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Full name cannot exceed 150 characters")));

        // Oversized headline (> 255 chars)
        UpdateProfileRequest oversizedHeadlineRequest = new UpdateProfileRequest(
                "Valid Name",
                "H".repeat(256),
                "Location",
                "Summary"
        );

        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oversizedHeadlineRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Headline cannot exceed 255 characters")));

        // Oversized location (> 150 chars)
        UpdateProfileRequest oversizedLocationRequest = new UpdateProfileRequest(
                "Valid Name",
                "Headline",
                "L".repeat(151),
                "Summary"
        );

        mockMvc.perform(put("/api/candidate/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oversizedLocationRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Location cannot exceed 150 characters")));
    }
}
