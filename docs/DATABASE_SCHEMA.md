# CareerConnect — Database Schema Specification

**Database:** `careerconnect_db`  
**Engine:** MySQL 8.0 InnoDB  
**Charset/Collation:** `utf8mb4` / `utf8mb4_unicode_ci`  
**Timestamps:** UTC `DATETIME(3)`  
**Primary Keys:** `BIGINT UNSIGNED AUTO_INCREMENT`  
**Migration:** `V1__create_core_schema.sql` (Flyway managed)  

---

## 1. Entity-Relationship Overview

```
users (1) <---------- (1) candidates (1) <---- (M) applications (M) ----> (1) jobs (1) <---- (1) companies
  ^                                                     |                                       ^
  |                                                     v (1)                                   | (1)
  +------------------ (1) recruiters (M) ---------------+ (M)                                  |
                                 |                                                              |
                                 +--------------------------------------------------------------+
```

---

## 2. Table Specifications

### 1. `users`
* `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
* `email` VARCHAR(254) NOT NULL UNIQUE
* `password_hash` VARCHAR(255) NOT NULL
* `role` ENUM('CANDIDATE', 'RECRUITER', 'ADMIN') NOT NULL
* `is_active` BOOLEAN NOT NULL DEFAULT TRUE
* `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
* `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)

### 2. `candidates`
* `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
* `user_id` BIGINT UNSIGNED NOT NULL UNIQUE (FK `users.id` ON DELETE CASCADE)
* `full_name` VARCHAR(150) NOT NULL
* `location` VARCHAR(150) NULL
* `headline` VARCHAR(255) NULL
* `summary` TEXT NULL
* `created_at` DATETIME(3), `updated_at` DATETIME(3)

### 3. `companies`
* `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
* `name` VARCHAR(150) NOT NULL UNIQUE
* `industry` VARCHAR(100) NULL
* `location` VARCHAR(150) NULL
* `website` VARCHAR(255) NULL
* `description` TEXT NULL
* `company_size` VARCHAR(50) NULL
* `is_active` BOOLEAN NOT NULL DEFAULT TRUE
* `created_at` DATETIME(3), `updated_at` DATETIME(3)

### 4. `recruiters`
* `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
* `user_id` BIGINT UNSIGNED NOT NULL UNIQUE (FK `users.id` ON DELETE CASCADE)
* `company_id` BIGINT UNSIGNED NULL (FK `companies.id` ON DELETE SET NULL, **INDEXED, NOT UNIQUE**)
* `full_name` VARCHAR(150) NOT NULL
* `created_at` DATETIME(3), `updated_at` DATETIME(3)

### 5. `skills`
* `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
* `name` VARCHAR(100) NOT NULL
* `normalized_name` VARCHAR(100) NOT NULL UNIQUE (lowercase, trimmed)
* `created_at` DATETIME(3)

### 6. `candidate_skills`
* `candidate_id` BIGINT UNSIGNED (FK `candidates.id` ON DELETE CASCADE)
* `skill_id` BIGINT UNSIGNED (FK `skills.id` ON DELETE RESTRICT)
* PRIMARY KEY (`candidate_id`, `skill_id`)

### 7. `education`
* `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
* `candidate_id` BIGINT UNSIGNED NOT NULL (FK `candidates.id` ON DELETE CASCADE)
* `institution` VARCHAR(200) NOT NULL
* `degree` VARCHAR(100) NOT NULL
* `field_of_study` VARCHAR(100) NOT NULL
* `start_year` INT NOT NULL
* `end_year` INT NULL
* `grade` VARCHAR(50) NULL
* `sort_order` INT NOT NULL DEFAULT 0
* INDEX (`candidate_id`, `sort_order`)

### 8. `projects`
* `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
* `candidate_id` BIGINT UNSIGNED NOT NULL (FK `candidates.id` ON DELETE CASCADE)
* `title` VARCHAR(150) NOT NULL
* `description` TEXT NOT NULL
* `project_url` VARCHAR(255) NULL
* `sort_order` INT NOT NULL DEFAULT 0
* INDEX (`candidate_id`, `sort_order`)

### 9. `experience`
* `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
* `candidate_id` BIGINT UNSIGNED NOT NULL (FK `candidates.id` ON DELETE CASCADE)
* `company_name` VARCHAR(150) NOT NULL
* `role_title` VARCHAR(150) NOT NULL
* `start_date` DATE NOT NULL
* `end_date` DATE NULL
* `is_current` BOOLEAN NOT NULL DEFAULT FALSE
* `description` TEXT NULL
* `sort_order` INT NOT NULL DEFAULT 0
* INDEX (`candidate_id`, `sort_order`)

