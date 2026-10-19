# CareerConnect — Viva Voce & Technical Defense Preparation Guide

**Project:** CareerConnect — Early-Career Recruitment Platform  
**Target:** Academic Viva Voce, Technical Evaluation & Architecture Review  
**Baseline:** Spring Boot 3.3.4 (Java 21) + React 18 (TypeScript) + MySQL 8.0 (JdbcTemplate)

---

## 1. Spring Boot & Core Framework

### Q1: What is Spring Boot, and why was it chosen over standard Spring MVC?
> **Answer:** Spring Boot is an opinionated extension of the Spring framework that simplifies application configuration and deployment. It provides embedded web servers (Apache Tomcat), pre-configured starter dependencies (`spring-boot-starter-web`, `spring-boot-starter-jdbc`), and automatic configuration based on classpath contents. We chose Spring Boot because it allows rapid setup of production-ready REST services without requiring complex XML configuration or external servlet container deployment, while retaining enterprise-grade dependency injection, transaction management, and security.

### Q2: What is Spring Boot Auto-Configuration and how does it work in CareerConnect?
> **Answer:** Auto-configuration automatically configures beans in the Spring Application Context based on the JAR libraries present on the runtime classpath. It is triggered by the `@SpringBootApplication` annotation (which meta-annotates `@EnableAutoConfiguration`).
> In CareerConnect:
> - Because `spring-boot-starter-web` is on the classpath, Spring Boot auto-configures `DispatcherServlet`, Jackson `ObjectMapper`, and embedded Tomcat.
> - Because `spring-boot-starter-jdbc` and `mysql-connector-j` are present, it auto-configures a `HikariDataSource` pool and injects `JdbcTemplate` using the database properties defined in `application.yml`.
> - Because `flyway-core` is present, it auto-configures Flyway to run database migrations on startup.
> - Because `spring-boot-starter-security` is present, it establishes the default security filter chain, which we selectively override in `SecurityConfig.java`.

### Q3: What is Dependency Injection (DI) and Inversion of Control (IoC)? How is it applied?
> **Answer:** Inversion of Control is an architectural principle where the control of object creation and lifecycle management is transferred from the program code to an external container (the Spring IoC container). Dependency Injection is the pattern used to supply dependencies to a class rather than the class instantiating them itself.
> In CareerConnect, we use constructor-based injection (e.g., `JobService` receives `JobRepository` via its constructor with `@Autowired` or Lombok/standard Java constructors). This enables loose coupling, improves testability (allowing repositories to be easily mocked in JUnit tests), and guarantees immutability of service dependencies via `private final` fields.

---

## 2. Three-Tier System Architecture

### Q4: Explain the Three-Tier Architecture of CareerConnect.
> **Answer:** CareerConnect is structured into three discrete, decoupled tiers:
> 1. **Presentation Tier (Frontend & Controllers):** The React 18 TypeScript Single-Page Application (SPA) communicates over HTTP via REST APIs handled by Spring `@RestController` classes (`JobController`, `ApplicationController`). Controllers handle HTTP deserialization, payload validation (`@Valid`), status codes, and JSON response enveloping (`ApiResponse<T>`).
> 2. **Business Tier (Service Layer):** Implemented by `@Service` classes (`ApplicationService`, `JobService`, `CandidateProfileService`). Contains all business logic, validation rules, state machine transitions, and database transaction boundaries (`@Transactional`).
> 3. **Data Access Tier (Repository Layer):** Implemented by `@Repository` classes (`JobRepository`, `ApplicationRepository`). Directly executes parameterized SQL queries against MySQL using Spring's `JdbcTemplate`.

```
[React 18 SPA]
      │ HTTP REST
      ▼
[Controller Tier]  <-- HTTP, DTO Validation, JSON Envelope
      │ Method Calls
      ▼
[Service Tier]     <-- Business Rules, State Machines, @Transactional
      │ SQL Calls
      ▼
[Repository Tier]  <-- Parameterized SQL via JdbcTemplate
      │ JDBC / TCP
      ▼
[MySQL 8.0 Engine]
```

