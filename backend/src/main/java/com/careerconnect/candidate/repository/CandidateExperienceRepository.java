package com.careerconnect.candidate.repository;

import com.careerconnect.candidate.dto.CreateExperienceRequest;
import com.careerconnect.candidate.dto.ExperienceDto;
import com.careerconnect.candidate.dto.UpdateExperienceRequest;
import com.careerconnect.common.util.JdbcUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class CandidateExperienceRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ExperienceDto> experienceRowMapper = (rs, rowNum) -> {
        Date start = rs.getDate("start_date");
        Date end = rs.getDate("end_date");
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");

        return new ExperienceDto(
                rs.getLong("id"),
                rs.getLong("candidate_id"),
                rs.getString("company_name"),
                rs.getString("role_title"),
                start != null ? start.toLocalDate() : null,
                end != null ? end.toLocalDate() : null,
                rs.getBoolean("is_current"),
                rs.getString("description"),
                rs.getInt("sort_order"),
                createdTs != null ? createdTs.toInstant() : null,
                updatedTs != null ? updatedTs.toInstant() : null
        );
    };

    public CandidateExperienceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long insert(Long candidateId, CreateExperienceRequest request) {
        String sql = """
                INSERT INTO experience (
                    candidate_id, company_name, role_title, start_date, end_date,
                    is_current, description, sort_order, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        Timestamp now = Timestamp.from(Instant.now());
        boolean isCurrent = Boolean.TRUE.equals(request.getIsCurrent());
        int sortOrder = request.getSortOrder() != null ? request.getSortOrder() : 0;

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, candidateId);
            ps.setString(2, request.getCompanyName().trim());
            ps.setString(3, request.getRoleTitle().trim());
            ps.setDate(4, Date.valueOf(request.getStartDate()));
            if (!isCurrent && request.getEndDate() != null) {
                ps.setDate(5, Date.valueOf(request.getEndDate()));
            } else {
                ps.setNull(5, Types.DATE);
            }
            ps.setBoolean(6, isCurrent);
            if (request.getDescription() != null && !request.getDescription().trim().isEmpty()) {
                ps.setString(7, request.getDescription().trim());
            } else {
                ps.setNull(7, Types.VARCHAR);
            }
            ps.setInt(8, sortOrder);
            ps.setTimestamp(9, now);
            ps.setTimestamp(10, now);
            return ps;
        }, keyHolder);

        return JdbcUtils.extractGeneratedId(keyHolder);
    }

    public Optional<ExperienceDto> findByIdAndCandidateId(Long id, Long candidateId) {
        if (id == null || candidateId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT id, candidate_id, company_name, role_title, start_date, end_date,
                       is_current, description, sort_order, created_at, updated_at
                FROM experience
                WHERE id = ? AND candidate_id = ?
                """;
        try {
            ExperienceDto experience = jdbcTemplate.queryForObject(sql, experienceRowMapper, id, candidateId);
            return Optional.ofNullable(experience);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<ExperienceDto> findByCandidateId(Long candidateId) {
        if (candidateId == null) {
            return List.of();
        }
        String sql = """
                SELECT id, candidate_id, company_name, role_title, start_date, end_date,
                       is_current, description, sort_order, created_at, updated_at
                FROM experience
                WHERE candidate_id = ?
                ORDER BY sort_order ASC, start_date DESC, id ASC
                """;
        return jdbcTemplate.query(sql, experienceRowMapper, candidateId);
    }

    public int update(Long id, Long candidateId, UpdateExperienceRequest request) {
        String sql = """
                UPDATE experience
                SET company_name = ?,
                    role_title = ?,
                    start_date = ?,
                    end_date = ?,
                    is_current = ?,
                    description = ?,
                    sort_order = ?,
                    updated_at = ?
                WHERE id = ? AND candidate_id = ?
                """;
        Timestamp now = Timestamp.from(Instant.now());
        boolean isCurrent = Boolean.TRUE.equals(request.getIsCurrent());
        int sortOrder = request.getSortOrder() != null ? request.getSortOrder() : 0;
        String description = (request.getDescription() != null && !request.getDescription().trim().isEmpty())
                ? request.getDescription().trim() : null;
        Date endDate = (!isCurrent && request.getEndDate() != null)
                ? Date.valueOf(request.getEndDate()) : null;

        return jdbcTemplate.update(
                sql,
                request.getCompanyName().trim(),
                request.getRoleTitle().trim(),
                Date.valueOf(request.getStartDate()),
                endDate,
                isCurrent,
                description,
                sortOrder,
                now,
                id,
                candidateId
        );
    }

    public int delete(Long id, Long candidateId) {
        if (id == null || candidateId == null) {
            return 0;
        }
        String sql = "DELETE FROM experience WHERE id = ? AND candidate_id = ?";
        return jdbcTemplate.update(sql, id, candidateId);
    }
}
