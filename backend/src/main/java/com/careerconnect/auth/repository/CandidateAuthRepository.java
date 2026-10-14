package com.careerconnect.auth.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class CandidateAuthRepository {

    private final JdbcTemplate jdbcTemplate;

    public CandidateAuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CandidateAuthData(Long candidateId, Long userId, String fullName) {}

    public Long save(Long userId, String fullName) {
        String sql = "INSERT INTO candidates (user_id, full_name, created_at, updated_at) VALUES (?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        Timestamp now = Timestamp.from(Instant.now());

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, fullName.trim());
            ps.setTimestamp(3, now);
            ps.setTimestamp(4, now);
            return ps;
        }, keyHolder);

        return com.careerconnect.common.util.JdbcUtils.extractGeneratedId(keyHolder);
    }

    public Optional<CandidateAuthData> findByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, user_id, full_name FROM candidates WHERE user_id = ?";
        try {
            CandidateAuthData data = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new CandidateAuthData(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getString("full_name")
            ), userId);
            return Optional.ofNullable(data);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