### Q5: Why should business logic never reside in Controllers?
> **Answer:** 
> 1. **Separation of Concerns:** Controllers should only be concerned with HTTP protocol concerns (headers, routing, deserialization, response formatting).
> 2. **Reusability:** Business logic placed in a Service can be invoked by multiple controllers (e.g., candidate vs. admin controllers) or scheduled background tasks without code duplication.
> 3. **Transaction Management:** Spring's `@Transactional` boundary works via Spring AOP proxies at the service layer. Placing business rules and DB operations across controller boundaries compromises transaction rollback guarantees.
> 4. **Testability:** Service classes can be unit-tested in isolation without mocking HTTP request/response objects or starting a web environment.

---

## 3. Database & Persistence Layer

### Q6: Why did you choose MySQL and Spring `JdbcTemplate` instead of JPA / Hibernate?
> **Answer:** We intentionally avoided JPA/Hibernate for deliberate engineering reasons:
> 1. **Explicit Performance & No Hidden Latency:** JPA frequently introduces unpredictable lazy-loading issues, unintended N+1 queries, and detached entity complexities. With `JdbcTemplate`, every SQL query executed against MySQL is explicit, human-readable, and verifiable.
> 2. **Deterministic Data Snapshots:** Capturing application snapshots as immutable JSON columns (`candidate_snapshot`, `resume_snapshot`) is straightforward with direct SQL without wrestling with ORM cascading states.
> 3. **SQL Injection Immunity via Parameterization:** `JdbcTemplate` enforces parameterized queries (`PreparedStatement`), eliminating SQL injection vulnerabilities natively.
> 4. **Transparent Database Schema Control:** Database evolution is strictly handled through Flyway versioned migrations (`V1__...` to `V5__...`), ensuring the schema is identical in local development, testing, and CI/CD.

### Q7: What are Foreign Keys and Normalization, and how are they implemented in CareerConnect?
> **Answer:** 
> - **Normalization:** We organized the relational database to Third Normal Form (3NF) to eliminate data redundancy and insertion/deletion anomalies. For instance, candidate skills are modeled through a normalized junction table (`candidate_skills` connecting `candidates` and `skills`), rather than storing comma-delimited strings in the candidate table.
> - **Foreign Keys:** Enforce referential integrity at the database engine level (InnoDB). For example, `applications.job_id` references `jobs.id(ON DELETE RESTRICT)`, preventing orphaned application records if a job is targeted for deletion. Similarly, cascade rules (`ON DELETE CASCADE`) are applied to child entity tables like `candidate_skills` and `education` when a candidate profile is deleted.

---

## 4. Security & Authentication

### Q8: How does JWT Authentication work in CareerConnect?
> **Answer:** 
> 1. The client sends user credentials to `POST /api/auth/login`.
> 2. `AuthService` verifies the user's email and checks the password hash against MySQL using BCrypt (`BCryptPasswordEncoder`).
> 3. Upon success, a stateless JSON Web Token (JWT) is generated containing the user's ID, email, role (`ROLE_CANDIDATE`, `ROLE_RECRUITER`, `ROLE_ADMIN`), issued timestamp, and 30-minute expiration timestamp, cryptographically signed with HMAC-SHA256 (`HS256`).
> 4. On subsequent requests, the client passes this token in the `Authorization: Bearer <token>` header.
> 5. `JwtAuthenticationFilter` intercepts the request, verifies the signature and expiration, extracts user claims, and sets an authenticated `UsernamePasswordAuthenticationToken` in the `SecurityContextHolder`.

### Q9: Why is token storage in JavaScript runtime memory safer than `localStorage` or `sessionStorage`?
> **Answer:** `localStorage` and `sessionStorage` are globally accessible to any JavaScript code executing on the domain. If an application suffers from a Cross-Site Scripting (XSS) vulnerability or a compromised third-party script, malicious code can read `localStorage.getItem("token")` and exfiltrate the JWT. Storing the token in memory (inside the React auth context / closures) prevents persistent client-side storage extraction.

