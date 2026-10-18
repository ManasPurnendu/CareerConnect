package com.careerconnect.admin.repository;

import com.careerconnect.admin.dto.AdminCompanyDto;
import com.careerconnect.admin.dto.AdminDashboardDto;
import com.careerconnect.admin.dto.AdminJobDto;
import com.careerconnect.admin.dto.AdminUserDto;
import com.careerconnect.application.domain.ApplicationStatus;
import com.careerconnect.common.model.Role;
import com.careerconnect.common.util.DateTimeUtils;
import com.careerconnect.jobs.model.ExperienceLevel;
import com.careerconnect.jobs.model.JobStatus;
import com.careerconnect.jobs.model.JobType;
import com.careerconnect.jobs.model.WorkMode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure JdbcTemplate repository providing platform-wide administrative read-only queries.
 */
@Repository
public class AdminQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // -------------------------------------------------------------
    // Dashboard Summary
    // -------------------------------------------------------------

    public AdminDashboardDto getDashboardSummary() {
        AdminDashboardDto.UserCounts userCounts = jdbcTemplate.queryForObject("""
                SELECT
                    COUNT(*) AS total,
                    COUNT(CASE WHEN role = 'CANDIDATE' THEN 1 END) AS candidates,
                    COUNT(CASE WHEN role = 'RECRUITER' THEN 1 END) AS recruiters,
                    COUNT(CASE WHEN is_active = TRUE THEN 1 END) AS active_users,
                    COUNT(CASE WHEN is_active = FALSE THEN 1 END) AS inactive_users
                FROM users
                """, (rs, rowNum) -> new AdminDashboardDto.UserCounts(
                rs.getLong("total"),
                rs.getLong("candidates"),
                rs.getLong("recruiters"),
                rs.getLong("active_users"),
                rs.getLong("inactive_users")
        ));

        AdminDashboardDto.CompanyCounts companyCounts = jdbcTemplate.queryForObject("""
                SELECT
                    COUNT(*) AS total,
                    COUNT(CASE WHEN is_active = TRUE THEN 1 END) AS active_companies,
                    COUNT(CASE WHEN is_active = FALSE THEN 1 END) AS inactive_companies
                FROM companies
                """, (rs, rowNum) -> new AdminDashboardDto.CompanyCounts(
                rs.getLong("total"),
                rs.getLong("active_companies"),
                rs.getLong("inactive_companies")
        ));

        AdminDashboardDto.JobCounts jobCounts = jdbcTemplate.queryForObject("""
                SELECT
                    COUNT(*) AS total,
                    COUNT(CASE WHEN status = 'ACTIVE' THEN 1 END) AS active_jobs,
                    COUNT(CASE WHEN status = 'DRAFT' THEN 1 END) AS draft_jobs,
                    COUNT(CASE WHEN status = 'CLOSED' THEN 1 END) AS closed_jobs,
                    COUNT(CASE WHEN status = 'ARCHIVED' THEN 1 END) AS archived_jobs
                FROM jobs
                WHERE deleted_at IS NULL
                """, (rs, rowNum) -> new AdminDashboardDto.JobCounts(
                rs.getLong("total"),
                rs.getLong("active_jobs"),
                rs.getLong("draft_jobs"),
                rs.getLong("closed_jobs"),
                rs.getLong("archived_jobs")
        ));

        AdminDashboardDto.ApplicationCounts appCounts = jdbcTemplate.queryForObject("""
                SELECT
                    COUNT(*) AS total,
                    COUNT(CASE WHEN status = 'APPLIED' THEN 1 END) AS applied_apps,
                    COUNT(CASE WHEN status = 'UNDER_REVIEW' THEN 1 END) AS review_apps,
                    COUNT(CASE WHEN status = 'SHORTLISTED' THEN 1 END) AS shortlist_apps,
                    COUNT(CASE WHEN status = 'REJECTED' THEN 1 END) AS reject_apps,
                    COUNT(CASE WHEN status = 'SELECTED' THEN 1 END) AS select_apps,
                    COUNT(CASE WHEN status = 'WITHDRAWN' THEN 1 END) AS withdraw_apps
                FROM applications
                """, (rs, rowNum) -> new AdminDashboardDto.ApplicationCounts(
                rs.getLong("total"),
                rs.getLong("applied_apps"),
                rs.getLong("review_apps"),
                rs.getLong("shortlist_apps"),
                rs.getLong("reject_apps"),
                rs.getLong("select_apps"),
                rs.getLong("withdraw_apps")
        ));

        return new AdminDashboardDto(userCounts, companyCounts, jobCounts, appCounts);
    }

    // -------------------------------------------------------------
    // Users Listing
    // -------------------------------------------------------------

    public List<AdminUserDto> findUsers(Role role, Boolean active, String q, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT u.id, u.email, u.role, u.is_active, u.created_at, u.updated_at,
                       c.id AS candidate_id, c.full_name AS candidate_name,
                       r.id AS recruiter_id, r.full_name AS recruiter_name,
                       comp.id AS company_id, comp.name AS company_name
                FROM users u
                LEFT JOIN candidates c ON u.id = c.user_id
                LEFT JOIN recruiters r ON u.id = r.user_id
                LEFT JOIN companies comp ON r.company_id = comp.id
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();

        if (role != null) {
            sql.append(" AND u.role = ?");
            params.add(role.name());
        }
        if (active != null) {
            sql.append(" AND u.is_active = ?");
            params.add(active);
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.trim().toLowerCase() + "%";
            sql.append(" AND (LOWER(u.email) LIKE ? OR LOWER(c.full_name) LIKE ? OR LOWER(r.full_name) LIKE ?)");
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        sql.append(" ORDER BY u.created_at DESC, u.id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            String fullName = rs.getString("candidate_name");
            if (fullName == null) {
                fullName = rs.getString("recruiter_name");
            }

            return new AdminUserDto(
                    rs.getLong("id"),
                    rs.getString("email"),
                    Role.valueOf(rs.getString("role")),
                    fullName,
                    rs.getBoolean("is_active"),
                    rs.getObject("candidate_id") != null ? rs.getLong("candidate_id") : null,
                    rs.getObject("recruiter_id") != null ? rs.getLong("recruiter_id") : null,
                    rs.getObject("company_id") != null ? rs.getLong("company_id") : null,
                    rs.getString("company_name"),
                    DateTimeUtils.toInstant(rs.getTimestamp("created_at")),
                    DateTimeUtils.toInstant(rs.getTimestamp("updated_at"))
            );
        }, params.toArray());
    }

    public long countUsers(Role role, Boolean active, String q) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM users u
                LEFT JOIN candidates c ON u.id = c.user_id
                LEFT JOIN recruiters r ON u.id = r.user_id
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();

        if (role != null) {
            sql.append(" AND u.role = ?");
            params.add(role.name());
        }
        if (active != null) {
            sql.append(" AND u.is_active = ?");
            params.add(active);
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.trim().toLowerCase() + "%";
            sql.append(" AND (LOWER(u.email) LIKE ? OR LOWER(c.full_name) LIKE ? OR LOWER(r.full_name) LIKE ?)");
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    // -------------------------------------------------------------
    // Companies Listing
    // -------------------------------------------------------------

    public List<AdminCompanyDto> findCompanies(Boolean active, String q, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.name, c.industry, c.location, c.website, c.description, c.company_size,
                       c.is_active, c.created_at, c.updated_at,
                       (SELECT COUNT(*) FROM jobs j WHERE j.company_id = c.id AND j.deleted_at IS NULL) AS job_count,
                       (SELECT COUNT(*) FROM recruiters r WHERE r.company_id = c.id) AS recruiter_count
                FROM companies c
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();

        if (active != null) {
            sql.append(" AND c.is_active = ?");
            params.add(active);
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.trim().toLowerCase() + "%";
            sql.append(" AND (LOWER(c.name) LIKE ? OR LOWER(c.industry) LIKE ? OR LOWER(c.location) LIKE ?)");
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        sql.append(" ORDER BY c.created_at DESC, c.id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new AdminCompanyDto(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("industry"),
                rs.getString("location"),
                rs.getString("website"),
                rs.getString("description"),
                rs.getString("company_size"),
                rs.getBoolean("is_active"),
                rs.getLong("job_count"),
                rs.getLong("recruiter_count"),
                DateTimeUtils.toInstant(rs.getTimestamp("created_at")),
                DateTimeUtils.toInstant(rs.getTimestamp("updated_at"))
        ), params.toArray());
    }

    public long countCompanies(Boolean active, String q) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM companies c WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (active != null) {
            sql.append(" AND c.is_active = ?");
            params.add(active);
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.trim().toLowerCase() + "%";
            sql.append(" AND (LOWER(c.name) LIKE ? OR LOWER(c.industry) LIKE ? OR LOWER(c.location) LIKE ?)");
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    // -------------------------------------------------------------
    // Jobs Listing
    // -------------------------------------------------------------

    public record AdminJobEntity(
            Long id,
            Long companyId,
            String companyName,
            String title,
            String description,
            JobType jobType,
            ExperienceLevel experienceLevel,
            WorkMode workMode,
            String location,
            BigDecimal salaryMinLpa,
            BigDecimal salaryMaxLpa,
            LocalDate applicationDeadline,
            JobStatus status,
            long applicationCount,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public List<AdminJobEntity> findJobs(JobStatus status, Long companyId, String q, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT j.id, j.company_id, c.name AS company_name, j.title, j.description,
                       j.job_type, j.experience_level, j.work_mode, j.location, j.salary_min_lpa, j.salary_max_lpa,
                       j.application_deadline, j.status, j.created_at, j.updated_at,
                       (SELECT COUNT(*) FROM applications a WHERE a.job_id = j.id) AS application_count
                FROM jobs j
                JOIN companies c ON j.company_id = c.id
                WHERE j.deleted_at IS NULL
                """);
        List<Object> params = new ArrayList<>();

        if (status != null) {
            sql.append(" AND j.status = ?");
            params.add(status.name());
        }
        if (companyId != null) {
            sql.append(" AND j.company_id = ?");
            params.add(companyId);
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.trim().toLowerCase() + "%";
            sql.append(" AND (LOWER(j.title) LIKE ? OR LOWER(c.name) LIKE ?)");
            params.add(pattern);
            params.add(pattern);
        }

        sql.append(" ORDER BY j.created_at DESC, j.id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new AdminJobEntity(
                rs.getLong("id"),
                rs.getLong("company_id"),
                rs.getString("company_name"),
                rs.getString("title"),
                rs.getString("description"),
                JobType.valueOf(rs.getString("job_type")),
                ExperienceLevel.valueOf(rs.getString("experience_level")),
                WorkMode.valueOf(rs.getString("work_mode")),
                rs.getString("location"),
                rs.getBigDecimal("salary_min_lpa"),
                rs.getBigDecimal("salary_max_lpa"),
                rs.getDate("application_deadline") != null ? rs.getDate("application_deadline").toLocalDate() : null,
                JobStatus.valueOf(rs.getString("status")),
                rs.getLong("application_count"),
                DateTimeUtils.toInstant(rs.getTimestamp("created_at")),
                DateTimeUtils.toInstant(rs.getTimestamp("updated_at"))
        ), params.toArray());
    }

    public long countJobs(JobStatus status, Long companyId, String q) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM jobs j
                JOIN companies c ON j.company_id = c.id
                WHERE j.deleted_at IS NULL
                """);
        List<Object> params = new ArrayList<>();

        if (status != null) {
            sql.append(" AND j.status = ?");
            params.add(status.name());
        }
        if (companyId != null) {
            sql.append(" AND j.company_id = ?");
            params.add(companyId);
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.trim().toLowerCase() + "%";
            sql.append(" AND (LOWER(j.title) LIKE ? OR LOWER(c.name) LIKE ?)");
            params.add(pattern);
            params.add(pattern);
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    public List<String> findSkillsByJobId(Long jobId) {
        String sql = """
                SELECT s.name
                FROM job_skills js
                JOIN skills s ON js.skill_id = s.id
                WHERE js.job_id = ?
                ORDER BY s.name ASC
                """;
        return jdbcTemplate.queryForList(sql, String.class, jobId);
    }

    // -------------------------------------------------------------
    // Applications Listing & Detail
    // -------------------------------------------------------------

    public record AdminApplicationSummaryEntity(
            Long id,
            Long jobId,
            String jobTitle,
            Long companyId,
            String companyName,
            Long candidateId,
            String candidateName,
            String candidateEmail,
            ApplicationStatus status,
            Instant appliedAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public List<AdminApplicationSummaryEntity> findApplications(
            Long jobId, Long candidateId, Long companyId, ApplicationStatus status, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.id, a.job_id, j.title AS job_title, j.company_id, c.name AS company_name,
                       a.candidate_id, cand.full_name AS candidate_name, u.email AS candidate_email,
                       a.status, a.applied_at, a.created_at, a.updated_at
                FROM applications a
                JOIN jobs j ON a.job_id = j.id
                JOIN companies c ON j.company_id = c.id
                JOIN candidates cand ON a.candidate_id = cand.id
                JOIN users u ON cand.user_id = u.id
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();

        if (jobId != null) {
            sql.append(" AND a.job_id = ?");
            params.add(jobId);
        }
        if (candidateId != null) {
            sql.append(" AND a.candidate_id = ?");
            params.add(candidateId);
        }
        if (companyId != null) {
            sql.append(" AND j.company_id = ?");
            params.add(companyId);
        }
        if (status != null) {
            sql.append(" AND a.status = ?");
            params.add(status.name());
        }

        sql.append(" ORDER BY a.applied_at DESC, a.id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new AdminApplicationSummaryEntity(
                rs.getLong("id"),
                rs.getLong("job_id"),
                rs.getString("job_title"),
                rs.getLong("company_id"),
                rs.getString("company_name"),
                rs.getLong("candidate_id"),
                rs.getString("candidate_name"),
                rs.getString("candidate_email"),
                ApplicationStatus.valueOf(rs.getString("status")),
                DateTimeUtils.toInstant(rs.getTimestamp("applied_at")),
                DateTimeUtils.toInstant(rs.getTimestamp("created_at")),
                DateTimeUtils.toInstant(rs.getTimestamp("updated_at"))
        ), params.toArray());
    }

    public long countApplications(Long jobId, Long candidateId, Long companyId, ApplicationStatus status) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM applications a
                JOIN jobs j ON a.job_id = j.id
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();

        if (jobId != null) {
            sql.append(" AND a.job_id = ?");
            params.add(jobId);
        }
        if (candidateId != null) {
            sql.append(" AND a.candidate_id = ?");
            params.add(candidateId);
        }
        if (companyId != null) {
            sql.append(" AND j.company_id = ?");
            params.add(companyId);
        }
        if (status != null) {
            sql.append(" AND a.status = ?");
            params.add(status.name());
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    public record AdminApplicationDetailRecord(
            Long applicationId,
            Long jobId,
            String jobTitle,
            Long companyId,
            String companyName,
            Long candidateId,
            ApplicationStatus status,
            String coverLetter,
            Instant appliedAt,
            String candidateSnapshotJson,
            String resumeSnapshotJson
    ) {}

    public Optional<AdminApplicationDetailRecord> findApplicationDetailById(Long applicationId) {
        if (applicationId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT a.id AS application_id, a.job_id, j.title AS job_title, j.company_id, c.name AS company_name,
                       a.candidate_id, a.status, a.cover_letter, a.applied_at,
                       a.candidate_snapshot, a.resume_snapshot
                FROM applications a
                JOIN jobs j ON a.job_id = j.id
                JOIN companies c ON j.company_id = c.id
                WHERE a.id = ?
                """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new AdminApplicationDetailRecord(
                    rs.getLong("application_id"),
                    rs.getLong("job_id"),
                    rs.getString("job_title"),
                    rs.getLong("company_id"),
                    rs.getString("company_name"),
                    rs.getLong("candidate_id"),
                    ApplicationStatus.valueOf(rs.getString("status")),
                    rs.getString("cover_letter"),
                    DateTimeUtils.toInstant(rs.getTimestamp("applied_at")),
                    rs.getString("candidate_snapshot"),
                    rs.getString("resume_snapshot")
            ), applicationId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<AdminUserDto> findUserById(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT u.id, u.email, u.role, u.is_active, u.created_at, u.updated_at,
                       c.id AS candidate_id, c.full_name AS candidate_name,
                       r.id AS recruiter_id, r.full_name AS recruiter_name,
                       comp.id AS company_id, comp.name AS company_name
                FROM users u
                LEFT JOIN candidates c ON u.id = c.user_id
                LEFT JOIN recruiters r ON u.id = r.user_id
                LEFT JOIN companies comp ON r.company_id = comp.id
                WHERE u.id = ?
                """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                String fullName = rs.getString("candidate_name");
                if (fullName == null) {
                    fullName = rs.getString("recruiter_name");
                }
                return new AdminUserDto(
                        rs.getLong("id"),
                        rs.getString("email"),
                        Role.valueOf(rs.getString("role")),
                        fullName,
                        rs.getBoolean("is_active"),
                        rs.getObject("candidate_id") != null ? rs.getLong("candidate_id") : null,
                        rs.getObject("recruiter_id") != null ? rs.getLong("recruiter_id") : null,
                        rs.getObject("company_id") != null ? rs.getLong("company_id") : null,
                        rs.getString("company_name"),
                        DateTimeUtils.toInstant(rs.getTimestamp("created_at")),
                        DateTimeUtils.toInstant(rs.getTimestamp("updated_at"))
                );
            }, userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<AdminCompanyDto> findCompanyById(Long companyId) {
        if (companyId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT c.id, c.name, c.industry, c.location, c.website, c.description, c.company_size,
                       c.is_active, c.created_at, c.updated_at,
                       (SELECT COUNT(*) FROM jobs j WHERE j.company_id = c.id AND j.deleted_at IS NULL) AS job_count,
                       (SELECT COUNT(*) FROM recruiters r WHERE r.company_id = c.id) AS recruiter_count
                FROM companies c
                WHERE c.id = ?
                """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new AdminCompanyDto(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("industry"),
                    rs.getString("location"),
                    rs.getString("website"),
                    rs.getString("description"),
                    rs.getString("company_size"),
                    rs.getBoolean("is_active"),
                    rs.getLong("job_count"),
                    rs.getLong("recruiter_count"),
                    DateTimeUtils.toInstant(rs.getTimestamp("created_at")),
                    DateTimeUtils.toInstant(rs.getTimestamp("updated_at"))
            ), companyId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<AdminJobDto> findJobById(Long jobId) {
        if (jobId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT j.id, j.company_id, c.name AS company_name, j.title, j.description,
                       j.job_type, j.experience_level, j.work_mode, j.location,
                       j.salary_min_lpa, j.salary_max_lpa, j.application_deadline,
                       j.status,
                       (SELECT COUNT(*) FROM applications a WHERE a.job_id = j.id) AS application_count,
                       j.created_at, j.updated_at
                FROM jobs j
                JOIN companies c ON j.company_id = c.id
                WHERE j.id = ? AND j.deleted_at IS NULL
                """;
        try {
            AdminJobEntity entity = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new AdminJobEntity(
                    rs.getLong("id"),
                    rs.getLong("company_id"),
                    rs.getString("company_name"),
                    rs.getString("title"),
                    rs.getString("description"),
                    JobType.valueOf(rs.getString("job_type")),
                    ExperienceLevel.valueOf(rs.getString("experience_level")),
                    WorkMode.valueOf(rs.getString("work_mode")),
                    rs.getString("location"),
                    rs.getBigDecimal("salary_min_lpa"),
                    rs.getBigDecimal("salary_max_lpa"),
                    rs.getDate("application_deadline") != null ? rs.getDate("application_deadline").toLocalDate() : null,
                    JobStatus.valueOf(rs.getString("status")),
                    rs.getLong("application_count"),
                    DateTimeUtils.toInstant(rs.getTimestamp("created_at")),
                    DateTimeUtils.toInstant(rs.getTimestamp("updated_at"))
            ), jobId);

            if (entity == null) {
                return Optional.empty();
            }
            List<String> skills = findSkillsByJobId(jobId);
            return Optional.of(new AdminJobDto(
                    entity.id(),
                    entity.companyId(),
                    entity.companyName(),
                    entity.title(),
                    entity.description(),
                    entity.jobType(),
                    entity.experienceLevel(),
                    entity.workMode(),
                    entity.location(),
                    entity.salaryMinLpa(),
                    entity.salaryMaxLpa(),
                    entity.applicationDeadline(),
                    entity.status(),
                    entity.applicationCount(),
                    skills,
                    entity.createdAt(),
                    entity.updatedAt()
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
