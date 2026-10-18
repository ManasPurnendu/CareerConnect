package com.careerconnect.notification.repository;

import com.careerconnect.common.util.DateTimeUtils;
import com.careerconnect.common.util.JdbcUtils;
import com.careerconnect.notification.dto.NotificationDto;
import com.careerconnect.notification.model.NotificationType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class NotificationRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<NotificationDto> rowMapper = (rs, rowNum) -> new NotificationDto(
            rs.getLong("id"),
            NotificationType.valueOf(rs.getString("type")),
            rs.getString("title"),
            rs.getString("body"),
            rs.getString("resource_type"),
            rs.getObject("resource_id") != null ? rs.getLong("resource_id") : null,
            DateTimeUtils.toInstant(rs.getTimestamp("read_at")),
            DateTimeUtils.toInstant(rs.getTimestamp("created_at"))
    );

    public NotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts a new in-app notification.
     */
    public Long insert(
            Long userId,
            NotificationType type,
            String title,
            String body,
            String resourceType,
            Long resourceId,
            Instant createdAt
    ) {
        String sql = """
                INSERT INTO notifications (user_id, type, title, body, resource_type, resource_id, read_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, NULL, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        Timestamp timestamp = Timestamp.from(createdAt != null ? createdAt : Instant.now());

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, type.name());
            ps.setString(3, title);
            ps.setString(4, body);
            if (resourceType != null) {
                ps.setString(5, resourceType);
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            if (resourceId != null) {
                ps.setLong(6, resourceId);
            } else {
                ps.setNull(6, Types.BIGINT);
            }
            ps.setTimestamp(7, timestamp);
            return ps;
        }, keyHolder);

        return JdbcUtils.extractGeneratedId(keyHolder);
    }

    /**
     * Finds a single notification scoped strictly to the authenticated user.
     */
    public Optional<NotificationDto> findByIdAndUserId(Long id, Long userId) {
        if (id == null || userId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT id, user_id, type, title, body, resource_type, resource_id, read_at, created_at
                FROM notifications
                WHERE id = ? AND user_id = ?
                """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, id, userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Lists notifications for an authenticated user with pagination and optional unread filter.
     */
    public List<NotificationDto> findByUserId(Long userId, Boolean unreadOnly, int limit, int offset) {
        if (userId == null) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT id, user_id, type, title, body, resource_type, resource_id, read_at, created_at
                FROM notifications
                WHERE user_id = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (Boolean.TRUE.equals(unreadOnly)) {
            sql.append(" AND read_at IS NULL");
        }

        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    /**
     * Counts notifications for an authenticated user with optional unread filter.
     */
    public long countByUserId(Long userId, Boolean unreadOnly) {
        if (userId == null) {
            return 0L;
        }
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM notifications WHERE user_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (Boolean.TRUE.equals(unreadOnly)) {
            sql.append(" AND read_at IS NULL");
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    /**
     * Counts all unread notifications for an authenticated user.
     */
    public long countUnreadByUserId(Long userId) {
        return countByUserId(userId, true);
    }

    /**
     * Marks a specific notification as read if it belongs to the user and is unread.
     */
    public int markAsRead(Long id, Long userId, Instant readAt) {
        if (id == null || userId == null) {
            return 0;
        }
        String sql = """
                UPDATE notifications
                SET read_at = ?
                WHERE id = ? AND user_id = ? AND read_at IS NULL
                """;
        Timestamp timestamp = Timestamp.from(readAt != null ? readAt : Instant.now());
        return jdbcTemplate.update(sql, timestamp, id, userId);
    }

    /**
     * Marks all unread notifications as read for an authenticated user.
     */
    public int markAllAsRead(Long userId, Instant readAt) {
        if (userId == null) {
            return 0;
        }
        String sql = """
                UPDATE notifications
                SET read_at = ?
                WHERE user_id = ? AND read_at IS NULL
                """;
        Timestamp timestamp = Timestamp.from(readAt != null ? readAt : Instant.now());
        return jdbcTemplate.update(sql, timestamp, userId);
    }

    /**
     * Finds distinct candidate user IDs who applied to the specified job and have not withdrawn.
     */
    public List<Long> findCandidateUserIdsByJobId(Long jobId) {
        if (jobId == null) {
            return List.of();
        }
        String sql = """
                SELECT DISTINCT c.user_id
                FROM applications a
                JOIN candidates c ON a.candidate_id = c.id
                WHERE a.job_id = ? AND a.status != 'WITHDRAWN'
                """;
        return jdbcTemplate.queryForList(sql, Long.class, jobId);
    }
}
