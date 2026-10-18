# CareerConnect — Frontend Master Specification
**Document Version:** 1.0.0 (Frozen Architecture & Implementation Contract)  
**Target Platform:** Web (Desktop, Tablet, Mobile down to 320px)  
**Status:** DRAFT / AUDITED FROM REPOSITORY  
**Backend Verified Baseline:** Phase 8 Complete (677 automated tests, 0 failures, commit `c8fa785`)

---

## 1. Executive Summary & Authoritative Principles

### 1.1 Purpose
CareerConnect is an early-career recruitment platform specifically designed for college students, fresh graduates, interns, and early professionals with 0–2 years of experience. This document establishes the definitive, exhaustive specification for the CareerConnect React frontend. It bridges the completed, security-hardened Spring Boot backend with the user interface, ensuring zero misalignment, zero invented features, and strict preservation of architectural boundaries.

### 1.2 Architectural Boundary & Source of Truth
```
[ React 18+ Client (Presentation / UX Only) ]
                 ↓  (HTTP / JSON REST Envelopes + Bearer JWT)
[ Spring Boot 3.x Controllers (Validation / Contract Mapping) ]
                 ↓
[ Service Layer (Authoritative Business Logic & Ownership Security) ]
                 ↓
[ Repository Layer (Spring JdbcTemplate Parameterized SQL) ]
                 ↓
[ MySQL Database (Relational Constraints & Foreign Keys) ]
```

1. **Backend is the Sole Source of Truth:** Authorization, role boundaries, resource ownership, application lifecycle transitions, profile readiness gating, and persistent audit logging are enforced strictly on the server. The frontend is a presentation client.
2. **Client-Side Guards are UX Only:** Frontend route guards (`<ProtectedRoute>`) improve user experience by preventing disorienting flashes and redirecting unauthenticated users to `/login?next=...`. They do **not** replace backend security.
3. **No Weakening of Backend Security:** No backend controller or service will be relaxed, refactored, or bypassed to accommodate frontend convenience.
4. **Data Contract Integrity:** All requests and responses strictly adhere to the established JSON envelopes (`ApiResponse<T>`, `ApiErrorResponse`).
5. **No Invented Backend Capabilities:** Only capabilities implemented and verified in the backend repository through Phase 8 are included in the V1 scope.

---

## 2. Frozen V1 Architectural Constraints

### 2.1 Authentication & Token Lifecycle
* **Authentication Mechanism:** Stateless JWT (JSON Web Token), signed with HMAC-SHA256 on the backend.
* **Token Storage:** **In-memory storage only** (`React Context / AuthState`).
* **Strict Prohibitions:**
  * ❌ NO `localStorage`
  * ❌ NO `sessionStorage`
  * ❌ NO persistent browser cookies
  * ❌ NO token parameters in URL query strings or hashes
  * ❌ NO refresh token in V1 (V1 access token duration: 30 minutes)
* **Session Lifecycle:**
  * Login / Register returns `{ accessToken, tokenType: "Bearer", expiresAt, user }`.
  * The access token is stored in memory and attached via HTTP header: `Authorization: Bearer <token>`.
  * Page reloads clear memory; the frontend must gracefully redirect to `/login?next=<current_path>` if an in-memory session is lost.
  * Receiving an HTTP 401 `UNAUTHENTICATED` or `INVALID_CREDENTIALS` immediately purges the in-memory token and resets the auth context.

### 2.2 Client Identity & Ownership Derivation
* **Zero Client-Side Identity Spoofing:** The frontend never sends `userId`, `candidateId`, or `recruiterId` in query parameters or request bodies to assert ownership. All ownership is resolved server-side from the authenticated JWT `SecurityUser` principal.
* **Recruiter Company Association:** Recruiters belong to exactly one company (`recruiter.companyId`). If `companyId` is null, the recruiter is gated to company creation before any job operations can be performed.

---

## 3. Complete Backend API Inventory (Audited)

Every endpoint documented below has been verified against active `@RestController` source code in `backend/src/main/java/com/careerconnect/`.

### 3.1 Authentication & Session (`AuthController`)

#### `POST /api/auth/register`
* **Access:** Public (Anonymous)
* **Request Body:** `RegisterRequest` (`email`, `password`, `role`: `CANDIDATE` | `RECRUITER`, `fullName`)
* **Constraints:** Password min 8 chars with uppercase, lowercase, digit, special char. Role cannot be `ADMIN`.
* **Success Response:** `201 Created` → `ApiResponse<AuthResponse>` (`accessToken`, `tokenType`, `expiresAt`, `user`)
* **Error Codes:** 400 `VALIDATION_FAILED`, 409 `EMAIL_ALREADY_EXISTS`
* **Consuming Screen:** `/register`
* **UI Next State:** Set auth context; navigate to `/candidate/profile` or `/recruiter/company`.

#### `POST /api/auth/login`
* **Access:** Public (Anonymous)
* **Request Body:** `LoginRequest` (`email`, `password`)
* **Success Response:** `200 OK` → `ApiResponse<AuthResponse>` (`accessToken`, `tokenType`, `expiresAt`, `user`)
* **Error Codes:** 400 `VALIDATION_FAILED`, 401 `INVALID_CREDENTIALS`, 403 `FORBIDDEN` (account deactivated)
* **Consuming Screen:** `/login`
* **UI Next State:** Set auth context; navigate to `next` query param or default dashboard (`/candidate`, `/recruiter`, `/admin`).

#### `GET /api/auth/me`
* **Access:** Authenticated (Any role)
* **Headers:** `Authorization: Bearer <token>`
* **Success Response:** `200 OK` → `ApiResponse<AuthUserDto>` (`id`, `email`, `role`, `fullName`, `candidateId`, `recruiterId`, `companyId`, `isActive`)
* **Error Codes:** 401 `UNAUTHENTICATED`
* **Consuming Screen:** Top Navigation / App Init
* **UI Next State:** Populate user identity badge and role-based navigation links.

#### `POST /api/auth/logout`
* **Access:** Authenticated (Any role)
* **Headers:** `Authorization: Bearer <token>`
* **Success Response:** `204 No Content` (Empty body)
* **Consuming Screen:** Top Navigation / User Dropdown
* **UI Next State:** Clear in-memory token, reset user state to `null`, navigate to `/login`.

---

### 3.2 System & Health (`HealthController`)

#### `GET /api/health`
* **Access:** Public
* **Success Response:** `200 OK` → `ApiResponse<Map<String, Object>>` (`status: "UP"`, `timestamp`)
* **Consuming Screen:** Public Footer / Status Widget / Error Boundaries

---

### 3.3 Public Discovery (`JobController`, `CompanyController`)

#### `GET /api/jobs`
* **Access:** Public
* **Query Parameters:**
  * `q` (string, keyword search against title, description, skills, company name)
  * `location` (string)
  * `jobType` (enum: `FULL_TIME`, `PART_TIME`, `INTERNSHIP`, `CONTRACT`)
  * `experienceLevel` (enum: `ENTRY_LEVEL`, `MID_LEVEL`, `SENIOR_LEVEL`)
  * `workMode` (enum: `REMOTE`, `HYBRID`, `ON_SITE`)
  * `skill` (string)
  * `minSalaryLpa` (decimal)
  * `page` (int, default 0)
  * `size` (int, default 20)
  * `sort` (string: `newest`, `salary_desc`, `deadline_asc`, default `newest`)
* **Success Response:** `200 OK` → `ApiResponse<List<JobSummaryDto>>` + `PaginationMetadata`
* **Filters Enforced Server-Side:** Returns only `status = 'ACTIVE'`, `deleted_at IS NULL`, active company, and `application_deadline >= CURRENT_DATE`.
* **Consuming Screen:** `/jobs` (Public Job Discovery)
* **UI States:** Loading skeletons, job card list, empty state ("No jobs match your criteria"), pagination controls.

