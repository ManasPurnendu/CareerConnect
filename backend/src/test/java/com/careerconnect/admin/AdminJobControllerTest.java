package com.careerconnect.admin;

import com.careerconnect.common.model.Role;
import com.careerconnect.common.util.JdbcUtils;
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

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminJobControllerTest {

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
        jdbcTemplate.execute("DELETE FROM job_skills");
        jdbcTemplate.execute("DELETE FROM jobs");
        jdbcTemplate.execute("DELETE FROM recruiters");
        jdbcTemplate.execute("DELETE FROM companies");
    }

    @Test
    @DisplayName("Non-admin roles cannot access /api/admin/jobs")
    void nonAdmin_cannotAccessJobsList() throws Exception {
        mockMvc.perform(get("/api/admin/jobs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/api/admin/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mockMvc.perform(get("/api/admin/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Admin can list jobs platform-wide across multiple companies")
    void adminCanListJobs_platformWide() throws Exception {
        long comp1 = insertCompany("Google");
        long comp2 = insertCompany("Microsoft");

        insertJob(comp1, "Staff Software Engineer", JobStatus.ACTIVE, null);
        insertJob(comp2, "Principal Architect", JobStatus.ACTIVE, null);
        insertJob(comp1, "Archived Job Role", JobStatus.ARCHIVED, null);
        insertJob(comp2, "Deleted Job Role", JobStatus.ACTIVE, LocalDate.now()); // soft-deleted

        mockMvc.perform(get("/api/admin/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(3))) // 3 non-deleted jobs across both companies
                .andExpect(jsonPath("$.data[*].title", hasItem("Staff Software Engineer")))
                .andExpect(jsonPath("$.data[*].title", hasItem("Principal Architect")))
                .andExpect(jsonPath("$.data[*].title", hasItem("Archived Job Role")))
                .andExpect(jsonPath("$.pagination.totalElements").value(3));
    }

    @Test
    @DisplayName("Admin can filter jobs by status and companyId")
    void adminCanFilterJobs_byStatusAndCompany() throws Exception {
        long comp1 = insertCompany("Apple");
        long comp2 = insertCompany("Amazon");

        insertJob(comp1, "iOS Engineer", JobStatus.ACTIVE, null);
        insertJob(comp1, "Swift Intern", JobStatus.DRAFT, null);
        insertJob(comp1, "Old Project", JobStatus.ARCHIVED, null);
        insertJob(comp2, "Cloud Dev", JobStatus.ACTIVE, null);

        // Filter status = ARCHIVED
        mockMvc.perform(get("/api/admin/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("status", "ARCHIVED")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("Old Project"))
                .andExpect(jsonPath("$.data[0].status").value("ARCHIVED"));

        // Filter companyId = comp2
        mockMvc.perform(get("/api/admin/jobs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("companyId", String.valueOf(comp2))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("Cloud Dev"));
    }

    private long insertCompany(String name) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO companies (name, is_active, created_at, updated_at) " +
                            "VALUES (?, true, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, name);
            return ps;
        }, kh);
        return JdbcUtils.extractGeneratedId(kh);
    }

    private long insertJob(long companyId, String title, JobStatus status, LocalDate deletedAt) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO jobs (company_id, title, description, job_type, experience_level, " +
                            "work_mode, location, salary_min_lpa, salary_max_lpa, application_deadline, status, " +
                            "deleted_at, created_at, updated_at) " +
                            "VALUES (?, ?, 'Desc', 'FULL_TIME', 'FRESHER', 'REMOTE', 'Remote', " +
                            "20.00, 30.00, ?, ?, ?, NOW(), NOW())",
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
        return JdbcUtils.extractGeneratedId(kh);
    }
}
