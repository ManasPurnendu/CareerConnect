package com.careerconnect.company.repository;

import com.careerconnect.common.util.DateTimeUtils;
import com.careerconnect.company.dto.CompanyDetailDto;
import com.careerconnect.company.dto.CompanySummaryDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
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
}
