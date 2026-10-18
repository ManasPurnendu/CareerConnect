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
public class RecruiterAuthRepository {

    private final JdbcTemplate jdbcTemplate;

    public RecruiterAuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record RecruiterAuthData(Long recruiterId, Long userId, Long companyId, String fullName) {}

    public Long save(Long userId, String fullName) {
        String sql = "INSERT INTO recruiters (user_id, company_id, full_name, created_at, updated_at) " +
                "VALUES (?, NULL, ?, ?, ?)";
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

    public Optional<RecruiterAuthData> findByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, user_id, company_id, full_name FROM recruiters WHERE user_id = ?";
        try {
            RecruiterAuthData data = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new RecruiterAuthData(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getObject("company_id") != null ? rs.getLong("company_id") : null,
                    rs.getString("full_name")
            ), userId);
            return Optional.ofNullable(data);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Long> findCompanyIdByRecruiterId(Long recruiterId) {
        if (recruiterId == null) {
            return Optional.empty();
        }
        String sql = "SELECT company_id FROM recruiters WHERE id = ?";
        try {
            Long companyId = jdbcTemplate.queryForObject(sql, Long.class, recruiterId);
            return Optional.ofNullable(companyId);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean updateCompanyId(Long recruiterId, Long companyId) {
        if (recruiterId == null) {
            return false;
        }
        String sql = "UPDATE recruiters SET company_id = ?, updated_at = ? WHERE id = ?";
        Timestamp now = Timestamp.from(Instant.now());
        int rows = jdbcTemplate.update(sql, companyId, now, recruiterId);
        return rows > 0;
    }

    public boolean existsById(Long recruiterId) {
        if (recruiterId == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM recruiters WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, recruiterId);
        return count != null && count > 0;
    }
}
