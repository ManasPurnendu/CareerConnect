package com.careerconnect.auth.controller;

import com.careerconnect.auth.dto.LoginRequest;
import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.common.model.Role;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/auth/register should successfully register a candidate and return JWT")
    void shouldRegisterCandidateSuccessfully() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "candidate.alice@example.com",
                "Password123!",
                Role.CANDIDATE,
                "Alice Candidate"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("candidate.alice@example.com"))
                .andExpect(jsonPath("$.data.user.role").value("CANDIDATE"))
                .andExpect(jsonPath("$.data.user.fullName").value("Alice Candidate"))
                .andExpect(jsonPath("$.data.user.candidateId").isNumber())
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.user.recruiterId").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/register should successfully register a recruiter and return JWT")
    void shouldRegisterRecruiterSuccessfully() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "recruiter.bob@example.com",
                "Password123!",
                Role.RECRUITER,
                "Bob Recruiter"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.user.email").value("recruiter.bob@example.com"))
                .andExpect(jsonPath("$.data.user.role").value("RECRUITER"))
                .andExpect(jsonPath("$.data.user.fullName").value("Bob Recruiter"))
                .andExpect(jsonPath("$.data.user.recruiterId").isNumber())
                .andExpect(jsonPath("$.data.user.candidateId").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/register should reject ADMIN self-registration")
    void shouldRejectAdminSelfRegistration() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "malicious.admin@example.com",
                "Password123!",
                Role.ADMIN,
                "Fake Admin"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Administrative accounts cannot be registered publicly."));
    }

    @Test
    @DisplayName("POST /api/auth/register should reject duplicate email with 409 EMAIL_ALREADY_EXISTS")
    void shouldRejectDuplicateEmailRegistration() throws Exception {
        RegisterRequest request1 = new RegisterRequest(
                "duplicate.user@example.com",
                "Password123!",
                Role.CANDIDATE,
                "First User"
        );
        RegisterRequest request2 = new RegisterRequest(
                "DUPLICATE.USER@EXAMPLE.COM", // Case-insensitive duplicate check
                "Password456!",
                Role.CANDIDATE,
                "Second User"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("An account with this email address already exists."));
    }

    @Test
    @DisplayName("POST /api/auth/register should reject invalid email format")
    void shouldRejectInvalidEmail() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "not-an-email",
                "Password123!",
                Role.CANDIDATE,
                "User Name"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/auth/register should reject password shorter than 8 characters")
    void shouldRejectShortPassword() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "shortpass@example.com",
                "short",
                Role.CANDIDATE,
                "Short Pass User"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/auth/login should authenticate registered user and return token")
    void shouldLoginSuccessfully() throws Exception {
        String email = "login.candidate@example.com";
        String password = "CorrectPassword123!";

        RegisterRequest registerReq = new RegisterRequest(email, password, Role.CANDIDATE, "Login Candidate");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest(email.toUpperCase(), password); // Test case insensitivity
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.role").value("CANDIDATE"))
                .andExpect(jsonPath("$.data.user.fullName").value("Login Candidate"));
    }

    @Test
    @DisplayName("POST /api/auth/login should reject incorrect password with 401 INVALID_CREDENTIALS")
    void shouldRejectIncorrectPassword() throws Exception {
        String email = "wrongpass@example.com";
        RegisterRequest registerReq = new RegisterRequest(email, "Password123!", Role.CANDIDATE, "Wrong Pass User");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest(email, "WrongPassword999!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    @DisplayName("POST /api/auth/login should reject non-existent email with 401 INVALID_CREDENTIALS")
    void shouldRejectNonExistentEmail() throws Exception {
        LoginRequest loginReq = new LoginRequest("nonexistent@example.com", "Password123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("GET /api/auth/me should return current user with valid Bearer token")
    void shouldGetMeWithValidToken() throws Exception {
        RegisterRequest registerReq = new RegisterRequest(
                "me.test@example.com",
                "Password123!",
                Role.CANDIDATE,
                "Me Test Candidate"
        );

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("me.test@example.com"))
                .andExpect(jsonPath("$.data.role").value("CANDIDATE"))
                .andExpect(jsonPath("$.data.fullName").value("Me Test Candidate"))
                .andExpect(jsonPath("$.data.candidateId").isNumber());
    }

    @Test
    @DisplayName("GET /api/auth/me should reject request without token with 401 UNAUTHENTICATED")
    void shouldRejectGetMeWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("GET /api/auth/me should reject request with forged Bearer token")
    void shouldRejectGetMeWithForgedToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer forged.token.here"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("POST /api/auth/logout should return HTTP 204 No Content when authenticated")
    void shouldLogoutSuccessfully() throws Exception {
        RegisterRequest registerReq = new RegisterRequest(
                "logout.user@example.com",
                "Password123!",
                Role.CANDIDATE,
                "Logout User"
        );

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/auth/logout should reject unauthenticated request with 401 UNAUTHENTICATED")
    void shouldRejectLogoutWithoutToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }
}
