package com.careerconnect.admin.audit.service;

import com.careerconnect.admin.audit.dto.AuditLogDto;
import com.careerconnect.admin.audit.model.AuditLog;
import com.careerconnect.admin.audit.repository.AuditLogRepository;
import com.careerconnect.common.exception.ValidationException;
import com.careerconnect.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careerconnect.common.pagination.PageRequestDto;
import com.careerconnect.common.pagination.PageResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service providing server-authoritative audit logging for administrative mutations.
 * Enforces authenticated ADMIN actor identity via SecurityUtils and guarantees append-only semantics.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Records an audit event using the server-authoritative authenticated ADMIN identity.
     * Metadata is serialized to JSON from a Map.
     *
     * @param action     Audit action identifier
     * @param entityType Target entity type
     * @param entityId   Optional target entity ID
     * @param metadata   Key-value pairs to store as JSON
     * @return Generated audit log primary key ID
     */
    @Transactional
    public Long recordEvent(String action, String entityType, Long entityId, Map<String, Object> metadata) {
        String metadataJson = null;
        if (metadata != null && !metadata.isEmpty()) {
            try {
                metadataJson = objectMapper.writeValueAsString(metadata);
            } catch (JsonProcessingException e) {
                throw new ValidationException("Failed to serialize audit metadata: " + e.getMessage());
            }
        }
        return recordEvent(action, entityType, entityId, metadataJson);
    }

    /**
     * Records an audit event using the server-authoritative authenticated ADMIN identity.
     * Accepts a pre-formatted JSON metadata string or null.
     *
     * @param action       Audit action identifier
     * @param entityType   Target entity type
     * @param entityId     Optional target entity ID
     * @param metadataJson JSON string or null
     * @return Generated audit log primary key ID
     */
    @Transactional
    public Long recordEvent(String action, String entityType, Long entityId, String metadataJson) {
        // Enforce server-authoritative identity: actor must be authenticated ADMIN
        Long adminUserId = SecurityUtils.getRequiredAdminUserId();

        validateEvent(action, entityType, metadataJson);

        Instant now = Instant.now();
        Long auditId = auditLogRepository.insert(adminUserId, action.trim(), entityType.trim(), entityId, metadataJson, now);
        log.info("Recorded audit log [ID: {}]: actor={}, action={}, entityType={}, entityId={}",
                auditId, adminUserId, action.trim(), entityType.trim(), entityId);
        return auditId;
    }

    /**
     * Convenience method to record an audit event without metadata.
     *
     * @param action     Audit action identifier
     * @param entityType Target entity type
     * @param entityId   Optional target entity ID
     * @return Generated audit log primary key ID
     */
    @Transactional
    public Long recordEvent(String action, String entityType, Long entityId) {
        return recordEvent(action, entityType, entityId, (String) null);
    }

    /**
     * Retrieves an audit log by primary key.
     *
     * @param id Audit log ID
     * @return Optional containing the AuditLogDto if found
     */
    @Transactional(readOnly = true)
    public Optional<AuditLogDto> findById(Long id) {
        return auditLogRepository.findById(id).map(this::toDto);
    }

    /**
     * Lists audit logs with optional filtering and deterministic pagination.
     */
    @Transactional(readOnly = true)
    public PageResult<AuditLogDto> getAuditLogs(Long actorUserId, String action, String entityType, Long entityId, PageRequestDto pageRequest) {
        PageRequestDto pr = pageRequest != null ? pageRequest : new PageRequestDto();
        int limit = pr.getSize();
        int offset = pr.getOffset();

        List<AuditLogDto> items = auditLogRepository
                .findAuditLogs(actorUserId, action, entityType, entityId, limit, offset)
                .stream()
                .map(this::toDto)
                .toList();

        long total = auditLogRepository.countAuditLogs(actorUserId, action, entityType, entityId);
        return PageResult.of(items, pr.getPage(), pr.getSize(), total);
    }

    private void validateEvent(String action, String entityType, String metadataJson) {
        if (action == null || action.isBlank()) {
            throw new ValidationException("Audit action cannot be blank.");
        }
        if (action.trim().length() > 100) {
            throw new ValidationException("Audit action cannot exceed 100 characters.");
        }
        if (entityType == null || entityType.isBlank()) {
            throw new ValidationException("Audit entity type cannot be blank.");
        }
        if (entityType.trim().length() > 50) {
            throw new ValidationException("Audit entity type cannot exceed 50 characters.");
        }
        if (metadataJson != null && !metadataJson.isBlank()) {
            try {
                objectMapper.readTree(metadataJson);
            } catch (JsonProcessingException e) {
                throw new ValidationException("Invalid JSON format for audit metadata.");
            }
        }
    }

    private AuditLogDto toDto(AuditLog auditLog) {
        return new AuditLogDto(
                auditLog.getId(),
                auditLog.getActorUserId(),
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getMetadata(),
                auditLog.getCreatedAt()
        );
    }
}
