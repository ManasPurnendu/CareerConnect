package com.careerconnect.candidate.repository;

import com.careerconnect.candidate.dto.CandidateSkillDto;
import com.careerconnect.common.util.JdbcUtils;
import org.springframework.dao.DuplicateKeyException;
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
import java.util.List;
import java.util.Optional;

@Repository
public class CandidateSkillRepository {

    private final JdbcTemplate jdbcTemplate;

    public record SkillEntity(Long id, String name, String normalizedName) {}

    private final RowMapper<CandidateSkillDto> candidateSkillRowMapper = (rs, rowNum) -> new CandidateSkillDto(
            rs.getLong("id"),
            rs.getString("name")
    );

    private final RowMapper<SkillEntity> skillEntityRowMapper = (rs, rowNum) -> new SkillEntity(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("normalized_name")
    );

    public CandidateSkillRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByCandidateId(Long candidateId) {
        if (candidateId == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM candidate_skills WHERE candidate_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, candidateId);
        return count != null && count > 0;
    }

    public List<CandidateSkillDto> findByCandidateId(Long candidateId) {
        if (candidateId == null) {
            return List.of();
        }
        String sql = """
                SELECT s.id, s.name
                FROM candidate_skills cs
                JOIN skills s ON cs.skill_id = s.id
                WHERE cs.candidate_id = ?
                ORDER BY s.name ASC
                """;
        return jdbcTemplate.query(sql, candidateSkillRowMapper, candidateId);
    }

    public Optional<SkillEntity> findSkillByNormalizedName(String normalizedName) {
        if (normalizedName == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, name, normalized_name FROM skills WHERE normalized_name = ?";
        try {
            SkillEntity skill = jdbcTemplate.queryForObject(sql, skillEntityRowMapper, normalizedName);
            return Optional.ofNullable(skill);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Long createSkill(String name, String normalizedName) {
        String sql = "INSERT INTO skills (name, normalized_name, created_at) VALUES (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        Timestamp now = Timestamp.from(Instant.now());

        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, name);
                ps.setString(2, normalizedName);
                ps.setTimestamp(3, now);
                return ps;
            }, keyHolder);

            return JdbcUtils.extractGeneratedId(keyHolder);
        } catch (DuplicateKeyException e) {
            // Concurrent insert race condition: retrieve existing
            return findSkillByNormalizedName(normalizedName)
                    .map(SkillEntity::id)
                    .orElseThrow(() -> e);
        }
    }

    public boolean hasSkill(Long candidateId, Long skillId) {
        if (candidateId == null || skillId == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM candidate_skills WHERE candidate_id = ? AND skill_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, candidateId, skillId);
        return count != null && count > 0;
    }

    public void assignSkill(Long candidateId, Long skillId) {
        String sql = "INSERT INTO candidate_skills (candidate_id, skill_id, created_at) VALUES (?, ?, ?)";
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(sql, candidateId, skillId, now);
    }

    public int deleteSkill(Long candidateId, Long skillId) {
        if (candidateId == null || skillId == null) {
            return 0;
        }
        String sql = "DELETE FROM candidate_skills WHERE candidate_id = ? AND skill_id = ?";
        return jdbcTemplate.update(sql, candidateId, skillId);
    }
}
