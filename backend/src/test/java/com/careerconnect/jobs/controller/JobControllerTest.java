package com.careerconnect.jobs.controller;

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
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long activeCompanyId;
    private Long inactiveCompanyId;
    private Long activeJobId1;
    private Long activeJobId2;
    private Long activeJobId3;
    private Long draftJobId;
    private Long closedJobId;
    private Long archivedJobId;
    private Long expiredJobId;
    private Long softDeletedJobId;
    private Long inactiveCompanyJobId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM candidate_skills");
        jdbcTemplate.execute("DELETE FROM job_skills");
        jdbcTemplate.execute("DELETE FROM jobs");
        jdbcTemplate.execute("DELETE FROM skills");
        jdbcTemplate.execute("DELETE FROM companies");

        // 1. Seed Skills
        jdbcTemplate.update("INSERT INTO skills (name, normalized_name) VALUES (?, ?)", "Java", "java");
        Long javaSkillId = jdbcTemplate.queryForObject("SELECT id FROM skills WHERE normalized_name = 'java'", Long.class);

        jdbcTemplate.update("INSERT INTO skills (name, normalized_name) VALUES (?, ?)", "React", "react");
        Long reactSkillId = jdbcTemplate.queryForObject("SELECT id FROM skills WHERE normalized_name = 'react'", Long.class);

        jdbcTemplate.update("INSERT INTO skills (name, normalized_name) VALUES (?, ?)", "Spring Boot", "spring boot");
        Long springSkillId = jdbcTemplate.queryForObject("SELECT id FROM skills WHERE normalized_name = 'spring boot'", Long.class);

        // 2. Seed Companies
        jdbcTemplate.update("""
                INSERT INTO companies (name, industry, location, website, description, company_size, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "Apex Innovations", "Technology", "Bengaluru", "https://apex.example.com", "High tech innovations", "101-500", true);
        activeCompanyId = jdbcTemplate.queryForObject("SELECT id FROM companies WHERE name = 'Apex Innovations'", Long.class);

        jdbcTemplate.update("""
                INSERT INTO companies (name, industry, location, website, description, company_size, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "Deactivated Org", "Services", "Hyderabad", "https://deact.example.com", "Closed", "1-10", false);
        inactiveCompanyId = jdbcTemplate.queryForObject("SELECT id FROM companies WHERE name = 'Deactivated Org'", Long.class);

        LocalDate future1 = LocalDate.now().plusMonths(1);
        LocalDate future2 = LocalDate.now().plusMonths(2);
        LocalDate future3 = LocalDate.now().plusMonths(3);
        LocalDate pastDate = LocalDate.now().minusDays(2);

        // 3. Seed Valid Active Jobs
        // Job 1: Java + Spring Boot, Backend, Bengaluru, ONSITE, 6-9 LPA, INTERNSHIP
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, responsibilities, requirements, job_type, experience_level, work_mode, location, salary_min_lpa, salary_max_lpa, application_deadline, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TIMESTAMP '2026-10-14 10:00:00')
                """, activeCompanyId, "Java Backend Intern", "Work on high throughput Java services", "Build REST APIs", "Solid OOP foundations",
                "INTERNSHIP", "INTERNSHIP", "ONSITE", "Bengaluru", 6.00, 9.00, future3, "ACTIVE");
        activeJobId1 = jdbcTemplate.queryForObject("SELECT id FROM jobs WHERE title = 'Java Backend Intern'", Long.class);
        jdbcTemplate.update("INSERT INTO job_skills (job_id, skill_id) VALUES (?, ?)", activeJobId1, javaSkillId);
        jdbcTemplate.update("INSERT INTO job_skills (job_id, skill_id) VALUES (?, ?)", activeJobId1, springSkillId);

        // Job 2: React, Frontend, Pune, REMOTE, 10-15 LPA, FRESHER
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, responsibilities, requirements, job_type, experience_level, work_mode, location, salary_min_lpa, salary_max_lpa, application_deadline, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TIMESTAMP '2026-10-14 11:00:00')
                """, activeCompanyId, "Frontend Engineer", "Build beautiful web UIs", "Implement design system", "Proficient in React & CSS",
                "FULL_TIME", "FRESHER", "REMOTE", "Pune", 10.00, 15.00, future1, "ACTIVE");
        activeJobId2 = jdbcTemplate.queryForObject("SELECT id FROM jobs WHERE title = 'Frontend Engineer'", Long.class);
        jdbcTemplate.update("INSERT INTO job_skills (job_id, skill_id) VALUES (?, ?)", activeJobId2, reactSkillId);

        // Job 3: Java, Full Stack, Bengaluru, HYBRID, 12-18 LPA, ZERO_TO_ONE_YEARS
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, responsibilities, requirements, job_type, experience_level, work_mode, location, salary_min_lpa, salary_max_lpa, application_deadline, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TIMESTAMP '2026-10-14 12:00:00')
                """, activeCompanyId, "Full Stack Associate", "Full stack web development", "Maintain end-to-end features", "Java & React experience",
                "FULL_TIME", "ZERO_TO_ONE_YEARS", "HYBRID", "Bengaluru", 12.00, 18.00, future2, "ACTIVE");
        activeJobId3 = jdbcTemplate.queryForObject("SELECT id FROM jobs WHERE title = 'Full Stack Associate'", Long.class);
        jdbcTemplate.update("INSERT INTO job_skills (job_id, skill_id) VALUES (?, ?)", activeJobId3, javaSkillId);
        jdbcTemplate.update("INSERT INTO job_skills (job_id, skill_id) VALUES (?, ?)", activeJobId3, reactSkillId);

        // 4. Seed Ineligible / Non-public Jobs (Must NEVER be returned by public endpoints)
        // A. Draft
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, job_type, experience_level, work_mode, location, application_deadline, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, activeCompanyId, "Draft Unreleased Job", "Draft notes", "FULL_TIME", "FRESHER", "REMOTE", "Delhi", future1, "DRAFT");
        draftJobId = jdbcTemplate.queryForObject("SELECT id FROM jobs WHERE title = 'Draft Unreleased Job'", Long.class);

        // B. Closed
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, job_type, experience_level, work_mode, location, application_deadline, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, activeCompanyId, "Closed Position", "Position is closed", "FULL_TIME", "FRESHER", "REMOTE", "Delhi", future1, "CLOSED");
        closedJobId = jdbcTemplate.queryForObject("SELECT id FROM jobs WHERE title = 'Closed Position'", Long.class);

        // C. Archived
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, job_type, experience_level, work_mode, location, application_deadline, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, activeCompanyId, "Archived Moderated Job", "Removed by admin", "FULL_TIME", "FRESHER", "REMOTE", "Delhi", future1, "ARCHIVED");
        archivedJobId = jdbcTemplate.queryForObject("SELECT id FROM jobs WHERE title = 'Archived Moderated Job'", Long.class);

        // D. Expired Deadline
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, job_type, experience_level, work_mode, location, application_deadline, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, activeCompanyId, "Expired Deadline Job", "Deadline passed yesterday", "FULL_TIME", "FRESHER", "REMOTE", "Delhi", pastDate, "ACTIVE");
        expiredJobId = jdbcTemplate.queryForObject("SELECT id FROM jobs WHERE title = 'Expired Deadline Job'", Long.class);

        // E. Soft-Deleted
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, job_type, experience_level, work_mode, location, application_deadline, status, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, activeCompanyId, "Deleted Job", "Soft deleted job", "FULL_TIME", "FRESHER", "REMOTE", "Delhi", future1, "ACTIVE");
        softDeletedJobId = jdbcTemplate.queryForObject("SELECT id FROM jobs WHERE title = 'Deleted Job'", Long.class);

        // F. Inactive Company Job
        jdbcTemplate.update("""
                INSERT INTO jobs (company_id, title, description, job_type, experience_level, work_mode, location, application_deadline, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, inactiveCompanyId, "Orphaned Company Job", "Company is deactivated", "FULL_TIME", "FRESHER", "REMOTE", "Delhi", future1, "ACTIVE");
        inactiveCompanyJobId = jdbcTemplate.queryForObject("SELECT id FROM jobs WHERE title = 'Orphaned Company Job'", Long.class);
    }

    @Test
    @DisplayName("GET /api/jobs should return all active public jobs with pagination and exclude non-public ones")
    void shouldReturnActivePublicJobsOnly() throws Exception {
        mockMvc.perform(get("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[*].title", containsInAnyOrder("Java Backend Intern", "Frontend Engineer", "Full Stack Associate")))
                .andExpect(jsonPath("$.data[*].title", not(hasItem("Draft Unreleased Job"))))
                .andExpect(jsonPath("$.data[*].title", not(hasItem("Closed Position"))))
                .andExpect(jsonPath("$.data[*].title", not(hasItem("Archived Moderated Job"))))
                .andExpect(jsonPath("$.data[*].title", not(hasItem("Expired Deadline Job"))))
                .andExpect(jsonPath("$.data[*].title", not(hasItem("Deleted Job"))))
                .andExpect(jsonPath("$.data[*].title", not(hasItem("Orphaned Company Job"))))
                .andExpect(jsonPath("$.pagination.totalElements").value(3))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(20));
    }

    @Test
    @DisplayName("GET /api/jobs?keyword=Frontend should search by title and description")
    void shouldFilterJobsByKeyword() throws Exception {
        mockMvc.perform(get("/api/jobs?keyword=Frontend")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("Frontend Engineer"));
    }

    @Test
    @DisplayName("GET /api/jobs?location=Pune should filter by location")
    void shouldFilterJobsByLocation() throws Exception {
        mockMvc.perform(get("/api/jobs?location=Pune")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].location").value("Pune"));
    }

    @Test
    @DisplayName("GET /api/jobs?employmentType=INTERNSHIP should filter by employment type")
    void shouldFilterJobsByEmploymentType() throws Exception {
        mockMvc.perform(get("/api/jobs?employmentType=INTERNSHIP")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("Java Backend Intern"))
                .andExpect(jsonPath("$.data[0].jobType").value("INTERNSHIP"));
    }

    @Test
    @DisplayName("GET /api/jobs?experienceLevel=ZERO_TO_ONE_YEARS should filter by experience level")
    void shouldFilterJobsByExperienceLevel() throws Exception {
        mockMvc.perform(get("/api/jobs?experienceLevel=ZERO_TO_ONE_YEARS")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("Full Stack Associate"))
                .andExpect(jsonPath("$.data[0].experienceLevel").value("ZERO_TO_ONE_YEARS"));
    }

    @Test
    @DisplayName("GET /api/jobs?workMode=REMOTE should filter by work mode")
    void shouldFilterJobsByWorkMode() throws Exception {
        mockMvc.perform(get("/api/jobs?workMode=REMOTE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("Frontend Engineer"))
                .andExpect(jsonPath("$.data[0].workMode").value("REMOTE"));
    }

    @Test
    @DisplayName("GET /api/jobs?skills=React should filter by required skill name")
    void shouldFilterJobsBySkill() throws Exception {
        mockMvc.perform(get("/api/jobs?skills=React")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].title", containsInAnyOrder("Frontend Engineer", "Full Stack Associate")));
    }

    @Test
    @DisplayName("GET /api/jobs?skills=Java,Spring Boot should filter jobs having both required skills")
    void shouldFilterJobsByMultipleSkills() throws Exception {
        mockMvc.perform(get("/api/jobs?skills=Java,Spring Boot")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("Java Backend Intern"));
    }

    @Test
    @DisplayName("GET /api/jobs?minSalary=12 should filter by minimum salary threshold")
    void shouldFilterJobsByMinSalary() throws Exception {
        mockMvc.perform(get("/api/jobs?minSalary=12")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].title", containsInAnyOrder("Frontend Engineer", "Full Stack Associate")));
    }

    @Test
    @DisplayName("GET /api/jobs?maxSalary=9 should filter by maximum salary threshold")
    void shouldFilterJobsByMaxSalary() throws Exception {
        mockMvc.perform(get("/api/jobs?maxSalary=9")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("Java Backend Intern"));
    }

    @Test
    @DisplayName("GET /api/jobs?sort=latest should sort jobs newest first")
    void shouldSortJobsByLatest() throws Exception {
        mockMvc.perform(get("/api/jobs?sort=latest")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Full Stack Associate"));
    }

    @Test
    @DisplayName("GET /api/jobs?sort=oldest should sort jobs oldest first")
    void shouldSortJobsByOldest() throws Exception {
        mockMvc.perform(get("/api/jobs?sort=oldest")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Java Backend Intern"));
    }

    @Test
    @DisplayName("GET /api/jobs?sort=deadline,asc should sort jobs by application deadline ascending")
    void shouldSortJobsByDeadlineAsc() throws Exception {
        mockMvc.perform(get("/api/jobs?sort=deadline,asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Frontend Engineer")); // nearest deadline (future1)
    }

    @Test
    @DisplayName("GET /api/jobs?sort=salary,desc should sort jobs by salary descending")
    void shouldSortJobsBySalaryDesc() throws Exception {
        mockMvc.perform(get("/api/jobs?sort=salary,desc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Full Stack Associate")); // 18 LPA max
    }

    @Test
    @DisplayName("GET /api/jobs/{id} should return complete job detail for active public job")
    void shouldReturnJobDetailForValidActiveJob() throws Exception {
        mockMvc.perform(get("/api/jobs/" + activeJobId1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(activeJobId1))
                .andExpect(jsonPath("$.data.title").value("Java Backend Intern"))
                .andExpect(jsonPath("$.data.responsibilities").value("Build REST APIs"))
                .andExpect(jsonPath("$.data.requirements").value("Solid OOP foundations"))
                .andExpect(jsonPath("$.data.company.name").value("Apex Innovations"))
                .andExpect(jsonPath("$.data.skills", hasSize(2)))
                .andExpect(jsonPath("$.data.skills[*].name", containsInAnyOrder("Java", "Spring Boot")));
    }

    @Test
    @DisplayName("GET /api/jobs/{id} on non-public jobs should return 404 with data:null and JOB_NOT_FOUND")
    void shouldReturn404ForNonPublicJobDirectAccess() throws Exception {
        // DRAFT
        mockMvc.perform(get("/api/jobs/" + draftJobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        // CLOSED
        mockMvc.perform(get("/api/jobs/" + closedJobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        // ARCHIVED
        mockMvc.perform(get("/api/jobs/" + archivedJobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        // EXPIRED
        mockMvc.perform(get("/api/jobs/" + expiredJobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        // SOFT DELETED
        mockMvc.perform(get("/api/jobs/" + softDeletedJobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));

        // INACTIVE COMPANY
        mockMvc.perform(get("/api/jobs/" + inactiveCompanyJobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/jobs/999999 on nonexistent job should return 404 with data:null and JOB_NOT_FOUND")
    void shouldReturn404ForNonexistentJob() throws Exception {
        mockMvc.perform(get("/api/jobs/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }
}
