# CareerConnect — Backend Implementation Guide

**Document:** Backend Implementation Guide  
**Project:** CareerConnect  
**Phase:** Phase 3 Complete (Public Discovery & Jobs, Auth & Security)  
**Authority:** `CAREERCONNECT_MASTER_SPECIFICATION.md`  

---

## 1. Quick Start & Prerequisites

* **Runtime:** Java 21 LTS.
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

3. **Run Automated Test Suite (All 49 Tests):**
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
├── JobService (Public job search, filtering, sorting, zero-based pagination, and active-status invariants)
├── CompanyService (Public company discovery, active status invariants, active job aggregation)
├── AuthService (Transactional user registration, role provisioning, credential verification, JWT issuance)
├── HealthService (System & database health check)
└── AdminUserInitializer (CommandLineRunner provisioning initial secure admin)
       |
       | Parameterized SQL & Explicit RowMappers
       v
Data Access Layer (JdbcTemplate - Zero ORM)
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

### A. Public Discovery Endpoints (Phase 3)

| Method | Path | Access | Query Parameters | Success Response | Error Codes |
|---|---|---|---|---|---|
| `GET` | `/api/jobs` | Public | `q, location, jobType, experienceLevel, workMode, skill, minSalaryLpa, sort, page, size` | `200 OK` (Paginated `JobSummaryDto[]`) | `VALIDATION_FAILED` |
| `GET` | `/api/jobs/{id}` | Public | None | `200 OK` (`JobDetailDto`) | `JOB_NOT_FOUND` |
| `GET` | `/api/companies` | Public | `q, page, size` | `200 OK` (Paginated `CompanySummaryDto[]`) | `VALIDATION_FAILED` |
| `GET` | `/api/companies/{id}` | Public | None | `200 OK` (`CompanyDetailDto` with `activeJobs`) | `RESOURCE_NOT_FOUND` |

### B. Authentication Module Endpoints (Phase 2)

| Method | Path | Access | Request Body | Success Response | Error Codes |
|---|---|---|---|---|---|
| `POST` | `/api/auth/register` | Public | `{ email, password, role, fullName }` | `201 Created` (`AuthResponse`) | `VALIDATION_FAILED`, `EMAIL_ALREADY_EXISTS` |
| `POST` | `/api/auth/login` | Public | `{ email, password }` | `200 OK` (`AuthResponse`) | `VALIDATION_FAILED`, `INVALID_CREDENTIALS`, `FORBIDDEN` |
| `GET` | `/api/auth/me` | Authenticated | None (`Authorization: Bearer <token>`) | `200 OK` (`AuthUserDto`) | `UNAUTHENTICATED`, `FORBIDDEN` |
| `POST` | `/api/auth/logout` | Authenticated | None (`Authorization: Bearer <token>`) | `204 No Content` | `UNAUTHENTICATED` |

---

## 4. Academic Traceability & Automated Testing

All 49 automated tests in the test suite verify genuine application functionality:

* `JobControllerTest` (13 tests): Public active job pagination, search filtering (`q`), location filtering, job type filtering, experience level filtering, work mode filtering, required skill filtering, minimum salary threshold filtering, deadline sorting (`asc`), salary sorting (`desc`), complete job detail retrieval with nested skills/company, strict 404 rejection for non-public jobs (`DRAFT`, `CLOSED`, `ARCHIVED`, `EXPIRED`, `DELETED`, `INACTIVE_COMPANY`), and 404 on nonexistent job ID.
* `CompanyControllerTest` (6 tests): Active company pagination, keyword search, accurate active job counting, complete company detail with nested active jobs, strict 404 exclusion on inactive company direct access, and 404 on nonexistent company ID.
* `AuthControllerTest` (14 tests): Candidate registration, recruiter registration, admin self-registration rejection, duplicate email handling (case-insensitive), email format validation, password length validation, valid/invalid login, token verification, `/auth/me` protected retrieval, and 204 logout.
* `JwtServiceTest` (4 tests): Cryptographic secret key enforcement, claim extraction, expiration timestamping, and tampered token rejection.
* `GlobalExceptionHandlerTest` (4 tests): Verification of canonical `ApiErrorResponse` format and accurate HTTP status code mapping (400, 404, 409, 422).
* `ApiResponseContractTest` (3 tests): Strict conformity of single-item and paginated collection envelopes with the Master Specification.
* `PageRequestDtoTest` (3 tests): Default page/size, offset calculation, and upper bound capping (max 100).
* `HealthControllerTest` (1 test): Live end-to-end Three-Tier request flow and database connectivity check.
* `CareerConnectApplicationTests` (1 test): Spring context bootstrapping with Flyway migration execution.

