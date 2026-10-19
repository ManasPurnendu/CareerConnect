# CareerConnect — Final Demonstration Script & Presentation Guide

**Platform:** CareerConnect — Early-Career Recruitment Platform  
**Target Duration:** 8–12 Minutes  
**Audience:** Academic Evaluators, Industry Reviewers, Technical Viva Panel  
**Design System:** Stitch Design 2 (Emerald / Minimalist Warm Off-White / Inter & Sans-Serif)  
**Verification Baseline:** Spring Boot 3.3.4 (680 backend tests) + React 18 / Vite (178 frontend tests)

---

## 1. Demonstration Timing & Overview

| Step | Time | Section | Primary Actor | Key Technical Concept |
|:---:|:---:|:---|:---|:---|
| **1–3** | 0:00–1:45 | Platform Intro, Problem & Architecture | Presenter | High-Level Vision, 3-Tier Architecture, Spring Boot Auto-config |
| **4** | 1:45–2:45 | Public Job Discovery & Filtering | Guest | Unauthenticated REST API, Parameterized SQL Filtering |
| **5–6** | 2:45–4:15 | Candidate Profile & Readiness Checklist | Candidate | JWT Bearer Auth, Readiness Business Rule, Resume Upload |
| **7–9** | 4:15–5:45 | Deterministic Job Match & Application Flow | Candidate | Exact Match Formula, Snapshot Persistence, Duplicate Guard |
| **10–12** | 5:45–7:15 | Recruiter Setup & Job Lifecycle | Recruiter | Company Binding Rule, Job Draft/Publish State Machine |
| **13–15** | 7:15–8:45 | Applicant Review, Status Transition & Resume | Recruiter | Application State Machine, Protected Resume Access |
| **16–18** | 8:45–10:15 | Admin Moderation & Append-Only Audit Logs | Admin | Global Moderation, Self-Deactivation Guard, Audit Logging |
| **19–20** | 10:15–11:30 | Technical Test Evidence & Closing Summary | Presenter | 680 Backend / 178 Frontend Tests, Zero JPA, V1 Freeze |

---

## 2. Step-by-Step Demo Script (20 Steps)

### Step 1: Project Introduction
- **What to click / show:** Open browser to landing page (`http://localhost:5173/`).
- **What to say:** "Welcome. Today I am presenting CareerConnect, an early-career recruitment platform engineered specifically for students, fresh graduates, and recruiters. Unlike generalist job boards that overwhelm entry-level candidates, CareerConnect enforces complete transparency with deterministic skill matching, strict profile readiness criteria, and immutable application records."
- **Technical concept demonstrated:** Modern Single-Page Application (SPA) initialization, responsive Stitch Design 2 aesthetic.
- **Academic requirement satisfied:** Application/problem domain introduction, Presentation Tier presentation.

---

### Step 2: Problem Statement & Early-Career Focus
- **What to click / show:** Scroll through landing page value propositions and call-to-action buttons.
- **What to say:** "Early-career applicants face three chronic issues: opaque application statuses, black-box AI scoring that filters candidates silently, and fragmented job postings. CareerConnect solves this by offering deterministic skill matching—meaning candidates see the exact mathematical percentage and missing skills—alongside guaranteed real-time status transitions and candidate data snapshots."
- **Technical concept demonstrated:** Domain-specific problem modeling without synthetic or opaque black-box algorithms.
- **Academic requirement satisfied:** Problem statement definition, domain alignment.

---

### Step 3: Three-Tier System Architecture
- **What to click / show:** Show architectural slide or terminal / devtools showing API calls.
- **What to say:** "CareerConnect follows an enterprise Three-Tier Architecture:
  1. **Presentation Tier:** React 18 with TypeScript, Vite, Tailwind CSS, and in-memory JWT authentication.
  2. **Business Tier:** Spring Boot 3.3.4 REST Controllers, Service Layer enforcing deterministic business rules and validation.
  3. **Data Access Tier:** Spring `JdbcTemplate` with raw, parameterized SQL executing against MySQL 8.0, managed via Flyway migrations.
  Notice that we deliberately avoided JPA/Hibernate to prevent hidden N+1 query latency and maintain full control over SQL execution plans."
