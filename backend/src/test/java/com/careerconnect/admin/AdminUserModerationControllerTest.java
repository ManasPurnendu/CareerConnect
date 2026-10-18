package com.careerconnect.admin;

import com.careerconnect.common.model.Role;
import com.careerconnect.common.util.JdbcUtils;
import com.careerconnect.security.JwtService;
import com.careerconnect.security.SecurityUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserModerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private Long adminUserId;
    private String adminToken;
    private String candidateToken;
    private String recruiterToken;

    @BeforeEach
    void setUp() {
        tearDown();

        Long existingAdminId = jdbcTemplate.query(
                "SELECT id FROM users WHERE email = 'admin@careerconnect.com'",
                (rs, rowNum) -> rs.getLong("id")
        ).stream().findFirst().orElse(null);

        if (existingAdminId == null) {
            KeyHolder kh = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO users (email, password_hash, role, is_active, created_at, updated_at) " +
                                "VALUES ('admin@careerconnect.com', '$2a$12$dummyHashForAdminTestPass1234567890123456', 'ADMIN', true, NOW(), NOW())",
                        Statement.RETURN_GENERATED_KEYS
                );
                return ps;
            }, kh);
            adminUserId = JdbcUtils.extractGeneratedId(kh);
        } else {
            adminUserId = existingAdminId;
        }

        SecurityUser adminUser = new SecurityUser(adminUserId, "admin@careerconnect.com", "HASH", Role.ADMIN, "Admin User", null, null, null, true);
        adminToken = jwtService.generateToken(adminUser);

        SecurityUser candidateUser = new SecurityUser(998L, "cand@example.com", "HASH", Role.CANDIDATE, "Candidate", 55L, null, null, true);
        candidateToken = jwtService.generateToken(candidateUser);

        SecurityUser recruiterUser = new SecurityUser(999L, "rec@example.com", "HASH", Role.RECRUITER, "Recruiter", null, 66L, 77L, true);
        recruiterToken = jwtService.generateToken(recruiterUser);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM audit_logs");
        jdbcTemplate.execute("DELETE FROM application_status_history");
        jdbcTemplate.execute("DELETE FROM applications");
        jdbcTemplate.execute("DELETE FROM job_skills");
        jdbcTemplate.execute("DELETE FROM jobs");
        jdbcTemplate.execute("DELETE FROM recruiters");
        jdbcTemplate.execute("DELETE FROM candidates");
        jdbcTemplate.execute("DELETE FROM companies");
        jdbcTemplate.execute("DELETE FROM users WHERE email != 'admin@careerconnect.com'");
    }

    private Long createUser(String email, Role role, boolean active, String rawPassword) {
        KeyHolder kh = new GeneratedKeyHolder();
        String hash = passwordEncoder.encode(rawPassword);
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO users (email, password_hash, role, is_active, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, email);
            ps.setString(2, hash);
            ps.setString(3, role.name());
            ps.setBoolean(4, active);
            return ps;
        }, kh);
        Long userId = JdbcUtils.extractGeneratedId(kh);

        if (role == Role.CANDIDATE) {
            jdbcTemplate.update("INSERT INTO candidates (user_id, full_name, created_at, updated_at) VALUES (?, 'Test Candidate', NOW(), NOW())", userId);
        } else if (role == Role.RECRUITER) {
            jdbcTemplate.update("INSERT INTO recruiters (user_id, full_name, created_at, updated_at) VALUES (?, 'Test Recruiter', NOW(), NOW())", userId);
        }
        return userId;
    }

    @Test
    @DisplayName("P8.3-USR-01: Admin successfully deactivates an active user and creates exactly one audit record")
    void adminDeactivatesActiveUser() throws Exception {
        Long targetUserId = createUser("target.user@example.com", Role.CANDIDATE, true, "Pass@1234");

        mockMvc.perform(patch("/api/admin/users/{userId}/status", targetUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(targetUserId.intValue())))
                .andExpect(jsonPath("$.data.active", is(false)));

        // Verify DB state
        Boolean isActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM users WHERE id = ?", Boolean.class, targetUserId);
        assertThat(isActive).isFalse();

        // Verify exactly one audit log written
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'USER' AND entity_id = ?",
                Integer.class, targetUserId);
        assertThat(auditCount).isEqualTo(1);

        Long actorId = jdbcTemplate.queryForObject(
                "SELECT actor_user_id FROM audit_logs WHERE entity_type = 'USER' AND entity_id = ?",
                Long.class, targetUserId);
        String action = jdbcTemplate.queryForObject(
                "SELECT action FROM audit_logs WHERE entity_type = 'USER' AND entity_id = ?",
                String.class, targetUserId);
        String metadataStr = jdbcTemplate.queryForObject(
                "SELECT metadata FROM audit_logs WHERE entity_type = 'USER' AND entity_id = ?",
                String.class, targetUserId);

        assertThat(actorId).isEqualTo(adminUserId);
        assertThat(action).isEqualTo("USER_DEACTIVATED");
        assertThat(metadataStr).contains("previousIsActive").contains("newIsActive");
    }

    @Test
    @DisplayName("P8.3-USR-02: Admin successfully reactivates an inactive user and creates exactly one audit record")
    void adminReactivatesInactiveUser() throws Exception {
        Long targetUserId = createUser("inactive.user@example.com", Role.RECRUITER, false, "Pass@1234");

        mockMvc.perform(patch("/api/admin/users/{userId}/status", targetUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(targetUserId.intValue())))
                .andExpect(jsonPath("$.data.active", is(true)));

        Boolean isActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM users WHERE id = ?", Boolean.class, targetUserId);
        assertThat(isActive).isTrue();

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'USER' AND entity_id = ?",
                Integer.class, targetUserId);
        assertThat(auditCount).isEqualTo(1);

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT actor_user_id, action, metadata FROM audit_logs WHERE entity_type = 'USER' AND entity_id = ?",
                targetUserId);
        assertThat(audit.get("actor_user_id")).isEqualTo(adminUserId);
        assertThat(audit.get("action")).isEqualTo("USER_REACTIVATED");
    }

    @Test
    @DisplayName("P8.3-USR-03: Self-deactivation by admin is rejected with 422, leaves DB untouched, creates no audit record")
    void selfDeactivationRejected() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{userId}/status", adminUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATUS_TRANSITION")));

        Boolean isActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM users WHERE id = ?", Boolean.class, adminUserId);
        assertThat(isActive).isTrue();

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs", Integer.class);
        assertThat(auditCount).isEqualTo(0);
    }

    @Test
    @DisplayName("P8.3-USR-04: Same-state deactivation (already inactive) is rejected with 422 and creates no audit record")
    void sameStateDeactivationRejected() throws Exception {
        Long targetUserId = createUser("already.inactive@example.com", Role.CANDIDATE, false, "Pass@1234");

        mockMvc.perform(patch("/api/admin/users/{userId}/status", targetUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATUS_TRANSITION")));

        Integer auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Integer.class);
        assertThat(auditCount).isEqualTo(0);
    }

    @Test
    @DisplayName("P8.3-USR-05: Same-state reactivation (already active) is rejected with 422 and creates no audit record")
    void sameStateReactivationRejected() throws Exception {
        Long targetUserId = createUser("already.active@example.com", Role.CANDIDATE, true, "Pass@1234");

        mockMvc.perform(patch("/api/admin/users/{userId}/status", targetUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": true}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("INVALID_STATUS_TRANSITION")));

        Integer auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Integer.class);
        assertThat(auditCount).isEqualTo(0);
    }

    @Test
    @DisplayName("P8.3-USR-06: Deactivated user is blocked from authenticating via login endpoint")
    void deactivatedUserCannotAuthenticate() throws Exception {
        String rawPassword = "CandidatePassword@123";
        Long targetUserId = createUser("cand.login.blocked@example.com", Role.CANDIDATE, true, rawPassword);

        // First verify login succeeds while active
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "cand.login.blocked@example.com",
                                "password", rawPassword
                        ))))
                .andExpect(status().isOk());

        // Admin deactivates the user
        mockMvc.perform(patch("/api/admin/users/{userId}/status", targetUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isOk());

        // Attempting login now must fail with 403 Forbidden
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "cand.login.blocked@example.com",
                                "password", rawPassword
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @DisplayName("P8.3-USR-07: Moderation on non-existent user returns 404 and creates no audit record")
    void nonExistentUserReturns404() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{userId}/status", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("RESOURCE_NOT_FOUND")));

        Integer auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Integer.class);
        assertThat(auditCount).isEqualTo(0);
    }

    @Test
    @DisplayName("P8.3-USR-08: Guest (401), Candidate (403), and Recruiter (403) are denied access")
    void authorizationChecks() throws Exception {
        Long targetUserId = createUser("user.auth.test@example.com", Role.CANDIDATE, true, "Pass@1234");

        // Guest -> 401
        mockMvc.perform(patch("/api/admin/users/{userId}/status", targetUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isUnauthorized());

        // Candidate -> 403
        mockMvc.perform(patch("/api/admin/users/{userId}/status", targetUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isForbidden());

        // Recruiter -> 403
        mockMvc.perform(patch("/api/admin/users/{userId}/status", targetUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P8.3-USR-09: Client cannot spoof actor ID via body or query parameters")
    void clientCannotSpoofActorId() throws Exception {
        Long targetUserId = createUser("spoof.test@example.com", Role.CANDIDATE, true, "Pass@1234");

        // Attempting to send actorUserId = 999 in payload
        mockMvc.perform(patch("/api/admin/users/{userId}/status?actorUserId=999", targetUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false, \"actorUserId\": 999, \"adminId\": 999}"))
                .andExpect(status().isOk());

        // Audit must record authenticated adminUserId, NOT 999
        Long recordedActor = jdbcTemplate.queryForObject(
                "SELECT actor_user_id FROM audit_logs WHERE entity_type = 'USER' AND entity_id = ?",
                Long.class, targetUserId);
        assertThat(recordedActor).isEqualTo(adminUserId);
    }
}