### 10. `resumes`
* `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
* `candidate_id` BIGINT UNSIGNED NOT NULL (FK `candidates.id` ON DELETE CASCADE)
* `original_filename` VARCHAR(255) NOT NULL
* `storage_key` VARCHAR(255) NOT NULL UNIQUE
* `content_type` VARCHAR(100) NOT NULL DEFAULT 'application/pdf'
* `size_bytes` BIGINT NOT NULL
* `is_current` BOOLEAN NOT NULL DEFAULT TRUE
* INDEX (`candidate_id`, `is_current`)

### 11. `jobs`
* `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
* `company_id` BIGINT UNSIGNED NOT NULL (FK `companies.id` ON DELETE RESTRICT)
* `title` VARCHAR(200) NOT NULL
* `description` TEXT NOT NULL
* `responsibilities` TEXT NULL
* `requirements` TEXT NULL
* `job_type` ENUM('INTERNSHIP', 'FULL_TIME', 'PART_TIME', 'APPRENTICESHIP', 'TRAINEE') NOT NULL
* `experience_level` ENUM('INTERNSHIP', 'FRESHER', 'ZERO_TO_ONE_YEARS', 'ONE_TO_TWO_YEARS') NOT NULL
* `work_mode` ENUM('ONSITE', 'HYBRID', 'REMOTE') NOT NULL
* `location` VARCHAR(150) NULL
* `salary_min_lpa` DECIMAL(10, 2) NULL
* `salary_max_lpa` DECIMAL(10, 2) NULL
* `salary_currency` VARCHAR(10) NOT NULL DEFAULT 'INR'
* `application_deadline` DATE NOT NULL
* `status` ENUM('DRAFT', 'ACTIVE', 'CLOSED', 'ARCHIVED') NOT NULL DEFAULT 'DRAFT'
* `deleted_at` DATETIME(3) NULL
* INDEX `idx_jobs_discovery` (`status`, `application_deadline`, `deleted_at`)
* INDEX `idx_jobs_company_status` (`company_id`, `status`)

### 12. `job_skills`
* `job_id` BIGINT UNSIGNED (FK `jobs.id` ON DELETE CASCADE)
* `skill_id` BIGINT UNSIGNED (FK `skills.id` ON DELETE RESTRICT)
* PRIMARY KEY (`job_id`, `skill_id`)

### 13. `saved_jobs`
* `candidate_id` BIGINT UNSIGNED (FK `candidates.id` ON DELETE CASCADE)
* `job_id` BIGINT UNSIGNED (FK `jobs.id` ON DELETE CASCADE)
* `created_at` DATETIME(3)
* PRIMARY KEY (`candidate_id`, `job_id`)

### 14. `applications`
* `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
* `job_id` BIGINT UNSIGNED NOT NULL (FK `jobs.id` ON DELETE RESTRICT)
* `candidate_id` BIGINT UNSIGNED NOT NULL (FK `candidates.id` ON DELETE RESTRICT)
* `cover_letter` TEXT NULL
* `status` ENUM('APPLIED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW', 'SELECTED', 'REJECTED', 'WITHDRAWN') NOT NULL DEFAULT 'APPLIED'
* `candidate_snapshot` JSON NOT NULL
* `resume_snapshot` JSON NOT NULL
* `applied_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
* **UNIQUE KEY `uk_application_candidate_job` (`candidate_id`, `job_id`)**
* INDEX `idx_app_job_status_time` (`job_id`, `status`, `applied_at`)
* INDEX `idx_app_candidate_time` (`candidate_id`, `applied_at`)

### 15. `application_status_history`
* `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
* `application_id` BIGINT UNSIGNED NOT NULL (FK `applications.id` ON DELETE CASCADE)
* `from_status` ENUM(...) NULL
* `to_status` ENUM(...) NOT NULL
* `changed_by_user_id` BIGINT UNSIGNED NOT NULL (FK `users.id` ON DELETE RESTRICT)
* `note` VARCHAR(500) NULL
* `created_at` DATETIME(3)
* INDEX `idx_app_history_app_time` (`application_id`, `created_at`)

### 16. `notifications`
* `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
* `user_id` BIGINT UNSIGNED NOT NULL (FK `users.id` ON DELETE CASCADE)
* `type` VARCHAR(50) NOT NULL
* `title` VARCHAR(200) NOT NULL
* `body` TEXT NOT NULL
* `resource_type` VARCHAR(50) NULL
* `resource_id` BIGINT UNSIGNED NULL
* `read_at` DATETIME(3) NULL
* `created_at` DATETIME(3)
* INDEX `idx_notifications_user_read` (`user_id`, `read_at`, `created_at`)

### 17. `audit_logs`
* `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
* `actor_user_id` BIGINT UNSIGNED NOT NULL (FK `users.id` ON DELETE RESTRICT)
* `action` VARCHAR(100) NOT NULL
* `entity_type` VARCHAR(50) NOT NULL
* `entity_id` BIGINT UNSIGNED NULL
* `metadata` JSON NULL
* `created_at` DATETIME(3)
* INDEX `idx_audit_logs_entity` (`entity_type`, `entity_id`, `created_at`)
* INDEX `idx_audit_logs_actor` (`actor_user_id`, `created_at`)
