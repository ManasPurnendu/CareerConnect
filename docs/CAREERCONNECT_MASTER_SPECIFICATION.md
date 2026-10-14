# CareerConnect — Final Master Specification

**ID:** CC-MASTER-001 • **Version:** 1.2 • **Phase:** 0 • **Status:** FROZEN • **Last updated:** 2026-10-14  
**Authority:** This document is the single source of truth for CareerConnect implementation. It is self-contained and governs frontend, backend, database, testing, UI, and human development. Change requires an approved entry with rationale, affected API/schema/tests, and a version update.

## 1. Executive summary and principles

CareerConnect is a candidate-first early-career recruitment platform for internships, freshers, recent graduates, and candidates with 0–2 years’ experience. It solves the path from job discovery through profile-backed application and status tracking, while giving recruiters a structured employer/job/applicant workflow and admins a lightweight moderation layer.

Candidate journey: **Discover → Match → Apply → Track**. Recruiter journey: **Post → Review → Shortlist → Manage → Hire**. V1 principles: candidate-first; early-career focus; transparent deterministic matching; simple structured workflow; security/privacy; accessible UI; and an editorial, institutional, modern, human-designed identity. V1 is not an AI hiring system, social network, or general senior-hiring platform.

## 2. Actors, roles, and permissions

| Actor | Auth | Purpose/capabilities | Restrictions |
|---|---|---|---|
| Guest | no | browse active jobs/companies; register/login | cannot save/apply/view protected records |
| Candidate | `CANDIDATE` | own profile/resume/skills/education/projects/experience, saved jobs, applications, notifications | only own records; cannot recruit or moderate |
| Recruiter | `RECRUITER` | create/join one company; manage its jobs/applicants/notifications | at most one company; only relevant application data |
| Admin | `ADMIN` | dashboard, user/company/job/application moderation, audit | provisioned only; no public registration |

| Capability | Guest | Candidate | Recruiter | Admin |
|---|---:|---:|---:|---:|
| Browse active jobs/companies | ✓ | ✓ | ✓ | ✓ |
| Register/login/logout | ✓ | ✓ | ✓ | provisioned only |
| Own candidate profile/resume/saved/applications | — | ✓ | — | — |
| Company and job management | — | — | own company | all for moderation |
| Applicant snapshot/resume access | — | own | own-company job only | ✓ |
| Archive/deactivate/audit | — | — | — | ✓ |

All authorization is server-side. Candidate ownership is token candidate ID; recruiter ownership is `application.job.company_id = token recruiter.company_id`. Altering an ID in URL/body never grants access.

## 3. V1 scope and exclusions

**V1:** auth; public job/company discovery; candidate profile and current PDF resume; skills/education/projects/experience; saved jobs; application submission/snapshot/status/withdrawal; recruiter company, jobs, applicants and transparent match; in-app notifications; admin moderation/audit; responsive accessible UI; React/Spring Boot/JdbcTemplate/MySQL; automated/manual tests.

**Future only:** email verification/password reset, recruiter teams, employer/candidate verification, saved searches/alerts, resume versions, certificates, recommendations/ranking, interview scheduling/video interview, assessments, real-time chat, payments, social/network feed, ratings, AI chatbot/resume generation/interview evaluation/ML, mobile apps, internationalization, advanced analytics, learning paths and integrations. Future work has no V1 route/API/table/UI.

## 4. Business rules

### Candidate

- Browsing does not require profile completion. Applying requires `fullName`, one education record, one skill, and one current resume.
- A candidate has at most one application per job, including after withdrawal. Profile edits are allowed after applying; application snapshots never change.
- Candidate may withdraw only `APPLIED`, `UNDER_REVIEW`, `SHORTLISTED`, or `INTERVIEW` applications.

### Company, recruiter, jobs

- **One company has many recruiters.** A recruiter has zero or one company; company membership is required before publishing. `recruiters.company_id` is nullable and is **not unique**.
- A job requires title, description, job type, experience level, work mode, location unless remote, at least one required skill, and deadline today/future. Salary/education requirement are optional; salary is INR LPA with `min > 0`, `max >= min`.
- Recruiter owns jobs through company membership. New job is `DRAFT`; recruiter workflow is `DRAFT → ACTIVE → CLOSED`. Deadline expiry removes public eligibility and should persist a close.
- Recruiter may edit own draft/active job. Existing application snapshots remain historic. Recruiter soft-deletes only non-active zero-application jobs; jobs with applications only close. `ARCHIVED` is admin-only moderation; it is public-hidden/read-only and restores only to `CLOSED`.

