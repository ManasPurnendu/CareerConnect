# CareerConnect — Backend Implementation Guide

**Document:** Backend Implementation Guide  
**Project:** CareerConnect  
**Phase:** Phase 8 (P8.1–P8.5: Admin Dashboard, Moderation Mutations, Atomic Audit, Security Hardening & Comprehensive Integration Verification Complete)
**Authority:** `CAREERCONNECT_MASTER_SPECIFICATION.md`  

---

## 1. Quick Start & Prerequisites

* **Runtime:** Java 21 LTS (or higher).
* **Database:** MySQL 8.0+ running on `localhost:3306`.
* **Build Tool:** Apache Maven Wrapper (`./mvnw`).

### Setup Steps

1. **Clone & Navigate:**
   ```bash
   cd backend
   ```

2. **Configure Database Credentials:**
   * By default, the backend connects to `jdbc:mysql://localhost:3306/careerconnect_db`.
   * For local development, copy `application-local.example.yml` to `application-local.yml` (gitignored):
     ```bash
     cp application-local.example.yml application-local.yml
     ```
   * Set your local MySQL password in `application-local.yml`:
     ```yaml
     spring:
       datasource:
         password: YOUR_LOCAL_MYSQL_PASSWORD
     ```

3. **Run Automated Test Suite (All 677 Tests):**
   ```bash
   ./mvnw clean test
   ```

4. **Start the Application:**
   ```bash
   ./mvnw spring-boot:run
   ```
   * Flyway automatically executes migrations on startup and brings `careerconnect_db` up to date.
   * Default administrator account is provisioned: `admin@careerconnect.com` / `Admin@123456`.
   * Access health check and public endpoints:
     ```bash
     curl http://localhost:8080/api/health
     curl http://localhost:8080/api/jobs
     curl http://localhost:8080/api/companies
     ```

---

## 2. Implemented Architecture & Modules

```
React Client (Vite)
       |
       | REST API (HTTP / JSON)
       v
Presentation Layer
├── RecruiterCompanyController (GET/PUT /api/recruiter/company)
├── CandidateProfileController (GET/PUT /api/candidate/profile, GET /api/candidate/profile/readiness)
├── CandidateEducationController (POST, PUT, DELETE /api/candidate/education)
├── CandidateSkillController (GET, POST, DELETE /api/candidate/skills)
├── CandidateProjectController (POST, PUT, DELETE /api/candidate/projects)
├── CandidateExperienceController (POST, PUT, DELETE /api/candidate/experience)
├── CandidateResumeController (POST, GET, DELETE /api/candidate/resume)
├── JobController (GET /api/jobs, GET /api/jobs/{jobId})
├── CompanyController (GET /api/companies, GET /api/companies/{companyId})
├── AuthController (POST /api/auth/register, POST /api/auth/login, GET /api/auth/me, POST /api/auth/logout)
├── HealthController (GET /api/health)
├── GlobalExceptionHandler (@RestControllerAdvice for 400, 401, 403, 404, 409, 422, 500)
└── SecurityFilter (JwtAuthenticationFilter, RestAuthenticationEntryPoint, RestAccessDeniedHandler)
       |
       | Validated DTOs & Domain Models
       v
Business Service Layer
├── RecruiterCompanyService (Recruiter company retrieval & transactional upsert)
├── CandidateProfileService (Basic profile retrieval and updates)
├── CandidateProfileReadinessService (Deterministic application readiness calculation)
├── CandidateEducationService (Education lifecycle and academic year validation)
├── CandidateSkillService (Skill assignment, case-insensitive normalization, duplicate prevention)
├── CandidateProjectService (Portfolio project CRUD with ownership enforcement)
├── CandidateExperienceService (Work experience management with current/end-date invariants)
├── CandidateResumeService (PDF validation, magic-byte inspection, UUID storage, cleanup)
├── JobService (Public job search, filtering, sorting, zero-based pagination, and active-status invariants)
├── CompanyService (Public company discovery, active status invariants, active job aggregation)
├── AuthService (Transactional user registration, role provisioning, credential verification, JWT issuance)
├── HealthService (System & database health check)
└── AdminUserInitializer (CommandLineRunner provisioning initial secure admin)
       |
       | Parameterized SQL & Explicit RowMappers
       v
Data Access Layer (JdbcTemplate - Zero ORM)
├── CandidateProfileRepository (candidates table)
├── CandidateEducationRepository (education table)
├── CandidateSkillRepository (skills, candidate_skills tables)
├── CandidateProjectRepository (projects table)
├── CandidateExperienceRepository (experience table)
├── CandidateResumeRepository (resumes table)
├── JobRepository (jobs, job_skills, skills - parameterized SQL with batch skill loading)
├── CompanyRepository (companies - parameterized SQL with active job count subqueries)
├── UserRepository (users table)
├── CandidateAuthRepository (candidates table)
├── RecruiterAuthRepository (recruiters table)
└── HealthRepository (SELECT 1 ping)
       |
       v
MySQL Database (careerconnect_db)
```

---

## 3. Implemented Endpoints Summary

### A. Candidate Profile Endpoints (Phase 4)

