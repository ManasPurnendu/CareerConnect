package com.careerconnect.auth.repository;

import com.careerconnect.auth.model.User;
import com.careerconnect.common.model.Role;
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
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<User> userRowMapper = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("password_hash"),
            Role.valueOf(rs.getString("role")),
            rs.getBoolean("is_active"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null
    );

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long save(User user) {
        String sql = "INSERT INTO users (email, password_hash, role, is_active, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        Instant now = Instant.now();
        Timestamp nowTs = Timestamp.from(now);

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, user.getEmail().trim().toLowerCase());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getRole().name());
            ps.setBoolean(4, user.isActive());
            ps.setTimestamp(5, nowTs);
            ps.setTimestamp(6, nowTs);
            return ps;
        }, keyHolder);

        Long generatedId = com.careerconnect.common.util.JdbcUtils.extractGeneratedId(keyHolder);
        user.setId(generatedId);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return generatedId;
    }

    public Optional<User> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, email, password_hash, role, is_active, created_at, updated_at " +
                "FROM users WHERE LOWER(TRIM(email)) = LOWER(TRIM(?))";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, email);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<User> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, email, password_hash, role, is_active, created_at, updated_at " +
                "FROM users WHERE id = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, id);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean existsByEmail(String email) {
        if (email == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM users WHERE LOWER(TRIM(email)) = LOWER(TRIM(?))";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    }

    public void updateActiveStatus(Long id, boolean isActive) {
        String sql = "UPDATE users SET is_active = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, isActive, Timestamp.from(Instant.now()), id);
    }
}
