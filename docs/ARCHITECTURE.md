# CareerConnect — System Architecture Document

**Document:** System Architecture Specification  
**Project:** CareerConnect  
**Phase:** Phase 1 Foundation  
**Authority:** `CAREERCONNECT_MASTER_SPECIFICATION.md`  

---

## 1. System Overview

CareerConnect is built as a clean modular monolithic backend in Spring Boot 3 with Java 21, communicating with MySQL 8.0 via `JdbcTemplate`, designed to serve an independently developed React frontend.

```
+-------------------------------------------------------------+
|                      React Frontend                         |
|        (TypeScript, Vite, In-Memory JWT Authentication)     |
+-------------------------------------------------------------+
                              |
                              | HTTP / JSON (Bearer JWT)
                              v
+-------------------------------------------------------------+
|                     Presentation Layer                      |
|           Controllers, Global Exception Handler,            |
|                Bean Validation, Canonical DTOs              |
+-------------------------------------------------------------+
                              |
                              | DTOs & Identity
                              v
+-------------------------------------------------------------+
|                       Business Layer                        |
|              Domain Services, State Machines,               |
|         Deterministic Matcher, Transaction Boundaries       |
+-------------------------------------------------------------+
                              |
                              | Parameterized SQL / Entities
                              v
+-------------------------------------------------------------+
|                     Data Access Layer                       |
|           JdbcTemplate Repositories, Explicit RowMappers    |
+-------------------------------------------------------------+
                              |
                              | SQL (InnoDB, utf8mb4)
                              v
+-------------------------------------------------------------+
|                      MySQL 8.0 Database                     |
|           careerconnect_db (Versioned via Flyway)           |
+-------------------------------------------------------------+
```

---

## 2. Core Architectural Invariants

1. **No ORM / No JPA:** All queries are explicit, readable, parameterized SQL statements executed via Spring's `JdbcTemplate`.
2. **Three-Tier Strict Direction:**
   - Controllers never contain SQL or business rules, and never call repositories directly.
   - Services enforce all business rules, authorization, transaction boundaries (`@Transactional`), and application status state machines.
   - Repositories execute SQL and map rows via `RowMapper`. Repositories do not perform business authorization.
3. **Identity from Authentication Token Only:** Request body IDs (`candidateId`, `recruiterId`, `companyId`) are never trusted for authorization.
4. **Immutable Application Snapshots:** Candidate profile and resume information are captured as JSON snapshots at submission time. Historical records are never modified when candidate profile changes.
5. **Deterministic Matching Engine:** Skill match percentage is strictly:
   $$\text{round}\left(100 \times \frac{\text{matchedRequiredSkills}}{\text{totalRequiredSkills}}\right)$$
   No AI, ML, recommendation engines, or semantic fuzziness.

---

## 3. Package Structure

```
com.careerconnect
├── CareerConnectApplication.java
├── config/                 # SecurityConfig, CorsConfig, DatabaseConfig
├── common/                 # Reusable cross-cutting infrastructure
│   ├── response/          # ApiResponse<T>, ApiErrorResponse, PaginationMetadata
│   ├── pagination/        # PageRequestDto, PageResult<T>
│   ├── exception/         # GlobalExceptionHandler, ErrorCode, Custom Exceptions
│   └── util/              # DateTimeUtils
├── health/                 # Health feature module
│   ├── controller/        # HealthController (GET /api/health)
│   ├── service/           # HealthService
│   ├── repository/        # HealthRepository (JdbcTemplate)
│   └── dto/               # HealthResponse
├── auth/                   # Phase 2
├── candidate/              # Phase 3
├── jobs/                   # Phase 4
├── applications/           # Phase 5
├── company/                # Phase 4 & 6
├── recruiter/              # Phase 6
├── matching/               # Deterministic Matcher
├── notifications/          # In-app notifications
└── admin/                  # Phase 7
```
