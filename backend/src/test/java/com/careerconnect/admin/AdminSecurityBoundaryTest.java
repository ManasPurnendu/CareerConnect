package com.careerconnect.admin;

import com.careerconnect.common.model.Role;
import com.careerconnect.security.JwtService;
import com.careerconnect.security.SecurityUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityBoundaryTest {

    @TestConfiguration
    static class TestAdminControllerConfig {
        @RestController
        @RequestMapping("/api/admin/ping")
        static class TestAdminController {
            @GetMapping
            public ResponseEntity<Map<String, String>> ping() {
                return ResponseEntity.ok(Map.of("status", "ok", "message", "Admin access verified"));
            }
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private String createAdminToken() {
        SecurityUser admin = new SecurityUser(1L, "admin@careerconnect.com", "HASH", Role.ADMIN, "Platform Admin", null, null, null, true);
        return jwtService.generateToken(admin);
    }

    private String createCandidateToken() {
        SecurityUser candidate = new SecurityUser(2L, "candidate@example.com", "HASH", Role.CANDIDATE, "Jane Candidate", 101L, null, null, true);
        return jwtService.generateToken(candidate);
    }

    private String createRecruiterToken() {
        SecurityUser recruiter = new SecurityUser(3L, "recruiter@example.com", "HASH", Role.RECRUITER, "John Recruiter", null, 201L, 301L, true);
        return jwtService.generateToken(recruiter);
    }

    @Test
    @DisplayName("Unauthenticated guest accessing /api/admin/** is rejected with 401 Unauthorized")
    void guestAccess_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/ping")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Authenticated CANDIDATE accessing /api/admin/** is rejected with 403 Forbidden")
    void candidateAccess_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createCandidateToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Authenticated RECRUITER accessing /api/admin/** is rejected with 403 Forbidden")
    void recruiterAccess_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createRecruiterToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Authenticated ADMIN accessing /api/admin/** is permitted with 200 OK")
    void adminAccess_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createAdminToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.message").value("Admin access verified"));
    }

    @Test
    @DisplayName("Candidate accessing unmapped /api/admin/users route is denied with 403 before reaching dispatcher")
    void candidateAccessUnmappedRoute_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createCandidateToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Recruiter accessing unmapped /api/admin/companies route is denied with 403 before reaching dispatcher")
    void recruiterAccessUnmappedRoute_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createRecruiterToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Guest accessing unmapped /api/admin/audit-logs route is denied with 401 before reaching dispatcher")
    void guestAccessUnmappedRoute_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }
}