### Matching, applications, admin

- Normalize skill via `lower(trim(name))`; `percentage = round(100 * matchedRequired / requiredSkills)`. Return `matchedSkills`, `missingSkills`, `additionalSkills`; do not block application, rank candidates, or make a hiring decision.
- Application submission checks active/non-expired job, completeness/resume, and no duplicate; stores immutable profile/resume snapshots. Status event has optional 500-character note, history row, and notification.
- Admin may deactivate users, moderate/archive company/job and read audit logs; every action creates audit log.

## 5. Canonical enum and state registry

| Domain | Label | Exact API/DB value | Constraint |
|---|---|---|---|
| Role | Candidate/Recruiter/Admin | `CANDIDATE`, `RECRUITER`, `ADMIN` | Admin no self-registration |
| Job type | Internship/Full-time/Part-time/Apprenticeship/Trainee | `INTERNSHIP`, `FULL_TIME`, `PART_TIME`, `APPRENTICESHIP`, `TRAINEE` | V1 only |
| Experience | Internship/Fresher/0–1 years/1–2 years | `INTERNSHIP`, `FRESHER`, `ZERO_TO_ONE_YEARS`, `ONE_TO_TWO_YEARS` | labels are separate from machine values |
| Work mode | On-site/Hybrid/Remote | `ONSITE`, `HYBRID`, `REMOTE` | V1 |
| Job state | Draft/Active/Closed/Archived | `DRAFT`, `ACTIVE`, `CLOSED`, `ARCHIVED` | archive admin only |
| Application | Applied/Under review/Shortlisted/Interview/Selected/Rejected/Withdrawn | `APPLIED`, `UNDER_REVIEW`, `SHORTLISTED`, `INTERVIEW`, `SELECTED`, `REJECTED`, `WITHDRAWN` | exact uppercase strings |

Allowed application transitions: APPLIED→UNDER_REVIEW/SHORTLISTED/REJECTED/WITHDRAWN; UNDER_REVIEW→SHORTLISTED/REJECTED/WITHDRAWN; SHORTLISTED→INTERVIEW/REJECTED/WITHDRAWN; INTERVIEW→SELECTED/REJECTED/WITHDRAWN. Recruiter performs every non-withdraw transition; candidate performs withdrawal. SELECTED/REJECTED/WITHDRAWN terminal; no same-state event, reopen, or APPLIED→SELECTED.

## 6. User journeys and information architecture

Candidate: public job search/filter/detail/match → login/register when Apply/Save is selected → profile completion/upload PDF → apply → own timeline/notification → permitted withdrawal. Recruiter: register/login → create or join company → draft job → publish → own-job applicants → inspect snapshots/match → status transition → close. Admin: login → dashboard → filter moderation records → deactivate/archive → audit.

Navigation zones: public header (`Jobs`, `Companies`, `About`, auth); candidate sidebar (Dashboard, Jobs, Saved, Applications, Profile, Notifications, Settings); recruiter sidebar (Dashboard, Jobs, Applications, Company, Notifications, Settings); admin sidebar (Dashboard, Users, Companies, Jobs, Applications, Moderation, Audit, Settings).

## 7. Complete sitemap and page specification

| Route | Access | V1 | Layout/purpose |
|---|---|---:|---|
| `/`, `/jobs`, `/jobs/:jobId`, `/companies`, `/companies/:companyId` | public | ✓ | public header; home/discovery/detail/company |
| `/login`, `/register`, `/about`, `/privacy`, `/terms` | public | ✓ | auth/static layout |
| `/verify-email`, `/forgot-password`, `/reset-password` | public | Future | no V1 implementation |
| `/candidate`, `/candidate/jobs`, `/candidate/jobs/:jobId`, `/candidate/saved`, `/candidate/applications`, `/candidate/applications/:applicationId`, `/candidate/profile`, `/candidate/profile/edit`, `/candidate/notifications`, `/candidate/settings` | candidate | ✓ | candidate shell; own resources |
| `/recruiter`, `/recruiter/jobs`, `/recruiter/jobs/new`, `/recruiter/jobs/:jobId`, `/recruiter/jobs/:jobId/edit`, `/recruiter/applications`, `/recruiter/applications/:applicationId`, `/recruiter/company`, `/recruiter/company/edit`, `/recruiter/notifications`, `/recruiter/settings` | recruiter | ✓ | recruiter shell; own company resources |
| `/recruiter/team` | recruiter | Future | excluded |
| `/admin`, `/admin/users`, `/admin/companies`, `/admin/jobs`, `/admin/applications`, `/admin/moderation`, `/admin/audit`, `/admin/settings` | admin | ✓ | admin shell/moderation |
| `/admin/verification` | admin | Future | excluded |
| `/403`, `/404`, `/500` | all | ✓ | system state |

