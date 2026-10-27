package com.careerconnect.admin;

import com.careerconnect.common.model.Role;
import com.careerconnect.common.util.JdbcUtils;
import com.careerconnect.security.JwtService;
import com.careerconnect.security.SecurityUser;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

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
        jdbcTemplate.execute("DELETE FROM recruiters");
        jdbcTemplate.execute("DELETE FROM candidates");
        jdbcTemplate.execute("DELETE FROM companies");
        jdbcTemplate.execute("DELETE FROM users WHERE email != 'admin@careerconnect.com'");
    }

    @Test
    @DisplayName("Non-admin roles cannot access /api/admin/users")
    void nonAdmin_cannotAccessUsersList() throws Exception {
        // Guest -> 401
        mockMvc.perform(get("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        // Candidate -> 403
        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        // Recruiter -> 403
        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Admin can list users with pagination and deterministic ordering")
    void adminCanListUsers_withPagination() throws Exception {
        long u1 = insertUser("user1@example.com", Role.CANDIDATE, true);
        insertCandidate(u1, "User One");

        long u2 = insertUser("user2@example.com", Role.CANDIDATE, true);
        insertCandidate(u2, "User Two");

        long u3 = insertUser("user3@example.com", Role.RECRUITER, false);
        long compId = insertCompany("Tech Co", true);
        insertRecruiter(u3, compId, "User Three");

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(2))
                .andExpect(jsonPath("$.pagination.totalElements").value(4)) // admin + 3 users
                .andExpect(jsonPath("$.pagination.totalPages").value(2));
    }

    @Test
    @DisplayName("Admin can filter users by role, active status, and search query")
    void adminCanFilterUsers() throws Exception {
        long u1 = insertUser("dev.alex@company.com", Role.CANDIDATE, true);
        insertCandidate(u1, "Alex Morgan");

        long u2 = insertUser("rec.sarah@hr.com", Role.RECRUITER, true);
        long compId = insertCompany("HR Global", true);
        insertRecruiter(u2, compId, "Sarah Connor");

        long u3 = insertUser("cand.inactive@domain.com", Role.CANDIDATE, false);
        insertCandidate(u3, "Inactive Candidate");

        // Filter by role = RECRUITER
        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("role", "RECRUITER")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].email").value("rec.sarah@hr.com"))
                .andExpect(jsonPath("$.data[0].role").value("RECRUITER"))
                .andExpect(jsonPath("$.data[0].fullName").value("Sarah Connor"));

        // Filter by active = false
        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("active", "false")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].email").value("cand.inactive@domain.com"))
                .andExpect(jsonPath("$.data[0].active").value(false));

        // Filter by search query q = "Alex"
        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("q", "alex")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].email").value("dev.alex@company.com"))
                .andExpect(jsonPath("$.data[0].fullName").value("Alex Morgan"));
    }

    @Test
    @DisplayName("Password hash, JWT tokens, and credentials are never exposed in user list")
    void passwordAndSecurityCredentials_areNeverExposed() throws Exception {
        long u1 = insertUser("secure.user@example.com", Role.CANDIDATE, true);
        insertCandidate(u1, "Secure Candidate");

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("q", "secure.user")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].password").doesNotExist())
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data[0].password_hash").doesNotExist())
                .andExpect(jsonPath("$.data[0].jwt").doesNotExist())
                .andExpect(jsonPath("$.data[0].token").doesNotExist())
                .andExpect(jsonPath("$.data[0].secret").doesNotExist());
    }

    private long insertUser(String email, Role role, boolean active) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO users (email, password_hash, role, is_active, created_at, updated_at) " +
                            "VALUES (?, '$2a$12$superSecretHashThatMustNeverLeak12345678901234', ?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, email);
            ps.setString(2, role.name());
            ps.setBoolean(3, active);
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }

    private long insertCandidate(long userId, String fullName) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO candidates (user_id, full_name, created_at, updated_at) " +
                            "VALUES (?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, userId);
            ps.setString(2, fullName);
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }

    private long insertCompany(String name, boolean active) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO companies (name, is_active, created_at, updated_at) " +
                            "VALUES (?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, name);
            ps.setBoolean(2, active);
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }

    private long insertRecruiter(long userId, long companyId, String fullName) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO recruiters (user_id, company_id, full_name, created_at, updated_at) " +
                            "VALUES (?, ?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, userId);
            ps.setLong(2, companyId);
            ps.setString(3, fullName);
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }
}