- **Technical concept demonstrated:** Strict layer decoupling (Presentation $\rightarrow$ Business $\rightarrow$ Data Access), Spring Boot Auto-Configuration, explicit JDBC data access.
- **Academic requirement satisfied:** Three-tier architecture, Spring Boot auto-configuration, Controller $\rightarrow$ Service $\rightarrow$ Repository design pattern.

---

### Step 4: Public Job Discovery & Filtering
- **What to click:** Click on "Explore Jobs" (`/jobs`). Use search input to filter by "Software Engineer" or filter by Job Type ("Full Time") and Experience Level ("Entry Level").
- **What to show:** Public job cards updating instantly. Click on a job card to open `/jobs/:id`.
- **What to say:** "Notice that guests can discover active job listings without authenticating. When we search or filter, the frontend sends a query to `GET /api/jobs` with sanitized query parameters. The repository executes parameterized SQL with pagination and filter clauses, ensuring SQL injection prevention."
- **Technical concept demonstrated:** Public REST endpoints (`GET /api/jobs`), SQL parameterized filtering, responsive UI rendering.
- **Academic requirement satisfied:** Public REST endpoint, data retrieval layer, presentation tier data binding.

---

### Step 5: Candidate Registration & Login
- **What to click:** Click "Sign In" (`/login`), or "Sign Up" (`/register`). Log in with verified candidate credentials (`candidate1@careerconnect.local` / `Candidate@123`).
- **What to show:** Instant redirection to `/candidate/dashboard`. Token stored strictly in JavaScript runtime memory.
- **What to say:** "Upon submitting credentials, `POST /api/auth/login` validates the BCrypt password hash. A cryptographically signed HMAC-SHA256 JWT access token (30-minute validity) is returned and kept strictly in memory—never in `localStorage` or `sessionStorage`—mitigating Cross-Site Scripting (XSS) token exfiltration attacks."
- **Technical concept demonstrated:** Spring Security, BCrypt hashing, JWT stateless authentication, token storage security.
- **Academic requirement satisfied:** Authentication mechanism, secure credential handling, REST Controller $\rightarrow$ Service flow.

---

### Step 6: Candidate Profile & Application Readiness Checklist
- **What to click:** Navigate to "My Profile" (`/candidate/profile`).
- **What to show:** Profile summary, Education records, Skill chips, and the Resume section. Show the "Application Readiness" widget displaying 100% (or all green checkmarks).
- **What to say:** "A core business rule of CareerConnect is the **Application Readiness Contract**. A candidate cannot submit an application unless:
  1. Full name is provided.
  2. At least one education entry exists.
  3. At least one technical skill is cataloged.
  4. An active resume is uploaded.
  Here, our candidate profile satisfies all four preconditions, so the application gate is unlocked."
- **Technical concept demonstrated:** Business rule enforcement in `CandidateProfileService`, profile-to-readiness aggregation.
- **Academic requirement satisfied:** Meaningful Service Layer Business Rule #1 (Profile Readiness), Domain entity interactions (`Candidate`, `Education`, `Skill`, `Resume`).

---

### Step 7: Deterministic Job Matching
- **What to click:** Navigate back to `/jobs/1` or candidate job board.
- **What to show:** The "Skill Match Analysis" card showing Match: **80%**, Matched Skills (Java, Spring Boot, Git, SQL), Missing Skills (Docker).
- **What to say:** "Unlike platforms using opaque AI embeddings, CareerConnect uses a deterministic match formula:
  $$\text{Match \%} = \text{round}\left(100 \times \frac{\text{Count of Matched Required Skills}}{\text{Total Required Skills}}\right)$$
  Skill strings are normalized using case-insensitive trimmed matching (`lower(trim(name))`). This provides 100% predictable, auditable feedback to both student and recruiter."
- **Technical concept demonstrated:** Deterministic business calculation in `JobMatchingService`, skill set intersection.
- **Academic requirement satisfied:** Service Layer algorithm, auditable business calculation without black-box dependencies.

