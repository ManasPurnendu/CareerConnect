# CareerConnect — Backend Implementation Guide

**Document:** Backend Implementation Guide  
**Project:** CareerConnect  
**Phase:** Phase 2 Complete (Authentication, JWT & Security Foundation)  
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

3. **Run Automated Test Suite (All 30 Tests):**
   ```bash
   ./mvnw clean test
   ```

4. **Start the Application:**
   ```bash
   ./mvnw spring-boot:run
   ```
   * Flyway automatically executes migrations on startup and brings `careerconnect_db` up to date.
   * Default administrator account is provisioned: `admin@careerconnect.com` / `Admin@123456`.
   * Access health check endpoint:
     ```bash
     curl http://localhost:8080/api/health
     ```

---

## 2. Implemented Architecture & Modules

```
React Client (Vite)
       |
       | REST API (HTTP / JSON)
       v
Presentation Layer
├── AuthController (POST /register, POST /login, GET /me, POST /logout)
├── HealthController (GET /api/health)
├── GlobalExceptionHandler (@RestControllerAdvice for 400, 401, 403, 404, 409, 422, 500)
└── SecurityFilter (JwtAuthenticationFilter, RestAuthenticationEntryPoint, RestAccessDeniedHandler)
       |
       | Validated DTOs & Authenticated SecurityUser
       v
Business Service Layer
├── AuthService (Transactional user registration, role provisioning, credential verification, JWT issuance)
├── HealthService (System & database health check)
└── AdminUserInitializer (CommandLineRunner provisioning initial secure admin)
       |
       | Parameterized SQL & Explicit RowMappers
       v
Data Access Layer (JdbcTemplate - Zero ORM)
├── UserRepository (users table)
├── CandidateAuthRepository (candidates table)
├── RecruiterAuthRepository (recruiters table)
└── HealthRepository (SELECT 1 ping)
       |
       v
MySQL Database (careerconnect_db)
```

---

## 3. Phase 2: Authentication Module Specifications

### A. Endpoints

| Method | Path | Access | Request Body | Success Response | Error Codes |
|---|---|---|---|---|---|
| `POST` | `/api/auth/register` | Public | `{ email, password, role, fullName }` | `201 Created` (`AuthResponse`) | `VALIDATION_FAILED`, `EMAIL_ALREADY_EXISTS` |
| `POST` | `/api/auth/login` | Public | `{ email, password }` | `200 OK` (`AuthResponse`) | `VALIDATION_FAILED`, `INVALID_CREDENTIALS`, `FORBIDDEN` |
| `GET` | `/api/auth/me` | Authenticated | None (`Authorization: Bearer <token>`) | `200 OK` (`AuthUserDto`) | `UNAUTHENTICATED`, `FORBIDDEN` |
| `POST` | `/api/auth/logout` | Authenticated | None (`Authorization: Bearer <token>`) | `204 No Content` | `UNAUTHENTICATED` |

### B. Security & Token Invariants

* **Stateless Token:** HMAC-SHA256 signed access token (JJWT), 30-minute validity.
* **Claims:** `userId`, `email`, `role`, `fullName`, `candidateId` (if candidate), `recruiterId` and `companyId` (if recruiter).
* **Storage Contract:** Held strictly in frontend memory (no cookies, no localStorage/sessionStorage, no refresh token in V1).
* **Password Encryption:** Passwords hashed with `BCryptPasswordEncoder(12)`. Password hashes are **never returned in any DTO or log**.
* **Identity Resolution:** Server-side `SecurityUtils` extracts verified identity from `SecurityContextHolder`. Request body IDs are never trusted.

---

## 4. Academic Traceability Summary

All 30 automated tests in the test suite verify genuine application functionality:

* `AuthControllerTest` (14 tests): Candidate registration, recruiter registration, admin self-registration rejection, duplicate email handling (case-insensitive), email format validation, password length validation, valid/invalid login, token verification, `/auth/me` protected retrieval, and 204 logout.
* `JwtServiceTest` (4 tests): Cryptographic secret key enforcement, claim extraction, expiration timestamping, and tampered token rejection.
* `GlobalExceptionHandlerTest` (4 tests): Verification of canonical `ApiErrorResponse` format and accurate HTTP status code mapping (400, 404, 409, 422).
* `ApiResponseContractTest` (3 tests): Strict conformity of single-item and paginated collection envelopes with the Master Specification.
* `PageRequestDtoTest` (3 tests): Default page/size, offset calculation, and upper bound capping (max 100).
* `HealthControllerTest` (1 test): Live end-to-end Three-Tier request flow and database connectivity check.
* `CareerConnectApplicationTests` (1 test): Spring context bootstrapping with Flyway migration execution.
