package com.careerconnect.company.repository;

import com.careerconnect.common.util.DateTimeUtils;
import com.careerconnect.common.util.JdbcUtils;
import com.careerconnect.company.dto.CompanyDetailDto;
import com.careerconnect.company.dto.CompanySummaryDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class CompanyRepository {

    private final JdbcTemplate jdbcTemplate;

    public CompanyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<CompanySummaryDto> companySummaryRowMapper = new RowMapper<>() {
        @Override
        public CompanySummaryDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new CompanySummaryDto(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("industry"),
                    rs.getString("location"),
                    rs.getString("website"),
                    rs.getString("company_size"),
                    rs.getLong("active_job_count")
            );
        }
    };

    public List<CompanySummaryDto> findActiveCompanies(String q, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.name, c.industry, c.location, c.website, c.company_size,
                       (SELECT COUNT(*) FROM jobs j
                        WHERE j.company_id = c.id
                          AND j.status = 'ACTIVE'
                          AND j.application_deadline >= CURRENT_DATE
                          AND j.deleted_at IS NULL) AS active_job_count
                FROM companies c
                WHERE c.is_active = TRUE
                """);

        List<Object> params = new ArrayList<>();
        if (q != null && !q.trim().isEmpty()) {
            sql.append(" AND (LOWER(c.name) LIKE ? OR LOWER(c.industry) LIKE ? OR LOWER(c.location) LIKE ?)");
            String pattern = "%" + q.trim().toLowerCase() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        sql.append(" ORDER BY c.name ASC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), companySummaryRowMapper, params.toArray());
    }

    public long countActiveCompanies(String q) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM companies c WHERE c.is_active = TRUE");
        List<Object> params = new ArrayList<>();

        if (q != null && !q.trim().isEmpty()) {
            sql.append(" AND (LOWER(c.name) LIKE ? OR LOWER(c.industry) LIKE ? OR LOWER(c.location) LIKE ?)");
            String pattern = "%" + q.trim().toLowerCase() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    public Optional<CompanySummaryDto> findActiveCompanySummaryById(Long id) {
        String sql = """
                SELECT c.id, c.name, c.industry, c.location, c.website, c.company_size,
                       (SELECT COUNT(*) FROM jobs j
                        WHERE j.company_id = c.id
                          AND j.status = 'ACTIVE'
                          AND j.application_deadline >= CURRENT_DATE
                          AND j.deleted_at IS NULL) AS active_job_count
                FROM companies c
                WHERE c.id = ? AND c.is_active = TRUE
                """;

        List<CompanySummaryDto> results = jdbcTemplate.query(sql, companySummaryRowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<CompanyDetailDto> findActiveCompanyDetailById(Long id) {
        String sql = """
                SELECT c.id, c.name, c.industry, c.location, c.website, c.description, c.company_size, c.created_at,
                       (SELECT COUNT(*) FROM jobs j
                        WHERE j.company_id = c.id
                          AND j.status = 'ACTIVE'
                          AND j.application_deadline >= CURRENT_DATE
                          AND j.deleted_at IS NULL) AS active_job_count
                FROM companies c
                WHERE c.id = ? AND c.is_active = TRUE
                """;

        List<CompanyDetailDto> results = jdbcTemplate.query(sql, (rs, rowNum) -> new CompanyDetailDto(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("industry"),
                rs.getString("location"),
                rs.getString("website"),
                rs.getString("description"),
                rs.getString("company_size"),
                rs.getLong("active_job_count"),
                Collections.emptyList(), // will be populated by service
                DateTimeUtils.toInstant(rs.getTimestamp("created_at"))
        ), id);

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public record CompanyRecord(
            Long id,
            String name,
            String industry,
            String location,
            String website,
            String description,
            String companySize,
            boolean isActive,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public Optional<CompanyRecord> findCompanyById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT id, name, industry, location, website, description, company_size, is_active, created_at, updated_at
                FROM companies
                WHERE id = ?
                """;
        List<CompanyRecord> results = jdbcTemplate.query(sql, (rs, rowNum) -> new CompanyRecord(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("industry"),
                rs.getString("location"),
                rs.getString("website"),
                rs.getString("description"),
                rs.getString("company_size"),
                rs.getBoolean("is_active"),
                DateTimeUtils.toInstant(rs.getTimestamp("created_at")),
                DateTimeUtils.toInstant(rs.getTimestamp("updated_at"))
        ), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public boolean existsByNameAndNotId(String name, Long id) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        String sql;
        Object[] params;
        if (id != null) {
            sql = "SELECT COUNT(*) FROM companies WHERE LOWER(TRIM(name)) = LOWER(TRIM(?)) AND id <> ?";
            params = new Object[]{name, id};
        } else {
            sql = "SELECT COUNT(*) FROM companies WHERE LOWER(TRIM(name)) = LOWER(TRIM(?))";
            params = new Object[]{name};
        }
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, params);
        return count != null && count > 0;
    }

    public Long insertCompany(String name, String industry, String location, String website, String description, String companySize) {
        String sql = """
                INSERT INTO companies (name, industry, location, website, description, company_size, is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        Timestamp now = Timestamp.from(Instant.now());

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name.trim());
            ps.setString(2, industry != null ? industry.trim() : null);
            ps.setString(3, location != null ? location.trim() : null);
            ps.setString(4, website != null ? website.trim() : null);
            ps.setString(5, description != null ? description.trim() : null);
            ps.setString(6, companySize != null ? companySize.trim() : null);
            ps.setTimestamp(7, now);
            ps.setTimestamp(8, now);
            return ps;
        }, keyHolder);

        return JdbcUtils.extractGeneratedId(keyHolder);
    }

    public boolean updateCompany(Long id, String name, String industry, String location, String website, String description, String companySize) {
        String sql = """
                UPDATE companies
                SET name = ?, industry = ?, location = ?, website = ?, description = ?, company_size = ?, updated_at = ?
                WHERE id = ?
                """;
        Timestamp now = Timestamp.from(Instant.now());
        int rows = jdbcTemplate.update(
                sql,
                name.trim(),
                industry != null ? industry.trim() : null,
                location != null ? location.trim() : null,
                website != null ? website.trim() : null,
                description != null ? description.trim() : null,
                companySize != null ? companySize.trim() : null,
                now,
                id
        );
        return rows > 0;
    }
}