---

### Step 8: Submitting an Application & Immutable Snapshot Creation
- **What to click:** Click "Apply Now". Confirm application modal submission.
- **What to show:** Success banner: "Application submitted successfully!". Button changes to "Applied".
- **What to say:** "When the candidate clicks Apply, `POST /api/candidate/applications` executes. Inside a single database transaction:
  1. The service verifies the job is `ACTIVE`.
  2. It verifies the candidate has not already applied.
  3. It verifies candidate readiness.
  4. It copies the candidate's current profile, education, skills, and resume metadata into a JSON **Application Snapshot**.
  This ensures that if the candidate later updates their resume or profile, the recruiter's historical record of this specific application remains immutable and legally auditable."
- **Technical concept demonstrated:** Database transactions (`@Transactional`), point-in-time snapshot persistence (`candidate_snapshot`, `resume_snapshot`), duplicate check.
- **Academic requirement satisfied:** Service Layer Business Rule #2 (Application Snapshot & Duplicate Prevention), transactional integrity.

---

### Step 9: Application Tracking & Duplicate Prevention
- **What to click:** Navigate to "My Applications" (`/candidate/applications`). Try to click "Apply" again on the same job.
- **What to show:** Application row with status badge `APPLIED`. Attempting to re-apply is disabled in UI, and if attempted via API returns `409 CONFLICT` with error code `DUPLICATE_APPLICATION`.
- **What to say:** "The application dashboard shows real-time status. Notice the system prevents duplicate applications. Furthermore, if a candidate withdraws an application, the record transitions to `WITHDRAWN` and the unique index prevents re-applying to that same job, eliminating recruiter spam."
- **Technical concept demonstrated:** Idempotency, unique constraint enforcement, canonical API error envelope handling.
- **Academic requirement satisfied:** Business rule enforcement, REST error status handling (`409 Conflict`).

---

### Step 10: Recruiter Authentication & Company Profile Binding
- **What to click:** Log out of candidate account. Log in as recruiter (`recruiter1@careerconnect.local` / `Recruiter@123`). Navigate to `/recruiter/company`.
- **What to show:** Recruiter company details (Acme Innovations, Tech, 50-200 employees, verified domain).
- **What to say:** "We now switch to the Recruiter role. A recruiter account must be linked to an approved company before any job can be posted. If a recruiter has no company assigned, the job creation service aborts with a validation failure."
- **Technical concept demonstrated:** Role-Based Access Control (`ROLE_RECRUITER`), company-ownership relationship checking.
- **Academic requirement satisfied:** User roles & permissions, domain entity relationship (`Recruiter` $\rightarrow$ `Company`).

---

### Step 11: Job Posting Creation (Draft State)
- **What to click:** Navigate to "Jobs" $\rightarrow$ Click "Post a Job" (`/recruiter/jobs/new`). Fill in title "Junior Backend Engineer", requirements, select skills. Save as `DRAFT`.
- **What to show:** Job created with status badge `DRAFT`. Notice it does not appear on the public job discovery board.
- **What to say:** "The recruiter creates a new job posting. In `DRAFT` state, all fields remain freely editable. However, the job is completely invisible to public candidates and guests until explicitly published."
- **Technical concept demonstrated:** State management, status filtering (`status = 'ACTIVE'` for public views).
- **Academic requirement satisfied:** Data persistence, business entity state lifecycle.

---

### Step 12: Publishing Job & Fundamental Field Immutability
- **What to click:** Click "Publish Job" on the draft.
- **What to show:** Status transitions to `ACTIVE`. 
- **What to say:** "When published, `POST /api/recruiter/jobs/{id}/publish` transitions status from `DRAFT` to `ACTIVE`. A key business rule enforced in `JobService` is: once a job begins receiving candidate applications, fundamental fields (such as Required Skills and Experience Level) cannot be modified, preventing bait-and-switch postings."
- **Technical concept demonstrated:** State transition validation, entity update locking rules.
- **Academic requirement satisfied:** Service Layer Business Rule #3 (Job Field Immutability post-application).

