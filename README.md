# CareerConnect
> **Candidate-First Early-Career Recruitment Platform**  
> *Final Academic Submission & Production Release (V1)*

[![Java](https://img.shields.io/badge/Java-21_LTS-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0_InnoDB-blue.svg)](https://www.mysql.com/)
[![React](https://img.shields.io/badge/React-19_TypeScript-blue.svg)](https://react.dev/)
[![Vite](https://img.shields.io/badge/Vite-8-purple.svg)](https://vitejs.dev/)
[![Backend Tests](https://img.shields.io/badge/Backend_Tests-680_Passed-success.svg)](#testing)
[![Frontend Tests](https://img.shields.io/badge/Frontend_Tests-178_Passed-success.svg)](#testing)

---

## 1. Overview & Problem Statement

CareerConnect is an institutional, candidate-first recruitment platform built specifically for early-career job seekers (students, interns, freshers, and professionals with 0–2 years of experience).

Traditional job platforms are optimized for experienced hires and enterprise headhunting. They burden early-career applicants with opaque keyword-filtering algorithms, confusing resume uploaders, and zero visibility into application status. Concurrently, recruiters are overwhelmed by unstructured applicant pools lacking transparent skill verification.

CareerConnect solves these challenges through:
* **Transparent Public Discovery**: Unrestricted search and filtering across verified companies and active entry-level jobs.
* **Deterministic Skill Matching**: Mathematical, transparent skill alignment calculation ($$\text{Match \%} = \text{round}\left(100 \times \frac{\text{Matched Required Skills}}{\text{Total Required Skills}}\right)$$) that provides clear feedback without black-box AI.
* **Authoritative State Machine**: Server-enforced application status lifecycle (`APPLIED` $\rightarrow$ `UNDER_REVIEW` $\rightarrow$ `SHORTLISTED` $\rightarrow$ `INTERVIEW` $\rightarrow$ `SELECTED` / `REJECTED` / `WITHDRAWN`).
* **Point-in-Time Immutable Snapshots**: Archival-grade JSON snapshots preserving candidate profiles and resumes as they existed at the exact moment of application.
* **Server-Authoritative Security**: Memory-only JWT architecture with strict ownership enforcement across Candidate, Recruiter, and Admin boundaries.

---

## 2. Key Architecture & Technology Stack

The platform is designed as a **Three-Tier Monolithic Modular Layered System**:

```
[ React 19 Frontend (TypeScript, Vite) ]
                    ↓  HTTP / REST (Memory-Only Bearer JWT)
[ Spring Boot 3 Presentation Layer (Controllers, Bean Validation, API Envelopes) ]
                    ↓  DTOs & Security Context
[ Spring Boot 3 Business Layer (Services, State Machine, Transaction Boundaries) ]
                    ↓  Parameterized SQL Queries
[ Spring Boot 3 Data Access Layer (JdbcTemplate Repositories, Custom RowMappers) ]
                    ↓  SQL (InnoDB, utf8mb4)
[ MySQL 8.0 Database (Normalized 3NF Schema, Flyway Migrations, JSON Snapshots) ]
```

### Core Architectural Invariants
* **Strictly No ORM / No JPA**: All database persistence is executed using explicit, parameterized SQL through Spring's `JdbcTemplate`. This eliminates Hibernate overhead, N+1 query problems, and lazy-loading runtime errors while demonstrating explicit relational database mastery.
* **Stateless Memory-Only Authentication**: JWT tokens are retained strictly in JavaScript memory during active user sessions. No tokens or sensitive authentication states are persisted to `localStorage`, `sessionStorage`, or cookies. Refreshing the browser intentionally clears the session.
* **Database Snapshots**: Relational tables store candidate profiles and resumes dynamically, but submitted applications store static JSON snapshots in MySQL `JSON` columns (`candidate_snapshot`, `resume_snapshot`), guaranteeing 100% historical immutability.

---

## 3. Technology Matrix

| Layer | Technologies & Tools |
| :--- | :--- |
| **Backend** | Java 21 LTS, Spring Boot 3.3.4, Spring MVC, Spring Security 6.3, Spring JDBC (`JdbcTemplate`), HikariCP, Flyway, Jakarta Bean Validation, Maven 3.9 |
| **Frontend** | React 19, TypeScript 5, Vite 8, React Router DOM v7, Axios, Material Symbols Outlined, Modern CSS Tokens (Stitch Design 2 System) |
| **Database** | MySQL 8.0 Community Server (InnoDB Engine, `utf8mb4` encoding) |
| **Testing** | JUnit 5 (Jupiter), AssertJ, Mockito, Spring Boot Test (`MockMvc`), Vitest, React Testing Library, JSDOM |

---

## 4. Repository Structure

```
.
├── backend/                             # Spring Boot 3.3.4 Application
│   ├── src/main/java/com/careerconnect/ # Java 21 Source Code
│   │   ├── admin/                       # Platform Moderation & Audit Module
│   │   ├── application/                 # Applications, Snapshots & Match Engine
│   │   ├── auth/                        # Registration & Login Endpoints
│   │   ├── candidate/                   # Candidate Profile, Education, Skills, Resumes
│   │   ├── common/                      # Exception Handling, Canonical Envelopes, Pagination
│   │   ├── company/                     # Company Profiles & Recruiter Onboarding
│   │   ├── config/                      # SecurityFilterChain, CORS, WebMvc Configuration
│   │   ├── jobs/                        # Job Creation, Query, Mutation & Lifecycle
│   │   ├── notification/                # In-App Notification Engine
│   │   └── security/                    # Stateless JWT Filter & Server SecurityUtils
│   ├── src/main/resources/
│   │   ├── application.yml              # Base Spring Boot Configuration
│   │   └── db/migration/                # Flyway Versioned Migration Scripts
│   ├── src/test/java/com/careerconnect/ # 680 Automated Regression Tests
│   ├── pom.xml                          # Maven Project Object Model
│   └── mvnw                             # Apache Maven Wrapper
├── frontend/                            # React 19 + TypeScript + Vite SPA
│   ├── src/
│   │   ├── api/                         # Typed Axios REST API Clients
│   │   ├── auth/                        # In-Memory AuthContext & Route Guards
│   │   ├── components/                  # Design System Components (Modals, Badges, Tables)
│   │   ├── pages/                       # Public, Candidate, Recruiter & Admin Views
│   │   └── routes/                      # React Router DOM Configuration
│   ├── package.json                     # Node.js Dependencies & Scripts
│   └── vite.config.ts                   # Vite Build Configuration
├── docs/                                # Academic & Architectural Documentation
│   ├── CAREERCONNECT_FINAL_ACADEMIC_REPORT.md # Comprehensive Final Academic Report
│   ├── ACADEMIC_TRACEABILITY_MATRIX.md  # 38-Point Academic Traceability Matrix
│   ├── API_CONTRACT.md                  # Complete REST API Contract Specification
│   ├── ARCHITECTURE.md                  # System Architecture Specification
│   ├── DATABASE_SCHEMA.md               # 17-Table Relational Schema & Invariants
│   ├── FINAL_DEMO_SCRIPT.md             # 8–12 Minute Live Demo Script
│   ├── FINAL_VIVA_PREPARATION.md        # Comprehensive Viva Voce Q&A Package
│   └── FINAL_SUBMISSION_CHECKLIST.md    # Pre-Flight Submission Verification Checklist
└── README.md                            # Primary Project Documentation
```

---

## 5. Setup & Execution Guide

### Prerequisites
* Java 21 LTS installed (`java -version`)
* Node.js v20+ and npm installed (`node -v`)
* MySQL Server 8.0 running on `localhost:3306`

### Database Setup
1. Open MySQL terminal or client:
   ```sql
   CREATE DATABASE careerconnect_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
2. Verify credentials match `backend/src/main/resources/application.yml` or define a local override `backend/application-local.yml`:
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/careerconnect_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
       username: root
       password: your_mysql_password
   ```

### Backend Execution
```bash
cd backend
# Run full automated test suite (680 tests)
./mvnw clean test

# Run Spring Boot backend server (starts on http://localhost:8080)
./mvnw spring-boot:run
```
*Note: Flyway will automatically execute database migrations (`V1__create_core_schema.sql`) on startup.*

### Frontend Execution
```bash
cd frontend
# Install dependencies
npm install

# Run frontend test suite (178 tests)
npm test -- --run

# Run TypeScript compilation check
npx tsc -b

# Start Vite development server (starts on http://localhost:5173)
npm run dev

# Build production bundle
npm run build
```

---

## 6. Testing & Quality Assurance

The platform adheres to rigorous testing standards across all tiers:

* **Backend Unit & Integration Tests**: **680 tests passed**, 0 failures, 0 errors, 0 skipped.
  ```bash
  cd backend && ./mvnw test
  ```
* **Frontend Component & Unit Tests**: **178 tests passed** across 36 test suites, 0 failures.
  ```bash
  cd frontend && npm test -- --run
  ```
* **TypeScript Compilation**: Clean compilation with zero warnings or errors.
  ```bash
  cd frontend && npx tsc -b
  ```
* **E2E & Adversarial Contract Verification**: 62 automated release gates passed, verifying cross-tenant isolation, state machine transitions, immutable snapshots, and admin self-deactivation protection.

---

## 7. Canonical API Envelopes

All REST endpoints return predictable, standardized JSON response structures:

### Success Response (`ApiResponse<T>`)
```json
{
  "success": true,
  "data": {
    "applicationId": 42,
    "status": "APPLIED",
    "appliedAt": "2026-10-20T02:08:50Z"
  },
  "message": null
}
```

### Paginated Success Response (`PageResult<T>`)
```json
{
  "success": true,
  "data": [ ... ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 84,
    "totalPages": 5,
    "isFirst": true,
    "isLast": false
  }
}
```

### Error Response (`ApiErrorResponse`)
```json
{
  "success": false,
  "data": null,
  "message": "A candidate may apply to a job at most once.",
  "errorCode": "DUPLICATE_APPLICATION"
}
```

---

## 8. Scope & Deferred Capabilities

### Implemented V1 Features
* Public job and company search, pagination, and detail viewing
* Stateless candidate, recruiter, and admin authentication
* Candidate profile completion (education, skills, projects, experience, PDF resume)
* Profile application readiness validation (`fullName`, $\ge 1$ education, $\ge 1$ skill, resume)
* Deterministic mathematical skill matching engine
* Single-application enforcement and candidate withdrawal
* Recruiter company onboarding and job publishing (`DRAFT` $\rightarrow$ `ACTIVE` $\rightarrow$ `CLOSED`)
* Recruiter applicant inspection, snapshot examination, and protected resume streaming
* Recruiter application status progression state machine
* In-app transactional notification center
* Admin moderation (user/company status toggle, job archival/restoration, audit logs)

### Intentionally Deferred
* **Saved Jobs**: Retained in database schema for future enhancement, but client-side UI and API endpoints are intentionally deferred from V1.

---

## 9. Academic Documentation & Viva Preparation

Comprehensive academic artifacts are located in the `docs/` directory:
* [Final Academic Report](file:///Users/purne/Desktop/FSWD-Project/docs/CAREERCONNECT_FINAL_ACADEMIC_REPORT.md): In-depth 20-section report covering all theoretical and technical requirements.
* [Academic Traceability Matrix](file:///Users/purne/Desktop/FSWD-Project/docs/ACADEMIC_TRACEABILITY_MATRIX.md): Detailed 38-point mapping of academic requirements to production code.
* [Live Demo Script](file:///Users/purne/Desktop/FSWD-Project/docs/FINAL_DEMO_SCRIPT.md): 8–12 minute structured presentation guide with talking points.
* [Viva Voce Preparation](file:///Users/purne/Desktop/FSWD-Project/docs/FINAL_VIVA_PREPARATION.md): Comprehensive Q&A covering Spring Boot, JDBC, Security, and State Machines.
* [Submission Checklist](file:///Users/purne/Desktop/FSWD-Project/docs/FINAL_SUBMISSION_CHECKLIST.md): Verification matrix confirming submission readiness.

---

## 10. License & Academic Attribution
Developed as an academic capstone and full-stack software engineering project demonstrating enterprise Spring Boot and React software architecture. All code is original and verified.
