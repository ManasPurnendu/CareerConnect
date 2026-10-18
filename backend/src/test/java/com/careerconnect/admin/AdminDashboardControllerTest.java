package com.careerconnect.admin;

import com.careerconnect.application.domain.ApplicationStatus;
import com.careerconnect.common.model.Role;
import com.careerconnect.jobs.model.JobStatus;
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

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDashboardControllerTest {

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

        // Ensure admin user exists
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
            adminUserId = com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
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
        jdbcTemplate.execute("DELETE FROM application_status_history");
        jdbcTemplate.execute("DELETE FROM applications");
        jdbcTemplate.execute("DELETE FROM resumes");
        jdbcTemplate.execute("DELETE FROM candidate_skills");
        jdbcTemplate.execute("DELETE FROM education");
        jdbcTemplate.execute("DELETE FROM job_skills");
        jdbcTemplate.execute("DELETE FROM jobs");
        jdbcTemplate.execute("DELETE FROM recruiters");
        jdbcTemplate.execute("DELETE FROM candidates");
        jdbcTemplate.execute("DELETE FROM companies");
        jdbcTemplate.execute("DELETE FROM users WHERE email != 'admin@careerconnect.com'");
    }

    @Test
    @DisplayName("Guest receives 401 when accessing dashboard")
    void guestAccess_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Candidate receives 403 when accessing dashboard")
    void candidateAccess_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Recruiter receives 403 when accessing dashboard")
    void recruiterAccess_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Admin can access dashboard and receives correct aggregate metrics")
    void adminAccess_returnsAccurateDashboardSummary() throws Exception {
        // Seed 1 active candidate user + profile
        long candUserId = insertUser("c1@test.com", Role.CANDIDATE, true);
        long candidateId = insertCandidate(candUserId, "Alice Walker");

        // Seed 1 inactive candidate user + profile
        long candUserId2 = insertUser("c2@test.com", Role.CANDIDATE, false);
        insertCandidate(candUserId2, "Inactive Bob");

        // Seed 1 recruiter user + company + profile
        long recUserId = insertUser("r1@test.com", Role.RECRUITER, true);
        long companyId = insertCompany("Acme Corp", true);
        long recruiterId = insertRecruiter(recUserId, companyId, "Recruiter One");

        // Seed 1 inactive company
        insertCompany("Inactive Co", false);

        // Seed jobs
        long activeJobId = insertJob(companyId, "Java Dev", JobStatus.ACTIVE, null);
        long draftJobId = insertJob(companyId, "Draft Job", JobStatus.DRAFT, null);
        long closedJobId = insertJob(companyId, "Closed Job", JobStatus.CLOSED, null);
        long archivedJobId = insertJob(companyId, "Archived Job", JobStatus.ARCHIVED, null);
        long deletedJobId = insertJob(companyId, "Deleted Job", JobStatus.ACTIVE, LocalDate.now()); // Soft-deleted

        // Seed applications
        insertApplication(activeJobId, candidateId, ApplicationStatus.APPLIED);
        insertApplication(closedJobId, candidateId, ApplicationStatus.SHORTLISTED);

        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // Users breakdown: 1 admin + 2 candidates + 1 recruiter = 4 users
                .andExpect(jsonPath("$.data.users.total").value(4))
                .andExpect(jsonPath("$.data.users.candidates").value(2))
                .andExpect(jsonPath("$.data.users.recruiters").value(1))
                .andExpect(jsonPath("$.data.users.active").value(3)) // admin + c1 + r1
                .andExpect(jsonPath("$.data.users.inactive").value(1)) // c2
                // Companies breakdown
                .andExpect(jsonPath("$.data.companies.total").value(2))
                .andExpect(jsonPath("$.data.companies.active").value(1))
                .andExpect(jsonPath("$.data.companies.inactive").value(1))
                // Jobs breakdown (excluding soft-deleted)
                .andExpect(jsonPath("$.data.jobs.total").value(4))
                .andExpect(jsonPath("$.data.jobs.active").value(1))
                .andExpect(jsonPath("$.data.jobs.draft").value(1))
                .andExpect(jsonPath("$.data.jobs.closed").value(1))
                .andExpect(jsonPath("$.data.jobs.archived").value(1))
                // Applications breakdown
                .andExpect(jsonPath("$.data.applications.total").value(2))
                .andExpect(jsonPath("$.data.applications.applied").value(1))
                .andExpect(jsonPath("$.data.applications.shortlisted").value(1))
                .andExpect(jsonPath("$.data.applications.rejected").value(0))
                .andExpect(jsonPath("$.data.applications.selected").value(0));
    }

    private long insertUser(String email, Role role, boolean active) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO users (email, password_hash, role, is_active, created_at, updated_at) " +
                            "VALUES (?, 'HASH', ?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, email);
            ps.setString(2, role.name());
            ps.setBoolean(3, active);
            return ps;
        }, kh);
        return com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
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
        return com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
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
        return com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
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
        return com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
    }

    private long insertJob(long companyId, String title, JobStatus status, LocalDate deletedAt) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO jobs (company_id, title, description, job_type, experience_level, " +
                            "work_mode, location, salary_min_lpa, salary_max_lpa, application_deadline, status, " +
                            "deleted_at, created_at, updated_at) " +
                            "VALUES (?, ?, 'Job desc', 'FULL_TIME', 'FRESHER', 'ONSITE', 'Bangalore', " +
                            "10.00, 15.00, ?, ?, ?, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, companyId);
            ps.setString(2, title);
            ps.setDate(3, Date.valueOf(LocalDate.now().plusMonths(1)));
            ps.setString(4, status.name());
            if (deletedAt != null) {
                ps.setDate(5, Date.valueOf(deletedAt));
            } else {
                ps.setNull(5, java.sql.Types.DATE);
            }
            return ps;
        }, kh);
        return com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
    }

    private long insertApplication(long jobId, long candidateId, ApplicationStatus status) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO applications (job_id, candidate_id, status, cover_letter, " +
                            "candidate_snapshot, resume_snapshot, applied_at, created_at, updated_at) " +
                            "VALUES (?, ?, ?, 'Cover letter', '{}', '{}', NOW(), NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, jobId);
            ps.setLong(2, candidateId);
            ps.setString(3, status.name());
            return ps;
        }, kh);
        return com.careerconnect.common.util.JdbcUtils.extractGeneratedId(kh);
    }
}