All V1 pages have loading skeleton, useful empty state, error/retry state, keyboard focus, and mobile layout. Home: hero/search/categories/featured data/how-it-works. Jobs: query-bound filters, result count, cards/pagination. Job detail: metadata/skills/description/Apply/Save. Candidate dashboard: profile strength/recent apps/saved/notifications. Profile: editable sections/in-line errors. Application detail: immutable snapshot + all-status timeline. Recruiter job form: Save Draft/Publish. Recruiter applicant: snapshot/protected resume/match/status control. Admin: filterable paginated tables and confirmation notices. Anonymous protected routes redirect `/login?next=...`; wrong role `/403`.

## 8. Design system and components

**Direction:** Editorial + Institutional + Modern; restrained, professional, information-led—not generic AI SaaS.

```css
:root { --cc-ink:#202522; --cc-forest:#29483F; --cc-olive:#65705A; --cc-ivory:#F5F2EA; --cc-paper:#FBFAF6; --cc-warm-gray:#D8D4CA; --cc-muted-text:#74766F; --cc-soft-green:#DCE6DF; --cc-success:#2F6B4F; --cc-warning:#8A6519; --cc-danger:#A23A32; --cc-focus:#29483F; --cc-font-display:"DM Serif Display",Georgia,serif; --cc-font-body:"Inter",system-ui,sans-serif; --cc-space-1:4px; --cc-space-2:8px; --cc-space-3:12px; --cc-space-4:16px; --cc-space-5:24px; --cc-space-6:32px; --cc-space-7:48px; --cc-space-8:64px; --cc-radius-sm:4px; --cc-radius-md:8px; --cc-radius-lg:12px; --cc-border:1px solid #D8D4CA; --cc-shadow-sm:0 1px 2px rgb(32 37 34 / 8%); --cc-shadow-md:0 6px 18px rgb(32 37 34 / 10%); }
```

Body 16px/1.5; small 14px; H1 48px desktop/36px mobile, H2 32px, H3 24px; display font for headings and Inter body/UI. Max width 1200px; 12/8/4 columns, 24/20/16px gutters; breakpoints 640/768/1024/1280px; stack below 768px. Inputs/buttons minimum 44px. Cards: paper, Warm Gray border, 12px radius, 24px padding. Status badges use colour plus label: APPLIED warm gray, UNDER_REVIEW soft green, SHORTLISTED olive, INTERVIEW forest, SELECTED success, REJECTED danger, WITHDRAWN muted. WCAG AA, semantic labels, visible focus, aria-live errors, reduced-motion support required. Prohibit purple/blue AI gradients, glassmorphism, neon, sparkle/rainbow dashboards, generic AI/stock hero, excessive rounding/pills/whitespace/motion, unlabeled icon controls.

| Group | Components (purpose/data) |
|---|---|
| Common/navigation | Button, TextField, Select, TextArea, Dialog, Badge, Card, Pagination, EmptyState, ErrorState, Skeleton, PublicHeader, RoleSidebar, PageHeader, Footer, ProtectedRoute |
| Jobs/company | JobCard (`JobSummary`), JobFilters, JobMeta, SkillChipList, SaveJobButton, CompanyForm, JobForm (`JobUpsertRequest`) |
| Applications/profile | ApplicationCard, ApplicationStatusControl, StatusTimeline (`ApplicationStatusHistory[]`), MatchSummary (`JobMatch`), ProfileStrength, ResumeUpload, ApplicantSnapshot |
| Recruiter/admin | ModerationTable, ApplicantList, NotificationList |

Components accept typed DTO data/callbacks, not HTTP clients; pages orchestrate mutations. Shared enums live only in `types/api.ts`.

## 9. Frontend, backend, and project architecture

Frontend: React + TypeScript + Vite + React Router; typed fetch API client; `AuthProvider` and feature hooks; local component state for form UI (no unapproved global state library). Tree: `frontend/src/{app,api,components/{ui,layout,domain},features/{auth,jobs,candidate,recruiter,admin},pages,routes,types,styles,hooks,utils}`.