---

### Step 13: Recruiter Applicant Review & Match Visualization
- **What to click:** Navigate to "Applicants" on job #1 (`/recruiter/jobs/1/applicants`).
- **What to show:** Candidate application row. View candidate match score (80%), date applied, status `APPLIED`.
- **What to say:** "The recruiter views their applicant pool. Notice they can immediately see the deterministic skill match score calculated against their job requirements, along with the candidate's snapshot summary."
- **Technical concept demonstrated:** Relational join query (`applications` $\bowtie$ `candidates` $\bowtie$ `users`), cross-entity presentation.
- **Academic requirement satisfied:** Recruiter workflow, data aggregation layer.

---

### Step 14: Application Status Transition (State Machine)
- **What to click:** Open candidate application detail (`/recruiter/applications/1`). Change status dropdown from `APPLIED` to `UNDER_REVIEW`, then `SHORTLISTED`.
- **What to show:** Status updates dynamically on screen.
- **What to say:** "The recruiter transitions the candidate's status. The `ApplicationService` enforces a strict State Machine:
  - Allowed forward flows: `APPLIED` $\rightarrow$ `UNDER_REVIEW` $\rightarrow$ `SHORTLISTED` $\rightarrow$ `INTERVIEW` $\rightarrow$ `SELECTED`.
  - Rejection (`REJECTED`) is permitted from any active state.
  - Notice that recruiters **cannot** set an application to `WITHDRAWN`; that transition is strictly reserved for the candidate who owns the application."
- **Technical concept demonstrated:** Formal finite state machine validation in Service layer, role boundary checking.
- **Academic requirement satisfied:** Service Layer Business Rule #4 (Application State Machine & Role Boundary).

---

### Step 15: Protected Resume Access & Stream Handling
- **What to click:** Click "View Resume" on the candidate application modal.
- **What to show:** Resume preview opens seamlessly in a secure browser tab or modal using an in-memory `Blob` URL.
- **What to say:** "Notice how the resume is retrieved: there is no public URL like `/uploads/resume.pdf`! The frontend issues an authenticated `GET /api/candidate/applications/{id}/resume` request. The backend verifies recruiter ownership of the associated job, streams the byte array with `Content-Type: application/pdf`, and the frontend displays it via a short-lived `URL.createObjectURL(blob)`. Unauthenticated access returns `401 Unauthorized`."
- **Technical concept demonstrated:** Protected binary resource streaming, object URL lifecycle management, cross-tenant isolation.
- **Academic requirement satisfied:** Non-functional security requirement, protected resource handling.

---

### Step 16: Admin Dashboard & System Metrics
- **What to click:** Log out of recruiter account. Log in as administrator (`admin@careerconnect.local` / `Admin@123`). Navigate to `/admin/dashboard`.
- **What to show:** Metric cards (Total Users, Companies, Jobs, Applications, System Health).
- **What to say:** "We now switch to the Administrator role. The Admin dashboard provides comprehensive platform observability, monitoring platform health, user distribution, and moderation backlogs."
- **Technical concept demonstrated:** Multi-tenant administration, aggregate reporting queries via `JdbcTemplate`.
- **Academic requirement satisfied:** Admin workflow, role hierarchy (`ROLE_ADMIN`).

---

### Step 17: Admin Moderation & Self-Deactivation Guard
- **What to click:** Navigate to "Users" (`/admin/users`). Locate candidate or recruiter user, show Deactivate toggle.
- **What to show:** Try to deactivate Admin user #1 (the logged-in admin). The action is prevented with a clear error: "Cannot deactivate primary system administrator".
- **What to say:** "Admin users have full moderation capability over users, jobs, and companies. When an abusive job is archived, the backend sets its status to `ARCHIVED`. If restored, it restores strictly to `CLOSED` so a recruiter must re-verify it before going active. Additionally, our Service layer enforces that system admin account #1 cannot deactivate itself, preventing irreversible lockout."
- **Technical concept demonstrated:** Moderation safety rules, self-destruction prevention logic.
- **Academic requirement satisfied:** Service Layer Business Rule #5 (Admin Moderation & Self-Deactivation Guard).

