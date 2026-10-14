-- ============================================================================
-- CareerConnect Core Database Schema Migration
-- Migration: V1__create_core_schema.sql
-- Database: careerconnect_db
-- Dialect: MySQL 8.0+ (InnoDB, utf8mb4, UTC DATETIME(3))
-- Authority: CAREERCONNECT_MASTER_SPECIFICATION.md Section 10
-- ============================================================================

-- 1. Users
CREATE TABLE IF NOT EXISTS users (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('CANDIDATE', 'RECRUITER', 'ADMIN') NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Candidates
CREATE TABLE IF NOT EXISTS candidates (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    location VARCHAR(150) NULL,
    headline VARCHAR(255) NULL,
    summary TEXT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_candidates_user UNIQUE (user_id),
    CONSTRAINT fk_candidates_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Companies
CREATE TABLE IF NOT EXISTS companies (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    industry VARCHAR(100) NULL,
    location VARCHAR(150) NULL,
    website VARCHAR(255) NULL,
    description TEXT NULL,
    company_size VARCHAR(50) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_companies_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Recruiters (Note: company_id is NULLABLE and NOT UNIQUE: one company has many recruiters)
CREATE TABLE IF NOT EXISTS recruiters (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    company_id BIGINT UNSIGNED NULL,
    full_name VARCHAR(150) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_recruiters_user UNIQUE (user_id),
    CONSTRAINT fk_recruiters_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_recruiters_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE SET NULL,
    INDEX idx_recruiters_company (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Skills
CREATE TABLE IF NOT EXISTS skills (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_skills_normalized_name UNIQUE (normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Candidate Skills (Many-to-Many)
CREATE TABLE IF NOT EXISTS candidate_skills (
    candidate_id BIGINT UNSIGNED NOT NULL,
    skill_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (candidate_id, skill_id),
    CONSTRAINT fk_cs_candidate FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE,
    CONSTRAINT fk_cs_skill FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Education
CREATE TABLE IF NOT EXISTS education (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT UNSIGNED NOT NULL,
    institution VARCHAR(200) NOT NULL,
    degree VARCHAR(100) NOT NULL,
    field_of_study VARCHAR(100) NOT NULL,
    start_year INT NOT NULL,
    end_year INT NULL,
    grade VARCHAR(50) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_education_candidate FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE,
    INDEX idx_education_candidate (candidate_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Projects
CREATE TABLE IF NOT EXISTS projects (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    project_url VARCHAR(255) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_projects_candidate FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE,
    INDEX idx_projects_candidate (candidate_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. Experience
CREATE TABLE IF NOT EXISTS experience (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT UNSIGNED NOT NULL,
    company_name VARCHAR(150) NOT NULL,
    role_title VARCHAR(150) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    is_current BOOLEAN NOT NULL DEFAULT FALSE,
    description TEXT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_experience_candidate FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE,
    INDEX idx_experience_candidate (candidate_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. Resumes
CREATE TABLE IF NOT EXISTS resumes (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT UNSIGNED NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL DEFAULT 'application/pdf',
    size_bytes BIGINT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_resumes_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_resumes_candidate FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE,
    INDEX idx_resumes_candidate_current (candidate_id, is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. Jobs
CREATE TABLE IF NOT EXISTS jobs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    responsibilities TEXT NULL,
    requirements TEXT NULL,
    job_type ENUM('INTERNSHIP', 'FULL_TIME', 'PART_TIME', 'APPRENTICESHIP', 'TRAINEE') NOT NULL,
    experience_level ENUM('INTERNSHIP', 'FRESHER', 'ZERO_TO_ONE_YEARS', 'ONE_TO_TWO_YEARS') NOT NULL,
    work_mode ENUM('ONSITE', 'HYBRID', 'REMOTE') NOT NULL,
    location VARCHAR(150) NULL,
    salary_min_lpa DECIMAL(10, 2) NULL,
    salary_max_lpa DECIMAL(10, 2) NULL,
    salary_currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    application_deadline DATE NOT NULL,
    status ENUM('DRAFT', 'ACTIVE', 'CLOSED', 'ARCHIVED') NOT NULL DEFAULT 'DRAFT',
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_jobs_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT,
    INDEX idx_jobs_discovery (status, application_deadline, deleted_at),
    INDEX idx_jobs_company_status (company_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. Job Skills (Many-to-Many)
CREATE TABLE IF NOT EXISTS job_skills (
    job_id BIGINT UNSIGNED NOT NULL,
    skill_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (job_id, skill_id),
    CONSTRAINT fk_js_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_js_skill FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. Saved Jobs
CREATE TABLE IF NOT EXISTS saved_jobs (
    candidate_id BIGINT UNSIGNED NOT NULL,
    job_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (candidate_id, job_id),
    CONSTRAINT fk_saved_candidate FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 14. Applications (With Immutable Snapshots and Unique Candidate-Job Constraint)
CREATE TABLE IF NOT EXISTS applications (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT UNSIGNED NOT NULL,
    candidate_id BIGINT UNSIGNED NOT NULL,
    cover_letter TEXT NULL,
    status ENUM('APPLIED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW', 'SELECTED', 'REJECTED', 'WITHDRAWN') NOT NULL DEFAULT 'APPLIED',
    candidate_snapshot JSON NOT NULL,
    resume_snapshot JSON NOT NULL,
    applied_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_application_candidate_job UNIQUE (candidate_id, job_id),
    CONSTRAINT fk_applications_job FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE RESTRICT,
    CONSTRAINT fk_applications_candidate FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE RESTRICT,
    INDEX idx_app_job_status_time (job_id, status, applied_at),
    INDEX idx_app_candidate_time (candidate_id, applied_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 15. Application Status History (Append-Only Immutable Audit)
CREATE TABLE IF NOT EXISTS application_status_history (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT UNSIGNED NOT NULL,
    from_status ENUM('APPLIED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW', 'SELECTED', 'REJECTED', 'WITHDRAWN') NULL,
    to_status ENUM('APPLIED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW', 'SELECTED', 'REJECTED', 'WITHDRAWN') NOT NULL,
    changed_by_user_id BIGINT UNSIGNED NOT NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_ash_application FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE,
    CONSTRAINT fk_ash_user FOREIGN KEY (changed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_app_history_app_time (application_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 16. In-App Notifications
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    resource_type VARCHAR(50) NULL,
    resource_id BIGINT UNSIGNED NULL,
    read_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_notifications_user_read (user_id, read_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 17. Admin Audit Logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT UNSIGNED NULL,
    metadata JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_audit_logs_entity (entity_type, entity_id, created_at),
    INDEX idx_audit_logs_actor (actor_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
