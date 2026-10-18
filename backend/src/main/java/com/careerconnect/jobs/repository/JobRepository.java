package com.careerconnect.jobs.repository;

import com.careerconnect.common.util.DateTimeUtils;
import com.careerconnect.common.util.JdbcUtils;
import com.careerconnect.company.dto.CompanySummaryDto;
import com.careerconnect.jobs.dto.JobDetailDto;
import com.careerconnect.jobs.dto.JobFilterParams;
import com.careerconnect.jobs.dto.JobSummaryDto;
import com.careerconnect.jobs.dto.SkillDto;
import com.careerconnect.jobs.model.ExperienceLevel;
import com.careerconnect.jobs.model.JobStatus;
import com.careerconnect.jobs.model.JobType;
import com.careerconnect.jobs.model.WorkMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

@Repository
public class JobRepository {

    private final JdbcTemplate jdbcTemplate;

    public JobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<JobSummaryDto> findActiveJobs(JobFilterParams params) {
        StringBuilder sql = new StringBuilder("""
                SELECT j.id, j.title, j.job_type, j.experience_level, j.work_mode, j.location,
                       j.salary_min_lpa, j.salary_max_lpa, j.salary_currency, j.application_deadline,
                       j.status, j.created_at,
                       c.id AS company_id, c.name AS company_name, c.industry AS company_industry,
                       c.location AS company_location, c.website AS company_website, c.company_size AS company_size,
                       (SELECT COUNT(*) FROM jobs j2
                        WHERE j2.company_id = c.id
                          AND j2.status = 'ACTIVE'
                          AND j2.application_deadline >= CURRENT_DATE
                          AND j2.deleted_at IS NULL) AS company_active_job_count
                FROM jobs j
                JOIN companies c ON j.company_id = c.id
                WHERE j.status = 'ACTIVE'
                  AND j.application_deadline >= CURRENT_DATE
                  AND j.deleted_at IS NULL
                  AND c.is_active = TRUE
                """);

        List<Object> queryParams = new ArrayList<>();
        applyFilters(sql, queryParams, params);

        applySorting(sql, params.sort());

        sql.append(" LIMIT ? OFFSET ?");
        queryParams.add(params.getSize());
        queryParams.add(params.getOffset());

        List<JobSummaryDto> jobs = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapJobSummary(rs), queryParams.toArray());

        if (jobs.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch load skills for all fetched jobs to eliminate N+1 queries
        Map<Long, List<SkillDto>> skillsByJobId = findSkillsForJobIds(jobs.stream().map(JobSummaryDto::id).toList());

        return jobs.stream()
                .map(job -> new JobSummaryDto(
                        job.id(),
                        job.title(),
                        job.company(),
                        job.jobType(),
                        job.experienceLevel(),
                        job.workMode(),
                        job.location(),
                        job.salaryMinLpa(),
                        job.salaryMaxLpa(),
                        job.salaryCurrency(),
                        job.applicationDeadline(),
                        job.status(),
                        skillsByJobId.getOrDefault(job.id(), Collections.emptyList()),
                        job.createdAt()
                ))
                .toList();
    }