| Method | Path | Access | Request / Body | Success Response | Error Codes |
|---|---|---|---|---|---|
| `GET` | `/api/candidate/profile` | Candidate | None | `200 OK` (`CandidateProfileResponse`) | `UNAUTHENTICATED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND` |
| `PUT` | `/api/candidate/profile` | Candidate | `{ fullName, headline, location, summary }` | `200 OK` (`CandidateProfileResponse`) | `VALIDATION_FAILED`, `UNAUTHENTICATED`, `FORBIDDEN` |
| `GET` | `/api/candidate/profile/readiness` | Candidate | None | `200 OK` (`ProfileReadinessDto`) | `UNAUTHENTICATED`, `FORBIDDEN` |
| `POST` | `/api/candidate/education` | Candidate | `{ institution, degree, fieldOfStudy, startYear, endYear, grade, sortOrder }` | `201 Created` (`EducationDto`) | `VALIDATION_FAILED`, `UNAUTHENTICATED`, `FORBIDDEN` |
| `PUT` | `/api/candidate/education/{id}` | Candidate | `{ institution, degree, fieldOfStudy, startYear, endYear, grade, sortOrder }` | `200 OK` (`EducationDto`) | `VALIDATION_FAILED`, `RESOURCE_NOT_FOUND` |
| `DELETE` | `/api/candidate/education/{id}` | Candidate | None | `204 No Content` | `RESOURCE_NOT_FOUND`, `UNAUTHENTICATED` |
| `GET` | `/api/candidate/skills` | Candidate | None | `200 OK` (`CandidateSkillDto[]`) | `UNAUTHENTICATED`, `FORBIDDEN` |
| `POST` | `/api/candidate/skills` | Candidate | `{ name }` | `201 Created` (`CandidateSkillDto`) | `VALIDATION_FAILED`, `CONFLICT` |
| `DELETE` | `/api/candidate/skills/{id}` | Candidate | None | `204 No Content` | `RESOURCE_NOT_FOUND`, `UNAUTHENTICATED` |
| `POST` | `/api/candidate/projects` | Candidate | `{ title, description, projectUrl, sortOrder }` | `201 Created` (`ProjectDto`) | `VALIDATION_FAILED`, `UNAUTHENTICATED` |
| `PUT` | `/api/candidate/projects/{id}` | Candidate | `{ title, description, projectUrl, sortOrder }` | `200 OK` (`ProjectDto`) | `VALIDATION_FAILED`, `RESOURCE_NOT_FOUND` |
| `DELETE` | `/api/candidate/projects/{id}` | Candidate | None | `204 No Content` | `RESOURCE_NOT_FOUND`, `UNAUTHENTICATED` |
| `POST` | `/api/candidate/experience` | Candidate | `{ companyName, roleTitle, startDate, endDate, isCurrent, description, sortOrder }` | `201 Created` (`ExperienceDto`) | `VALIDATION_FAILED`, `UNAUTHENTICATED` |
| `PUT` | `/api/candidate/experience/{id}` | Candidate | `{ companyName, roleTitle, startDate, endDate, isCurrent, description, sortOrder }` | `200 OK` (`ExperienceDto`) | `VALIDATION_FAILED`, `RESOURCE_NOT_FOUND` |
| `DELETE` | `/api/candidate/experience/{id}` | Candidate | None | `204 No Content` | `RESOURCE_NOT_FOUND`, `UNAUTHENTICATED` |
| `POST` | `/api/candidate/resume` | Candidate | Multipart `file` (PDF, max 5 MiB) | `201 Created` (`ResumeResponse`) | `CANDIDATE_RESUME_REQUIRED`, `RESUME_INVALID_TYPE`, `RESUME_TOO_LARGE` |
| `GET` | `/api/candidate/resume` | Candidate | None | `200 OK` (`ResumeResponse`) | `RESOURCE_NOT_FOUND`, `UNAUTHENTICATED` |
| `DELETE` | `/api/candidate/resume` | Candidate | None | `204 No Content` | `RESOURCE_NOT_FOUND`, `UNAUTHENTICATED` |

### B. Public Discovery Endpoints (Phase 3)

| Method | Path | Access | Query Parameters | Success Response | Error Codes |
|---|---|---|---|---|---|
| `GET` | `/api/jobs` | Public | `q, location, jobType, experienceLevel, workMode, skill, minSalaryLpa, sort, page, size` | `200 OK` (Paginated `JobSummaryDto[]`) | `VALIDATION_FAILED` |
| `GET` | `/api/jobs/{id}` | Public | None | `200 OK` (`JobDetailDto`) | `JOB_NOT_FOUND` |
| `GET` | `/api/companies` | Public | `q, page, size` | `200 OK` (Paginated `CompanySummaryDto[]`) | `VALIDATION_FAILED` |
| `GET` | `/api/companies/{id}` | Public | None | `200 OK` (`CompanyDetailDto` with `activeJobs`) | `RESOURCE_NOT_FOUND` |

### C. Authentication Module Endpoints (Phase 2)

| Method | Path | Access | Request Body | Success Response | Error Codes |
|---|---|---|---|---|---|
| `POST` | `/api/auth/register` | Public | `{ email, password, role, fullName }` | `201 Created` (`AuthResponse`) | `VALIDATION_FAILED`, `EMAIL_ALREADY_EXISTS` |
| `POST` | `/api/auth/login` | Public | `{ email, password }` | `200 OK` (`AuthResponse`) | `VALIDATION_FAILED`, `INVALID_CREDENTIALS`, `FORBIDDEN` |
| `GET` | `/api/auth/me` | Authenticated | None (`Authorization: Bearer <token>`) | `200 OK` (`AuthUserDto`) | `UNAUTHENTICATED`, `FORBIDDEN` |
| `POST` | `/api/auth/logout` | Authenticated | None (`Authorization: Bearer <token>`) | `204 No Content` | `UNAUTHENTICATED` |

### D. Applications & Matching Endpoints (Phase 5)