#### `GET /api/jobs/{jobId}`
* **Access:** Public
* **Success Response:** `200 OK` → `ApiResponse<JobDetailDto>` (title, company, description, responsibilities, requirements, skills, deadline, salary, stats)
* **Error Codes:** 404 `JOB_NOT_FOUND`
* **Consuming Screen:** `/jobs/:jobId` (Job Details)
* **UI States:** Full job details, skill tags, company metadata, "Apply Now" button (with readiness state if candidate logged in).

#### `GET /api/companies`
* **Access:** Public
* **Query Parameters:** `q` (string), `industry` (string), `location` (string), `page` (int, default 0), `size` (int, default 20)
* **Success Response:** `200 OK` → `ApiResponse<List<CompanySummaryDto>>` + `PaginationMetadata`
* **Consuming Screen:** `/companies` (Company Directory)

#### `GET /api/companies/{companyId}`
* **Access:** Public
* **Success Response:** `200 OK` → `ApiResponse<CompanyDetailDto>` (id, name, industry, location, website, description, companySize, activeJobsCount, recentJobs)
* **Error Codes:** 404 `RESOURCE_NOT_FOUND`
* **Consuming Screen:** `/companies/:companyId` (Company Profile)

---

### 3.4 Candidate Profile, Readiness & Resources (`CandidateProfileController`, `CandidateSkillController`, `CandidateEducationController`, `CandidateExperienceController`, `CandidateProjectController`, `CandidateResumeController`)

#### `GET /api/candidate/profile`
* **Access:** `hasRole('CANDIDATE')`
* **Success Response:** `200 OK` → `ApiResponse<CandidateProfileDto>` (headline, summary, location, phone, education[], skills[], experience[], projects[], currentResume)
* **Consuming Screen:** `/candidate/profile`

#### `PUT /api/candidate/profile`
* **Access:** `hasRole('CANDIDATE')`
* **Request Body:** `CandidateProfileUpdateRequest` (`headline`, `summary`, `location`, `phone`)
* **Success Response:** `200 OK` → `ApiResponse<CandidateProfileDto>`
* **Consuming Screen:** `/candidate/profile/edit`

#### `GET /api/candidate/profile/readiness`
* **Access:** `hasRole('CANDIDATE')`
* **Success Response:** `200 OK` → `ApiResponse<CandidateProfileReadinessDto>`:
  * `readyForApplications`: boolean (true strictly when score == 100)
  * `readinessScore`: integer (0–100)
  * `hasFullName`: boolean
  * `hasEducation`: boolean (>= 1 education record)
  * `hasSkills`: boolean (>= 1 skill tag)
  * `hasResume`: boolean (>= 1 active PDF resume)
  * `missingRequirements`: string[]
* **Consuming Screen:** Candidate Dashboard widget, Profile Header, Job Detail Apply Modal.

#### `GET /api/candidate/skills`
* **Access:** `hasRole('CANDIDATE')`
* **Success Response:** `200 OK` → `ApiResponse<List<CandidateSkillDto>>`

#### `POST /api/candidate/skills`
* **Access:** `hasRole('CANDIDATE')`
* **Request Body:** `{ "name": "Java" }`
* **Success Response:** `201 Created` → `ApiResponse<CandidateSkillDto>`
* **Error Codes:** 400 `VALIDATION_FAILED`, 409 `CONFLICT` (duplicate skill)

#### `DELETE /api/candidate/skills/{skillId}`
* **Access:** `hasRole('CANDIDATE')`
* **Success Response:** `204 No Content`
* **Error Codes:** 404 `RESOURCE_NOT_FOUND`

#### `POST /api/candidate/education`
* **Access:** `hasRole('CANDIDATE')`
* **Request Body:** `EducationCreateRequest` (`institutionName`, `degree`, `fieldOfStudy`, `startDate`, `endDate`, `grade`, `activities`)
* **Success Response:** `201 Created` → `ApiResponse<EducationDto>`

#### `PUT /api/candidate/education/{educationId}`
* **Access:** `hasRole('CANDIDATE')`
* **Request Body:** `EducationUpdateRequest`
* **Success Response:** `200 OK` → `ApiResponse<EducationDto>`
* **Error Codes:** 404 `RESOURCE_NOT_FOUND`

#### `DELETE /api/candidate/education/{educationId}`
* **Access:** `hasRole('CANDIDATE')`
* **Success Response:** `204 No Content`

#### `POST /api/candidate/experience`
* **Access:** `hasRole('CANDIDATE')`
* **Request Body:** `ExperienceCreateRequest` (`companyName`, `roleTitle`, `startDate`, `endDate`, `isCurrent`, `description`)
* **Success Response:** `201 Created` → `ApiResponse<ExperienceDto>`

#### `PUT /api/candidate/experience/{experienceId}`
* **Access:** `hasRole('CANDIDATE')`
* **Success Response:** `200 OK` → `ApiResponse<ExperienceDto>`

#### `DELETE /api/candidate/experience/{experienceId}`
* **Access:** `hasRole('CANDIDATE')`
* **Success Response:** `204 No Content`

#### `POST /api/candidate/projects`
* **Access:** `hasRole('CANDIDATE')`
* **Request Body:** `ProjectCreateRequest` (`title`, `description`, `projectUrl`, `startDate`, `endDate`)
* **Success Response:** `201 Created` → `ApiResponse<ProjectDto>`

#### `PUT /api/candidate/projects/{projectId}`
* **Access:** `hasRole('CANDIDATE')`
* **Success Response:** `200 OK` → `ApiResponse<ProjectDto>`

#### `DELETE /api/candidate/projects/{projectId}`
* **Access:** `hasRole('CANDIDATE')`
* **Success Response:** `204 No Content`

#### `POST /api/candidate/resume`
* **Access:** `hasRole('CANDIDATE')`
* **Content-Type:** `multipart/form-data` (`file`)
* **Validation:** Max 5 MiB, MIME `application/pdf`, magic byte validation (`%PDF-`).
* **Success Response:** `201 Created` → `ApiResponse<ResumeResponse>` (`id`, `originalFilename`, `sizeBytes`, `contentType`, `createdAt`)
* **Error Codes:** 400 `CANDIDATE_RESUME_REQUIRED`, 422 `RESUME_INVALID_TYPE`, 422 `RESUME_TOO_LARGE`

#### `GET /api/candidate/resume`
* **Access:** `hasRole('CANDIDATE')`
* **Parameters:** `?download=false` (default: returns metadata JSON), `?download=true` (streams PDF)
* **Success Response:** `200 OK` → `ApiResponse<ResumeResponse>` OR raw binary PDF
* **Error Codes:** 404 `RESOURCE_NOT_FOUND`

#### `GET /api/candidate/resume/download`
* **Access:** `hasRole('CANDIDATE')`
* **Success Response:** `200 OK` → Binary PDF stream (`Content-Disposition: inline; filename="..."`)
* **Error Codes:** 404 `RESOURCE_NOT_FOUND`

#### `DELETE /api/candidate/resume`
* **Access:** `hasRole('CANDIDATE')`
* **Success Response:** `204 No Content`

---

### 3.5 Matching & Applications (`JobMatchController`, `ApplicationController`, `CandidateApplicationController`)

#### `GET /api/jobs/{jobId}/match`
* **Access:** `hasRole('CANDIDATE')`
* **Success Response:** `200 OK` → `ApiResponse<JobMatchDto>`:
  * `jobId`: Long
  * `matchPercentage`: Integer (0 to 100)
  * `matchedSkillsCount`: Integer
  * `totalRequiredSkillsCount`: Integer
  * `matchedSkills`: String[]
  * `missingSkills`: String[]