Backend: Java 21+, Maven, Spring Boot 3+, MVC, Security, Bean Validation, MySQL driver, **JdbcTemplate only**. Flow: React → REST API → Controller (HTTP/DTO) → Service (business/ownership/transaction) → Repository (parameterized JdbcTemplate) → MySQL. No business logic/SQL in controllers, no direct controller→DB, no JPA/Hibernate. Tree: `backend/src/main/java/com/careerconnect/{config,security,controller,service,repository,domain,dto/{request,response},mapper,exception,validation}`; migrations `backend/src/main/resources/db/migration`; `docs/` contains this file; `database/` contains schema reference only after Phase 1.

## 10. Database contract

`careerconnect_db`; MySQL/InnoDB; `utf8mb4`; UTC `DATETIME(3)`; `BIGINT UNSIGNED AUTO_INCREMENT` IDs; snake_case DB/camelCase API; mutable records have created_at/updated_at/deleted_at where relevant. API enum strings equal DB `ENUM` members.

| Table | Key columns | Relationships/constraints/indexes |
|---|---|---|
| users | email VARCHAR(254), password_hash, role, is_active | unique normalized email |
| candidates | user_id, full_name, location, headline, summary | unique FK users |
| companies | name, industry, location, website, description, company_size, is_active | unique name; one→many recruiters/jobs |
| recruiters | user_id, company_id NULL, full_name | user_id UNIQUE; company_id FK **NOT UNIQUE** |
| skills | name, normalized_name | normalized_name UNIQUE |
| candidate_skills/job_skills/saved_jobs | paired FKs | composite PK |
| education/projects/experience | candidate_id plus described profile fields/sort_order | candidate/sort indexes |
| resumes | candidate_id, original_filename, storage_key, content_type, size_bytes, is_current | storage_key unique; one current per candidate |
| jobs | company_id,title,description,responsibilities,requirements,job_type,experience_level,work_mode,location,salary_min_lpa/max_lpa/currency,application_deadline,status | `(status,application_deadline,deleted_at)`, `(company_id,status)` |
| applications | job_id,candidate_id,cover_letter,status,candidate_snapshot JSON,resume_snapshot JSON,applied_at | unique candidate/job; job/status/time, candidate/time indexes |
| application_status_history | application_id,from_status,to_status,changed_by_user_id,note,created_at | immutable; application/time index |
| notifications | user_id,type,title,body,resource_type/id,read_at,created_at | user/read/time index |
| audit_logs | actor_user_id,action,entity_type/id,metadata JSON,created_at | entity/time index |

ER: users 1→0..1 candidates; users 1→0..1 recruiters; companies 1→many recruiters; companies 1→many jobs; candidates many↔many skills; jobs many↔many skills; candidates 1→many applications; jobs 1→many applications; application 1→many status history; candidates 1→many resumes/education/projects/experience/saved jobs. Application snapshot contains submitted profile skills/education/projects/experience and resume ID/filename/private key/MIME/size; later edits do not alter it.

## 11. API, response, authentication, security

Base `/api`; JSON UTF-8; numeric IDs; ISO-8601 UTC timestamps; date `YYYY-MM-DD`; pagination zero-based/default 20/max 100. Success `{success:true,data:{},message:null}`; collection `{success:true,data:[],pagination:{page,size,totalElements,totalPages},message:null}`; error `{success:false,data:null,message:"...",errorCode:"ERROR_CODE"}`.

| API | Role | Contract |
|---|---|---|
| POST `/auth/register`, `/auth/login`; POST `/auth/logout`; GET `/auth/me` | public/public/auth/auth | register `{email,password,role,fullName}` (not admin); auth response `{accessToken,tokenType:"Bearer",expiresAt,user}`; logout 204 |
| GET `/jobs`, `/jobs/{jobId}`, `/companies`, `/companies/{companyId}` | public | job filters `q,location,jobType,experienceLevel,workMode,skill,minSalaryLpa,page,size,sort`; active public only |
| GET/PUT `/candidate/profile`; skill/resource CRUD; POST `/candidate/resume` | candidate | own resources; multipart file |
| GET/PUT/DELETE `/candidate/saved-jobs/{jobId}`; POST `/jobs/{jobId}/applications`; GET/detail/withdraw `/candidate/applications`; GET/PATCH notifications | candidate | own data; apply `{coverLetter?}`; withdraw `{note?}` |
| GET/PUT `/recruiter/company`; job CRUD/publish/close; job applicants/detail/status/match; notifications | recruiter | own company only; job body `{title,description,responsibilities?,requirements?,jobType,experienceLevel,workMode,location?,salaryMinLpa?,salaryMaxLpa?,salaryCurrency?,applicationDeadline,requiredSkillIds}`; status `{status,note?}` |
| dashboard/list/moderate users/companies/jobs/applications/audit | admin | filters/page/size; archive/deactivate action audited |