| Method | Path | Access | Parameters / Body | Success Response | Error Codes |
|---|---|---|---|---|---|
| `GET` | `/api/jobs/{jobId}/match` | Candidate | None | `200 OK` (`JobMatchDto`) | `UNAUTHENTICATED`, `FORBIDDEN`, `JOB_NOT_FOUND` |
| `POST` | `/api/jobs/{jobId}/applications` | Candidate | `{ coverLetter }` | `201 Created` (`ApplicationResponseDto`) | `VALIDATION_FAILED`, `DUPLICATE_APPLICATION`, `CANDIDATE_PROFILE_INCOMPLETE`, `CANDIDATE_RESUME_REQUIRED`, `JOB_NOT_ACTIVE`, `JOB_DEADLINE_PASSED`, `JOB_NOT_FOUND` |
| `GET` | `/api/candidate/applications` | Candidate | `page, size` | `200 OK` (Paginated `ApplicationResponseDto[]`) | `UNAUTHENTICATED`, `FORBIDDEN` |
| `GET` | `/api/candidate/applications/{id}` | Candidate | None | `200 OK` (`ApplicationResponseDto`) | `UNAUTHENTICATED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND` |
| `PATCH` | `/api/candidate/applications/{id}/withdraw` | Candidate | None | `200 OK` (`ApplicationResponseDto`) | `UNAUTHENTICATED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND`, `INVALID_STATUS_TRANSITION` |

### E. Recruiter Company Management Endpoints (Phase 6.2)

| Method | Path | Access | Request Body | Success Response | Error Codes |
|---|---|---|---|---|---|
| `GET` | `/api/recruiter/company` | Recruiter | None | `200 OK` (`RecruiterCompanyResponseDto`) | `UNAUTHENTICATED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND` |
| `PUT` | `/api/recruiter/company` | Recruiter | `{ name, description, industry, website, location, companySize }` | `200 OK` (`RecruiterCompanyResponseDto`) | `VALIDATION_FAILED`, `UNAUTHENTICATED`, `FORBIDDEN`, `COMPANY_ALREADY_EXISTS` |

### F. Recruiter Job Lifecycle Endpoints (Phase 6.4)

| Method | Path | Access | Parameters | Success Response | Error Codes |
|---|---|---|---|---|---|
| `PATCH` | `/api/recruiter/jobs/{jobId}/publish` | Recruiter | `jobId` | `200 OK` (`JobDetailDto`) | `UNAUTHENTICATED`, `FORBIDDEN`, `JOB_NOT_FOUND`, `COMPANY_REQUIRED`, `INVALID_STATUS_TRANSITION`, `JOB_DEADLINE_PASSED`, `JOB_SKILL_REQUIRED`, `VALIDATION_FAILED` |
| `PATCH` | `/api/recruiter/jobs/{jobId}/close` | Recruiter | `jobId` | `200 OK` (`JobDetailDto`) | `UNAUTHENTICATED`, `FORBIDDEN`, `JOB_NOT_FOUND`, `COMPANY_REQUIRED`, `INVALID_STATUS_TRANSITION` |

### G. Recruiter Job Query Endpoints (Phase 6.5)

| Method | Path | Access | Parameters | Success Response | Error Codes |
|---|---|---|---|---|---|
| `GET` | `/api/recruiter/jobs` | Recruiter | `page, size` | `200 OK` (Paginated `JobSummaryDto[]`) | `UNAUTHENTICATED`, `FORBIDDEN`, `COMPANY_REQUIRED` |
| `GET` | `/api/recruiter/jobs/{jobId}` | Recruiter | `jobId` | `200 OK` (`JobDetailDto`) | `UNAUTHENTICATED`, `FORBIDDEN`, `JOB_NOT_FOUND`, `COMPANY_REQUIRED` |

### H. Recruiter Job Mutation Endpoints (Phase 6.3 & 6.6)

| Method | Path | Access | Parameters | Success Response | Error Codes |
|---|---|---|---|---|---|
| `POST` | `/api/recruiter/jobs` | Recruiter | Body (`RecruiterJobCreateRequest`) | `201 Created` (`JobDetailDto`) | `VALIDATION_FAILED`, `UNAUTHENTICATED`, `FORBIDDEN`, `COMPANY_REQUIRED`, `JOB_DEADLINE_PASSED`, `JOB_SKILL_REQUIRED` |
| `PUT` | `/api/recruiter/jobs/{jobId}` | Recruiter | `jobId`, Body (`RecruiterJobUpdateRequest`) | `200 OK` (`JobDetailDto`) | `VALIDATION_FAILED`, `UNAUTHENTICATED`, `FORBIDDEN`, `JOB_NOT_FOUND`, `COMPANY_REQUIRED`, `JOB_DEADLINE_PASSED`, `JOB_SKILL_REQUIRED`, `INVALID_STATUS_TRANSITION` |
| `DELETE` | `/api/recruiter/jobs/{jobId}` | Recruiter | `jobId` | `204 No Content` | `UNAUTHENTICATED`, `FORBIDDEN`, `JOB_NOT_FOUND`, `COMPANY_REQUIRED`, `INVALID_STATUS_TRANSITION` |

### I. Recruiter Applicant Query & Lifecycle Endpoints (Phase 7.1, 7.2 & 7.4)

| Method | Path | Access | Parameters | Success Response | Error Codes |
|---|---|---|---|---|---|
| `GET` | `/api/recruiter/jobs/{jobId}/applications` | Recruiter | `jobId`, `page`, `size`, `status` | `200 OK` (Paginated `ApplicantSummaryDto[]`) | `UNAUTHENTICATED`, `FORBIDDEN`, `JOB_NOT_FOUND`, `COMPANY_REQUIRED` |
| `GET` | `/api/recruiter/applications/{applicationId}` | Recruiter | `applicationId` | `200 OK` (`ApplicantDetailDto`) | `UNAUTHENTICATED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND`, `COMPANY_REQUIRED` |
| `PATCH` | `/api/recruiter/applications/{applicationId}/status` | Recruiter | `applicationId`, Body (`UpdateApplicationStatusRequest`) | `200 OK` (`ApplicantDetailDto`) | `VALIDATION_FAILED`, `UNAUTHENTICATED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND`, `COMPANY_REQUIRED`, `INVALID_STATUS_TRANSITION` |
| `GET` | `/api/recruiter/applications/{applicationId}/resume` | Recruiter | `applicationId`, `metadata` | `200 OK` (PDF stream or `ResumeResponse`) | `UNAUTHENTICATED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND`, `COMPANY_REQUIRED` |

