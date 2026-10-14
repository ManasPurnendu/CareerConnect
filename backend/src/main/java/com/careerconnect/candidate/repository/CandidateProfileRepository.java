package com.careerconnect.candidate.repository;

import com.careerconnect.candidate.dto.CandidateProfileResponse;
import com.careerconnect.candidate.dto.UpdateProfileRequest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class CandidateProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<CandidateProfileResponse> profileRowMapper = (rs, rowNum) -> new CandidateProfileResponse(
            rs.getLong("id"),
            rs.getString("full_name"),
            rs.getString("headline"),
            rs.getString("location"),
            rs.getString("summary")
    );

    public CandidateProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CandidateProfileResponse> findById(Long candidateId) {
        if (candidateId == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, full_name, headline, location, summary FROM candidates WHERE id = ?";
        try {
            CandidateProfileResponse profile = jdbcTemplate.queryForObject(sql, profileRowMapper, candidateId);
            return Optional.ofNullable(profile);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean existsById(Long candidateId) {
        if (candidateId == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM candidates WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, candidateId);
        return count != null && count > 0;
    }

    public boolean existsByIdWithFullName(Long candidateId) {
        if (candidateId == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM candidates WHERE id = ? AND full_name IS NOT NULL AND TRIM(full_name) <> ''";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, candidateId);
        return count != null && count > 0;
    }

    public int update(Long candidateId, UpdateProfileRequest request) {
        String sql = """
                UPDATE candidates
                SET full_name = ?,
                    headline = ?,
                    location = ?,
                    summary = ?,
                    updated_at = ?
                WHERE id = ?
                """;
        Timestamp now = Timestamp.from(Instant.now());
        String fullName = request.getFullName() != null ? request.getFullName().trim() : null;
        String headline = request.getHeadline() != null ? request.getHeadline().trim() : null;
        String location = request.getLocation() != null ? request.getLocation().trim() : null;
        String summary = request.getSummary() != null ? request.getSummary().trim() : null;

        return jdbcTemplate.update(sql, fullName, headline, location, summary, now, candidateId);
    }
}