* **Consuming Screen:** Job Detail Page, Job Card Badge, Apply Modal.

#### `POST /api/jobs/{jobId}/applications`
* **Access:** `hasRole('CANDIDATE')`
* **Request Body:** `ApplicationSubmissionRequest` (`coverLetter` optional, max 2000 chars)
* **Backend Pre-Flight Validations (Strict Order):**
  1. Job existence (`404 JOB_NOT_FOUND`)
  2. Job active & deadline not passed (`422 JOB_NOT_ACTIVE`, `422 JOB_DEADLINE_PASSED`)
  3. Duplicate check (`409 DUPLICATE_APPLICATION`)
  4. Profile readiness (`422 CANDIDATE_PROFILE_INCOMPLETE`)
  5. Resume presence (`422 CANDIDATE_RESUME_REQUIRED`)
* **Success Response:** `201 Created` → `ApiResponse<ApplicationResponseDto>` (includes immutable candidate & resume snapshot JSON, initial status `APPLIED`)
* **Consuming Screen:** `/jobs/:jobId` (Apply Dialog)
* **UI Next State:** Display submission confirmation, show "Applied" badge, link to `/candidate/applications/:id`.

#### `GET /api/candidate/applications`
* **Access:** `hasRole('CANDIDATE')`
* **Query Parameters:** `page` (default 0), `size` (default 20)
* **Success Response:** `200 OK` → `ApiResponse<List<ApplicationResponseDto>>` + `PaginationMetadata`
* **Consuming Screen:** `/candidate/applications` (Candidate Applications Dashboard)

#### `GET /api/candidate/applications/{applicationId}`
* **Access:** `hasRole('CANDIDATE')` (Owner only)
* **Success Response:** `200 OK` → `ApiResponse<ApplicationResponseDto>` (includes status history timeline, submitted snapshot, job title, company name)
* **Error Codes:** 404 `RESOURCE_NOT_FOUND` (404 returned on cross-candidate access for security isolation)
* **Consuming Screen:** `/candidate/applications/:applicationId`

#### `PATCH /api/candidate/applications/{applicationId}/withdraw`
* **Access:** `hasRole('CANDIDATE')` (Owner only)
* **Success Response:** `200 OK` → `ApiResponse<ApplicationResponseDto>` (status updated to `WITHDRAWN`)
* **Rules:** Permitted only from non-terminal states (`APPLIED`, `UNDER_REVIEW`, `SHORTLISTED`, `INTERVIEW`). Rejected if already `SELECTED`, `REJECTED`, or `WITHDRAWN`.
* **Error Codes:** 404 `RESOURCE_NOT_FOUND`, 422 `INVALID_STATUS_TRANSITION`
* **Consuming Screen:** `/candidate/applications/:applicationId` (Withdraw confirmation dialog)

---

### 3.6 Recruiter Company & Job Lifecycle (`RecruiterCompanyController`, `RecruiterJobLifecycleController`)

#### `GET /api/recruiter/company`
* **Access:** `hasRole('RECRUITER')`
* **Success Response:** `200 OK` → `ApiResponse<RecruiterCompanyResponseDto>`
* **Error Codes:** 404 `RESOURCE_NOT_FOUND` (recruiter does not yet have an affiliated company)
* **Consuming Screen:** `/recruiter/company`

#### `PUT /api/recruiter/company`
* **Access:** `hasRole('RECRUITER')`
* **Request Body:** `RecruiterCompanyUpsertRequest` (`name`, `industry`, `location`, `website`, `description`, `companySize`)
* **Success Response:** `200 OK` → `ApiResponse<RecruiterCompanyResponseDto>`
* **Error Codes:** 400 `VALIDATION_FAILED`, 409 `COMPANY_ALREADY_EXISTS`
* **Consuming Screen:** `/recruiter/company/edit`

#### `POST /api/recruiter/jobs`
* **Access:** `hasRole('RECRUITER')`
* **Request Body:** `RecruiterJobCreateRequest` (`title`, `description`, `responsibilities`, `requirements`, `jobType`, `experienceLevel`, `workMode`, `location`, `salaryMinLpa`, `salaryMaxLpa`, `salaryCurrency`, `applicationDeadline`, `requiredSkillIds`)
* **Prerequisite:** Recruiter must have an affiliated company (`422 COMPANY_REQUIRED`). Must have at least 1 skill ID (`422 JOB_SKILL_REQUIRED`). Deadline must be in future (`422 JOB_DEADLINE_INVALID`).
* **Initial State:** Created in `DRAFT` status.
* **Success Response:** `201 Created` → `ApiResponse<JobDetailDto>`
* **Consuming Screen:** `/recruiter/jobs/new`

#### `GET /api/recruiter/jobs`
* **Access:** `hasRole('RECRUITER')`
* **Query Parameters:** `page` (default 0), `size` (default 20)
* **Success Response:** `200 OK` → `ApiResponse<List<JobSummaryDto>>` + `PaginationMetadata`
* **Consuming Screen:** `/recruiter/jobs` (Job Management Dashboard)

#### `GET /api/recruiter/jobs/{jobId}`
* **Access:** `hasRole('RECRUITER')` (Company ownership required)
* **Success Response:** `200 OK` → `ApiResponse<JobDetailDto>`
* **Error Codes:** 404 `JOB_NOT_FOUND`

#### `PUT /api/recruiter/jobs/{jobId}`
* **Access:** `hasRole('RECRUITER')` (Company ownership required)
* **Request Body:** `RecruiterJobUpdateRequest`
* **Rules:** If job has received applications, skill requirements cannot be removed/mutated (`422 INVALID_STATUS_TRANSITION`).
* **Success Response:** `200 OK` → `ApiResponse<JobDetailDto>`
* **Consuming Screen:** `/recruiter/jobs/:jobId/edit`

#### `PATCH /api/recruiter/jobs/{jobId}/publish`
* **Access:** `hasRole('RECRUITER')`
* **Transition:** `DRAFT` → `ACTIVE`
* **Success Response:** `200 OK` → `ApiResponse<JobDetailDto>`
* **Error Codes:** 422 `INVALID_STATUS_TRANSITION` (e.g. if already ACTIVE or CLOSED)
* **Consuming Screen:** `/recruiter/jobs` or `/recruiter/jobs/:jobId`

#### `PATCH /api/recruiter/jobs/{jobId}/close`
* **Access:** `hasRole('RECRUITER')`
* **Transition:** `ACTIVE` → `CLOSED`
* **Side-Effect:** Emits `JOB_CLOSED` in-app notification to all candidates who applied to this job.
* **Success Response:** `200 OK` → `ApiResponse<JobDetailDto>`
* **Error Codes:** 422 `INVALID_STATUS_TRANSITION`
* **Consuming Screen:** `/recruiter/jobs` or `/recruiter/jobs/:jobId`

#### `DELETE /api/recruiter/jobs/{jobId}`
* **Access:** `hasRole('RECRUITER')`
* **Rules:** Soft deletion (`deleted_at` timestamp). Only permitted if job has **zero** applications. Jobs with applications must be closed, not deleted.
* **Success Response:** `204 No Content`
* **Error Codes:** 422 `INVALID_STATUS_TRANSITION`, 404 `JOB_NOT_FOUND`

---

### 3.7 Recruiter Applicant Review & Protected Resumes (`RecruiterApplicantController`)

#### `GET /api/recruiter/jobs/{jobId}/applications`
* **Access:** `hasRole('RECRUITER')` (Company ownership of job required)
* **Query Parameters:** `status` (optional `ApplicationStatus`), `page` (default 0), `size` (default 20)
* **Success Response:** `200 OK` → `ApiResponse<List<ApplicantSummaryDto>>` + `PaginationMetadata`
* **Consuming Screen:** `/recruiter/jobs/:jobId/applications`