### J. In-App Notification Endpoints (Phase 7.3)

| Method | Path | Access | Parameters | Success Response | Error Codes |
|---|---|---|---|---|---|
| `GET` | `/api/notifications` | Any Authenticated | `page`, `size`, `unread` | `200 OK` (`NotificationSummaryResponse`) | `UNAUTHENTICATED` |
| `PATCH` | `/api/notifications/{id}/read` | Any Authenticated | `id` | `200 OK` (`NotificationDto`) | `UNAUTHENTICATED`, `RESOURCE_NOT_FOUND` |
| `PATCH` | `/api/notifications/{id}` | Any Authenticated | `id`, Body (`Map`) | `200 OK` (`NotificationDto`) | `UNAUTHENTICATED`, `RESOURCE_NOT_FOUND` |
| `PATCH` | `/api/notifications/read-all` | Any Authenticated | None | `200 OK` (`Map`) | `UNAUTHENTICATED` |

---

## 4. Academic Traceability & Automated Testing

All **659 automated tests** in the test suite verify genuine application functionality:

* **Comprehensive Testing & Final Verification (Phase 7.6 — 5 tests):**
  * `Phase7ComprehensiveWorkflowIntegrationTest` (5 tests): Comprehensive end-to-end multi-step verification of the entire Phase 7 functional surface and its deep interactions with Phase 5 (Candidate Applications & Deterministic Matching) and Phase 6 (Recruiter Company & Job Management):
    1. Complete candidate-to-recruiter lifecycle workflow: candidate profile, education, skills, and resume readiness; 100% deterministic skill matching; application submission with point-in-time snapshot preservation; recruiter applicant listing; immutable application detail verification (`ApplicantDetailDto`); binary protected PDF resume streaming; complete sequential status lifecycle transitions (`APPLIED -> UNDER_REVIEW -> SHORTLISTED -> INTERVIEW -> SELECTED`) with audit history verification; candidate in-app status notification receipt; single notification read marking; bulk `read-all` execution; candidate application detail review; and terminal state withdrawal prevention (`422 INVALID_STATUS_TRANSITION`).
    2. P6 active job closure notification workflow: closing an active job emits distinct `JOB_CLOSED` in-app notifications to all candidates who applied, leaves zero-application jobs with zero notifications, and rejects duplicate closure attempts (`422 INVALID_STATUS_TRANSITION`).
    3. P6 job fundamental requirement locking when applications exist: recruiter attempts to alter fundamental job requirements (salary, skills, job type) on an applied job return `422 INVALID_STATUS_TRANSITION`, while permitted operational fields update normally; job deletion is strictly prohibited once applications exist (`422 INVALID_STATUS_TRANSITION`).
    4. Candidate self-service withdrawal workflow: candidate withdraws application, appends audit history, prevents duplicate re-application (`409 DUPLICATE_APPLICATION`), and blocks subsequent recruiter transitions out of terminal `WITHDRAWN` state.
    5. Database integrity and foreign key consistency: automated relational queries verify zero orphaned applications, zero orphaned status histories, zero orphaned notifications, and zero orphaned resumes across the entire MySQL relational schema.
