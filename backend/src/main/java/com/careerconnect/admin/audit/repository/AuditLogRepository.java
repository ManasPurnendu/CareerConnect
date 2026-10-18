package com.careerconnect.admin.audit.repository;

import com.careerconnect.admin.audit.model.AuditLog;
import com.careerconnect.common.util.DateTimeUtils;
import com.careerconnect.common.util.JdbcUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for immutable audit log persistence using Spring JdbcTemplate.
 * Audit logs are strictly append-only: no update or delete operations are supported.
 */
@Repository
public class AuditLogRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private final RowMapper<AuditLog> rowMapper = (rs, rowNum) -> new AuditLog(
            rs.getLong("id"),
            rs.getLong("actor_user_id"),
            rs.getString("action"),
            rs.getString("entity_type"),
            rs.getObject("entity_id") != null ? rs.getLong("entity_id") : null,
            normalizeJson(rs.getString("metadata")),
            DateTimeUtils.toInstant(rs.getTimestamp("created_at"))
    );

    @org.springframework.beans.factory.annotation.Autowired
    public AuditLogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    private String normalizeJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            if (node.isTextual()) {
                return node.asText();
            }
            return rawJson;
        } catch (Exception e) {
            return rawJson;
        }
    }

    /**
     * Appends a new immutable audit log event.
     * Uses parameterized SQL to guarantee safe execution.
     *
     * @param actorUserId  Authenticated admin user ID performing the action
     * @param action       Action identifier (e.g. USER_DEACTIVATED, JOB_ARCHIVED)
     * @param entityType   Target entity type (e.g. USER, COMPANY, JOB)
     * @param entityId     Optional target entity primary key
     * @param metadataJson Optional JSON metadata string
     * @param createdAt    Creation timestamp (defaults to Instant.now() if null)
     * @return Generated primary key ID of the audit log record
     */
    public Long insert(
            Long actorUserId,
            String action,
            String entityType,
            Long entityId,
            String metadataJson,
            Instant createdAt
    ) {
        String sql = """
                INSERT INTO audit_logs (actor_user_id, action, entity_type, entity_id, metadata, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        Timestamp timestamp = Timestamp.from(createdAt != null ? createdAt : Instant.now());

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, actorUserId);
            ps.setString(2, action);
            ps.setString(3, entityType);
            if (entityId != null) {
                ps.setLong(4, entityId);
            } else {
                ps.setNull(4, Types.BIGINT);
            }
            if (metadataJson != null && !metadataJson.isBlank()) {
                ps.setString(5, metadataJson);
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            ps.setTimestamp(6, timestamp);
            return ps;
        }, keyHolder);

        return JdbcUtils.extractGeneratedId(keyHolder);
    }

    /**
     * Retrieves an audit log by primary key.
     *
     * @param id Audit log ID
     * @return Optional containing the AuditLog if found
     */
    public Optional<AuditLog> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT id, actor_user_id, action, entity_type, entity_id, metadata, created_at
                FROM audit_logs
                WHERE id = ?
                """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Lists audit logs with optional filters and deterministic pagination (created_at DESC, id DESC).
     */
    public List<AuditLog> findAuditLogs(Long actorUserId, String action, String entityType, Long entityId, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, actor_user_id, action, entity_type, entity_id, metadata, created_at
                FROM audit_logs
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();

        if (actorUserId != null) {
            sql.append(" AND actor_user_id = ?");
            params.add(actorUserId);
        }
        if (action != null && !action.isBlank()) {
            sql.append(" AND action = ?");
            params.add(action.trim());
        }
        if (entityType != null && !entityType.isBlank()) {
            sql.append(" AND entity_type = ?");
            params.add(entityType.trim());
        }
        if (entityId != null) {
            sql.append(" AND entity_id = ?");
            params.add(entityId);
        }

        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    /**
     * Counts audit logs matching optional filters.
     */
    public long countAuditLogs(Long actorUserId, String action, String entityType, Long entityId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM audit_logs WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (actorUserId != null) {
            sql.append(" AND actor_user_id = ?");
            params.add(actorUserId);
        }
        if (action != null && !action.isBlank()) {
            sql.append(" AND action = ?");
            params.add(action.trim());
        }
        if (entityType != null && !entityType.isBlank()) {
            sql.append(" AND entity_type = ?");
            params.add(entityType.trim());
        }
        if (entityId != null) {
            sql.append(" AND entity_id = ?");
            params.add(entityId);
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }
}