    public long countActiveJobs(JobFilterParams params) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM jobs j
                JOIN companies c ON j.company_id = c.id
                WHERE j.status = 'ACTIVE'
                  AND j.application_deadline >= CURRENT_DATE
                  AND j.deleted_at IS NULL
                  AND c.is_active = TRUE
                """);

        List<Object> queryParams = new ArrayList<>();
        applyFilters(sql, queryParams, params);

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, queryParams.toArray());
        return count != null ? count : 0L;
    }

    public Optional<JobDetailDto> findActiveJobById(Long id) {
        String sql = """
                SELECT j.id, j.title, j.description, j.responsibilities, j.requirements,
                       j.job_type, j.experience_level, j.work_mode, j.location,
                       j.salary_min_lpa, j.salary_max_lpa, j.salary_currency, j.application_deadline,
                       j.status, j.created_at, j.updated_at,
                       c.id AS company_id, c.name AS company_name, c.industry AS company_industry,
                       c.location AS company_location, c.website AS company_website, c.company_size AS company_size,
                       (SELECT COUNT(*) FROM jobs j2
                        WHERE j2.company_id = c.id
                          AND j2.status = 'ACTIVE'
                          AND j2.application_deadline >= CURRENT_DATE
                          AND j2.deleted_at IS NULL) AS company_active_job_count
                FROM jobs j
                JOIN companies c ON j.company_id = c.id
                WHERE j.id = ?
                  AND j.status = 'ACTIVE'
                  AND j.application_deadline >= CURRENT_DATE
                  AND j.deleted_at IS NULL
                  AND c.is_active = TRUE
                """;

        List<JobDetailDto> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
            CompanySummaryDto company = new CompanySummaryDto(
                    rs.getLong("company_id"),
                    rs.getString("company_name"),
                    rs.getString("company_industry"),
                    rs.getString("company_location"),
                    rs.getString("company_website"),
                    rs.getString("company_size"),
                    rs.getLong("company_active_job_count")
            );

            return new JobDetailDto(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("responsibilities"),
                    rs.getString("requirements"),
                    company,
                    JobType.valueOf(rs.getString("job_type")),
                    ExperienceLevel.valueOf(rs.getString("experience_level")),
                    WorkMode.valueOf(rs.getString("work_mode")),
                    rs.getString("location"),
                    rs.getBigDecimal("salary_min_lpa"),
                    rs.getBigDecimal("salary_max_lpa"),
                    rs.getString("salary_currency"),
                    rs.getDate("application_deadline").toLocalDate(),
                    JobStatus.valueOf(rs.getString("status")),
                    Collections.emptyList(),
                    DateTimeUtils.toInstant(rs.getTimestamp("created_at")),
                    DateTimeUtils.toInstant(rs.getTimestamp("updated_at"))
            );
        }, id);

        if (results.isEmpty()) {
            return Optional.empty();
        }

        JobDetailDto job = results.get(0);
        List<SkillDto> skills = findSkillsForJobId(job.id());

        return Optional.of(new JobDetailDto(
                job.id(),
                job.title(),
                job.description(),
                job.responsibilities(),
                job.requirements(),
                job.company(),
                job.jobType(),
                job.experienceLevel(),
                job.workMode(),
                job.location(),
                job.salaryMinLpa(),
                job.salaryMaxLpa(),
                job.salaryCurrency(),
                job.applicationDeadline(),
                job.status(),
                skills,
                job.createdAt(),
                job.updatedAt()
        ));
    }

    public List<JobSummaryDto> findActiveJobsByCompanyId(Long companyId) {
        String sql = """
                SELECT j.id, j.title, j.job_type, j.experience_level, j.work_mode, j.location,
                       j.salary_min_lpa, j.salary_max_lpa, j.salary_currency, j.application_deadline,
                       j.status, j.created_at,
                       c.id AS company_id, c.name AS company_name, c.industry AS company_industry,
                       c.location AS company_location, c.website AS company_website, c.company_size AS company_size,
                       (SELECT COUNT(*) FROM jobs j2
                        WHERE j2.company_id = c.id
                          AND j2.status = 'ACTIVE'
                          AND j2.application_deadline >= CURRENT_DATE
                          AND j2.deleted_at IS NULL) AS company_active_job_count
                FROM jobs j
                JOIN companies c ON j.company_id = c.id
                WHERE j.company_id = ?
                  AND j.status = 'ACTIVE'
                  AND j.application_deadline >= CURRENT_DATE
                  AND j.deleted_at IS NULL
                  AND c.is_active = TRUE
                ORDER BY j.created_at DESC, j.id DESC
                """;

        List<JobSummaryDto> jobs = jdbcTemplate.query(sql, (rs, rowNum) -> mapJobSummary(rs), companyId);
        if (jobs.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<SkillDto>> skillsByJobId = findSkillsForJobIds(jobs.stream().map(JobSummaryDto::id).toList());

        return jobs.stream()
                .map(job -> new JobSummaryDto(
                        job.id(),
                        job.title(),
                        job.company(),
                        job.jobType(),
                        job.experienceLevel(),
                        job.workMode(),
                        job.location(),
                        job.salaryMinLpa(),
                        job.salaryMaxLpa(),
                        job.salaryCurrency(),
                        job.applicationDeadline(),
                        job.status(),
                        skillsByJobId.getOrDefault(job.id(), Collections.emptyList()),
                        job.createdAt()
                ))
                .toList();
    }

    private void applyFilters(StringBuilder sql, List<Object> queryParams, JobFilterParams params) {
        if (params.keyword() != null && !params.keyword().trim().isEmpty()) {
            sql.append(" AND (LOWER(j.title) LIKE ? OR LOWER(j.description) LIKE ? OR LOWER(c.name) LIKE ?)");
            String pattern = "%" + params.keyword().trim().toLowerCase() + "%";
            queryParams.add(pattern);
            queryParams.add(pattern);
            queryParams.add(pattern);
        }

        if (params.location() != null && !params.location().trim().isEmpty()) {
            sql.append(" AND LOWER(j.location) LIKE ?");
            queryParams.add("%" + params.location().trim().toLowerCase() + "%");
        }

        if (params.employmentType() != null) {
            sql.append(" AND j.job_type = ?");
            queryParams.add(params.employmentType().name());
        }

        if (params.experienceLevel() != null) {
            sql.append(" AND j.experience_level = ?");
            queryParams.add(params.experienceLevel().name());
        }

        if (params.workMode() != null) {
            sql.append(" AND j.work_mode = ?");
            queryParams.add(params.workMode().name());
        }

        if (params.skills() != null && !params.skills().trim().isEmpty()) {
            String[] skillTokens = params.skills().split(",");
            for (String rawToken : skillTokens) {
                String skillSearch = rawToken.trim().toLowerCase();
                if (!skillSearch.isEmpty()) {
                    sql.append("""
                             AND EXISTS (
                                 SELECT 1 FROM job_skills js
                                 JOIN skills s ON js.skill_id = s.id
                                 WHERE js.job_id = j.id
                                   AND (LOWER(s.name) = ? OR s.normalized_name = ?)
                             )
                            """);
                    queryParams.add(skillSearch);
                    queryParams.add(skillSearch);
                }
            }
        }

        if (params.minSalary() != null) {
            sql.append(" AND (j.salary_max_lpa >= ? OR (j.salary_max_lpa IS NULL AND j.salary_min_lpa >= ?))");
            queryParams.add(params.minSalary());
            queryParams.add(params.minSalary());
        }

        if (params.maxSalary() != null) {
            sql.append(" AND (j.salary_min_lpa <= ? OR (j.salary_min_lpa IS NULL AND j.salary_max_lpa <= ?))");
            queryParams.add(params.maxSalary());
            queryParams.add(params.maxSalary());
        }
    }

    private void applySorting(StringBuilder sql, String sort) {
        if (sort == null || sort.trim().isEmpty()) {
            sql.append(" ORDER BY j.created_at DESC, j.id DESC");
            return;
        }

        String normalizedSort = sort.trim().toLowerCase();
        switch (normalizedSort) {
            case "latest":
            case "newest":
            case "createdat,desc":
                sql.append(" ORDER BY j.created_at DESC, j.id DESC");
                break;
            case "deadline":
            case "deadline,asc":
                sql.append(" ORDER BY j.application_deadline ASC, j.id DESC");
                break;
            case "deadline,desc":
                sql.append(" ORDER BY j.application_deadline DESC, j.id DESC");
                break;
            case "salary":
            case "salary,desc":
                sql.append(" ORDER BY COALESCE(j.salary_max_lpa, j.salary_min_lpa, 0) DESC, j.id DESC");
                break;
            case "salary,asc":
                sql.append(" ORDER BY COALESCE(j.salary_min_lpa, j.salary_max_lpa, 0) ASC, j.id DESC");
                break;
            case "title":
            case "title,asc":
                sql.append(" ORDER BY j.title ASC, j.id DESC");
                break;
            case "title,desc":
                sql.append(" ORDER BY j.title DESC, j.id DESC");
                break;
            case "createdat,asc":
            case "oldest":
                sql.append(" ORDER BY j.created_at ASC, j.id ASC");
                break;
            default:
                sql.append(" ORDER BY j.created_at DESC, j.id DESC");
                break;
        }
    }

    private JobSummaryDto mapJobSummary(ResultSet rs) throws SQLException {
        CompanySummaryDto company = new CompanySummaryDto(
                rs.getLong("company_id"),
                rs.getString("company_name"),
                rs.getString("company_industry"),
                rs.getString("company_location"),
                rs.getString("company_website"),
                rs.getString("company_size"),
                rs.getLong("company_active_job_count")
        );

        return new JobSummaryDto(
                rs.getLong("id"),
                rs.getString("title"),
                company,
                JobType.valueOf(rs.getString("job_type")),
                ExperienceLevel.valueOf(rs.getString("experience_level")),
                WorkMode.valueOf(rs.getString("work_mode")),
                rs.getString("location"),
                rs.getBigDecimal("salary_min_lpa"),
                rs.getBigDecimal("salary_max_lpa"),
                rs.getString("salary_currency"),
                rs.getDate("application_deadline").toLocalDate(),
                JobStatus.valueOf(rs.getString("status")),
                Collections.emptyList(),
                DateTimeUtils.toInstant(rs.getTimestamp("created_at"))
        );
    }

    private List<SkillDto> findSkillsForJobId(Long jobId) {
        String sql = """
                SELECT s.id, s.name
                FROM job_skills js
                JOIN skills s ON js.skill_id = s.id
                WHERE js.job_id = ?
                ORDER BY s.name ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new SkillDto(
                rs.getLong("id"),
                rs.getString("name")
        ), jobId);
    }

    private Map<Long, List<SkillDto>> findSkillsForJobIds(List<Long> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String inSql = String.join(",", Collections.nCopies(jobIds.size(), "?"));
        String sql = String.format("""
                SELECT js.job_id, s.id AS skill_id, s.name AS skill_name
                FROM job_skills js
                JOIN skills s ON js.skill_id = s.id
                WHERE js.job_id IN (%s)
                ORDER BY s.name ASC
                """, inSql);

        Map<Long, List<SkillDto>> map = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            Long jobId = rs.getLong("job_id");
            SkillDto skill = new SkillDto(rs.getLong("skill_id"), rs.getString("skill_name"));
            map.computeIfAbsent(jobId, k -> new ArrayList<>()).add(skill);
        }, jobIds.toArray());

        return map;
    }

    public boolean existsJobById(Long id) {
        if (id == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM jobs WHERE id = ? AND deleted_at IS NULL";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    public List<String> findRequiredSkillNamesByJobId(Long jobId) {
        if (jobId == null) {
            return Collections.emptyList();
        }
        String sql = """
                SELECT s.name
                FROM job_skills js
                JOIN skills s ON js.skill_id = s.id
                WHERE js.job_id = ?
                ORDER BY s.name ASC
                """;
        return jdbcTemplate.queryForList(sql, String.class, jobId);
    }

    public record JobLifecycleEntity(
            Long id,
            Long companyId,
            String title,
            String description,
            String responsibilities,
            String requirements,
            JobType jobType,
            ExperienceLevel experienceLevel,
            WorkMode workMode,
            String location,
            java.math.BigDecimal salaryMinLpa,
            java.math.BigDecimal salaryMaxLpa,
            String salaryCurrency,
            java.time.LocalDate applicationDeadline,
            JobStatus status,
            java.time.Instant deletedAt,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {}

    public Optional<JobLifecycleEntity> findJobByIdForRecruiter(Long jobId, Long companyId) {
        if (jobId == null || companyId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT id, company_id, title, description, responsibilities, requirements,
                       job_type, experience_level, work_mode, location,
                       salary_min_lpa, salary_max_lpa, salary_currency,
                       application_deadline, status, deleted_at, created_at, updated_at
                FROM jobs
                WHERE id = ? AND company_id = ? AND deleted_at IS NULL
                """;

        List<JobLifecycleEntity> list = jdbcTemplate.query(sql, (rs, rowNum) -> new JobLifecycleEntity(
                rs.getLong("id"),
                rs.getLong("company_id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("responsibilities"),
                rs.getString("requirements"),
                JobType.valueOf(rs.getString("job_type")),
                ExperienceLevel.valueOf(rs.getString("experience_level")),
                WorkMode.valueOf(rs.getString("work_mode")),
                rs.getString("location"),
                rs.getBigDecimal("salary_min_lpa"),
                rs.getBigDecimal("salary_max_lpa"),
                rs.getString("salary_currency"),
                rs.getDate("application_deadline") != null ? rs.getDate("application_deadline").toLocalDate() : null,
                JobStatus.valueOf(rs.getString("status")),
                DateTimeUtils.toInstant(rs.getTimestamp("deleted_at")),
                DateTimeUtils.toInstant(rs.getTimestamp("created_at")),
                DateTimeUtils.toInstant(rs.getTimestamp("updated_at"))
        ), jobId, companyId);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<JobDetailDto> findJobDetailByIdAndCompanyId(Long jobId, Long companyId) {
        if (jobId == null || companyId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT j.id, j.title, j.description, j.responsibilities, j.requirements,
                       j.job_type, j.experience_level, j.work_mode, j.location,
                       j.salary_min_lpa, j.salary_max_lpa, j.salary_currency, j.application_deadline,
                       j.status, j.created_at, j.updated_at,
                       c.id AS company_id, c.name AS company_name, c.industry AS company_industry,
                       c.location AS company_location, c.website AS company_website, c.company_size AS company_size,
                       (SELECT COUNT(*) FROM jobs j2
                        WHERE j2.company_id = c.id
                          AND j2.status = 'ACTIVE'
                          AND j2.application_deadline >= CURRENT_DATE
                          AND j2.deleted_at IS NULL) AS company_active_job_count
                FROM jobs j
                JOIN companies c ON j.company_id = c.id
                WHERE j.id = ? AND j.company_id = ? AND j.deleted_at IS NULL
                """;

        List<JobDetailDto> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
            CompanySummaryDto company = new CompanySummaryDto(
                    rs.getLong("company_id"),
                    rs.getString("company_name"),
                    rs.getString("company_industry"),
                    rs.getString("company_location"),
                    rs.getString("company_website"),
                    rs.getString("company_size"),
                    rs.getLong("company_active_job_count")
            );

            return new JobDetailDto(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("responsibilities"),
                    rs.getString("requirements"),
                    company,
                    JobType.valueOf(rs.getString("job_type")),
                    ExperienceLevel.valueOf(rs.getString("experience_level")),
                    WorkMode.valueOf(rs.getString("work_mode")),
                    rs.getString("location"),
                    rs.getBigDecimal("salary_min_lpa"),
                    rs.getBigDecimal("salary_max_lpa"),
                    rs.getString("salary_currency"),
                    rs.getDate("application_deadline") != null ? rs.getDate("application_deadline").toLocalDate() : null,
                    JobStatus.valueOf(rs.getString("status")),
                    Collections.emptyList(),
                    DateTimeUtils.toInstant(rs.getTimestamp("created_at")),
                    DateTimeUtils.toInstant(rs.getTimestamp("updated_at"))
            );
        }, jobId, companyId);

        if (results.isEmpty()) {
            return Optional.empty();
        }

        JobDetailDto job = results.get(0);
        List<SkillDto> skills = findSkillsForJobId(job.id());

        return Optional.of(new JobDetailDto(
                job.id(),
                job.title(),
                job.description(),
                job.responsibilities(),
                job.requirements(),
                job.company(),
                job.jobType(),
                job.experienceLevel(),
                job.workMode(),
                job.location(),
                job.salaryMinLpa(),
                job.salaryMaxLpa(),
                job.salaryCurrency(),
                job.applicationDeadline(),
                job.status(),
                skills,
                job.createdAt(),
                job.updatedAt()
        ));
    }

    public boolean updateJobStatus(Long jobId, Long companyId, JobStatus newStatus) {
        if (jobId == null || companyId == null || newStatus == null) {
            return false;
        }
        String sql = """
                UPDATE jobs
                SET status = ?, updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND company_id = ? AND deleted_at IS NULL
                """;
        int rows = jdbcTemplate.update(sql, newStatus.name(), jobId, companyId);
        return rows > 0;
    }

    public List<JobSummaryDto> findJobsByCompanyIdPaginated(Long companyId, int offset, int limit) {
        if (companyId == null) {
            return Collections.emptyList();
        }
        String sql = """
                SELECT j.id, j.title, j.job_type, j.experience_level, j.work_mode, j.location,
                       j.salary_min_lpa, j.salary_max_lpa, j.salary_currency, j.application_deadline,
                       j.status, j.created_at,
                       c.id AS company_id, c.name AS company_name, c.industry AS company_industry,
                       c.location AS company_location, c.website AS company_website, c.company_size AS company_size,
                       (SELECT COUNT(*) FROM jobs j2
                        WHERE j2.company_id = c.id
                          AND j2.status = 'ACTIVE'
                          AND j2.application_deadline >= CURRENT_DATE
                          AND j2.deleted_at IS NULL) AS company_active_job_count
                FROM jobs j
                JOIN companies c ON j.company_id = c.id
                WHERE j.company_id = ? AND j.deleted_at IS NULL
                ORDER BY j.created_at DESC, j.id DESC
                LIMIT ? OFFSET ?
                """;

        List<JobSummaryDto> jobs = jdbcTemplate.query(sql, (rs, rowNum) -> mapJobSummary(rs), companyId, limit, offset);
        if (jobs.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<SkillDto>> skillsByJobId = findSkillsForJobIds(jobs.stream().map(JobSummaryDto::id).toList());

        return jobs.stream()
                .map(job -> new JobSummaryDto(
                        job.id(),
                        job.title(),
                        job.company(),
                        job.jobType(),
                        job.experienceLevel(),
                        job.workMode(),
                        job.location(),
                        job.salaryMinLpa(),
                        job.salaryMaxLpa(),
                        job.salaryCurrency(),
                        job.applicationDeadline(),
                        job.status(),
                        skillsByJobId.getOrDefault(job.id(), Collections.emptyList()),
                        job.createdAt()
                ))
                .toList();
    }

    public long countJobsByCompanyId(Long companyId) {
        if (companyId == null) {
            return 0L;
        }
        String sql = "SELECT COUNT(*) FROM jobs WHERE company_id = ? AND deleted_at IS NULL";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, companyId);
        return count != null ? count : 0L;
    }

    public List<Long> findRequiredSkillIdsByJobId(Long jobId) {
        if (jobId == null) {
            return Collections.emptyList();
        }
        String sql = "SELECT skill_id FROM job_skills WHERE job_id = ? ORDER BY skill_id ASC";
        return jdbcTemplate.queryForList(sql, Long.class, jobId);
    }

    public int countExistingSkillIds(List<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return 0;
        }
        String inSql = String.join(",", Collections.nCopies(skillIds.size(), "?"));
        String sql = "SELECT COUNT(*) FROM skills WHERE id IN (" + inSql + ")";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, skillIds.toArray());
        return count != null ? count : 0;
    }

    public boolean hasApplications(Long jobId) {
        if (jobId == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM applications WHERE job_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, jobId);
        return count != null && count > 0;
    }

    public Long createJob(
            Long companyId,
            String title,
            String description,
            String responsibilities,
            String requirements,
            JobType jobType,
            ExperienceLevel experienceLevel,
            WorkMode workMode,
            String location,
            java.math.BigDecimal salaryMinLpa,
            java.math.BigDecimal salaryMaxLpa,
            String salaryCurrency,
            java.time.LocalDate applicationDeadline
    ) {
        String sql = """
                INSERT INTO jobs (
                    company_id, title, description, responsibilities, requirements,
                    job_type, experience_level, work_mode, location, salary_min_lpa,
                    salary_max_lpa, salary_currency, application_deadline, status,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            ps.setString(2, title);
            ps.setString(3, description);
            ps.setString(4, responsibilities);
            ps.setString(5, requirements);
            ps.setString(6, jobType.name());
            ps.setString(7, experienceLevel.name());
            ps.setString(8, workMode.name());
            ps.setString(9, location);
            ps.setBigDecimal(10, salaryMinLpa);
            ps.setBigDecimal(11, salaryMaxLpa);
            ps.setString(12, salaryCurrency != null ? salaryCurrency : "INR");
            ps.setDate(13, applicationDeadline != null ? java.sql.Date.valueOf(applicationDeadline) : null);
            return ps;
        }, keyHolder);

        return JdbcUtils.extractGeneratedId(keyHolder);
    }

    public boolean updateJobFields(
            Long jobId,
            Long companyId,
            String title,
            String description,
            String responsibilities,
            String requirements,
            JobType jobType,
            ExperienceLevel experienceLevel,
            WorkMode workMode,
            String location,
            java.math.BigDecimal salaryMinLpa,
            java.math.BigDecimal salaryMaxLpa,
            String salaryCurrency,
            java.time.LocalDate applicationDeadline
    ) {
        String sql = """
                UPDATE jobs
                SET title = ?,
                    description = ?,
                    responsibilities = ?,
                    requirements = ?,
                    job_type = ?,
                    experience_level = ?,
                    work_mode = ?,
                    location = ?,
                    salary_min_lpa = ?,
                    salary_max_lpa = ?,
                    salary_currency = ?,
                    application_deadline = ?,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND company_id = ? AND deleted_at IS NULL
                """;
        int rows = jdbcTemplate.update(
                sql,
                title,
                description,
                responsibilities,
                requirements,
                jobType.name(),
                experienceLevel.name(),
                workMode.name(),
                location,
                salaryMinLpa,
                salaryMaxLpa,
                salaryCurrency,
                applicationDeadline != null ? java.sql.Date.valueOf(applicationDeadline) : null,
                jobId,
                companyId
        );
        return rows > 0;
    }

    public void replaceJobSkills(Long jobId, Long companyId, List<Long> skillIds) {
        if (jobId == null || companyId == null) {
            return;
        }
        // Defense in depth: Verify job belongs to company and is not deleted before modifying associations
        String verifySql = "SELECT COUNT(*) FROM jobs WHERE id = ? AND company_id = ? AND deleted_at IS NULL";
        Integer count = jdbcTemplate.queryForObject(verifySql, Integer.class, jobId, companyId);
        if (count == null || count == 0) {
            return;
        }

        replaceJobSkills(jobId, skillIds);
    }

    public void replaceJobSkills(Long jobId, List<Long> skillIds) {
        if (jobId == null) {
            return;
        }
        // 1. Delete existing associations
        jdbcTemplate.update("DELETE FROM job_skills WHERE job_id = ?", jobId);

        // 2. Batch insert new associations
        if (skillIds != null && !skillIds.isEmpty()) {
            String insertSql = "INSERT INTO job_skills (job_id, skill_id, created_at) VALUES (?, ?, CURRENT_TIMESTAMP(3))";
            List<Object[]> batchParams = skillIds.stream()
                    .distinct()
                    .map(skillId -> new Object[]{jobId, skillId})
                    .toList();
            jdbcTemplate.batchUpdate(insertSql, batchParams);
        }
    }

    public boolean softDeleteJob(Long jobId, Long companyId) {
        if (jobId == null || companyId == null) {
            return false;
        }
        String sql = """
                UPDATE jobs
                SET deleted_at = CURRENT_TIMESTAMP(3),
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND company_id = ? AND deleted_at IS NULL
                """;
        int rows = jdbcTemplate.update(sql, jobId, companyId);
        return rows > 0;
    }
}