### Q10: Why is client-side route protection (e.g., React `ProtectedRoute`) not a security boundary?
> **Answer:** Frontend code runs on an untrusted client environment. Any user can manipulate browser memory, disable JavaScript, or issue HTTP requests directly using cURL or Postman. Client-side route guards exist exclusively for User Experience (UX)—redirecting users to login pages. True security and authorization are strictly enforced server-side using Spring Security (`@PreAuthorize("hasRole('ADMIN')")`, security filter chains, and ownership validation in Service classes).

---

## 5. Service Layer Business Rules & Domain Logic

### Q11: Explain the Candidate Application Readiness business rule.
> **Answer:** In `CandidateProfileService` / `ApplicationService`, a candidate cannot apply to any job unless four mandatory criteria are verified:
> 1. Full name is non-empty.
> 2. At least one education record exists in `education`.
> 3. At least one skill record exists in `candidate_skills`.
> 4. A primary resume is uploaded and recorded in `resumes`.
> If any criterion is missing, the service throws `BusinessRuleException` with error code `PROFILE_INCOMPLETE`, returning HTTP 422 with a specific list of missing profile requirements.

### Q12: How is duplicate application prevention enforced?
> **Answer:** Enforced through a two-layer defense:
> 1. **Service Layer Check:** Before inserting, `ApplicationService` queries `ApplicationRepository.findByCandidateAndJob(candidateId, jobId)`. If any record exists, it immediately rejects the request with HTTP 409 (`DUPLICATE_APPLICATION`).
> 2. **Database Engine Unique Constraint:** The `applications` table has a composite unique constraint `UNIQUE KEY uq_app_candidate_job (candidate_id, job_id)`. Even in high-concurrency race conditions, the database guarantees no duplicate row can ever be inserted.
> If a candidate withdraws an application, the row remains with status `WITHDRAWN`, deliberately preventing re-application to the same job.

### Q13: Why are Application Snapshots immutable, and why does this matter academically?
> **Answer:** When an application is created, `ApplicationService` serializes the candidate's current profile, education history, skills, and resume metadata into JSON stored in the `candidate_snapshot` and `resume_snapshot` columns of `applications`.
> - If a candidate later adds a master's degree, changes their phone number, or uploads a new resume, the recruiter sees the exact data that existed when the candidate applied.
> - Academically and legally, this demonstrates auditability, non-repudiation, and historical point-in-time consistency.

### Q14: Explain the Application Status State Machine. Why can't recruiters set `WITHDRAWN`?
> **Answer:** Application status transitions follow a deterministic state machine:
> - Initial state: `APPLIED`.
> - Valid transitions: `APPLIED` $\rightarrow$ `UNDER_REVIEW` $\rightarrow$ `SHORTLISTED` $\rightarrow$ `INTERVIEW` $\rightarrow$ `SELECTED`.
> - Terminal states: `SELECTED`, `REJECTED`, `WITHDRAWN`.
> - Rejection (`REJECTED`) is allowed from any active non-terminal state.
> - `WITHDRAWN` is strictly an actor-specific transition: only the candidate who owns the application can invoke `PUT /api/candidate/applications/{id}/withdraw`. Recruiters cannot set an application to `WITHDRAWN`, as that would allow a recruiter to falsify candidate intent.

```
                  ┌────────────────────────┐
                  │        APPLIED         │
                  └───────────┬────────────┘
                              │
                              ▼
                  ┌────────────────────────┐
                  │      UNDER_REVIEW      │
                  └───────────┬────────────┘
                              │
                              ▼
                  ┌────────────────────────┐
                  │      SHORTLISTED       │
                  └───────────┬────────────┘
                              │
                              ▼
                  ┌────────────────────────┐
                  │       INTERVIEW        │
                  └───────────┬────────────┘
                              │
                              ▼
                  ┌────────────────────────┐
                  │        SELECTED        │ (Terminal)
                  └────────────────────────┘

From any non-terminal state:
  ├── Recruiter action ──► [REJECTED]  (Terminal)
  └── Candidate action ──► [WITHDRAWN] (Terminal)
```