---

### Step 18: Append-Only Audit Logging
- **What to click:** Navigate to "Audit Logs" (`/admin/audit-logs`).
- **What to show:** Paginated table of immutable audit logs showing Actor ID, Action, Target Entity, Timestamp, and IP Address.
- **What to say:** "Every administrative and sensitive security action is recorded in the `audit_logs` table. This table is strictly append-only—there is no `UPDATE` or `DELETE` endpoint in the codebase for audit logs. This guarantees compliance, forensic traceability, and non-repudiation."
- **Technical concept demonstrated:** Append-only persistence pattern, forensic logging.
- **Academic requirement satisfied:** Audit logging architecture, security compliance.

---

### Step 19: Automated Test Evidence & Verification Baseline
- **What to click:** Switch to terminal or show pre-recorded test execution output.
- **What to show:** 
  - Backend: `680 tests passed, 0 failures, 0 errors, 0 skipped` (BUILD SUCCESS).
  - Frontend: `178 tests passed across 36 test files`.
  - TypeScript: `tsc -b` clean.
- **What to say:** "Our platform is backed by comprehensive automated test coverage:
  - 680 JUnit 5 and Spring Boot integration tests verifying controller bindings, service business rules, and JDBC query contracts.
  - 178 Vitest frontend tests verifying component rendering, state transitions, and route guards.
  - Zero regression errors, 100% build pass rate."
- **Technical concept demonstrated:** Automated test harness, continuous verification.
- **Academic requirement satisfied:** Automated testing evidence, Unit 4 testing criteria.

---

### Step 20: Closing Summary & Scope Alignment
- **What to click:** Return to CareerConnect landing page (`/`).
- **What to show:** Final clean view of CareerConnect.
- **What to say:** "To conclude: CareerConnect delivers a fully realized, technically verified V1 recruitment platform for early-career talent. We have maintained strict scope discipline:
  - V1 features (Profiles, Job Discovery, Deterministic Matching, Snapshots, State Machines, Protected Resumes, Admin Moderation) are 100% verified.
  - Saved Jobs is cataloged in the schema but intentionally deferred from V1 UI.
  - Future enhancements like AI ranking, chat, and mobile apps are clearly demarcated for future releases.
  Thank you, and I look forward to your questions."
- **Technical concept demonstrated:** Professional release management, scope boundary defense.
- **Academic requirement satisfied:** Final submission readiness, project presentation closure.

---

## 3. Quick Reference: Concept-to-Screen Mapping

| Academic Concept | Demo Screen / Action | Backend Component |
|:---|:---|:---|
| **Presentation Tier** | React 18 / Vite UI at `/jobs`, `/candidate/profile` | `AppRoutes.tsx`, `JobCard.tsx` |
| **Business Tier** | Readiness verification, Snapshot creation | `CandidateProfileService.java`, `ApplicationService.java` |
| **Data Access Tier** | Parameterized SQL query execution | `JobRepository.java`, `ApplicationRepository.java` |
| **Spring Auto-Configuration**| `CareerConnectApplication.java`, `application.yml` | `DataSourceAutoConfiguration`, `SecurityAutoConfiguration` |
| **REST Controller** | `POST /api/candidate/applications` | `ApplicationController.java` |
| **State Machine** | `UNDER_REVIEW` $\rightarrow$ `SHORTLISTED` $\rightarrow$ `SELECTED` | `ApplicationStatus.java`, `ApplicationService.java` |
| **Role-Based Security** | `/admin/*` blocked for Candidates / Recruiters | `SecurityConfig.java`, `JwtAuthenticationFilter.java` |
| **Deterministic Matching** | 80% Skill Match card on `/jobs/1` | `JobMatchingService.java` |
| **Binary Stream Security**| Protected Resume View via Blob URL | `ResumeController.java`, `ResumeService.java` |
| **Auditability** | Append-only audit table at `/admin/audit-logs` | `AuditLogService.java`, `AuditLogRepository.java` |
