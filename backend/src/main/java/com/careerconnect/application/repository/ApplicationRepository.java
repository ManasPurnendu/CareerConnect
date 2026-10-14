package com.careerconnect.application.repository;

import com.careerconnect.application.domain.ApplicationStatus;
import com.careerconnect.common.util.DateTimeUtils;
import com.careerconnect.common.util.JdbcUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class ApplicationRepository {

    private final JdbcTemplate jdbcTemplate;

    public ApplicationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record JobEligibility(
            Long id,
            String status,
            LocalDate applicationDeadline,
            Instant deletedAt
    ) {}

    public record ApplicationEntity(
            Long id,
            Long jobId,
            Long candidateId,
            String coverLetter,
            ApplicationStatus status,
            String candidateSnapshot,
            String resumeSnapshot,
            Instant appliedAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public Optional<JobEligibility> findJobEligibility(Long jobId) {
        if (jobId == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, status, application_deadline, deleted_at FROM jobs WHERE id = ?";
        try {
            JobEligibility eligibility = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Date deadlineDate = rs.getDate("application_deadline");
                Timestamp deletedTs = rs.getTimestamp("deleted_at");
                return new JobEligibility(
                        rs.getLong("id"),
                        rs.getString("status"),
                        deadlineDate != null ? deadlineDate.toLocalDate() : null,
                        DateTimeUtils.toInstant(deletedTs)
                );
            }, jobId);
            return Optional.ofNullable(eligibility);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean existsByCandidateIdAndJobId(Long candidateId, Long jobId) {
        if (candidateId == null || jobId == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM applications WHERE candidate_id = ? AND job_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, candidateId, jobId);
        return count != null && count > 0;
    }

    public Long insertApplication(
            Long jobId,
            Long candidateId,
            String coverLetter,
            ApplicationStatus status,
            String candidateSnapshotJson,
            String resumeSnapshotJson,
            Instant appliedAt
    ) {
        String sql = """
                INSERT INTO applications (
                    job_id, candidate_id, cover_letter, status,
                    candidate_snapshot, resume_snapshot,
                    applied_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        Timestamp timestamp = Timestamp.from(appliedAt != null ? appliedAt : Instant.now());

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, jobId);
            ps.setLong(2, candidateId);
            if (coverLetter != null && !coverLetter.trim().isEmpty()) {
                ps.setString(3, coverLetter.trim());
            } else {
                ps.setNull(3, Types.VARCHAR);
            }
            ps.setString(4, status.name());
            ps.setString(5, candidateSnapshotJson);
            ps.setString(6, resumeSnapshotJson);
            ps.setTimestamp(7, timestamp);
            ps.setTimestamp(8, timestamp);
            ps.setTimestamp(9, timestamp);
            return ps;
        }, keyHolder);

        return JdbcUtils.extractGeneratedId(keyHolder);
    }

    public Long insertStatusHistory(
            Long applicationId,
            ApplicationStatus fromStatus,
            ApplicationStatus toStatus,
            Long changedByUserId,
            String note,
            Instant createdAt
    ) {
        String sql = """
                INSERT INTO application_status_history (
                    application_id, from_status, to_status, changed_by_user_id, note, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        Timestamp timestamp = Timestamp.from(createdAt != null ? createdAt : Instant.now());

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, applicationId);
            if (fromStatus != null) {
                ps.setString(2, fromStatus.name());
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            ps.setString(3, toStatus.name());
            ps.setLong(4, changedByUserId);
            if (note != null && !note.trim().isEmpty()) {
                ps.setString(5, note.trim());
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            ps.setTimestamp(6, timestamp);
            return ps;
        }, keyHolder);

        return JdbcUtils.extractGeneratedId(keyHolder);
    }

    public Optional<ApplicationEntity> findById(Long applicationId) {
        if (applicationId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT id, job_id, candidate_id, cover_letter, status,
                       candidate_snapshot, resume_snapshot,
                       applied_at, created_at, updated_at
                FROM applications
                WHERE id = ?
                """;
        try {
            ApplicationEntity app = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new ApplicationEntity(
                    rs.getLong("id"),
                    rs.getLong("job_id"),
                    rs.getLong("candidate_id"),
                    rs.getString("cover_letter"),
                    ApplicationStatus.valueOf(rs.getString("status")),
                    rs.getString("candidate_snapshot"),
                    rs.getString("resume_snapshot"),
                    DateTimeUtils.toInstant(rs.getTimestamp("applied_at")),
                    DateTimeUtils.toInstant(rs.getTimestamp("created_at")),
                    DateTimeUtils.toInstant(rs.getTimestamp("updated_at"))
            ), applicationId);
            return Optional.ofNullable(app);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<ApplicationEntity> findByIdAndCandidateId(Long applicationId, Long candidateId) {
        if (applicationId == null || candidateId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT id, job_id, candidate_id, cover_letter, status,
                       candidate_snapshot, resume_snapshot,
                       applied_at, created_at, updated_at
                FROM applications
                WHERE id = ? AND candidate_id = ?
                """;
        try {
            ApplicationEntity app = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new ApplicationEntity(
                    rs.getLong("id"),
                    rs.getLong("job_id"),
                    rs.getLong("candidate_id"),
                    rs.getString("cover_letter"),
                    ApplicationStatus.valueOf(rs.getString("status")),
                    rs.getString("candidate_snapshot"),
                    rs.getString("resume_snapshot"),
                    DateTimeUtils.toInstant(rs.getTimestamp("applied_at")),
                    DateTimeUtils.toInstant(rs.getTimestamp("created_at")),
                    DateTimeUtils.toInstant(rs.getTimestamp("updated_at"))
            ), applicationId, candidateId);
            return Optional.ofNullable(app);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int updateStatus(Long applicationId, ApplicationStatus newStatus, Instant updatedAt) {
        if (applicationId == null || newStatus == null) {
            return 0;
        }
        String sql = "UPDATE applications SET status = ?, updated_at = ? WHERE id = ?";
        Timestamp timestamp = Timestamp.from(updatedAt != null ? updatedAt : Instant.now());
        return jdbcTemplate.update(sql, newStatus.name(), timestamp, applicationId);
    }

    public int updateStatusForCandidate(Long applicationId, Long candidateId, ApplicationStatus newStatus, Instant updatedAt) {
        if (applicationId == null || candidateId == null || newStatus == null) {
            return 0;
        }
        String sql = "UPDATE applications SET status = ?, updated_at = ? WHERE id = ? AND candidate_id = ?";
        Timestamp timestamp = Timestamp.from(updatedAt != null ? updatedAt : Instant.now());
        return jdbcTemplate.update(sql, newStatus.name(), timestamp, applicationId, candidateId);
    }

    public record ApplicationStatusHistoryEntity(
            Long id,
            Long applicationId,
            ApplicationStatus fromStatus,
            ApplicationStatus toStatus,
            Long changedByUserId,
            String note,
            Instant createdAt
    ) {}

    public java.util.List<ApplicationStatusHistoryEntity> findStatusHistoryByApplicationId(Long applicationId) {
        if (applicationId == null) {
            return java.util.List.of();
        }
        String sql = """
                SELECT id, application_id, from_status, to_status, changed_by_user_id, note, created_at
                FROM application_status_history
                WHERE application_id = ?
                ORDER BY created_at ASC, id ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ApplicationStatusHistoryEntity(
                rs.getLong("id"),
                rs.getLong("application_id"),
                rs.getString("from_status") != null ? ApplicationStatus.valueOf(rs.getString("from_status")) : null,
                ApplicationStatus.valueOf(rs.getString("to_status")),
                rs.getLong("changed_by_user_id"),
                rs.getString("note"),
                DateTimeUtils.toInstant(rs.getTimestamp("created_at"))
        ), applicationId);
    }

    public record ApplicationWithJobEntity(
            Long id,
            Long jobId,
            Long candidateId,
            String coverLetter,
            ApplicationStatus status,
            String candidateSnapshot,
            String resumeSnapshot,
            Instant appliedAt,
            Instant createdAt,
            Instant updatedAt,
            String jobTitle,
            String companyName
    ) {}

    public List<ApplicationWithJobEntity> findByCandidateId(Long candidateId, int limit, int offset) {
        if (candidateId == null) {
            return List.of();
        }
        String sql = """
                SELECT a.id, a.job_id, a.candidate_id, a.cover_letter, a.status,
                       a.candidate_snapshot, a.resume_snapshot,
                       a.applied_at, a.created_at, a.updated_at,
                       j.title AS job_title, c.name AS company_name
                FROM applications a
                LEFT JOIN jobs j ON a.job_id = j.id
                LEFT JOIN companies c ON j.company_id = c.id
                WHERE a.candidate_id = ?
                ORDER BY a.applied_at DESC, a.id DESC
                LIMIT ? OFFSET ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapApplicationWithJob(rs), candidateId, limit, offset);
    }

    public long countByCandidateId(Long candidateId) {
        if (candidateId == null) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM applications WHERE candidate_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, candidateId);
        return count != null ? count : 0;
    }

    public Optional<ApplicationWithJobEntity> findDetailByIdAndCandidateId(Long applicationId, Long candidateId) {
        if (applicationId == null || candidateId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT a.id, a.job_id, a.candidate_id, a.cover_letter, a.status,
                       a.candidate_snapshot, a.resume_snapshot,
                       a.applied_at, a.created_at, a.updated_at,
                       j.title AS job_title, c.name AS company_name
                FROM applications a
                LEFT JOIN jobs j ON a.job_id = j.id
                LEFT JOIN companies c ON j.company_id = c.id
                WHERE a.id = ? AND a.candidate_id = ?
                """;
        try {
            ApplicationWithJobEntity entity = jdbcTemplate.queryForObject(
                    sql, (rs, rowNum) -> mapApplicationWithJob(rs), applicationId, candidateId);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private ApplicationWithJobEntity mapApplicationWithJob(ResultSet rs) throws SQLException {
        return new ApplicationWithJobEntity(
                rs.getLong("id"),
                rs.getLong("job_id"),
                rs.getLong("candidate_id"),
                rs.getString("cover_letter"),
                ApplicationStatus.valueOf(rs.getString("status")),
                rs.getString("candidate_snapshot"),
                rs.getString("resume_snapshot"),
                DateTimeUtils.toInstant(rs.getTimestamp("applied_at")),
                DateTimeUtils.toInstant(rs.getTimestamp("created_at")),
                DateTimeUtils.toInstant(rs.getTimestamp("updated_at")),
                rs.getString("job_title"),
                rs.getString("company_name")
        );
    }
}