---

## 6. Deterministic Skill Matching Engine

### Q15: How does CareerConnect calculate the job match percentage? Is AI or Machine Learning used?
> **Answer:** CareerConnect uses a 100% deterministic, transparent mathematical algorithm implemented in `JobMatchingService`. Zero AI or black-box machine learning is used in V1.
> The formula is:
> $$\text{Match Percentage} = \text{round}\left(100 \times \frac{|\text{Candidate Skills} \cap \text{Job Required Skills}|}{|\text{Job Required Skills}|}\right)$$
> Skills are normalized by trimming leading/trailing whitespace and converting to lowercase (`skill.trim().toLowerCase()`). The service returns:
> - Match percentage (0–100%).
> - List of matched skills.
> - List of missing required skills.
> - Additional candidate skills.
> This approach ensures complete transparency, auditability, and zero algorithmic bias.

---

## 7. Protected Binary Resources & Resume Handling

### Q16: How are candidate resumes stored and securely served?
> **Answer:** 
> 1. Resumes are stored on the server's protected filesystem with randomized UUID filenames (e.g., `resume-uuid.pdf`), decoupled from public static asset directories.
> 2. Database records store file metadata (original filename, file path, size, MIME type).
> 3. Downloading requires authenticated access via `GET /api/candidate/applications/{id}/resume` or candidate profile resume endpoints.
> 4. `ResumeService` verifies authorization:
>    - If the user is a Candidate, they must own the resume.
>    - If the user is a Recruiter, the resume must belong to an application submitted to a job owned by that recruiter's company.
>    - If the user is an Admin, access is granted for moderation.
> 5. The file is streamed as binary bytes (`Resource` / `byte[]`) with `Content-Type: application/pdf` and `Content-Disposition: inline`.
> 6. On the frontend, the binary stream is converted into a temporary browser `Blob` URL (`URL.createObjectURL(blob)`), guaranteeing that raw file paths are never exposed.

---

## 8. Automated Testing & Verification

### Q17: Summarize the automated testing strategy and verified metrics.
> **Answer:** CareerConnect was verified through a multi-tier testing pyramid:
> - **Backend Tests:** 680 JUnit 5 and Spring Boot integration tests:
>   - Controller tests using `MockMvc` for HTTP status codes, security filters, and JSON serialization.
>   - Service tests with Mockito for business rule enforcement and state machines.
>   - Repository tests using Spring's `JdbcTemplate` for parameterized SQL correctness.
>   - Pass rate: **680 tests passed, 0 failures, 0 errors, 0 skipped**.
> - **Frontend Tests:** 178 Vitest and React Testing Library tests:
>   - Component rendering, form validation, error handling, route guards, and notification state.
>   - Pass rate: **178 tests passed across 36 test suites, 0 failures**.
> - **End-to-End Contract Verification:** 62 automated curl integration gates executing against live Spring Boot and MySQL containers, testing full user journeys, adversarial role violations, and database integrity.

---

## 9. Scope Boundaries & Project Discipline

### Q18: What is the status of the "Saved Jobs" feature?
> **Answer:** The `saved_jobs` table exists in the MySQL database schema (implemented in Flyway migration `V1__init_schema.sql`), but the backend REST endpoints and frontend UI interactions were **intentionally deferred** from V1 scope to prioritize core application workflows, deterministic matching, and security verification. It is cataloged as a known deferred feature and will be implemented in V2.

### Q19: What features are classified as Future Enhancements?
> **Answer:** The following features are explicitly out of V1 scope:
> - Email verification and automated SMTP dispatch.
> - Password reset via email token.
> - Recruiter business verification workflows.
> - Multi-member recruiter teams and role delegation.
> - Live chat and candidate-recruiter messaging.
> - AI resume parsing and semantic ranking algorithms.
> - Interview calendar scheduling (Google Meet / Zoom integration).
> - Online coding assessments and technical quizzes.
> - Payment gateways and premium job posting sponsorships.
> - Native mobile applications (iOS/Android).
