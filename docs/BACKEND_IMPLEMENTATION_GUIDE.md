# CareerConnect — Backend Implementation Guide

**Document:** Backend Implementation Guide  
**Project:** CareerConnect  
**Phase:** Phase 5 Complete (Applications, Deterministic Matching, Immutable Snapshots, Lifecycle State Machine)
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

3. **Run Automated Test Suite (All 144 Tests):**
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

---

## 4. Academic Traceability & Automated Testing

All **300 automated tests** in the test suite verify genuine application functionality:

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
  * `CareerConnectApplicationTests` (1 test): Spring context bootstrapping with Flyway migration execution.