* **P7 Security Hardening & Authorization Audit (Phase 7.5 — 24 tests):**
  * `Phase7SecurityHardeningAuditTest` (24 tests): Comprehensive verification of Phase 7 security hardening and authorization boundaries: role matrix verification (401 unauthenticated across recruiter, candidate, and notification endpoints; 403 non-recruiter rejection for candidate and admin; 200/204 authorized access), cross-company isolation (recruiter from Company A attempting to access Company B applicant lists, application details, status updates, resumes, and resume downloads receives generic 404 `JOB_NOT_FOUND` / `RESOURCE_NOT_FOUND` with zero metadata leakage), candidate data isolation (candidate A cannot view or withdraw candidate B's application), notification user isolation (User A notifications invisible to User B, foreign notification mark-as-read returns generic 404, `read-all` only affects current caller), client parameter/body ID tampering immunity (`?userId`, `?candidateId`, `?recruiterId`, `?companyId`, `?resumeId`), soft-deleted job isolation (applications, details, status updates, and resumes for soft-deleted jobs return 404), stale JWT company reassignment handling (database company reassignments immediately update authorization without trusting stale token claims), terminal application status transition prevention (transitions out of `REJECTED`, `SELECTED`, or `WITHDRAWN` return `422 INVALID_STATUS_TRANSITION`), recruiter prohibition from withdrawing applications (`422 INVALID_STATUS_TRANSITION`), status update 3-way transactional atomicity verification (status mutation, status history row, and candidate notification insert), sensitive error metadata leakage prevention (no emails, names, company names, job titles, paths, or SQL details in error envelopes), and resume storage path traversal prevention (`../../` rejected with 404).
* **Protected Resume Access (Phase 7.4 — 19 tests):**
  * `ProtectedResumeAccessIntegrationTest` (19 tests): Comprehensive verification of Phase 7.4 protected resume access: 401 unauthenticated access rejection across all protected endpoints, 403 non-recruiter rejection (Candidate & Admin), 422 unassociated recruiter handling (`COMPANY_REQUIRED`), recruiter access to own company applicant resume (binary PDF streaming with `application/pdf` and `inline; filename="..."` disposition, `/resume/download` alias, and JSON metadata with `?metadata=true`), strict cross-company isolation (recruiter from Company A attempting to access Company B application resume receives generic 404 `RESOURCE_NOT_FOUND` with zero resume metadata leakage), candidate access to own resume (metadata via `GET /api/candidate/resume`, download via `GET /api/candidate/resume/download`, and `?download=true`), candidate cross-access isolation (candidate B cannot access candidate A's resume), nonexistent application generic 404, missing resume 404 handling (when candidate deletes resume post-application), soft-deleted job 404 isolation, client parameter tampering immunity (`candidateId`, `userId`, `companyId`, `recruiterId`, `resumeId`), stale JWT company reassignment handling (database company reassignment immediately updates authorization without trusting stale token claims), and physical storage file missing 404 handling.
* **In-App Notifications Engine (Phase 7.3 — 21 tests):**
  * `NotificationEngineIntegrationTest` (20 tests): Comprehensive verification of the Phase 7.3 notifications engine: 401 unauthenticated access rejection across all endpoints (`GET /api/notifications`, `PATCH /api/notifications/{id}/read`, `PATCH /api/notifications/read-all`), authenticated candidate and recruiter access, strict user isolation (User A cannot view or mark User B's notification read with generic 404 `RESOURCE_NOT_FOUND`), client `userId` parameter tampering immunity, paginated listing with total unread count (`unreadCount`), unread filtering (`?unread=true`), deterministic `created_at DESC, id DESC` ordering, single notification read marking (`read_at = CURRENT_TIMESTAMP(3)`), idempotent re-marking of already-read notifications, `read-all` bulk marking with unread count reset while leaving other users unaffected, automatic trigger on recruiter application status transition (`APPLICATION_STATUS_CHANGED`, resource `APPLICATION`), automatic trigger on active job closure (`JOB_CLOSED`, resource `JOB`), zero notification generation on zero-applicant job closure, applicant isolation (unrelated candidates receive zero notifications), cross-company operation protection (zero notifications generated on rejected cross-company status update or job close attempts), terminal status mutation protection (zero notifications), and candidate self-service withdrawal isolation.
  * `ApplicationTransactionTest` (1 test): Explicit verification of 3-way transactional atomicity between status update, history logging, and in-app notification insertion (`recruiterStatusUpdateRollsBack_whenNotificationFails`), proving that notification insertion failure causes complete rollback of status mutation and history logging with zero partial state.
* **Recruiter Application Status Lifecycle (Phase 7.2 — 22 tests):**
  * `RecruiterApplicationLifecycleControllerTest` (22 tests): Exhaustive testing of Phase 7.2 status transitions: 401 unauthenticated access rejection, 403 non-recruiter rejection (Candidate & Admin), 422 unassociated recruiter handling (`COMPANY_REQUIRED`), cross-company application modification rejection with generic 404 (`RESOURCE_NOT_FOUND`), nonexistent application 404, soft-deleted job application 404, valid state transitions according to CC-MASTER-001 v1.2 (`APPLIED -> UNDER_REVIEW`, `UNDER_REVIEW -> SHORTLISTED`, `SHORTLISTED -> INTERVIEW`, `INTERVIEW -> SELECTED`, direct `APPLIED -> SHORTLISTED`, direct `APPLIED -> REJECTED`), rejection of terminal state mutations (`REJECTED`, `WITHDRAWN`, `SELECTED`), backward transition rejection, invalid skip rejection, same-state transition rejection (`422 INVALID_STATUS_TRANSITION`), recruiter prohibition from withdrawing (`WITHDRAWN`), note persistence (up to 500 characters), note overlength rejection (>500 chars -> `400 VALIDATION_FAILED`), null target status rejection (400), malformed body rejection (400), client payload spoofing immunity (`companyId`, `recruiterId`, `candidateId`, `changedByUserId`), and atomic status history append auditing.
* **Recruiter Applicant Query (Phase 7.1 — 20 tests):**
  * `RecruiterApplicantQueryControllerTest` (20 tests): Comprehensive verification of Phase 7.1 recruiter applicant listing and detail endpoints: unauthenticated rejection (401 on listing and detail), role-based authorization (rejection of Candidate and Admin with 403), company association enforcement (422 `COMPANY_REQUIRED` for unassociated recruiters on listing and detail), strict company ownership isolation (successful retrieval of own company job applicants and application detail; cross-company job listing returns generic 404 `JOB_NOT_FOUND`; cross-company application detail returns generic 404 `RESOURCE_NOT_FOUND`), soft-deleted job isolation (404), client parameter/body tampering immunity (`companyId`, `recruiterId`), stale JWT handling (database company association takes precedence over token claims), deterministic pagination and ordering (`applied_at DESC, id DESC`), status filtering (`?status=APPLIED`), complete detail response structure (immutable candidate snapshot, resume snapshot metadata, chronological audit status history, deterministic skill match breakdown), snapshot immutability (candidate post-submission profile mutations do not modify historical application snapshots), and empty result sets.
* **Recruiter Company & Jobs (Phase 6 — 132 tests):**
  * `RecruiterComprehensiveWorkflowTest` (10 tests): End-to-end integration test sweep verifying the entire recruiter journey across all P6 functional areas: company setup and persistence, job creation (`POST /api/recruiter/jobs`), initial `DRAFT` state, field and skill persistence, job editing, lifecycle progression (`DRAFT -> ACTIVE -> CLOSED`), candidate application integration (P5 workflow), application-based invariant enforcement (fundamental requirements strictly locked when applications exist, returning `422 INVALID_STATUS_TRANSITION`), permitted operational editing, delete protection on active jobs and applied jobs, zero-application soft deletion (`deleted_at = CURRENT_TIMESTAMP(3)`), removal from listings/details, cross-company isolation, stale JWT handling (reassignment and unassociation), canonical envelopes, and transactional atomicity.
  * `RecruiterJobSecurityHardeningTest` (23 tests): Exhaustive security hardening and ownership isolation verification across the entire recruiter job surface: unauthenticated rejection (401 across all 8 endpoints), candidate role rejection (403), admin role rejection (403), strict company ownership segregation (recruiter sees only own jobs; cross-company jobs never appear in listings), cross-company detail/update/delete/publish/close rejection with generic 404 (`JOB_NOT_FOUND`), zero metadata leakage (no disclosure of company identity, job title, description, skills, salary, or candidate counts), parameter and body tampering immunity (`companyId`, `recruiterId`, `userId`, `createdBy`, `status`, `deletedAt`, `createdAt`), stale JWT protection (company reassignment follows current DB association, company removal returns 422 `COMPANY_REQUIRED`, old company jobs unreachable), repository-level ownership guards on mutations (`updateJobFields`, `updateJobStatus`, `softDeleteJob`, `replaceJobSkills`), repository protection of deleted jobs, and complete role boundary matrix verification.
  * `RecruiterJobMutationControllerTest` (42 tests): Comprehensive verification of Phase 6.6 recruiter job editing and soft deletion: unauthenticated rejection (401), non-recruiter rejection (403 for Candidate & Admin), unassociated recruiter rejection (422 `COMPANY_REQUIRED`), recruiter updates own job successfully, cross-company update and deletion rejection (404 `JOB_NOT_FOUND`), query and payload `companyId` spoofing protection, field validation (missing title, missing skills, negative salary, max salary below min, non-remote location missing, past deadline, invalid enum), draft job editing, active job editing (zero applications), correct MySQL persistence, skill list atomicity and replacement, transaction rollback on invalid skill ID, detail and listing reflection, fundamental requirements lock when $\ge 1$ application exists (protecting title, jobType, expLevel, workMode, salary, core requirements, and required skills), permitted operational field updates (description, responsibilities, location detail, deadline), zero partial updates on fundamental rejection, soft-deletion of zero-application non-active jobs (`deleted_at = CURRENT_TIMESTAMP(3)`), removal from recruiter listing and detail (404), blocking deletion of jobs with applications (422 `INVALID_STATUS_TRANSITION`), preservation of application history and candidate snapshots, blocking direct deletion of active jobs, generic edit endpoint inability to publish/close/archive, and rejection of modification/deletion of archived or closed jobs.
  * `RecruiterJobQueryControllerTest` (18 tests): Comprehensive verification of Phase 6.5 recruiter job listing and detail query functionality: unauthenticated access rejection (401), non-recruiter access rejection (403 for Candidate & Admin), unassociated recruiter handling (422 `COMPANY_REQUIRED`), cross-company job access isolation (404 `JOB_NOT_FOUND`), client-supplied `companyId` parameter tampering immunity, empty collection handling, pagination boundary tests (page 0, page > 0, max size capping at 100), deterministic `created_at DESC, id DESC` ordering, complete detail exposure, and visibility across all lifecycle states (`DRAFT`, `ACTIVE`, `CLOSED`).
  * `RecruiterJobLifecycleControllerTest` (22 tests): Full verification of Phase 6.4 job lifecycle state machine: unauthenticated rejection (401), candidate and admin access rejection (403), company association requirement (422 `COMPANY_REQUIRED`), cross-company 404 isolation, stale JWT company manipulation protection, valid transition execution (`DRAFT -> ACTIVE`, `ACTIVE -> CLOSED`), rejection of invalid transitions (`DRAFT -> DRAFT`, `DRAFT -> CLOSED`, `CLOSED -> CLOSED`, `CLOSED -> ACTIVE`, `ARCHIVED -> ACTIVE/CLOSED`), deadline expiry rejection (422 `JOB_DEADLINE_PASSED`), missing skills rejection (422 `JOB_SKILL_REQUIRED`), non-remote location validation (422 `VALIDATION_FAILED`), direct database persistence of status mutations, and database relationship integrity.
  * `RecruiterCompanyControllerTest` (17 tests): Comprehensive verification of recruiter company management: unauthenticated access rejection (401), non-recruiter access rejection (403 for Candidate & Admin), 404 when no company is associated, company creation with atomic recruiter association (`recruiters.company_id`), transactional rollback on association failure (zero orphan rows), single-company cardinality (repeated PUT updates rather than creates), company update persistence, cross-company modification isolation, client-supplied `companyId`/`recruiterId` spoofing immunity, unique name conflict prevention (409 `COMPANY_ALREADY_EXISTS`), blank name validation (400 `VALIDATION_FAILED`), and backward compatibility regression of public company discovery endpoints.
* **Applications & Deterministic Matching (Phase 5 — 156 tests):**


  * `ApplicationStatusTest` (40 tests): Exhaustive testing of the authoritative state machine (valid transitions, invalid transitions, terminal states `SELECTED`, `REJECTED`, `WITHDRAWN`, same-state transitions, and null transitions).
  * `ApplicationSecurityAndOwnershipTest` (22 tests): Token authentication, role restrictions, candidate ID injection protection across queries/bodies/headers, and cross-candidate isolation.
  * `MatchServiceTest` (19 tests): Deterministic matching algorithms, case normalization, whitespace trimming, duplicate skill handling, integer rounding ratios (50%, 67%, 75%, 33%, 17%, 83%), and null safety.
  * `ApplicationSubmissionControllerTest` (17 tests): End-to-end submission testing, profile readiness gating, inactive/expired job rejection, cover letter length validation, duplicate prevention, and initial status history creation.
  * `CandidateApplicationQueryControllerTest` (14 tests): Candidate-scoped application listing, detail retrieval, pagination boundary clamping (default 20, max 100), deterministic `applied_at DESC, id DESC` ordering, and foreign application 404 isolation.
  * `CandidateApplicationWithdrawalControllerTest` (13 tests): Candidate self-service withdrawal, state machine enforcement, append-only status history auditing, and redundant withdrawal prevention.
  * `JobMatchControllerTest` (10 tests): REST integration testing of `/api/jobs/{id}/match`, candidate identity resolution from security context, recruiter prohibition (403), and unauthenticated rejection (401).
  * `ApplicationSnapshotImmutabilityTest` (9 tests): Verifies that post-submission candidate mutations (name, headline, bio, education, skills, projects, experience, resume) do NOT alter the historical application snapshots.
  * `CandidateSnapshotSerializationTest` (8 tests): Jackson serialization and deserialization round-trip fidelity, null preservation, escaped quotes/newlines/unicode handling, and forward-compatible tolerance of unknown JSON fields.
  * `ApplicationComprehensiveWorkflowTest` (4 tests): Multi-step candidate application lifecycle, 0% match application allowance, duplicate application blocking after withdrawal, pagination edge cases, and canonical error envelope validation.
  * `ApplicationTransactionTest` (2 tests): `@Transactional` rollback verification for application submission and withdrawal (simulated audit history failure causes complete rollback; zero orphaned application or dangling audit rows).
* **Candidate Profile & Resources (Phase 4 — 95 tests):**
  * `CandidateProfileControllerTest` (9 tests): Basic profile GET, PUT, persistence, blank name validation, recruiter 403, and unauthenticated 401.
  * `CandidateEducationControllerTest` (9 tests): Education CRUD, year validations, sort ordering, ownership isolation, and 404 on foreign records.
  * `CandidateSkillControllerTest` (9 tests): Skill assignment, lowercase normalization, duplicate prevention (409 CONFLICT), listing, and safe deletion from candidate_skills.
  * `CandidateProjectControllerTest` (9 tests): Portfolio projects CRUD, URL validation, sort ordering, and cross-candidate isolation.
  * `CandidateExperienceControllerTest` (9 tests): Experience CRUD, current vs past date invariants, start/end date validations, and ownership isolation.
  * `CandidateResumeControllerTest` (20 tests): PDF multipart upload, %PDF- magic-byte validation, 5 MiB ceiling, UUID file naming, physical file storage, replacement cleanup, delete cleanup, and 404 when no resume exists.
  * `CandidateProfileReadinessControllerTest` (16 tests): Real-time deterministic calculation of application readiness based on profile completion, education presence, skill mapping, and active resume.
  * `CandidateSecurityHardeningTest` (13 tests): Security audit verification covering cross-candidate 404 isolation across all candidate sub-resources, client candidateId injection rejection, path traversal filename sanitization, and SQL injection parameterization resilience.
  * `CandidateFullLifecycleRegressionTest` (1 test): Full end-to-end multi-step candidate lifecycle from registration through education, skills, projects, experience, resume upload, replacement, deletion, and dynamic readiness state transitions.
* **Public Discovery & Jobs (Phase 3 — 23 tests):**
  * `JobControllerTest` (17 tests): Public active job pagination, search filtering (`q`), location filtering, job type, experience level, work mode, skills, salary filtering, deadline and salary sorting, complete job detail retrieval, and strict exclusion of non-public jobs (`DRAFT`, `CLOSED`, `ARCHIVED`, `EXPIRED`, `DELETED`, `INACTIVE_COMPANY`).
  * `CompanyControllerTest` (6 tests): Active company pagination, keyword search, active job counts, and 404 exclusion of inactive companies.
* **Authentication & Security (Phase 2 — 18 tests):**
  * `AuthControllerTest` (14 tests): Candidate/recruiter registration, admin self-registration rejection, duplicate email handling, validation, login, token verification, `/auth/me`, and logout.
  * `JwtServiceTest` (4 tests): Cryptographic secret key enforcement, claim extraction, expiration timestamping, and tampered token rejection.
* **Core Foundation & Error Framework (Phase 1 — 8 tests):**
  * `GlobalExceptionHandlerTest` (4 tests): Canonical `ApiErrorResponse` envelope format and status code mapping (400, 404, 409, 422).
  * `ApiResponseContractTest` (3 tests): Strict conformity of single-item and paginated collection envelopes.
  * `PageRequestDtoTest` (3 tests): Default page/size, offset calculation, and upper bound capping (max 100).
  * `HealthControllerTest` (1 test): Live end-to-end Three-Tier request flow and database connectivity check.
* **Admin & Moderation Infrastructure (Phase 8.1, 8.2, 8.3, 8.4 & 8.5 — 133 tests):**
  * `Phase8ComprehensiveWorkflowIntegrationTest` (18 tests): Comprehensive end-to-end integration and cross-phase lifecycle verification suite: 44-step candidate-recruiter-admin journey (registration, profile readiness, skills, education, PDF resume upload, recruiter company setup, draft job creation, publishing, deterministic matching, application submission, snapshot immutability, recruiter review, protected resume streaming, valid sequential application status transitions `APPLIED -> UNDER_REVIEW -> SHORTLISTED -> INTERVIEW -> SELECTED`, candidate notifications and mark-read flow, active job closure notifications, admin dashboard consistency with DB counts, admin platform-wide visibility, admin resume streaming, audit logs, and admin job archive/restore); application state machine & terminal state locks (preventing re-application, preventing recruiter withdrawal); job lifecycle (recruiter lifecycle and admin moderation); company moderation (deactivation hides jobs without deleting relational entities; reactivation restores visibility); user moderation (deactivation revokes login; reactivation restores access; self-deactivation blocked); admin dashboard metrics exact matching with database counts; platform-wide application visibility across distinct companies; admin resume streaming with path traversal protection; append-only audit trail verification (rejection of modifying HTTP verbs with 405 Method Not Allowed); notification engine delivery across lifecycle operations; application snapshot immutability under live candidate mutations and admin moderation; deterministic matching calculation (100%, 50%, 0%); cross-company isolation; soft-deleted job exclusion; bounded pagination; canonical API envelopes; cross-phase role isolation matrix; and relational database integrity (zero orphan rows across applications, status histories, skills, resumes, and audit logs; transactional rollback on simulated audit write failure).
  * `Phase8SecurityHardeningAuditTest` (30 tests): Comprehensive adversarial audit and penetration testing across the entire Phase 8 surface covering all 30 authoritative invariants: base RBAC matrix enforcement (Guest 401 across all endpoints, Candidate 403, Recruiter 403, Admin 200), client actor identity spoofing immunity (`actorUserId`, `adminId`), role tampering immunity, self-deactivation prevention (`422 INVALID_STATUS_TRANSITION`), login revocation for deactivated users (403), company deactivation public job discovery hiding with job state preservation, company reactivation visibility restoration, job archive transitions (`DRAFT`/`ACTIVE`/`CLOSED` -> `ARCHIVED`), invalid archive rejection, restore strictly from `ARCHIVED` to `CLOSED` (rejecting all invalid origins), archived job public invisibility, historical application and snapshot immutability during archival, append-only audit log enforcement (rejecting mutation/deletion HTTP verbs with `405 METHOD_NOT_ALLOWED`), zero audit generation on rejected mutations, transaction rollback on audit failure, cross-resource platform-wide application visibility, protected resume authorization and storage path traversal prevention (`../../etc/passwd`), sensitive data leak prevention (zero password hashes, tokens, or absolute paths), SQL injection immunity via parameterized queries, pagination boundary clamping (default 20, max 100, safe negative handling), cross-phase role isolation, stale JWT account-state enforcement, client ID tampering immunity, and soft-deleted resource protection.
  * `AdminUserModerationControllerTest` (9 tests): Administrative user active status transitions (`PATCH /api/admin/users/{userId}/status`), activation and deactivation mutations, self-deactivation protection with canonical 422 rejection, same-state no-op rejection, login revocation enforcement for deactivated users, client actor ID tampering immunity, and atomic audit logging.
  * `AdminCompanyModerationControllerTest` (6 tests): Administrative company active status moderation (`PATCH /api/admin/companies/{companyId}/status`), company activation and deactivation, automatic hiding of company jobs from public discovery upon deactivation, restoration of job visibility upon reactivation, same-state no-op rejection, and atomic audit logging.
  * `AdminJobModerationControllerTest` (8 tests): Administrative job moderation (`PATCH /api/admin/jobs/{jobId}/archive` and `PATCH /api/admin/jobs/{jobId}/restore`), archiving from `DRAFT`/`ACTIVE`/`CLOSED`, public discovery hiding, preservation of historical applications/snapshots, restoration strictly from `ARCHIVED` to `CLOSED` (rejecting invalid transitions), same-state rejection, and atomic audit logging.
  * `AdminMutationTransactionTest` (4 tests): Spring `@Transactional` rollback guarantees for admin mutations (simulated audit log failure causes complete database rollback across user status, company status, job archive, and job restore; zero orphaned state mutations or dangling audit logs).
  * `AdminDashboardControllerTest` (4 tests): Platform-wide dashboard aggregate metrics calculation across users, companies, jobs, and applications; RBAC verification (guest 401, candidate 403, recruiter 403, admin 200).
  * `AdminUserControllerTest` (4 tests): Paginated administrative user listing with role and active status filtering, search by name/email, password hash/secret omission verification, and RBAC enforcement.
  * `AdminCompanyControllerTest` (3 tests): Paginated administrative company listing with active status filtering, keyword search, computed job and recruiter counts, and non-admin rejection.
  * `AdminJobControllerTest` (3 tests): Platform-wide cross-company job listing, inclusion of `ARCHIVED` status, exclusion of soft-deleted jobs (`deleted_at IS NULL`), status and company filtering, and RBAC enforcement.
  * `AdminApplicationControllerTest` (6 tests): Platform-wide application listing across all companies/jobs, application detail with immutable candidate and resume snapshots, complete status history, protected resume binary download and metadata inspection, and 404/RBAC enforcement.
  * `AdminAuditLogControllerTest` (4 tests): Read-only administrative audit log listing, pagination, deterministic `created_at DESC, id DESC` ordering, filtering by actor, action, entity type, entity ID, and mutation rejection.
  * `AdminSecurityTamperingTest` (3 tests): Privilege escalation protection against spoofed headers/parameters, platform-wide visibility verification (no recruiter company scoping inheritance), and credential leakage prevention.
  * `AdminSecurityBoundaryTest` (7 tests): `/api/admin/**` endpoint protection, authentication entry point, access denied handler, token validation.
  * `AuditLogRepositoryTest` (5 tests): Append-only audit record persistence, retrieval, and query filtering via Spring JdbcTemplate.
  * `AuditLogServiceTest` (13 tests): Server-authoritative admin actor identity resolution via `SecurityUtils.getRequiredAdminUserId()`, event payload validation, JSON serialization, and deterministic pagination.
  * `SecurityUtilsTest` (5 tests): Unit verification of security context resolution and role validation.


