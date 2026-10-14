package com.careerconnect.candidate.repository;

import com.careerconnect.candidate.dto.CreateEducationRequest;
import com.careerconnect.candidate.dto.EducationDto;
import com.careerconnect.candidate.dto.UpdateEducationRequest;
import com.careerconnect.common.util.JdbcUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class CandidateEducationRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<EducationDto> educationRowMapper = (rs, rowNum) -> {
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        return new EducationDto(
                rs.getLong("id"),
                rs.getLong("candidate_id"),
                rs.getString("institution"),
                rs.getString("degree"),
                rs.getString("field_of_study"),
                rs.getInt("start_year"),
                rs.getObject("end_year") != null ? rs.getInt("end_year") : null,
                rs.getString("grade"),
                rs.getInt("sort_order"),
                createdTs != null ? createdTs.toInstant() : null,
                updatedTs != null ? updatedTs.toInstant() : null
        );
    };

    public CandidateEducationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByCandidateId(Long candidateId) {
        if (candidateId == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM education WHERE candidate_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, candidateId);
        return count != null && count > 0;
    }

    public Long insert(Long candidateId, CreateEducationRequest request) {
        String sql = """
                INSERT INTO education (
                    candidate_id, institution, degree, field_of_study,
                    start_year, end_year, grade, sort_order, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        Timestamp now = Timestamp.from(Instant.now());
        int sortOrder = request.getSortOrder() != null ? request.getSortOrder() : 0;

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, candidateId);
            ps.setString(2, request.getInstitution().trim());
            ps.setString(3, request.getDegree().trim());
            ps.setString(4, request.getFieldOfStudy().trim());
            ps.setInt(5, request.getStartYear());
            if (request.getEndYear() != null) {
                ps.setInt(6, request.getEndYear());
            } else {
                ps.setNull(6, Types.INTEGER);
            }
            if (request.getGrade() != null) {
                ps.setString(7, request.getGrade().trim());
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

    public Optional<EducationDto> findByIdAndCandidateId(Long id, Long candidateId) {
        if (id == null || candidateId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT id, candidate_id, institution, degree, field_of_study,
                       start_year, end_year, grade, sort_order, created_at, updated_at
                FROM education
                WHERE id = ? AND candidate_id = ?
                """;
        try {
            EducationDto education = jdbcTemplate.queryForObject(sql, educationRowMapper, id, candidateId);
            return Optional.ofNullable(education);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<EducationDto> findByCandidateId(Long candidateId) {
        if (candidateId == null) {
            return List.of();
        }
        String sql = """
                SELECT id, candidate_id, institution, degree, field_of_study,
                       start_year, end_year, grade, sort_order, created_at, updated_at
                FROM education
                WHERE candidate_id = ?
                ORDER BY sort_order ASC, start_year DESC, id ASC
                """;
        return jdbcTemplate.query(sql, educationRowMapper, candidateId);
    }

    public int update(Long id, Long candidateId, UpdateEducationRequest request) {
        String sql = """
                UPDATE education
                SET institution = ?,
                    degree = ?,
                    field_of_study = ?,
                    start_year = ?,
                    end_year = ?,
                    grade = ?,
                    sort_order = ?,
                    updated_at = ?
                WHERE id = ? AND candidate_id = ?
                """;
        Timestamp now = Timestamp.from(Instant.now());
        int sortOrder = request.getSortOrder() != null ? request.getSortOrder() : 0;
        String grade = request.getGrade() != null ? request.getGrade().trim() : null;

        return jdbcTemplate.update(
                sql,
                request.getInstitution().trim(),
                request.getDegree().trim(),
                request.getFieldOfStudy().trim(),
                request.getStartYear(),
                request.getEndYear(),
                grade,
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
        String sql = "DELETE FROM education WHERE id = ? AND candidate_id = ?";
        return jdbcTemplate.update(sql, id, candidateId);
    }
}
