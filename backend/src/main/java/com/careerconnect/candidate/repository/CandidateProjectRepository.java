package com.careerconnect.candidate.repository;

import com.careerconnect.candidate.dto.CreateProjectRequest;
import com.careerconnect.candidate.dto.ProjectDto;
import com.careerconnect.candidate.dto.UpdateProjectRequest;
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
public class CandidateProjectRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ProjectDto> projectRowMapper = (rs, rowNum) -> {
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        return new ProjectDto(
                rs.getLong("id"),
                rs.getLong("candidate_id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("project_url"),
                rs.getInt("sort_order"),
                createdTs != null ? createdTs.toInstant() : null,
                updatedTs != null ? updatedTs.toInstant() : null
        );
    };

    public CandidateProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long insert(Long candidateId, CreateProjectRequest request) {
        String sql = """
                INSERT INTO projects (
                    candidate_id, title, description, project_url, sort_order, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        Timestamp now = Timestamp.from(Instant.now());
        int sortOrder = request.getSortOrder() != null ? request.getSortOrder() : 0;

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, candidateId);
            ps.setString(2, request.getTitle().trim());
            ps.setString(3, request.getDescription().trim());
            if (request.getProjectUrl() != null && !request.getProjectUrl().trim().isEmpty()) {
                ps.setString(4, request.getProjectUrl().trim());
            } else {
                ps.setNull(4, Types.VARCHAR);
            }
            ps.setInt(5, sortOrder);
            ps.setTimestamp(6, now);
            ps.setTimestamp(7, now);
            return ps;
        }, keyHolder);

        return JdbcUtils.extractGeneratedId(keyHolder);
    }

    public Optional<ProjectDto> findByIdAndCandidateId(Long id, Long candidateId) {
        if (id == null || candidateId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT id, candidate_id, title, description, project_url, sort_order, created_at, updated_at
                FROM projects
                WHERE id = ? AND candidate_id = ?
                """;
        try {
            ProjectDto project = jdbcTemplate.queryForObject(sql, projectRowMapper, id, candidateId);
            return Optional.ofNullable(project);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<ProjectDto> findByCandidateId(Long candidateId) {
        if (candidateId == null) {
            return List.of();
        }
        String sql = """
                SELECT id, candidate_id, title, description, project_url, sort_order, created_at, updated_at
                FROM projects
                WHERE candidate_id = ?
                ORDER BY sort_order ASC, id ASC
                """;
        return jdbcTemplate.query(sql, projectRowMapper, candidateId);
    }

    public int update(Long id, Long candidateId, UpdateProjectRequest request) {
        String sql = """
                UPDATE projects
                SET title = ?,
                    description = ?,
                    project_url = ?,
                    sort_order = ?,
                    updated_at = ?
                WHERE id = ? AND candidate_id = ?
                """;
        Timestamp now = Timestamp.from(Instant.now());
        int sortOrder = request.getSortOrder() != null ? request.getSortOrder() : 0;
        String projectUrl = (request.getProjectUrl() != null && !request.getProjectUrl().trim().isEmpty())
                ? request.getProjectUrl().trim() : null;

        return jdbcTemplate.update(
                sql,
                request.getTitle().trim(),
                request.getDescription().trim(),
                projectUrl,
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
        String sql = "DELETE FROM projects WHERE id = ? AND candidate_id = ?";
        return jdbcTemplate.update(sql, id, candidateId);
    }
}
