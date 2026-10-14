package com.careerconnect.candidate.repository;

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
import java.time.Instant;
import java.util.Optional;

@Repository
public class CandidateResumeRepository {

    private final JdbcTemplate jdbcTemplate;

    public record ResumeEntity(
            Long id,
            Long candidateId,
            String originalFilename,
            String storageKey,
            String contentType,
            long sizeBytes,
            boolean isCurrent,
            Instant createdAt,
            Instant updatedAt
    ) {}

    private final RowMapper<ResumeEntity> resumeRowMapper = (rs, rowNum) -> {
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        return new ResumeEntity(
                rs.getLong("id"),
                rs.getLong("candidate_id"),
                rs.getString("original_filename"),
                rs.getString("storage_key"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                rs.getBoolean("is_current"),
                createdTs != null ? createdTs.toInstant() : null,
                updatedTs != null ? updatedTs.toInstant() : null
        );
    };

    public CandidateResumeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ResumeEntity> findCurrentByCandidateId(Long candidateId) {
        if (candidateId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT id, candidate_id, original_filename, storage_key, content_type,
                       size_bytes, is_current, created_at, updated_at
                FROM resumes
                WHERE candidate_id = ? AND is_current = TRUE
                ORDER BY id DESC
                LIMIT 1
                """;
        try {
            ResumeEntity resume = jdbcTemplate.queryForObject(sql, resumeRowMapper, candidateId);
            return Optional.ofNullable(resume);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<ResumeEntity> findByIdAndCandidateId(Long id, Long candidateId) {
        if (id == null || candidateId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT id, candidate_id, original_filename, storage_key, content_type,
                       size_bytes, is_current, created_at, updated_at
                FROM resumes
                WHERE id = ? AND candidate_id = ?
                """;
        try {
            ResumeEntity resume = jdbcTemplate.queryForObject(sql, resumeRowMapper, id, candidateId);
            return Optional.ofNullable(resume);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Long insert(Long candidateId, String originalFilename, String storageKey, String contentType, long sizeBytes) {
        String sql = """
                INSERT INTO resumes (
                    candidate_id, original_filename, storage_key, content_type,
                    size_bytes, is_current, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, TRUE, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        Timestamp now = Timestamp.from(Instant.now());

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, candidateId);
            ps.setString(2, originalFilename);
            ps.setString(3, storageKey);
            ps.setString(4, contentType);
            ps.setLong(5, sizeBytes);
            ps.setTimestamp(6, now);
            ps.setTimestamp(7, now);
            return ps;
        }, keyHolder);

        return JdbcUtils.extractGeneratedId(keyHolder);
    }

    public int markPreviousAsNonCurrent(Long candidateId) {
        if (candidateId == null) {
            return 0;
        }
        String sql = "UPDATE resumes SET is_current = FALSE, updated_at = ? WHERE candidate_id = ? AND is_current = TRUE";
        Timestamp now = Timestamp.from(Instant.now());
        return jdbcTemplate.update(sql, now, candidateId);
    }

    public int deleteCurrentByCandidateId(Long candidateId) {
        if (candidateId == null) {
            return 0;
        }
        String sql = "DELETE FROM resumes WHERE candidate_id = ? AND is_current = TRUE";
        return jdbcTemplate.update(sql, candidateId);
    }

    public int deleteByIdAndCandidateId(Long id, Long candidateId) {
        if (id == null || candidateId == null) {
            return 0;
        }
        String sql = "DELETE FROM resumes WHERE id = ? AND candidate_id = ?";
        return jdbcTemplate.update(sql, id, candidateId);
    }
}