#### `GET /api/recruiter/applications/{applicationId}`
* **Access:** `hasRole('RECRUITER')` (Application must be for recruiter's company job)
* **Success Response:** `200 OK` → `ApiResponse<ApplicantDetailDto>`:
  * `applicationId`, `jobId`, `jobTitle`, `candidateId`, `status`, `coverLetter`, `appliedAt`
  * `candidateSnapshot`: Profile data frozen at time of submission (education, skills, projects, experience)
  * `resumeSnapshot`: Resume metadata frozen at submission (filename, sizeBytes, storageKey)
  * `match`: Deterministic match score calculated against job requirements
  * `statusHistory`: Ordered history of status transitions with actor IDs and notes
* **Error Codes:** 404 `RESOURCE_NOT_FOUND`
* **Consuming Screen:** `/recruiter/applications/:applicationId`

#### `PATCH /api/recruiter/applications/{applicationId}/status`
* **Access:** `hasRole('RECRUITER')` (Company ownership required)
* **Request Body:** `UpdateApplicationStatusRequest` (`status`, `note` optional, max 500 chars)
* **Valid Transitions:**
  * `APPLIED` → `UNDER_REVIEW`, `SHORTLISTED`, `REJECTED`
  * `UNDER_REVIEW` → `SHORTLISTED`, `REJECTED`
  * `SHORTLISTED` → `INTERVIEW`, `REJECTED`
  * `INTERVIEW` → `SELECTED`, `REJECTED`
  * Terminal states (`SELECTED`, `REJECTED`, `WITHDRAWN`) cannot transition.
  * Same-state updates are rejected.
  * Recruiters cannot transition to `WITHDRAWN`.
* **Atomic Side-Effect:** Persists status change, creates immutable status history entry, and emits `APPLICATION_STATUS_CHANGED` notification to candidate inside a 3-way atomic transaction.
* **Success Response:** `200 OK` → `ApiResponse<ApplicationStatusHistoryDto>`
* **Error Codes:** 422 `INVALID_STATUS_TRANSITION`, 404 `RESOURCE_NOT_FOUND`
* **Consuming Screen:** `/recruiter/applications/:applicationId` (Status transition control)

#### `GET /api/recruiter/applications/{applicationId}/resume`
* **Access:** `hasRole('RECRUITER')` (Company ownership of application required)
* **Query Parameters:** `?download=true` or accept header `application/pdf`
* **Success Response:** `200 OK` → Streams PDF binary (`Content-Disposition: inline; filename="..."`)
* **Error Codes:** 404 `RESOURCE_NOT_FOUND` (application not found or recruiter not authorized)
* **Consuming Screen:** `/recruiter/applications/:applicationId` (Protected Resume Preview / Download)

---

### 3.8 In-App Notifications (`NotificationController`)

#### `GET /api/notifications`
* **Access:** Authenticated (Any role, user-isolated)
* **Query Parameters:** `page` (default 0), `size` (default 20), `unread` (optional boolean)
* **Success Response:** `200 OK` → `ApiResponse<NotificationSummaryResponse>`:
  * `unreadCount`: Integer
  * `notifications`: List of `NotificationDto` (`id`, `userId`, `type`: `APPLICATION_STATUS_CHANGED` | `JOB_CLOSED`, `title`, `body`, `resourceType`: `APPLICATION` | `JOB`, `resourceId`, `readAt`, `createdAt`)
* **Consuming Screen:** Top Navigation Notification Bell, `/candidate/notifications`, `/recruiter/notifications`

#### `PATCH /api/notifications/{id}/read`
* **Access:** Authenticated (Owner only)
* **Success Response:** `200 OK` → `ApiResponse<NotificationDto>`
* **Consuming Screen:** Notification Popover / Notification Row

#### `PATCH /api/notifications/read-all`
* **Access:** Authenticated (Owner only)
* **Success Response:** `200 OK` → `ApiResponse<Map<String, Object>>` (`markedReadCount`: int)
* **Consuming Screen:** Notification Popover Header ("Mark all as read")

---

### 3.9 Admin Platform, Moderation & Audit (`AdminDashboardController`, `AdminUserController`, `AdminCompanyController`, `AdminJobController`, `AdminApplicationController`, `AdminAuditController`)

#### `GET /api/admin/dashboard`
* **Access:** `hasRole('ADMIN')`
* **Success Response:** `200 OK` → `ApiResponse<AdminDashboardDto>`:
  * `totalUsers`, `totalCandidates`, `totalRecruiters`, `totalAdmins`
  * `totalCompanies`, `activeCompanies`, `totalJobs`, `activeJobs`, `totalApplications`
  * `activeJobsCount`, `recentAuditLogsCount`
* **Consuming Screen:** `/admin` (Admin Overview Dashboard)

#### `GET /api/admin/users`
* **Access:** `hasRole('ADMIN')`
* **Query Parameters:** `role` (optional), `isActive` (optional), `q` (optional), `page`, `size`
* **Success Response:** `200 OK` → `ApiResponse<List<AdminUserDto>>` + `PaginationMetadata`
* **Consuming Screen:** `/admin/users`

#### `PATCH /api/admin/users/{userId}/status`
* **Access:** `hasRole('ADMIN')`
* **Request Body:** `AdminUpdateUserStatusRequest` (`isActive`: boolean)
* **Rules:** Admin cannot deactivate their own account (Self-lockout protection → `422 INVALID_STATUS_TRANSITION`).
* **Atomic Side-Effect:** Updates status and logs `USER_DEACTIVATED` / `USER_ACTIVATED` in audit trail.
* **Success Response:** `200 OK` → `ApiResponse<Void>`
* **Consuming Screen:** `/admin/users` (Action Toggle)

#### `GET /api/admin/companies`
* **Access:** `hasRole('ADMIN')`
* **Query Parameters:** `isActive` (optional), `q` (optional), `page`, `size`
* **Success Response:** `200 OK` → `ApiResponse<List<AdminCompanyDto>>` + `PaginationMetadata`
* **Consuming Screen:** `/admin/companies`

#### `PATCH /api/admin/companies/{companyId}/status`
* **Access:** `hasRole('ADMIN')`
* **Request Body:** `AdminUpdateCompanyStatusRequest` (`isActive`: boolean)
* **Atomic Side-Effect:** Updates company status; when deactivated, all associated jobs are dynamically hidden from public discovery without destroying historical applications. Logs `COMPANY_DEACTIVATED` in audit trail.
* **Success Response:** `200 OK` → `ApiResponse<Void>`
* **Consuming Screen:** `/admin/companies` (Action Toggle)

#### `GET /api/admin/jobs`
* **Access:** `hasRole('ADMIN')`
* **Query Parameters:** `companyId` (optional), `status` (optional), `q` (optional), `page`, `size`
* **Success Response:** `200 OK` → `ApiResponse<List<AdminJobDto>>` + `PaginationMetadata`
* **Consuming Screen:** `/admin/jobs`

#### `PATCH /api/admin/jobs/{jobId}/archive`
* **Access:** `hasRole('ADMIN')`
* **Transition:** `DRAFT` / `ACTIVE` / `CLOSED` → `ARCHIVED`
* **Atomic Side-Effect:** Sets status to `ARCHIVED`; hides from public discovery; logs `JOB_ARCHIVED` in audit trail.
* **Success Response:** `200 OK` → `ApiResponse<Void>`
* **Consuming Screen:** `/admin/jobs` (Archive Button)

#### `PATCH /api/admin/jobs/{jobId}/restore`
* **Access:** `hasRole('ADMIN')`
* **Transition:** Strictly `ARCHIVED` → `CLOSED` (Must be re-published by recruiter).
* **Atomic Side-Effect:** Sets status to `CLOSED`; logs `JOB_RESTORED` in audit trail.
* **Success Response:** `200 OK` → `ApiResponse<Void>`
* **Consuming Screen:** `/admin/jobs` (Restore Button)

#### `GET /api/admin/applications`
* **Access:** `hasRole('ADMIN')`
* **Query Parameters:** `jobId`, `candidateId`, `companyId`, `status`, `page`, `size`
* **Success Response:** `200 OK` → `ApiResponse<List<AdminApplicationSummaryDto>>` + `PaginationMetadata`
* **Consuming Screen:** `/admin/applications`

#### `GET /api/admin/applications/{applicationId}`
* **Access:** `hasRole('ADMIN')`
* **Success Response:** `200 OK` → `ApiResponse<AdminApplicationDetailDto>`
* **Consuming Screen:** `/admin/applications/:applicationId`

#### `GET /api/admin/applications/{applicationId}/resume`
* **Access:** `hasRole('ADMIN')`
* **Success Response:** `200 OK` → Binary PDF stream (`Content-Disposition: inline; filename="..."`)
* **Consuming Screen:** `/admin/applications/:applicationId` (Resume Viewer)

#### `GET /api/admin/audit-logs`
* **Access:** `hasRole('ADMIN')`
* **Query Parameters:** `actorId` (optional), `action` (optional), `entityType` (optional), `page`, `size`
* **Success Response:** `200 OK` → `ApiResponse<List<AuditLogDto>>` + `PaginationMetadata`:
  * `id`, `actorUserId`, `action`, `entityType`, `entityId`, `metadata` (JSON diff string), `createdAt`
* **Consuming Screen:** `/admin/audit` (Audit Log Table)

---

## 4. Frontend Route Architecture & Navigation Map

```
/ (Public Landing / Hero)
├── /jobs (Public Discovery)
│   └── /jobs/:jobId (Job Details & Match Overview)
├── /companies (Company Directory)
│   └── /companies/:companyId (Company Profile & Open Roles)
├── /login (Auth: Email + Password)
├── /register (Auth: Candidate / Recruiter)
├── /about (Static Institutional Overview)
├── /privacy (Privacy Policy)
├── /terms (Terms of Service)
│
├── /candidate/* (Role: CANDIDATE)
│   ├── /candidate (Dashboard: Profile Strength, Recent Applications, Notifications)
│   ├── /candidate/profile (Profile Viewer & Completion Tracker)
│   ├── /candidate/profile/edit (Multi-tab Resource Editor: Education, Skills, Experience, Projects)
│   ├── /candidate/applications (Applications Tracker: Status Filters, Withdraw Action)
│   ├── /candidate/applications/:applicationId (Application Snapshot & Timeline Detail)
│   ├── /candidate/notifications (In-App Notification Feed)
│   └── /candidate/settings (Account Overview)
│
├── /recruiter/* (Role: RECRUITER)
│   ├── /recruiter (Dashboard: Active Jobs, Recent Applicants, Quick Stats)
│   ├── /recruiter/company (Company Profile Viewer)
│   ├── /recruiter/company/edit (Company Details Upsert Form)
│   ├── /recruiter/jobs (Job Management List: Draft, Active, Closed)
│   ├── /recruiter/jobs/new (Job Creation Form)
│   ├── /recruiter/jobs/:jobId/edit (Job Modification Form)
│   ├── /recruiter/jobs/:jobId/applications (Job Applicants Review & Filter Table)
│   ├── /recruiter/applications/:applicationId (Applicant Snapshot, Match, Resume, Status Control)
│   ├── /recruiter/notifications (Notification Feed)
│   └── /recruiter/settings (Account Overview)
│
├── /admin/* (Role: ADMIN)
│   ├── /admin (Platform Overview & Key Health Metrics)
│   ├── /admin/users (User Directory & Deactivation Toggles)
│   ├── /admin/companies (Company Directory & Moderation Toggles)
│   ├── /admin/jobs (Job Directory & Archive / Restore Controls)
│   ├── /admin/applications (Platform Application Inspector)
│   ├── /admin/applications/:applicationId (Admin Snapshot & Protected Resume Viewer)
│   ├── /admin/audit (Append-Only Audit Trail Table & Metadata Diff Viewer)
│   └── /admin/settings (Admin Account Overview)
│
└── Error / System Routes
    ├── /403 (Forbidden: Access Denied / Insufficient Role)
    ├── /404 (Not Found: Invalid Resource / Path)
    └── /500 (Server Error: General Failure / Retry Screen)
```

---

## 5. Frontend ↔ Backend Integration Matrix

| Screen / Page | Triggering User Action | Backend Endpoint Called | HTTP Verb | Request Payload / Params | Expected Response Data | UI State Transition & Consequences |
|---|---|---|---|---|---|---|
| **`/login`** | Click "Sign In" | `/api/auth/login` | `POST` | `{ email, password }` | `ApiResponse<AuthResponse>` | On success: store token in memory, redirect to `next` or role home. On error: inline alert with `message`. |
| **`/register`** | Click "Create Account" | `/api/auth/register` | `POST` | `{ email, password, role, fullName }` | `ApiResponse<AuthResponse>` | On success: store token, redirect to role setup. On 409: highlight email field with duplicate message. |
| **`/jobs`** | Change filter / search input | `/api/jobs` | `GET` | `?q=...&location=...&jobType=...&page=0` | `ApiResponse<JobSummaryDto[]>` + `pagination` | Show loading skeleton; render job card list; update URL search params without reload. |
| **`/jobs/:jobId`** | Page load (Candidate logged in) | `/api/jobs/{id}/match` | `GET` | Path `{id}` | `ApiResponse<JobMatchDto>` | Render deterministic Match badge (e.g. "85% Match"), list matching & missing skills. |
| **`/jobs/:jobId`** | Click "Apply Now" (Modal) | `/api/candidate/profile/readiness` | `GET` | Headers: Bearer JWT | `ApiResponse<CandidateProfileReadinessDto>` | If `readyForApplications == false`, modal shows checklist of missing items with direct links. |
| **`/jobs/:jobId`** | Confirm Application Submit | `/api/jobs/{id}/applications` | `POST` | `{ coverLetter: "..." }` | `ApiResponse<ApplicationResponseDto>` | Submit button shows spinner; on success: display checkmark dialog; button switches to "Applied". |
| **`/candidate/profile/edit`** | Upload PDF Resume | `/api/candidate/resume` | `POST` | `multipart/form-data` (`file`) | `ApiResponse<ResumeResponse>` | Upload progress bar; on success: update resume metadata card; recalculate readiness score. |
| **`/candidate/applications/:id`**| Click "Withdraw Application" | `/api/candidate/applications/{id}/withdraw` | `PATCH` | Path `{id}` | `ApiResponse<ApplicationResponseDto>` | Confirmation prompt; on 200: timeline appends `WITHDRAWN` event; status badge updates; action disabled. |
| **`/recruiter/company/edit`** | Click "Save Company Profile"| `/api/recruiter/company` | `PUT` | `RecruiterCompanyUpsertRequest` | `ApiResponse<RecruiterCompanyResponseDto>`| Toast notification; unlock job creation navigation if previously unassociated. |
| **`/recruiter/jobs/new`** | Click "Save Draft" | `/api/recruiter/jobs` | `POST` | `RecruiterJobCreateRequest` | `ApiResponse<JobDetailDto>` | Redirect to `/recruiter/jobs`; job appears with `DRAFT` badge. |
| **`/recruiter/jobs`** | Click "Publish" on draft job | `/api/recruiter/jobs/{id}/publish` | `PATCH` | Path `{id}` | `ApiResponse<JobDetailDto>` | Job status chip transitions from `DRAFT` to `ACTIVE`; public URL becomes shareable. |
| **`/recruiter/jobs`** | Click "Close Job" | `/api/recruiter/jobs/{id}/close` | `PATCH` | Path `{id}` | `ApiResponse<JobDetailDto>` | Job status chip transitions to `CLOSED`; publish button disabled; notification emitted. |
| **`/recruiter/applications/:id`**| Select new status & click "Update" | `/api/recruiter/applications/{id}/status` | `PATCH` | `{ status, note }` | `ApiResponse<ApplicationStatusHistoryDto>` | Status dropdown shows spinner; timeline instantly appends new status item; candidate notified. |
| **`/recruiter/applications/:id`**| Click "View Resume" | `/api/recruiter/applications/{id}/resume` | `GET` | Headers: Bearer JWT | Binary PDF stream | Embed in inline PDF viewer modal or trigger browser inline tab with sanitized blob URL. |
| **Top Nav Bell** | Click "Mark as Read" on item | `/api/notifications/{id}/read` | `PATCH` | Path `{id}` | `ApiResponse<NotificationDto>` | Item background clears unread highlight; badge counter decrements by 1. |
| **Top Nav Bell** | Click "Mark All as Read" | `/api/notifications/read-all` | `PATCH` | Empty body | `ApiResponse<{ markedReadCount }>` | Badge counter clears to 0; all items render as read. |
| **`/admin/users`** | Toggle "Active" switch | `/api/admin/users/{id}/status` | `PATCH` | `{ isActive: false }` | `ApiResponse<Void>` | Switch transitions state; prompt prevents self-deactivation; audit entry generated on server. |
| **`/admin/jobs`** | Click "Archive" | `/api/admin/jobs/{id}/archive` | `PATCH` | Path `{id}` | `ApiResponse<Void>` | Job status chip changes to `ARCHIVED`; public visibility immediately revoked. |

---

## 6. Business Rules to Frontend UX Mapping

| Domain Rule | Authoritative Backend Enforcement | Frontend UX Presentation & Guardrail |
|---|---|---|
| **Candidate 100% Readiness Required to Apply** | Returns `422 CANDIDATE_PROFILE_INCOMPLETE` or `422 CANDIDATE_RESUME_REQUIRED` on `POST /applications`. | "Apply Now" button checks readiness; if < 100%, button opens an educational checklist detailing exactly what is missing (e.g. "Add at least 1 education record", "Upload resume") with 1-click links to `/candidate/profile/edit`. |
| **Strict Single Application Per Job** | Database unique constraint `uk_application_candidate_job` returns `409 DUPLICATE_APPLICATION`. | When inspecting job details, if candidate has already applied, "Apply Now" is replaced with an "Applied (View Status)" button linking directly to `/candidate/applications/:id`. |
| **Immutable Application Snapshots** | Submission captures candidate profile & resume metadata in JSON snapshots; subsequent candidate edits do not touch historical applications. | Application Detail view displays a banner: "Submission Snapshot as of [Date]". Shows exactly the profile information and resume filename submitted at that time. |
| **Terminal Status Locking** | Backend rejects transitions from `SELECTED`, `REJECTED`, or `WITHDRAWN` with `422 INVALID_STATUS_TRANSITION`. | In Recruiter Applicant Detail and Candidate View, status controls and withdrawal buttons are completely disabled and hidden once a terminal status is reached. |
| **Recruiter Withdrawal Prohibition** | Recruiters cannot transition an application to `WITHDRAWN` (`422 INVALID_STATUS_TRANSITION`). | The recruiter status transition dropdown explicitly excludes the `WITHDRAWN` option. |
| **Company Required for Recruiter Actions** | Returns `422 COMPANY_REQUIRED` on job creation or modification. | If recruiter's `companyId` is null, header banner alerts: "Complete company profile to publish jobs", and `/recruiter/jobs/new` redirects to `/recruiter/company/edit`. |
| **Fundamental Job Requirement Locking** | Returns `422 INVALID_STATUS_TRANSITION` on attempts to remove skills from jobs with active applications. | Job Edit form disables the "Remove Skill" button for existing required skills once applications count > 0, explaining: "Skill requirements cannot be deleted after applications are received." |
| **Job Soft Deletion With Applications Blocked** | Returns `422 INVALID_STATUS_TRANSITION` on deleting jobs with applications. | If job has >= 1 applications, the "Delete Job" button is disabled with tooltip: "Jobs with applications cannot be deleted. Close the job instead." |
| **Deterministic Skill Match Score** | Returns integer 0–100 calculated as `(matchedSkills / requiredSkills) * 100`. | Match chip uses clear percentage typography (e.g. "100% Match", "67% Match"). Clicking expands a popover comparing Candidate Skills vs Job Requirements with green checkmarks and gray missing tags. No AI/ML claims. |
| **Admin Self-Deactivation Protection** | Returns `422 INVALID_STATUS_TRANSITION` if authenticated admin attempts to deactivate their own account ID. | On `/admin/users`, the toggle switch next to the currently logged-in admin's row is disabled with label "(Current Account)". |

---

## 7. Visual Design Directions (For Stitch / Figma Exploration)

To avoid generic AI-generated aesthetics, CareerConnect evaluates three distinct, highly tailored visual directions:

### Direction A: Editorial / Institutional / Modern (Recommended Baseline)
* **Visual Character:** Trustworthy, intellectual, collegiate, authoritative, highly structured. Feels like a prestigious academic fellowship or high-end career registry.
* **Typography:** 
  * Display/Headings: `DM Serif Display` or `Newsreader` (Warm, dignified serif)
  * UI & Body: `Inter` or `Plus Jakarta Sans` (Neutral, ultra-legible grotesque sans)
* **Color Palette:**
  * Background: `#FBFAF6` (Warm Paper)
  * Surface/Card: `#FFFFFF` with `#E8E4DA` warm borders
  * Primary: `#202522` (Deep Ink)
  * Accent/Forest: `#29483F` (British Racing Green / Academic Ivy)
  * Olive Muted: `#65705A`
  * Text: `#202522` (Primary), `#74766F` (Muted)
* **Design Metrics:** 1px borders, subtle 4px/8px radii, zero glassmorphism, zero neon, spacious typography hierarchy.
* **Suitability:** **Highest.** Exactly matches the early-career and academic project principles.

### Direction B: Modern Professional Minimalist
* **Visual Character:** Crisp, utilitarian, Swiss international typographic style. Direct, functional, tech-forward.
* **Typography:** `Inter` throughout, leveraging distinct font weights (400, 500, 600, 700) and tabular figures for data tables.
* **Color Palette:**
  * Background: `#F8F9FA`
  * Surface: `#FFFFFF` with `#E2E8F0` cool gray borders
  * Primary: `#0F172A` (Slate 900)
  * Accent: `#2563EB` (Restrained Cobalt)
  * Text: `#0F172A` (Slate 900), `#64748B` (Slate 500)
* **Design Metrics:** 6px radius, clear high-contrast dividers, compact information density.

### Direction C: Contemporary Early-Career Platform
* **Visual Character:** Warm, encouraging, approachable yet strictly professional. Focuses on clarity for first-time job seekers.
* **Typography:** `Outfit` (Headings) + `Inter` (Body).
* **Color Palette:**
  * Background: `#FDFDFC`
  * Surface: `#FFFFFF` with `#EAE9E5` warm borders
  * Primary: `#1E293B`
  * Accent/Emerald: `#059669` (Growth/Opportunity)
  * Text: `#1E293B`, `#64748B`
* **Design Metrics:** 8px/12px radius, soft elevation shadows (`0 2px 8px rgba(0,0,0,0.04)`), generous touch targets.

---

## 8. Design System Tokens & Component Inventory

### 8.1 Design Tokens (CSS Custom Properties)
```css
:root {
  /* Colors */
  --cc-ink: #202522;
  --cc-forest: #29483F;
  --cc-olive: #65705A;
  --cc-ivory: #F5F2EA;
  --cc-paper: #FBFAF6;
  --cc-warm-gray: #D8D4CA;
  --cc-muted-text: #74766F;
  --cc-soft-green: #DCE6DF;
  --cc-success: #2F6B4F;
  --cc-warning: #8A6519;
  --cc-danger: #A23A32;
  --cc-focus: #29483F;

  /* Typography */
  --cc-font-display: "DM Serif Display", Georgia, serif;
  --cc-font-body: "Inter", system-ui, -apple-system, sans-serif;

  /* Spacing Scale */
  --cc-space-1: 4px;
  --cc-space-2: 8px;
  --cc-space-3: 12px;
  --cc-space-4: 16px;
  --cc-space-5: 24px;
  --cc-space-6: 32px;
  --cc-space-7: 48px;
  --cc-space-8: 64px;

  /* Shapes & Borders */
  --cc-radius-sm: 4px;
  --cc-radius-md: 8px;
  --cc-radius-lg: 12px;
  --cc-border: 1px solid var(--cc-warm-gray);
  --cc-shadow-sm: 0 1px 3px rgba(32, 37, 34, 0.06);
  --cc-shadow-md: 0 4px 12px rgba(32, 37, 34, 0.08);

  /* Layout */
  --cc-container-max: 1200px;
}
```

### 8.2 Component Hierarchy
1. **Global UI Primitives (`components/ui/`):**
   * `Button` (`variant`: primary, secondary, outline, danger; `size`: sm, md, lg; `isLoading`)
   * `TextField` / `TextArea` (with label, helperText, error state, character counter)
   * `Select` (native or styled accessible select)
   * `Badge` / `StatusBadge` (color + distinct text label)
   * `Card` (`variant`: flat, outlined, elevated)
   * `Dialog` / `Modal` (focus trapped, accessible title, escape key listener)
   * `Pagination` (page numbers, prev/next, total summary)
   * `EmptyState` (illustration/icon, title, message, primary action CTA)
   * `ErrorState` (error code explanation, retry button)
   * `Skeleton` (animated placeholder blocks for cards and tables)
2. **Layout Shells (`components/layout/`):**
   * `PublicHeader` (Logo, Jobs, Companies, About, Login, Register)
   * `RoleSidebar` (Candidate / Recruiter / Admin navigation links with active state indicator)
   * `PageHeader` (Breadcrumbs, title, primary action buttons)
   * `Footer` (Copyright, system status, legal links)
   * `ProtectedRoute` (Auth check, role verification, redirect handling)
3. **Product-Specific Domain Components (`components/domain/`):**
   * `JobCard` (Title, company, location, job type, salary LPA, deadline, skill chips, match percentage)
   * `JobFilters` (Keyword, location, jobType, workMode, experienceLevel, salary range)
   * `MatchSummaryBadge` (Deterministic match badge with score popover)
   * `ProfileReadinessMeter` (Progress bar, score 0–100%, missing checklist)
   * `StatusTimeline` (Sequential status history with actor badges, timestamps, notes)
   * `ApplicationCard` (Job summary, submitted date, current status badge, withdraw action)
   * `ApplicantSnapshotModal` (Tabs: Profile snapshot, Frozen resume details, Match analysis)
   * `ResumeUploadArea` (Drag-and-drop zone, PDF validation indicator, file size check)
   * `AuditLogTable` (Paginated table with expandable JSON metadata diff)

---

## 9. Screen State Management Matrix

Every primary page must support all 7 states without crashing or showing blank layouts:

| Screen | 1. Normal State | 2. Loading State | 3. Empty State | 4. Error State | 5. Validation State | 6. 401/403 State | 7. Mobile View (<768px) |
|---|---|---|---|---|---|---|---|
| **`/jobs`** | Grid of job cards with filter bar | 6 animated skeleton cards | "No jobs found matching your filters" + "Reset Filters" CTA | Error banner with "Retry" button | Highlight invalid salary input | N/A (Public) | Filter drawer replaces sidebar; cards stack vertically. |
| **`/candidate/profile`** | Sectioned profile view with readiness header | Skeleton cards for education/experience | "No education added yet" + "+ Add" CTA | "Failed to load profile" + Retry | Inline field errors under input fields | Redirect to `/login?next=/candidate/profile` | Tabbed navigation for sections; full-width inputs. |
| **`/candidate/applications`** | Table/card list of active & past applications | Skeleton rows | "You haven't applied to any jobs yet" + "Browse Jobs" CTA | Error alert | N/A | Redirect to `/login` | Status badges right-aligned; card layout replaces table. |
| **`/recruiter/jobs/:id/applications`**| Filterable applicant list with match scores | Skeleton rows | "No candidates have applied yet" | Error alert | N/A | 403 screen if job belongs to another company | Horizontal scroll or card cards; action dropdown. |
| **`/admin/audit`** | Paginated audit log table with metadata diffs | Skeleton table rows | "No audit logs recorded for this period" | Error alert | Highlight invalid date filter | 403 screen if non-admin | Compact table with collapsible detail modal. |

---

## 10. API Client & Frontend Architecture

### 10.1 Directory Layout (`frontend/src/`)
```
frontend/src/
├── api/
│   ├── client.ts             # Central Fetch/Axios instance, Bearer token injection, error normalizer
│   ├── authApi.ts            # Register, Login, Me, Logout
│   ├── jobsApi.ts            # Public Job Search, Details, Match
│   ├── companyApi.ts         # Public Company Search, Details
│   ├── candidateApi.ts       # Profile, Education, Skills, Projects, Experience, Resume
│   ├── applicationApi.ts     # Submit, List, Detail, Withdraw
│   ├── recruiterApi.ts       # Company Upsert, Job CRUD, Publish/Close, Applicants, Status Update
│   ├── adminApi.ts           # Dashboard, User/Company/Job Moderation, Audit Logs
│   └── notificationApi.ts    # List, Read, Read-All
├── app/
│   ├── App.tsx               # Root component with providers
│   └── router.tsx            # React Router route definitions
├── components/
│   ├── domain/               # Domain-specific components (JobCard, MatchBadge, etc.)
│   ├── layout/               # Shells (PublicHeader, RoleSidebar, PageHeader)
│   └── ui/                   # Design system primitives (Button, TextField, Modal)
├── context/
│   ├── AuthContext.tsx       # In-memory token, current user, login/logout callbacks
│   └── NotificationContext.tsx # Unread counter polling/refetching on mutation
├── hooks/
│   ├── useAuth.ts            # Access auth state and user identity
│   ├── useJobs.ts            # Query hook for job search
│   └── useNotifications.ts   # Notification actions
├── pages/
│   ├── admin/                # Admin views
│   ├── candidate/            # Candidate views
│   ├── public/               # Landing, Jobs, Companies, About
│   ├── recruiter/            # Recruiter views
│   └── system/               # 403, 404, 500 error pages
├── styles/
│   ├── tokens.css            # CSS variables
│   └── main.css              # Global baseline and utility styles
└── types/
    └── api.ts                # TypeScript interfaces matching backend DTOs & envelopes
```

### 10.2 Centralized Error Normalization (`api/client.ts`)
```typescript
export interface NormalizedApiError {
  status: number;
  message: string;
  errorCode: string;
  fieldErrors?: Record<string, string>;
}

export function normalizeError(error: any): NormalizedApiError {
  if (error.response && error.response.data) {
    const data = error.response.data;
    return {
      status: error.response.status,
      message: data.message || "An unexpected error occurred.",
      errorCode: data.errorCode || "INTERNAL_ERROR",
    };
  }
  return {
    status: 0,
    message: "Network error. Please check your connection.",
    errorCode: "NETWORK_ERROR",
  };
}
```

---

## 11. CORS & Development Integration

* **Backend Port:** `http://localhost:8080`
* **Vite Frontend Dev Port:** `http://localhost:5173`
* **CORS Current Status (Confirmed from `CorsConfig.java`):**
  * Allowed origins include: `http://localhost:3000,http://localhost:5173`
  * Allowed methods: `GET, POST, PUT, PATCH, DELETE, OPTIONS`
  * Allowed headers: `Authorization, Content-Type, Accept, Origin, X-Requested-With`
  * Exposed headers: `Authorization`
  * `allowCredentials: true`
* **Recommended Development Strategy:**
  * Configure Vite proxy in `vite.config.ts`:
    ```typescript
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        }
      }
    }
    ```
  * Using proxy prevents browser cross-origin friction during local development and eliminates localhost port mismatch errors.

---

## 12. Accessibility (WCAG 2.1 AA) Standards

1. **Semantic Structure:** Single `<h1>` per page, hierarchical `<h2>` and `<h3>`, `<main>`, `<nav>`, `<header>`, `<footer>`.
2. **Keyboard Navigation:** All interactive elements (`Button`, `Card` links, modal triggers) are tab-accessible with visible `--cc-focus` outlines (2px solid `--cc-forest` with 2px offset).
3. **Form Association:** Every input has an explicitly linked `<label htmlFor="...">`. Form validation errors link to input via `aria-describedby` and announce via `aria-live="polite"`.
4. **Touch Target Sizing:** Minimum 44×44px interactive touch target size for buttons, tabs, pagination triggers, and form controls.
5. **Color Independence:** Status badges combine color with distinct typography and icons (e.g. `SELECTED` uses green background, dark text, and a checkmark icon; `REJECTED` uses red background and cross icon).
6. **Motion Control:** `@media (prefers-reduced-motion: reduce)` disables all non-essential transitions and animations.

---

## 13. Frontend Integration Findings & Gaps

| Item | Classification | Description | Architectural Resolution |
|---|---|---|---|
| **Withdrawal HTTP Verb** | Informational | `CandidateApplicationController.java` line 47 implements `@PatchMapping("/{applicationId}/withdraw")`. `API_CONTRACT.md` section 5.5 mentions `POST`. Tests confirm `PATCH`. | Frontend API client must call `PATCH /api/candidate/applications/{id}/withdraw`. |
| **Saved Jobs Endpoint** | Low / Deferred | Master Spec line 150 mentions `GET/PUT/DELETE /candidate/saved-jobs/{jobId}` and DB schema includes `saved_jobs` table, but no Spring Boot controller exists. | Candidate Discover → Match → Apply → Track flow is 100% complete without it. Excluded from V1 blocking scope; deferred to future enhancement. |
| **In-Memory JWT Reloads** | High (UX Rule) | In-memory token storage means browser page refresh resets auth context. | Provide clear UX: seamless session recovery prompt or clean redirect to `/login?next=...` without crashing or showing broken half-authenticated state. |
| **PDF Resume Binary Handling**| High (Security Rule)| Protected resumes require Bearer token; standard `<a href="...">` links will trigger 401. | Frontend must fetch resume using API client with `Authorization: Bearer <token>`, convert response to Blob, and open via `URL.createObjectURL(blob)` in a sandboxed viewer. |

---

## 14. Frontend Implementation Roadmap (P9.1 – P9.9)

### P9.1 — Frontend Foundation & Design Tokens
* Initialize Vite + React 18 + TypeScript in `frontend/`.
* Configure `tokens.css` with CareerConnect design palette and DM Serif Display / Inter typography.
* Implement UI primitives (`Button`, `TextField`, `Card`, `Modal`, `Badge`).
* Implement central `apiClient.ts` with error normalization and in-memory token handling.

### P9.2 — Authentication, Session & Route Protection
* Implement `AuthContext` and `useAuth` hook.
* Build `/login` and `/register` with role selector (Candidate vs Recruiter).
* Implement `<ProtectedRoute>` with role-aware gating (`/candidate/*`, `/recruiter/*`, `/admin/*`).
* Implement `/403`, `/404`, and `/500` error screens.

### P9.3 — Public Discovery Experience
* Build Public Header, Footer, and Landing Page hero.
* Build `/jobs` with multi-attribute query-bound filters, search, and pagination.
* Build `/jobs/:jobId` detail view with company info and requirements.
* Build `/companies` directory and `/companies/:companyId` profile.

### P9.4 — Candidate Experience (Profile, Match, Apply, Track)
* Build Candidate Dashboard with Readiness Meter (0–100%).
* Build Profile Editor (Education, Skills, Experience, Projects CRUD).
* Build PDF Resume upload zone with magic byte & 5 MiB validation.
* Build Job Detail Deterministic Match summary badge popover.
* Build Application Submission Modal and confirmation dialog.
* Build `/candidate/applications` list and `/candidate/applications/:id` snapshot/timeline viewer.
* Build Application Withdrawal dialog.

### P9.5 — Recruiter Experience (Company, Jobs, Applicant Review)
* Build Recruiter Dashboard with quick stats.
* Build Company Profile upsert form (`/recruiter/company/edit`).
* Build Job Creation & Edit forms with skill selector and deadline validation.
* Build Job Lifecycle actions (Save Draft, Publish, Close, Delete).
* Build Job Applicants review table with status filters.
* Build Applicant Detail modal with frozen candidate & resume snapshots.
* Build Status Transition dropdown control with atomic note capture.
* Build Protected Resume streaming viewer.

### P9.6 — Admin Experience (Dashboard, Moderation, Audit)
* Build Admin Dashboard with platform aggregate metrics.
* Build `/admin/users` with user status toggle (with self-lockout guard).
* Build `/admin/companies` with company status toggle (cascading job hiding).
* Build `/admin/jobs` with Archive and Restore controls.
* Build `/admin/applications` platform-wide inspector and resume viewer.
* Build `/admin/audit` with paginated audit logs and JSON diff viewer.

### P9.7 — Notifications, Accessibility & Responsive Polish
* Build Top Navigation notification bell with unread badge counter.
* Build notification popover and notification feed with single & bulk read marking.
* Execute WCAG AA accessibility audit (keyboard trapping, ARIA attributes, color contrast).
* Execute mobile responsiveness audit down to 320px viewport.

### P9.8 — Full Integration & End-to-End Verification
* Execute full browser-driven canonical journey: Candidate registers → completes profile (100%) → uploads PDF → discovers job → checks match score → applies → Recruiter reviews snapshot & streams PDF → updates status to `SELECTED` → Candidate receives in-app notification → Admin inspects audit log.
* Run backend regression suite (677 tests) to verify 100% pass rate.

### P9.9 — Academic Submission Artifacts & Demo Readiness
* Capture UI screenshots for all key flows.
* Capture DevTools network traces showing authenticated Bearer tokens and JSON envelopes.
* Finalize academic traceability matrix and presentation guide.

---

## 15. Quality & Architecture Sign-Off
* [x] Every backend controller verified from code.
* [x] All 59 REST endpoint operations mapped and documented.
* [x] In-memory JWT constraint strictly preserved (no localStorage/sessionStorage).
* [x] All backend business rules mapped to frontend UX behaviors.
* [x] Design tokens and component hierarchy defined.
* [x] Zero backend production code modified.
* [x] Zero fake or future features invented.
