package com.careerconnect.company.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long activeCompanyId1;
    private Long activeCompanyId2;
    private Long inactiveCompanyId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM job_skills");
        jdbcTemplate.execute("DELETE FROM jobs");
        jdbcTemplate.execute("DELETE FROM companies");

        // Seed Active Company 1
        jdbcTemplate.update("""
                INSERT INTO companies (name, industry, location, website, description, company_size, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "TechCorp Solutions", "Technology", "Bengaluru", "https://techcorp.example.com", "Leading enterprise software provider", "501-1000", true);
        activeCompanyId1 = jdbcTemplate.queryForObject("SELECT id FROM companies WHERE name = 'TechCorp Solutions'", Long.class);

        // Seed Active Company 2
        jdbcTemplate.update("""
                INSERT INTO companies (name, industry, location, website, description, company_size, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "InnoFin Analytics", "Finance", "Mumbai", "https://innofin.example.com", "Fintech data platform", "51-200", true);
        activeCompanyId2 = jdbcTemplate.queryForObject("SELECT id FROM companies WHERE name = 'InnoFin Analytics'", Long.class);

        // Seed Inactive Company
        jdbcTemplate.update("""
                INSERT INTO companies (name, industry, location, website, description, company_size, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "ClosedCorp LLC", "Retail", "Delhi", "https://closedcorp.example.com", "Defunct company", "1-10", false);
        inactiveCompanyId = jdbcTemplate.queryForObject("SELECT id FROM companies WHERE name = 'ClosedCorp LLC'", Long.class);

        // Seed an Active Job for TechCorp
        LocalDate futureDate = LocalDate.now().plusMonths(2);
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, job_type, experience_level, work_mode, location, salary_min_lpa, salary_max_lpa, application_deadline, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, activeCompanyId1, "Junior Java Developer", "Build cloud backend services", "FULL_TIME", "FRESHER", "HYBRID", "Bengaluru", 6.00, 9.00, futureDate, "ACTIVE");
    }

    @Test
    @DisplayName("GET /api/companies should return paginated list of active companies only")
    void shouldReturnPaginatedActiveCompanies() throws Exception {
        mockMvc.perform(get("/api/companies")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].name", containsInAnyOrder("TechCorp Solutions", "InnoFin Analytics")))
                .andExpect(jsonPath("$.data[*].name", not(hasItem("ClosedCorp LLC"))))
                .andExpect(jsonPath("$.pagination.totalElements").value(2))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(20));
    }

    @Test
    @DisplayName("GET /api/companies should correctly calculate active job count")
    void shouldIncludeAccurateActiveJobCount() throws Exception {
        mockMvc.perform(get("/api/companies?q=TechCorp")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("TechCorp Solutions"))
                .andExpect(jsonPath("$.data[0].activeJobCount").value(1));
    }

    @Test
    @DisplayName("GET /api/companies?q=Mumbai should filter companies by location/name keyword")
    void shouldFilterCompaniesByKeyword() throws Exception {
        mockMvc.perform(get("/api/companies?q=Mumbai")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("InnoFin Analytics"));
    }

    @Test
    @DisplayName("GET /api/companies/{id} should return full company details with active jobs")
    void shouldReturnCompanyDetailWithActiveJobs() throws Exception {
        mockMvc.perform(get("/api/companies/" + activeCompanyId1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(activeCompanyId1))
                .andExpect(jsonPath("$.data.name").value("TechCorp Solutions"))
                .andExpect(jsonPath("$.data.activeJobCount").value(1))
                .andExpect(jsonPath("$.data.activeJobs", hasSize(1)))
                .andExpect(jsonPath("$.data.activeJobs[0].title").value("Junior Java Developer"));
    }

    @Test
    @DisplayName("GET /api/companies/{id} on inactive company should return 404 RESOURCE_NOT_FOUND")
    void shouldReturn404ForInactiveCompany() throws Exception {
        mockMvc.perform(get("/api/companies/" + inactiveCompanyId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/companies/999999 on nonexistent company should return 404 RESOURCE_NOT_FOUND")
    void shouldReturn404ForNonexistentCompany() throws Exception {
        mockMvc.perform(get("/api/companies/999999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