JWT is the sole auth mechanism: 30-minute signed Bearer access token held **in memory only**; no cookie, localStorage/sessionStorage, refresh token, URL token, or CSRF mechanism in V1. Backend validates signature/expiry/active user/role every request. BCrypt passwords, configured CORS origin + Authorization header, HTTPS production, rate-limited login, security headers, parameterized SQL, React output escaping, safe logs/no secrets, and no stack trace response are mandatory. Resume is PDF-only (`application/pdf` and verified signature), max 5 MiB, randomized private storage key; only owner, relevant recruiter application, or admin can protected-download it.

Validation hierarchy: frontend UX → DTO Bean Validation → service rules/ownership → DB constraints. Error codes: 400 `VALIDATION_FAILED`; 401 `UNAUTHENTICATED`,`INVALID_CREDENTIALS`; 403 `FORBIDDEN`; 404 `RESOURCE_NOT_FOUND`,`JOB_NOT_FOUND`; 409 `EMAIL_ALREADY_EXISTS`,`DUPLICATE_APPLICATION`; 422 `CANDIDATE_PROFILE_INCOMPLETE`,`CANDIDATE_RESUME_REQUIRED`,`COMPANY_REQUIRED`,`JOB_NOT_ACTIVE`,`JOB_DEADLINE_PASSED`,`JOB_DEADLINE_INVALID`,`JOB_SKILL_REQUIRED`,`INVALID_STATUS_TRANSITION`,`RESUME_INVALID_TYPE`,`RESUME_TOO_LARGE`; 500 `INTERNAL_ERROR`.

## 12. Notifications, testing, academics, implementation plan

V1 notifications are in-app only. Trigger on application status change and job closure; recipient is application candidate for status, affected candidate where applicable. List supports read/unread and PATCH `{read:true}`; no email/push delivery.

Test minimum: valid candidate registration; reject admin self-registration; recruiter publish without company; past deadline/zero skills rejected; incomplete/no-resume/duplicate/expired application rejected; foreign recruiter access blocked; full lifecycle history/notifications; invalid withdraw/reopen; exact match; injection-like search; invalid/oversize PDF; keyboard/error focus and 320px layout. Test services, JdbcTemplate repositories, controller/auth/envelopes, frontend pages/forms/guards, E2E canonical demo, security/validation/accessibility/responsive states.

Academic mapping: Java/Maven/Spring Boot REST uses Controller→Service→Repository→JdbcTemplate→SQL; many domain entities; validations/state rules; JUnit/integration/UI tests; architecture diagram, running app, API calls, DB relationship and test demonstrations satisfy required evidence.

After Phase 0: P1 bootstrap/migrations/config only; P2 schema and public jobs; P3 auth/security; P4 candidate profile/resume; P5 applications/matching; P6 recruiter company/jobs; P7 applicant/status/notifications; P8 admin; P9 accessibility/testing/demo. Each phase must not pull Future scope forward.

## 13. Traceability and final readiness

| Requirement | Page/component | API/service | Repository/table | Tests |
|---|---|---|---|---|
| Auth | Login/AuthProvider | auth/AuthService | users | registration/auth tests |
| Discovery | Jobs/JobCard | GET jobs/JobService | jobs/companies/skills | filter/API tests |
| Profile | Profile/ResumeUpload | candidate/CandidateService | candidates/profile tables/resumes | prerequisite/upload tests |
| Apply/status | ApplicationCard/Timeline | applications/ApplicationService | applications/history/notifications | duplicate/lifecycle tests |
| Recruit | JobForm/ApplicantSnapshot | recruiter/RecruiterService | companies/jobs/job_skills | company/job ownership tests |
| Match | MatchSummary | match/MatchService | candidate_skills/job_skills | percentage test |
| Moderation | ModerationTable | admin/AdminService | targets/audit_logs | role/audit tests |

**PHASE 0 STATUS: FROZEN.** Readiness: scope ✓; roles/permissions ✓; rules/enums ✓; routes/pages ✓; API/database ✓; auth/security ✓; design/components ✓; tests/academics ✓; Future features separated ✓. This file is ready as the implementation contract.
