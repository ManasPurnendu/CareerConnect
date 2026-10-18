package com.careerconnect.admin.repository;

import com.careerconnect.jobs.model.JobStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * Pure JdbcTemplate repository providing narrowly scoped administrative moderation mutations.
 * Enforces expected-current-state WHERE predicates to prevent accidental or concurrent state corruption.
 */
@Repository
public class AdminMutationRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminMutationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // -------------------------------------------------------------
    // User Moderation Mutations
    // -------------------------------------------------------------

    public Optional<UserStatusRecord> findUserStatusById(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, is_active FROM users WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
                    new UserStatusRecord(rs.getLong("id"), rs.getBoolean("is_active")), userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int updateUserActiveStatus(Long userId, boolean isActive) {
        String sql = "UPDATE users SET is_active = ?, updated_at = ? WHERE id = ?";
        return jdbcTemplate.update(sql, isActive, Timestamp.from(Instant.now()), userId);
    }

    // -------------------------------------------------------------
    // Company Moderation Mutations
    // -------------------------------------------------------------

    public Optional<CompanyStatusRecord> findCompanyStatusById(Long companyId) {
        if (companyId == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, is_active FROM companies WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
                    new CompanyStatusRecord(rs.getLong("id"), rs.getBoolean("is_active")), companyId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int updateCompanyActiveStatus(Long companyId, boolean isActive) {
        String sql = "UPDATE companies SET is_active = ?, updated_at = ? WHERE id = ?";
        return jdbcTemplate.update(sql, isActive, Timestamp.from(Instant.now()), companyId);
    }

    // -------------------------------------------------------------
    // Job Moderation Mutations
    // -------------------------------------------------------------

    public Optional<JobStatusRecord> findJobStatusById(Long jobId) {
        if (jobId == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, status FROM jobs WHERE id = ? AND deleted_at IS NULL";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
                    new JobStatusRecord(rs.getLong("id"), JobStatus.valueOf(rs.getString("status"))), jobId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int archiveJob(Long jobId) {
        // Only DRAFT, ACTIVE, or CLOSED jobs can be archived; ARCHIVED or deleted jobs cannot
        String sql = "UPDATE jobs SET status = 'ARCHIVED', updated_at = ? " +
                "WHERE id = ? AND status IN ('DRAFT', 'ACTIVE', 'CLOSED') AND deleted_at IS NULL";
        return jdbcTemplate.update(sql, Timestamp.from(Instant.now()), jobId);
    }

    public int restoreJob(Long jobId) {
        // CC-MASTER-001 specification: only ARCHIVED jobs can be restored, and they restore strictly to CLOSED
        String sql = "UPDATE jobs SET status = 'CLOSED', updated_at = ? " +
                "WHERE id = ? AND status = 'ARCHIVED' AND deleted_at IS NULL";
        return jdbcTemplate.update(sql, Timestamp.from(Instant.now()), jobId);
    }

    // Records for lightweight status checks
    public record UserStatusRecord(Long id, boolean active) {}
    public record CompanyStatusRecord(Long id, boolean active) {}
    public record JobStatusRecord(Long id, JobStatus status) {}
}
