# CareerConnect — REST API Contract

**Document:** API Contract Specification  
**Project:** CareerConnect  
**Phase:** Phase 8 Complete (Admin Dashboard, Moderation Mutations, Atomic Audit, Security Hardening & Release Gate)  
**Base URL:** `http://localhost:8080/api`  
**Envelope Policy:** Canonical JSON Envelope  

---

## 1. General Principles

1. **Envelope Structure:**
   * Single Resource:
     ```json
     {
       "success": true,
       "data": { ... },
       "message": null
     }
     ```
   * Paginated Collection:
     ```json
     {
       "success": true,
       "data": [ ... ],
       "pagination": {
         "page": 0,
         "size": 20,
         "totalElements": 142,
         "totalPages": 8
       },
       "message": null
     }
     ```
   * Error:
     ```json
     {
       "success": false,
       "data": null,
       "message": "Human-readable explanation.",
       "errorCode": "MACHINE_STABLE_ERROR_CODE"
     }
     ```

2. **Standard HTTP Status Codes:**
   * `200 OK`: Request succeeded.
   * `201 Created`: Resource successfully created.
   * `204 No Content`: Request succeeded with no response body (e.g. logout).
   * `400 Bad Request`: Input validation failed (`VALIDATION_FAILED`).
   * `401 Unauthorized`: Authentication missing, expired, or invalid (`UNAUTHENTICATED`, `INVALID_CREDENTIALS`).
   * `403 Forbidden`: Authenticated user lacks permission (`FORBIDDEN`).
   * `404 Not Found`: Resource does not exist (`RESOURCE_NOT_FOUND`, `JOB_NOT_FOUND`).
   * `409 Conflict`: Unique constraint violation (`EMAIL_ALREADY_EXISTS`, `DUPLICATE_APPLICATION`).
   * `422 Unprocessable Content`: Domain business rule violation (e.g. `JOB_DEADLINE_PASSED`, `CANDIDATE_PROFILE_INCOMPLETE`).
   * `500 Internal Server Error`: Server exception (`INTERNAL_ERROR`).

---

## 2. Authentication Endpoints

### 2.1 Register User
* **Method:** `POST`
* **Path:** `/api/auth/register`
* **Access:** Public
* **Request Headers:** `Content-Type: application/json`
* **Request Body:**
  ```json
  {
    "email": "candidate@example.com",
    "password": "Password123!",
    "role": "CANDIDATE",
    "fullName": "Jane Doe"
  }
  ```
  * Note: `role` must be `CANDIDATE` or `RECRUITER`. `ADMIN` is strictly rejected.
* **Success Response (`201 Created`):**
  ```json
  {
    "success": true,
    "data": {
      "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
      "tokenType": "Bearer",
      "expiresAt": "2026-10-14T19:00:00.000Z",
      "user": {
        "id": 1,
        "email": "candidate@example.com",
        "role": "CANDIDATE",
        "fullName": "Jane Doe",
        "candidateId": 1
      }
    },
    "message": "Registration successful"
  }
  ```
* **Potential Errors:**
  * `400 Bad Request` (`VALIDATION_FAILED`): Malformed JSON, blank email/password/name, invalid email format, password < 8 chars.
  * `409 Conflict` (`EMAIL_ALREADY_EXISTS`): Email already in use.
  * `422 Unprocessable Content` (`VALIDATION_FAILED`): Attempting to register as `ADMIN`.

---

### 2.2 Login User
* **Method:** `POST`
* **Path:** `/api/auth/login`
* **Access:** Public
* **Request Headers:** `Content-Type: application/json`
* **Request Body:**
  ```json
  {
    "email": "candidate@example.com",
    "password": "Password123!"
  }
  ```
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": {
      "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
      "tokenType": "Bearer",
      "expiresAt": "2026-10-14T19:00:00.000Z",
      "user": {
        "id": 1,
        "email": "candidate@example.com",
        "role": "CANDIDATE",
        "fullName": "Jane Doe",
        "candidateId": 1
      }
    },
    "message": null
  }
  ```
* **Potential Errors:**
  * `400 Bad Request` (`VALIDATION_FAILED`): Blank email or password.
  * `401 Unauthorized` (`INVALID_CREDENTIALS`): Email not found or incorrect password.
  * `403 Forbidden` (`FORBIDDEN`): User account deactivated.

---

### 2.3 Get Current User Profile
* **Method:** `GET`
* **Path:** `/api/auth/me`
* **Access:** Authenticated (Any Role)
* **Request Headers:** `Authorization: Bearer <accessToken>`
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": {
      "id": 1,
      "email": "candidate@example.com",
      "role": "CANDIDATE",
      "fullName": "Jane Doe",
      "candidateId": 1
    },
    "message": null
  }
  ```
* **Potential Errors:**
  * `401 Unauthorized` (`UNAUTHENTICATED`): Missing, expired, or tampered token.

---

### 2.4 Logout
* **Method:** `POST`
* **Path:** `/api/auth/logout`
* **Access:** Authenticated
* **Request Headers:** `Authorization: Bearer <accessToken>`
* **Success Response (`204 No Content`):** Empty body.
* **Potential Errors:**
  * `401 Unauthorized` (`UNAUTHENTICATED`): Missing or invalid token.

---

## 3. System Endpoints

### 3.1 Health Check
* **Method:** `GET`
* **Path:** `/api/health`
* **Access:** Public
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": {
      "status": "UP",
      "database": "CONNECTED",
      "timestamp": "2026-10-14T18:32:00.000Z",
      "version": "1.0.0"
    },
    "message": null
  }
  ```

---

## 4. Public Discovery Endpoints

### 4.1 Search & Filter Jobs
* **Method:** `GET`
* **Path:** `/api/jobs`
* **Access:** Public
* **Query Parameters:**
  * `keyword` (string, optional): Full-text search matching title, description, or company name.
  * `location` (string, optional): Location substring match.
  * `employmentType` (enum, optional): `INTERNSHIP`, `FULL_TIME`, `PART_TIME`, `APPRENTICESHIP`, `TRAINEE`.
  * `experienceLevel` (enum, optional): `INTERNSHIP`, `FRESHER`, `ZERO_TO_ONE_YEARS`, `ONE_TO_TWO_YEARS`.
  * `workMode` (enum, optional): `ONSITE`, `HYBRID`, `REMOTE`.
  * `skills` (string, optional): Comma-separated skill names; jobs matching any listed skill are returned.
  * `minSalary` (decimal, optional): Minimum salary threshold in LPA. Matches jobs whose `salaryMaxLpa >= minSalary`.
  * `maxSalary` (decimal, optional): Maximum salary threshold in LPA. Matches jobs whose `salaryMinLpa <= maxSalary`.
  * `sort` (string, optional): `latest` (default), `oldest`, `deadline,asc`, `salary,desc`, `title,asc`.
  * `page` (int, optional): Zero-based page index (default 0).
  * `size` (int, optional): Page size (default 20, max 100).
* **Public Invariant:** Only returns jobs where `status = 'ACTIVE'`, `application_deadline >= CURRENT_DATE`, `deleted_at IS NULL`, and `companies.is_active = TRUE`.
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": [
      {
        "id": 1,
        "title": "Software Engineer Intern",
        "company": {
          "id": 1,
          "name": "TechCorp Solutions",
          "industry": "Technology",
          "location": "Bengaluru",
          "website": "https://techcorp.example.com",
          "companySize": "501-1000",
          "activeJobCount": 3
        },
        "jobType": "INTERNSHIP",
        "experienceLevel": "FRESHER",
        "workMode": "HYBRID",
        "location": "Bengaluru",
        "salaryMinLpa": 6.00,
        "salaryMaxLpa": 9.00,
        "salaryCurrency": "INR",
        "applicationDeadline": "2026-12-31",
        "status": "ACTIVE",
        "skills": [
          { "id": 1, "name": "Java" },
          { "id": 2, "name": "Spring Boot" }
        ],
        "createdAt": "2026-10-14T10:00:00Z"
      }
    ],
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1
    },
    "message": null
  }
  ```

---

### 4.2 Get Job Details
* **Method:** `GET`
* **Path:** `/api/jobs/{jobId}`
* **Access:** Public
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": {
      "id": 1,
      "title": "Software Engineer Intern",
      "description": "Develop high throughput backend microservices.",
      "responsibilities": "Design and build REST APIs using Spring Boot.",
      "requirements": "Strong problem-solving and OOP fundamentals.",
      "company": {
        "id": 1,
        "name": "TechCorp Solutions",
        "industry": "Technology",
        "location": "Bengaluru",
        "website": "https://techcorp.example.com",
        "companySize": "501-1000",
        "activeJobCount": 3
      },
      "jobType": "INTERNSHIP",
      "experienceLevel": "FRESHER",
      "workMode": "HYBRID",
      "location": "Bengaluru",
      "salaryMinLpa": 6.00,
      "salaryMaxLpa": 9.00,
      "salaryCurrency": "INR",
      "applicationDeadline": "2026-12-31",
      "status": "ACTIVE",
      "skills": [
        { "id": 1, "name": "Java" },
        { "id": 2, "name": "Spring Boot" }
      ],
      "createdAt": "2026-10-14T10:00:00Z",
      "updatedAt": "2026-10-14T10:00:00Z"
    },
    "message": null
  }
  ```
* **Potential Errors:**
  * `404 Not Found` (`JOB_NOT_FOUND`): Nonexistent, DRAFT, CLOSED, ARCHIVED, soft-deleted, past deadline, or deactivated company.

---

### 4.3 Search & Browse Companies
* **Method:** `GET`
* **Path:** `/api/companies`
* **Access:** Public
* **Query Parameters:**
  * `q` (string, optional): Search by company name, industry, or location.
  * `page` (int, optional): Zero-based page index (default 0).
  * `size` (int, optional): Page size (default 20, max 100).
* **Public Invariant:** Only returns companies where `is_active = TRUE`.
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": [
      {
        "id": 1,
        "name": "TechCorp Solutions",
        "industry": "Technology",
        "location": "Bengaluru",
        "website": "https://techcorp.example.com",
        "companySize": "501-1000",
        "activeJobCount": 3
      }
    ],
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1
    },
    "message": null
  }
  ```

---

### 4.4 Get Company Details
* **Method:** `GET`
* **Path:** `/api/companies/{companyId}`
* **Access:** Public
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": {
      "id": 1,
      "name": "TechCorp Solutions",
      "industry": "Technology",
      "location": "Bengaluru",
      "website": "https://techcorp.example.com",
      "description": "Leading enterprise software and cloud provider.",
      "companySize": "501-1000",
      "activeJobCount": 1,
      "activeJobs": [
        {
          "id": 1,
          "title": "Software Engineer Intern",
          "company": { ... },
          "jobType": "INTERNSHIP",
          "experienceLevel": "FRESHER",
          "workMode": "HYBRID",
          "location": "Bengaluru",
          "salaryMinLpa": 6.00,
          "salaryMaxLpa": 9.00,
          "salaryCurrency": "INR",
          "applicationDeadline": "2026-12-31",
          "status": "ACTIVE",
          "skills": [ { "id": 1, "name": "Java" } ],
          "createdAt": "2026-10-14T10:00:00Z"
        }
      ],
      "createdAt": "2026-10-14T09:00:00Z"
    },
    "message": null
  }
  ```
* **Potential Errors:**
  * `404 Not Found` (`RESOURCE_NOT_FOUND`): Nonexistent company or `is_active = FALSE`.

---

## 4. Candidate Profile & Resource Endpoints (Phase 4)

All endpoints under `/api/candidate/**` require an authenticated session with `ROLE_CANDIDATE`. Candidate identity is resolved strictly from token claims via `SecurityUtils.getRequiredCandidateId()`.

### 4.1 Get Profile
* **Method:** `GET`
* **Path:** `/api/candidate/profile`
* **Access:** `ROLE_CANDIDATE`
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": {
      "id": 1,
      "fullName": "Jane Doe",
      "headline": "Full Stack Engineer",
      "location": "Bengaluru, India",
      "summary": "Specialized in distributed systems and Spring Boot."
    },
    "message": null
  }
  ```
* **Potential Errors:** `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `404 RESOURCE_NOT_FOUND`.

### 4.2 Update Profile
* **Method:** `PUT`
* **Path:** `/api/candidate/profile`
* **Access:** `ROLE_CANDIDATE`
* **Request Body:**
  ```json
  {
    "fullName": "Jane Doe",
    "headline": "Lead Systems Architect",
    "location": "Bengaluru, India",
    "summary": "Updated summary text."
  }
  ```
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": {
      "id": 1,
      "fullName": "Jane Doe",
      "headline": "Lead Systems Architect",
      "location": "Bengaluru, India",
      "summary": "Updated summary text."
    },
    "message": "Profile updated successfully"
  }
  ```
* **Potential Errors:** `400 Bad Request` (`VALIDATION_FAILED` on blank `fullName`), `401 UNAUTHENTICATED`, `403 FORBIDDEN`.

### 4.3 Profile Readiness
* **Method:** `GET`
* **Path:** `/api/candidate/profile/readiness`
* **Access:** `ROLE_CANDIDATE`
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": {
      "readyToApply": true,
      "profileComplete": true,
      "hasEducation": true,
      "hasSkills": true,
      "hasResume": true,
      "missingRequirements": []
    },
    "message": null
  }
  ```
* **Deterministic Missing Requirements:** `["PROFILE", "EDUCATION", "SKILLS", "RESUME"]`.

### 4.4 Education CRUD
* `POST /api/candidate/education` (`201 Created`):
  * Request: `{ "institution": "MIT", "degree": "B.S.", "fieldOfStudy": "Computer Science", "startYear": 2018, "endYear": 2022, "grade": "4.0", "sortOrder": 1 }`
  * Validation: startYear/endYear in [1900, 2100], `endYear >= startYear`.
* `PUT /api/candidate/education/{educationId}` (`200 OK`): updates record; returns `404 RESOURCE_NOT_FOUND` on foreign record.
* `DELETE /api/candidate/education/{educationId}` (`204 No Content`): deletes record; returns `404 RESOURCE_NOT_FOUND` on foreign record.

### 4.5 Skills Management
* `GET /api/candidate/skills` (`200 OK`): returns `CandidateSkillDto[]` sorted alphabetically by name.
* `POST /api/candidate/skills` (`201 Created`): `{ "name": "Java" }`. Skill names normalized to lowercase in global `skills` table; duplicate assignments return `409 CONFLICT`.
* `DELETE /api/candidate/skills/{skillId}` (`204 No Content`): removes mapping from `candidate_skills`; foreign deletions return `404 RESOURCE_NOT_FOUND`. Global `skills` definition is preserved.

### 4.6 Projects CRUD
* `POST /api/candidate/projects` (`201 Created`): `{ "title": "Portfolio Site", "description": "Personal project", "projectUrl": "https://example.com", "sortOrder": 1 }`.
* `PUT /api/candidate/projects/{projectId}` (`200 OK`): updates project; cross-candidate returns `404 RESOURCE_NOT_FOUND`.
* `DELETE /api/candidate/projects/{projectId}` (`204 No Content`): deletes project; cross-candidate returns `404 RESOURCE_NOT_FOUND`.

### 4.7 Experience CRUD
* `POST /api/candidate/experience` (`201 Created`): `{ "companyName": "Acme", "roleTitle": "Software Engineer", "startDate": "2022-01-01", "endDate": "2023-12-31", "isCurrent": false, "description": "...", "sortOrder": 1 }`.
  * Date Invariants: `startDate` required; past experience requires `endDate`; `endDate >= startDate`; current experience stores `endDate = null`.
* `PUT /api/candidate/experience/{experienceId}` (`200 OK`): updates record; cross-candidate returns `404 RESOURCE_NOT_FOUND`.
* `DELETE /api/candidate/experience/{experienceId}` (`204 No Content`): deletes record; cross-candidate returns `404 RESOURCE_NOT_FOUND`.

### 4.8 Resume Management
* `POST /api/candidate/resume` (`201 Created`):
  * Headers: `Content-Type: multipart/form-data`
  * Part: `file` (PDF, max 5 MiB)
  * Validates: non-empty, `application/pdf` MIME type, `%PDF-` magic-byte header.
  * Storage: UUID key `<uuid>.pdf` in private directory; replaces previous resume and deletes old physical file.
  * Response: `{ "id": 1, "originalFilename": "resume.pdf", "contentType": "application/pdf", "sizeBytes": 12345, "isCurrent": true, "createdAt": "...", "updatedAt": "..." }`.
* `GET /api/candidate/resume` (`200 OK`): retrieves current resume metadata; returns `404 RESOURCE_NOT_FOUND` if none. Supporting `?download=true` streams the raw PDF binary.
* `GET /api/candidate/resume/download` (`200 OK`): streams the current resume PDF binary (`application/pdf`, `Content-Disposition: inline; filename="<filename>"`). Returns `404 RESOURCE_NOT_FOUND` if no current resume exists.
* `DELETE /api/candidate/resume` (`204 No Content`): marks non-current / deletes DB record and deletes physical file from disk. Subsequent `GET` returns `404`.

---

## 5. Phase 5 — Applications & Matching Endpoints

### 5.1 Deterministic Skill Matching
* **Method:** `GET`
* **Path:** `/api/jobs/{jobId}/match`
* **Access:** `ROLE_CANDIDATE` (authenticated candidate)
* **Description:** Performs a deterministic match between the candidate's skills and the job's required skills. Candidate identity is extracted strictly from the JWT authentication context.
* **Algorithm:**
  * Normalization: `lower(trim(skill))`
  * Deduplication: duplicates across candidate or job skills are counted exactly once.
  * Formula: $\text{percentage} = \text{round}(100 \times \frac{\text{matchedRequired}}{\text{totalRequired}})$
  * Zero required skills $\rightarrow$ $100\%$ match.
  * Zero candidate skills with requirements $\rightarrow$ $0\%$ match.
  * Empty candidate and job skills $\rightarrow$ $100\%$ match.
  * Additional candidate skills are captured in `additionalSkills` and do not inflate the score beyond $100\%$.
  * Informational preview only; a $0\%$ match never blocks application submission.
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": {
      "percentage": 67,
      "matchedSkills": ["Java", "SQL"],
      "missingSkills": ["Spring Boot"],
      "additionalSkills": ["Python"]
    },
    "message": null
  }
  ```
* **Potential Errors:** `401 UNAUTHENTICATED`, `403 FORBIDDEN` (recruiters), `404 JOB_NOT_FOUND`.

### 5.2 Application Submission
* **Method:** `POST`
* **Path:** `/api/jobs/{jobId}/applications`
* **Access:** `ROLE_CANDIDATE`
* **Request Body:**
  ```json
  {
    "coverLetter": "Optional cover letter up to 5000 characters."
  }
  ```
* **Business Invariants:**
  1. Job must exist, not be soft-deleted, have status `ACTIVE`, and have application deadline on or after the current UTC date.
  2. Candidate must satisfy profile readiness: completed profile, at least one education record, at least one skill, and an active resume.
  3. Candidate can submit at most one application per job across the system lifetime (`uk_application_candidate_job`). Duplicate applications return `409 DUPLICATE_APPLICATION`.
  4. Submission atomically captures immutable `candidate_snapshot` and `resume_snapshot`.
  5. Initial application status is `APPLIED`.
  6. Atomically creates initial append-only status history row with `from_status = null` and `to_status = APPLIED`.
* **Success Response (`201 Created`):**
  ```json
  {
    "success": true,
    "data": {
      "applicationId": 1,
      "jobId": 3,
      "candidateId": 7,
      "status": "APPLIED",
      "coverLetter": "Optional cover letter up to 5000 characters.",
      "appliedAt": "2026-10-14T21:35:49.390Z"
    },
    "message": null
  }
  ```
* **Potential Errors:** `400 VALIDATION_FAILED`, `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `404 JOB_NOT_FOUND`, `409 DUPLICATE_APPLICATION`, `422 CANDIDATE_PROFILE_INCOMPLETE`, `422 CANDIDATE_RESUME_REQUIRED`, `422 JOB_NOT_ACTIVE`, `422 JOB_DEADLINE_PASSED`.

### 5.3 Candidate Application List
* **Method:** `GET`
* **Path:** `/api/candidate/applications`
* **Access:** `ROLE_CANDIDATE`
* **Query Parameters:** `page` (default 0), `size` (default 20, max 100).
* **Ordering:** Deterministic `applied_at DESC, id DESC`.
* **Description:** Returns a paginated collection of the authenticated candidate's applications. Cross-candidate applications are strictly filtered out by SQL WHERE clause.
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": [
      {
        "applicationId": 1,
        "jobId": 3,
        "candidateId": 7,
        "status": "APPLIED",
        "coverLetter": "...",
        "appliedAt": "2026-10-14T21:35:49.390Z",
        "jobTitle": "SQL Data Analyst",
        "companyName": "TechNova Solutions",
        "candidateSnapshot": { ... },
        "resumeSnapshot": { ... }
      }
    ],
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1
    },
    "message": null
  }
  ```
* **Potential Errors:** `401 UNAUTHENTICATED`, `403 FORBIDDEN`.

### 5.4 Candidate Application Detail
* **Method:** `GET`
* **Path:** `/api/candidate/applications/{applicationId}`
* **Access:** `ROLE_CANDIDATE`
* **Description:** Retrieves complete application details including frozen snapshots, job title, company name, and full chronological status history.
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": {
      "applicationId": 1,
      "jobId": 3,
      "candidateId": 7,
      "status": "APPLIED",
      "coverLetter": "...",
      "appliedAt": "2026-10-14T21:35:49.390Z",
      "jobTitle": "SQL Data Analyst",
      "companyName": "TechNova Solutions",
      "candidateSnapshot": {
        "candidateId": 7,
        "fullName": "Alice Johnson",
        "email": "alice@example.com",
        "headline": "Full Stack Engineer",
        "location": "Bengaluru, India",
        "summary": "Experienced full stack software engineer.",
        "education": [ ... ],
        "skills": ["Java"],
        "projects": [ ... ],
        "experience": [ ... ]
      },
      "resumeSnapshot": {
        "id": 3,
        "originalFilename": "alice_resume.pdf",
        "storageKey": "2d3ccc64-a713-4834-a5dc-702396e4be02.pdf",
        "contentType": "application/pdf",
        "sizeBytes": 50
      },
      "statusHistory": [
        {
          "id": 1,
          "applicationId": 1,
          "toStatus": "APPLIED",
          "changedByUserId": 9,
          "createdAt": "2026-10-14T21:35:49.390Z"
        }
      ]
    },
    "message": null
  }
  ```
* **Potential Errors:** `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `404 RESOURCE_NOT_FOUND` (if nonexistent or owned by another candidate).

### 5.5 Candidate Application Withdrawal
* **Method:** `PATCH`
* **Path:** `/api/candidate/applications/{applicationId}/withdraw`
* **Access:** `ROLE_CANDIDATE`
* **Description:** Transitions an application to `WITHDRAWN` status. Allowed only from non-terminal states (`APPLIED`, `UNDER_REVIEW`, `SHORTLISTED`, `INTERVIEW`).
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": {
      "applicationId": 1,
      "jobId": 3,
      "candidateId": 7,
      "status": "WITHDRAWN",
      "coverLetter": "...",
      "appliedAt": "2026-10-14T21:35:49.390Z"
    },
    "message": null
  }
  ```
* **Potential Errors:** `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `404 RESOURCE_NOT_FOUND` (if foreign or nonexistent), `422 INVALID_STATUS_TRANSITION` (if already withdrawn or terminal).

---

## 6. Recruiter Company Management API

All endpoints under `/api/recruiter/**` require an authenticated recruiter (`ROLE_RECRUITER`).

### 6.1 Get Recruiter Company
* **Method:** `GET`
* **Path:** `/api/recruiter/company`
* **Access:** `ROLE_RECRUITER`
* **Description:** Retrieves the authenticated recruiter's current associated company. Ownership is resolved server-side from `recruiters.company_id`.
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": {
      "id": 1,
      "name": "Acme Innovations",
      "logo": null,
      "description": "Leading cloud and enterprise solutions",
      "industry": "Information Technology",
      "website": "https://acme.example.com",
      "location": "Bengaluru",
      "companySize": "501-1000",
      "isActive": true,
      "createdAt": "2026-10-14T22:00:00.000Z",
      "updatedAt": "2026-10-14T22:00:00.000Z"
    },
    "message": null
  }
  ```
* **Potential Errors:**
  * `401 UNAUTHENTICATED`: Missing or invalid token.
  * `403 FORBIDDEN`: Non-recruiter role.
  * `404 RESOURCE_NOT_FOUND`: Recruiter has no associated company.

### 6.2 Create or Update Recruiter Company (Upsert)
* **Method:** `PUT`
* **Path:** `/api/recruiter/company`
* **Access:** `ROLE_RECRUITER`
* **Description:** If recruiter has no associated company (`company_id IS NULL`), creates a new company and atomically associates the recruiter. If recruiter already has an associated company, updates that company.
* **Request Body:**
  ```json
  {
    "name": "Acme Innovations",
    "description": "Leading cloud and enterprise solutions",
    "industry": "Information Technology",
    "website": "https://acme.example.com",
    "location": "Bengaluru",
    "companySize": "501-1000"
  }
  ```
* **Validation Rules:**
  * `name`: Required, non-blank, max 150 chars.
  * `industry`: Max 100 chars.
  * `location`: Max 150 chars.
  * `website`: Max 255 chars.
  * `description`: Max 5000 chars.
  * `companySize`: Max 50 chars.
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": {
      "id": 1,
      "name": "Acme Innovations",
      "logo": null,
      "description": "Leading cloud and enterprise solutions",
      "industry": "Information Technology",
      "website": "https://acme.example.com",
      "location": "Bengaluru",
      "companySize": "501-1000",
      "isActive": true,
      "createdAt": "2026-10-14T22:00:00.000Z",
      "updatedAt": "2026-10-14T22:00:00.000Z"
    },
    "message": null
  }
  ```
* **Potential Errors:**
  * `400 VALIDATION_FAILED`: Blank name or exceeded field lengths.
  * `401 UNAUTHENTICATED`: Missing or invalid token.
  * `403 FORBIDDEN`: Non-recruiter role.
  * `409 COMPANY_ALREADY_EXISTS`: Company name conflicts with an existing company.

---

## 7. Recruiter Job Lifecycle API (Phase 6.4)

All lifecycle endpoints under `/api/recruiter/jobs/**` require an authenticated recruiter (`ROLE_RECRUITER`) and server-side verification that the target job belongs to the recruiter's active company.

### 7.1 Publish Job
* **Method:** `PATCH`
* **Path:** `/api/recruiter/jobs/{jobId}/publish`
* **Access:** `ROLE_RECRUITER`
* **Description:** Transitions a recruiter-owned job from `DRAFT` to `ACTIVE`. Enforces complete job specification, non-past application deadline, and at least one required skill.
* **Request Headers:**
  * `Authorization: Bearer <jwt-token>`
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": {
      "id": 1,
      "title": "Software Engineer Intern",
      "description": "Full stack development role...",
      "company": {
        "id": 10,
        "name": "Acme Innovations",
        "industry": "Information Technology",
        "location": "Bengaluru",
        "website": "https://acme.example.com",
        "companySize": "501-1000",
        "activeJobCount": 1
      },
      "jobType": "INTERNSHIP",
      "experienceLevel": "FRESHER",
      "workMode": "HYBRID",
      "location": "Bengaluru",
      "salaryMinLpa": 6.00,
      "salaryMaxLpa": 12.00,
      "salaryCurrency": "INR",
      "applicationDeadline": "2026-12-31",
      "status": "ACTIVE",
      "skills": [
        { "id": 1, "name": "Java" },
        { "id": 2, "name": "Spring Boot" }
      ],
      "createdAt": "2026-10-16T04:00:00Z",
      "updatedAt": "2026-10-16T04:30:00Z"
    },
    "message": null
  }
  ```
* **Potential Errors:**
  * `401 UNAUTHENTICATED`: Missing or invalid token.
  * `403 FORBIDDEN`: Non-recruiter role.
  * `404 JOB_NOT_FOUND`: Job ID does not exist or does not belong to the recruiter's company.
  * `422 COMPANY_REQUIRED`: Recruiter has no associated company.
  * `422 INVALID_STATUS_TRANSITION`: Job status is not `DRAFT` (e.g. already `ACTIVE`, `CLOSED`, or `ARCHIVED`).
  * `422 JOB_DEADLINE_PASSED`: Application deadline is before current date.
  * `422 JOB_SKILL_REQUIRED`: Job has no required skills associated.
  * `422 VALIDATION_FAILED`: Missing title, description, or location for non-remote job.

---

### 7.2 Close Job
* **Method:** `PATCH`
* **Path:** `/api/recruiter/jobs/{jobId}/close`
* **Access:** `ROLE_RECRUITER`
* **Description:** Transitions a recruiter-owned job from `ACTIVE` to `CLOSED`.
* **Request Headers:**
  * `Authorization: Bearer <jwt-token>`
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": {
      "id": 1,
      "title": "Software Engineer Intern",
      "status": "CLOSED",
      ...
    },
    "message": null
  }
  ```
* **Potential Errors:**
  * `401 UNAUTHENTICATED`: Missing or invalid token.
  * `403 FORBIDDEN`: Non-recruiter role.
  * `404 JOB_NOT_FOUND`: Job ID does not exist or does not belong to the recruiter's company.
  * `422 COMPANY_REQUIRED`: Recruiter has no associated company.
  * `422 INVALID_STATUS_TRANSITION`: Job status is not `ACTIVE` (e.g. `DRAFT`, already `CLOSED`, or `ARCHIVED`).

---

## 8. Recruiter Job Query API (Phase 6.5)

### 8.1 List Recruiter Jobs
* **Method:** `GET`
* **Path:** `/api/recruiter/jobs`
* **Access:** `ROLE_RECRUITER`
* **Description:** Retrieves a paginated list of all jobs belonging to the authenticated recruiter's associated company across all lifecycle states (`DRAFT`, `ACTIVE`, `CLOSED`, `ARCHIVED`). Ordered deterministically by `created_at DESC, id DESC`.
* **Query Parameters:**
  * `page` (integer, optional, default: 0): Zero-based page index.
  * `size` (integer, optional, default: 20, max: 100): Page size.
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": [
      {
        "id": 1,
        "title": "Software Engineer Intern",
        "company": {
          "id": 10,
          "name": "Acme Innovations",
          "industry": "Information Technology",
          "location": "Bengaluru",
          "website": "https://acme.example.com",
          "companySize": "501-1000",
          "activeJobCount": 1
        },
        "jobType": "FULL_TIME",
        "experienceLevel": "FRESHER",
        "workMode": "HYBRID",
        "location": "Bengaluru",
        "salaryMinLpa": 6.00,
        "salaryMaxLpa": 12.00,
        "salaryCurrency": "INR",
        "applicationDeadline": "2026-12-31",
        "status": "DRAFT",
        "skills": [
          { "id": 1, "name": "Java" }
        ],
        "createdAt": "2026-10-16T04:00:00Z"
      }
    ],
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1
    },
    "message": null
  }
  ```
* **Potential Errors:**
  * `401 UNAUTHENTICATED`: Missing or invalid token.
  * `403 FORBIDDEN`: Non-recruiter role.
  * `422 COMPANY_REQUIRED`: Recruiter has no associated company.

---

### 8.2 Get Recruiter Job Detail
* **Method:** `GET`
* **Path:** `/api/recruiter/jobs/{jobId}`
* **Access:** `ROLE_RECRUITER`
* **Description:** Retrieves the complete specifications and skill requirements of a specific job belonging to the authenticated recruiter's associated company.
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": {
      "id": 1,
      "title": "Software Engineer Intern",
      "description": "Full stack development role...",
      "responsibilities": "Design microservices...",
      "requirements": "Java, Spring Boot...",
      "company": {
        "id": 10,
        "name": "Acme Innovations",
        "industry": "Information Technology",
        "location": "Bengaluru",
        "website": "https://acme.example.com",
        "companySize": "501-1000",
        "activeJobCount": 1
      },
      "jobType": "FULL_TIME",
      "experienceLevel": "FRESHER",
      "workMode": "HYBRID",
      "location": "Bengaluru",
      "salaryMinLpa": 6.00,
      "salaryMaxLpa": 12.00,
      "salaryCurrency": "INR",
      "applicationDeadline": "2026-12-31",
      "status": "DRAFT",
      "skills": [
        { "id": 1, "name": "Java" }
      ],
      "createdAt": "2026-10-16T04:00:00Z",
      "updatedAt": "2026-10-16T04:30:00Z"
    },
    "message": null
  }
  ```
* **Potential Errors:**
  * `401 UNAUTHENTICATED`: Missing or invalid token.
  * `403 FORBIDDEN`: Non-recruiter role.
  * `404 JOB_NOT_FOUND`: Job does not exist or does not belong to recruiter's company.
  * `422 COMPANY_REQUIRED`: Recruiter has no associated company.

---

## 9. Recruiter Job Mutation API (Phase 6.3 & 6.6)

### 9.1 Create Job
* **Method:** `POST`
* **Path:** `/api/recruiter/jobs`
* **Access:** `ROLE_RECRUITER`
* **Description:** Creates a new job in `DRAFT` status belonging strictly to the authenticated recruiter's active company. Server-authoritative company assignment and skill association.
* **Request Headers:**
  * `Authorization: Bearer <jwt-token>`
* **Request Body (`RecruiterJobCreateRequest`):**
  ```json
  {
    "title": "Software Engineer Intern",
    "description": "Comprehensive role description...",
    "responsibilities": "Develop web services and microservices...",
    "requirements": "Proficiency in Java and relational databases",
    "jobType": "INTERNSHIP",
    "experienceLevel": "FRESHER",
    "workMode": "HYBRID",
    "location": "Bengaluru",
    "salaryMinLpa": 6.00,
    "salaryMaxLpa": 12.00,
    "salaryCurrency": "INR",
    "applicationDeadline": "2026-12-31",
    "requiredSkillIds": [1, 2]
  }
  ```
* **Validation Rules:**
  * `title`: Required, non-blank, max 200 characters.
  * `description`: Required, non-blank, max 10000 characters.
  * `jobType`: Required, valid `JobType` enum (`INTERNSHIP`, `FULL_TIME`, `PART_TIME`, `APPRENTICESHIP`, `TRAINEE`).
  * `experienceLevel`: Required, valid `ExperienceLevel` enum (`INTERNSHIP`, `FRESHER`, `ZERO_TO_ONE_YEARS`, `ONE_TO_TWO_YEARS`).
  * `workMode`: Required, valid `WorkMode` enum (`ONSITE`, `HYBRID`, `REMOTE`).
  * `location`: Required for non-`REMOTE` jobs; max 150 characters.
  * `applicationDeadline`: Required, cannot be in the past (`@FutureOrPresent`).
  * `salaryMinLpa`: Must be greater than 0 if provided.
  * `salaryMaxLpa`: Must be greater than or equal to `salaryMinLpa` if provided.
  * `salaryCurrency`: Defaults to `"INR"`, max 10 characters.
  * `requiredSkillIds`: At least one valid skill ID required; all supplied skill IDs must exist in the database.
* **Security & Ownership Rules:**
  * Initial status is strictly server-set to `DRAFT`.
  * Recruiter identity resolved server-side from `SecurityUtils.getRequiredRecruiterId()`.
  * Company identity resolved authoritative from database `recruiters.company_id`.
  * Client cannot manipulate ownership, status, or timestamps via payload (`companyId`, `recruiterId`, `id`, `status`, `createdAt`, `deletedAt` are ignored).
* **Success Response (`201 Created`):**
  ```json
  {
    "success": true,
    "data": {
      "id": 1,
      "title": "Software Engineer Intern",
      "description": "Comprehensive role description...",
      "company": {
        "id": 10,
        "name": "Acme Innovations",
        "industry": "Information Technology",
        "location": "Bengaluru",
        "website": "https://acme.example.com",
        "companySize": "501-1000",
        "activeJobCount": 0
      },
      "jobType": "INTERNSHIP",
      "experienceLevel": "FRESHER",
      "workMode": "HYBRID",
      "location": "Bengaluru",
      "salaryMinLpa": 6.00,
      "salaryMaxLpa": 12.00,
      "salaryCurrency": "INR",
      "applicationDeadline": "2026-12-31",
      "status": "DRAFT",
      "skills": [
        { "id": 1, "name": "Java" },
        { "id": 2, "name": "Spring Boot" }
      ],
      "createdAt": "2026-10-16T05:30:00Z",
      "updatedAt": "2026-10-16T05:30:00Z"
    },
    "message": null
  }
  ```
* **Potential Errors:**
  * `400 VALIDATION_FAILED`: Blank title/description, missing enum, negative salary, or past deadline.
  * `401 UNAUTHENTICATED`: Missing or invalid token.
  * `403 FORBIDDEN`: Non-recruiter role.
  * `422 COMPANY_REQUIRED`: Recruiter has no associated company.
  * `422 JOB_DEADLINE_PASSED`: Application deadline is before current date.
  * `422 JOB_SKILL_REQUIRED`: Missing required skills.
  * `422 VALIDATION_FAILED`: Location missing for non-remote job, non-existent skill ID, or max salary < min salary.

---

### 9.2 Update Job
* **Method:** `PUT`
* **Path:** `/api/recruiter/jobs/{jobId}`
* **Access:** `ROLE_RECRUITER`
* **Description:** Updates the fields and skill requirements of an existing recruiter-owned job. Status transitions are strictly segregated and cannot be performed via this generic edit endpoint.
* **Request Headers:**
  * `Authorization: Bearer <jwt-token>`
* **Request Body:**
  ```json
  {
    "title": "Senior Backend Engineer",
    "description": "Comprehensive job description...",
    "responsibilities": "Lead system architecture...",
    "requirements": "Strong Java and distributed systems experience",
    "jobType": "FULL_TIME",
    "experienceLevel": "ONE_TO_TWO_YEARS",
    "workMode": "HYBRID",
    "location": "Bengaluru",
    "salaryMinLpa": 12.00,
    "salaryMaxLpa": 24.00,
    "salaryCurrency": "INR",
    "applicationDeadline": "2026-12-31",
    "requiredSkillIds": [1, 2, 5]
  }
  ```
* **Validation Rules:**
  * `title`: Required, non-blank, max 200 characters.
  * `description`: Required, non-blank, max 10000 characters.
  * `jobType`: Required, valid `JobType` enum (`INTERNSHIP`, `FULL_TIME`, `PART_TIME`, `APPRENTICESHIP`, `TRAINEE`).
  * `experienceLevel`: Required, valid `ExperienceLevel` enum (`INTERNSHIP`, `FRESHER`, `ZERO_TO_ONE_YEARS`, `ONE_TO_TWO_YEARS`).
  * `workMode`: Required, valid `WorkMode` enum (`ONSITE`, `HYBRID`, `REMOTE`).
  * `location`: Required for non-`REMOTE` jobs; max 150 characters.
  * `applicationDeadline`: Required, cannot be in the past (`@FutureOrPresent`).
  * `salaryMinLpa`: Must be greater than 0 if provided.
  * `salaryMaxLpa`: Must be greater than or equal to `salaryMinLpa` if provided.
  * `salaryCurrency`: Defaults to `"INR"`, max 10 characters.
  * `requiredSkillIds`: At least one valid skill ID required; all supplied skill IDs must exist in the database.
* **Security & Ownership Rules:**
  * Recruiter identity resolved server-side from `SecurityUtils.getRequiredRecruiterId()`.
  * Company identity resolved authoritative from database `recruiters.company_id`.
  * Client cannot manipulate ownership via payload or query parameters.
  * Cross-company access returns generic `404 JOB_NOT_FOUND` to prevent metadata leakage.
* **Lifecycle & Status Rules:**
  * `DRAFT` and `ACTIVE` jobs may be edited.
  * `CLOSED` jobs cannot be edited (`422 INVALID_STATUS_TRANSITION`).
  * `ARCHIVED` jobs are admin-owned and cannot be edited by recruiters (`422 INVALID_STATUS_TRANSITION`).
  * Generic edit endpoint does not change `status`, `company_id`, or `created_at`.
* **Application Invariant (Fundamental Requirements):**
  * If a job has $\ge 1$ application, fundamental requirements (`title`, `jobType`, `experienceLevel`, `workMode`, `salaryMinLpa`, `salaryMaxLpa`, `salaryCurrency`, `requirements`, and `requiredSkillIds`) are locked and cannot be changed.
  * Attempting to modify fundamental requirements on a job with applications returns `422 INVALID_STATUS_TRANSITION`.
  * Permitted non-fundamental operational fields (`description`, `responsibilities`, `location` for remote/hybrid updates, and `applicationDeadline`) remain editable.
  * Transaction rollback ensures no partial updates if validation or skill update fails.
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "data": {
      "id": 1,
      "title": "Senior Backend Engineer",
      "description": "Comprehensive job description...",
      "responsibilities": "Lead system architecture...",
      "requirements": "Strong Java and distributed systems experience",
      "company": {
        "id": 10,
        "name": "Acme Innovations",
        "industry": "Information Technology",
        "location": "Bengaluru",
        "website": "https://acme.example.com",
        "companySize": "501-1000",
        "activeJobCount": 1
      },
      "jobType": "FULL_TIME",
      "experienceLevel": "ONE_TO_TWO_YEARS",
      "workMode": "HYBRID",
      "location": "Bengaluru",
      "salaryMinLpa": 12.00,
      "salaryMaxLpa": 24.00,
      "salaryCurrency": "INR",
      "applicationDeadline": "2026-12-31",
      "status": "DRAFT",
      "skills": [
        { "id": 1, "name": "Java" },
        { "id": 2, "name": "Spring Boot" },
        { "id": 5, "name": "PostgreSQL" }
      ],
      "createdAt": "2026-10-16T04:00:00Z",
      "updatedAt": "2026-10-16T05:00:00Z"
    },
    "message": null
  }
  ```
* **Potential Errors:**
  * `400 VALIDATION_FAILED`: Malformed payload, missing title/description/deadline, or invalid enum.
  * `401 UNAUTHENTICATED`: Missing or invalid token.
  * `403 FORBIDDEN`: Non-recruiter role.
  * `404 JOB_NOT_FOUND`: Job does not exist or does not belong to recruiter's company.
  * `422 COMPANY_REQUIRED`: Recruiter has no associated company.
  * `422 VALIDATION_FAILED`: Non-remote job without location, salary max below min, non-existent skill ID.
  * `422 JOB_DEADLINE_PASSED`: Application deadline in the past.
  * `422 JOB_SKILL_REQUIRED`: Missing required skill IDs.
  * `422 INVALID_STATUS_TRANSITION`: Closed or archived job modification, or fundamental requirements changed after applications exist.

---

### 9.3 Delete Job
* **Method:** `DELETE`
* **Path:** `/api/recruiter/jobs/{jobId}`
* **Access:** `ROLE_RECRUITER`
* **Description:** Soft-deletes a recruiter-owned job by setting `deleted_at = CURRENT_TIMESTAMP(3)`. Soft-deleted jobs immediately cease to appear in public searches, recruiter listings, and recruiter detail endpoints.
* **Request Headers:**
  * `Authorization: Bearer <jwt-token>`
* **Security & Invariants:**
  * Recruiter ownership verified against authoritative `company_id`.
  * Cross-company deletion returns `404 JOB_NOT_FOUND`.
  * **Critical Rule:** Jobs with $\ge 1$ application CANNOT be deleted (returns `422 INVALID_STATUS_TRANSITION`). Recruiter must close the job instead. Candidate application history and immutable snapshots remain fully intact.
  * Active jobs cannot be deleted directly; they must first be closed (`422 INVALID_STATUS_TRANSITION`).
  * Archived jobs cannot be deleted by recruiters (`422 INVALID_STATUS_TRANSITION`).
  * Zero-application `DRAFT` and `CLOSED` jobs can be safely soft-deleted.
* **Success Response (`204 No Content`):** Empty body.
* **Potential Errors:**
  * `401 UNAUTHENTICATED`: Missing or invalid token.
  * `403 FORBIDDEN`: Non-recruiter role.
  * `404 JOB_NOT_FOUND`: Job does not exist or does not belong to recruiter's company.
  * `422 COMPANY_REQUIRED`: Recruiter has no associated company.
  * `422 INVALID_STATUS_TRANSITION`: Job has applications, job is currently active, or job is archived.

---

## 10. Recruiter Application Management (Phase 7.1)

All applicant query endpoints under `/api/recruiter/**` require an authenticated recruiter (`ROLE_RECRUITER`) and server-side verification that the target job or application belongs to the recruiter's active company.

---

### 10.1 List Applicants for Job
* **Method:** `GET`
* **Path:** `/api/recruiter/jobs/{jobId}/applications`
* **Access:** `ROLE_RECRUITER`
* **Description:** Retrieves a paginated list of applicants for a specific job belonging to the authenticated recruiter's company. Each applicant summary contains applicant metadata, point-in-time snapshot summary data (name, email, headline), application status, timestamp, and deterministically calculated match percentage.
* **Query Parameters:**
  * `page` (optional, default `0`): Zero-based page index.
  * `size` (optional, default `20`, max `100`): Page size.
  * `status` (optional): Filter by application status (`APPLIED`, `UNDER_REVIEW`, `SHORTLISTED`, `INTERVIEW`, `SELECTED`, `REJECTED`, `WITHDRAWN`).
* **Request Headers:**
  * `Authorization: Bearer <jwt-token>`
* **Security & Invariants:**
  * Recruiter identity resolved server-side from security context.
  * Recruiter's active company verified server-side from MySQL (`recruiters.company_id`).
  * Job ownership verified server-side (`jobs.company_id = recruiter.company_id` and `jobs.deleted_at IS NULL`).
  * Cross-company job access returns generic `404 JOB_NOT_FOUND` with zero metadata leakage.
  * Ordering is deterministic: `applied_at DESC, id DESC`.
* **Success Response (`200 OK`):**
```json
{
  "success": true,
  "data": [
    {
      "applicationId": 101,
      "jobId": 12,
      "candidateId": 45,
      "candidateName": "Jane Doe",
      "candidateEmail": "jane.doe@example.com",
      "candidateHeadline": "Backend Java Engineer",
      "status": "APPLIED",
      "appliedAt": "2026-10-18T10:00:00Z",
      "matchPercentage": 75
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```
* **Potential Errors:**
  * `401 UNAUTHENTICATED`: Missing or invalid token.
  * `403 FORBIDDEN`: Non-recruiter role (`CANDIDATE`, `ADMIN`).
  * `404 JOB_NOT_FOUND`: Job does not exist, is soft-deleted, or belongs to another company.
  * `422 COMPANY_REQUIRED`: Recruiter does not belong to an active company.

---

### 10.2 Get Applicant Detail
* **Method:** `GET`
* **Path:** `/api/recruiter/applications/{applicationId}`
* **Access:** `ROLE_RECRUITER`
* **Description:** Retrieves the complete detail of an applicant for review by a recruiter belonging to the hiring company. Includes point-in-time immutable candidate snapshot, immutable resume snapshot metadata, current application status, full chronological status audit history, and deterministic skill match breakdown. Does not expose physical resume binary downloads.
* **Request Headers:**
  * `Authorization: Bearer <jwt-token>`
* **Security & Invariants:**
  * Recruiter identity resolved server-side from security context.
  * Recruiter's active company resolved server-side from database.
  * Application must belong to a job owned by the recruiter's company (`jobs.deleted_at IS NULL`).
  * Cross-company application access returns generic `404 RESOURCE_NOT_FOUND` ("Application with ID {id} not found or not owned by your company").
  * Does not expose live candidate profile data; strictly renders the historic immutable snapshot taken at submission.
* **Success Response (`200 OK`):**
```json
{
  "success": true,
  "data": {
    "applicationId": 101,
    "jobId": 12,
    "jobTitle": "Senior Backend Developer",
    "candidateId": 45,
    "status": "APPLIED",
    "coverLetter": "Passionate about building scalable distributed systems.",
    "appliedAt": "2026-10-18T10:00:00Z",
    "candidateSnapshot": {
      "candidateId": 45,
      "fullName": "Jane Doe",
      "email": "jane.doe@example.com",
      "headline": "Backend Java Engineer",
      "location": "Bengaluru",
      "summary": "Experienced engineer with 4+ years of Java/Spring expertise.",
      "education": [
        {
          "id": 1,
          "institution": "National Institute of Technology",
          "degree": "B.Tech",
          "fieldOfStudy": "Computer Science",
          "startYear": 2018,
          "endYear": 2022,
          "sortOrder": 0
        }
      ],
      "skills": ["Java", "Spring Boot", "MySQL", "Docker", "Git"],
      "projects": [],
      "experience": []
    },
    "resumeSnapshot": {
      "id": 25,
      "originalFilename": "jane_doe_resume.pdf",
      "storageKey": "7f8b9c...-resume.pdf",
      "contentType": "application/pdf",
      "sizeBytes": 1048576
    },
    "statusHistory": [
      {
        "id": 1,
        "applicationId": 101,
        "toStatus": "APPLIED",
        "changedByUserId": 50,
        "createdAt": "2026-10-18T10:00:00Z"
      }
    ],
    "match": {
      "matchedSkills": ["Java", "Spring Boot", "MySQL"],
      "missingSkills": ["AWS"],
      "additionalSkills": ["Docker", "Git"],
      "percentage": 75
    }
  }
}
```
* **Potential Errors:**
  * `401 UNAUTHENTICATED`: Missing or invalid token.
  * `403 FORBIDDEN`: Non-recruiter role (`CANDIDATE`, `ADMIN`).
  * `404 RESOURCE_NOT_FOUND`: Application does not exist, belongs to another company, or job is soft-deleted.
  * `422 COMPANY_REQUIRED`: Recruiter does not belong to an active company.

---

### 10.3 Update Application Status
* **Method:** `PATCH`
* **Path:** `/api/recruiter/applications/{applicationId}/status`
* **Access:** `ROLE_RECRUITER`
* **Description:** Updates the status of an application for a job owned by the authenticated recruiter's active company. Appends an immutable audit record to `application_status_history` in the same atomic transaction. Strictly enforces the specification-defined state machine.
* **Request Headers:**
  * `Authorization: Bearer <jwt-token>`
  * `Content-Type: application/json`
* **Request Body:**
```json
{
  "status": "SHORTLISTED",
  "note": "Candidate demonstrated strong backend design during preliminary screening."
}
```
* **Validation & Security Invariants:**
  * `status`: Required. Must be a valid `ApplicationStatus` enum name (`UNDER_REVIEW`, `SHORTLISTED`, `INTERVIEW`, `SELECTED`, `REJECTED`). Recruiter cannot set `WITHDRAWN` (candidate-only).
  * `note`: Optional string, maximum 500 characters.
  * Identity resolved server-side (`SecurityUtils.getRequiredRecruiterId()`, `SecurityUtils.getRequiredCurrentUserId()`). Client cannot inject `recruiterId`, `companyId`, `candidateId`, or `changedByUserId`.
  * Recruiter active company dynamically resolved from database; company in token claim is never trusted over DB state.
  * Strict company ownership: Job must belong to recruiter's active company with `jobs.deleted_at IS NULL`. Cross-company access returns generic `404 RESOURCE_NOT_FOUND` with zero metadata leakage.
  * State Machine Transitions:
    * `APPLIED -> UNDER_REVIEW, SHORTLISTED, REJECTED, WITHDRAWN` (WITHDRAWN is candidate-only)
    * `UNDER_REVIEW -> SHORTLISTED, REJECTED, WITHDRAWN`
    * `SHORTLISTED -> INTERVIEW, REJECTED, WITHDRAWN`
    * `INTERVIEW -> SELECTED, REJECTED, WITHDRAWN`
    * Terminal states: `SELECTED`, `REJECTED`, `WITHDRAWN` (no outgoing transitions).
    * Rejects same-state, backward, invalid skips, and terminal modifications with `422 INVALID_STATUS_TRANSITION`.
  * Transactional Atomicity: Status update on `applications` and append-only insert to `application_status_history` succeed or roll back together.
* **Success Response (`200 OK`):**
```json
{
  "success": true,
  "data": {
    "applicationId": 101,
    "jobId": 12,
    "jobTitle": "Senior Backend Developer",
    "candidateId": 45,
    "status": "SHORTLISTED",
    "coverLetter": "Passionate about building scalable distributed systems.",
    "appliedAt": "2026-10-18T10:00:00Z",
    "candidateSnapshot": { ... },
    "resumeSnapshot": { ... },
    "statusHistory": [
      {
        "id": 1,
        "applicationId": 101,
        "fromStatus": null,
        "toStatus": "APPLIED",
        "changedByUserId": 50,
        "note": null,
        "createdAt": "2026-10-18T10:00:00Z"
      },
      {
        "id": 2,
        "applicationId": 101,
        "fromStatus": "APPLIED",
        "toStatus": "SHORTLISTED",
        "changedByUserId": 12,
        "note": "Candidate demonstrated strong backend design during preliminary screening.",
        "createdAt": "2026-10-18T11:30:00Z"
      }
    ],
    "match": { ... }
  }
}
```
* **Potential Errors:**
  * `400 VALIDATION_FAILED`: Target status missing/null or note exceeds 500 characters.
  * `401 UNAUTHENTICATED`: Missing or invalid token.
  * `403 FORBIDDEN`: Non-recruiter role (`CANDIDATE`, `ADMIN`).
  * `404 RESOURCE_NOT_FOUND`: Application does not exist, belongs to another company, or job is soft-deleted.
  * `422 COMPANY_REQUIRED`: Recruiter does not belong to an active company.
  * `422 INVALID_STATUS_TRANSITION`: Attempted status transition is not permitted by the state machine (same-state, backward, terminal state, skip, or recruiter attempting WITHDRAWN).

---

### 10.4 Protected Resume Access (Phase 7.4)
* **Method:** `GET`
* **Path:** `/api/recruiter/applications/{applicationId}/resume` (or `/api/recruiter/applications/{applicationId}/resume/download`)
* **Access:** `ROLE_RECRUITER`
* **Description:** Provides protected access to the applicant's resume for an application submitted to a job owned by the authenticated recruiter's active company. By default (or with `Accept: application/pdf`), streams the raw PDF binary. When requested with `?metadata=true` or `Accept: application/json`, returns resume metadata.
* **Request Headers:**
  * `Authorization: Bearer <jwt-token>`
  * `Accept: application/pdf` (or `*/*` for file streaming; `application/json` for metadata)
* **Query Parameters:**
  * `metadata` (optional boolean, default `false`): When `true`, returns JSON metadata instead of binary file stream.
* **Security & Invariants:**
  * Recruiter identity resolved server-side from security context (`SecurityUtils.getRequiredRecruiterId()`).
  * Recruiter's current company resolved directly from MySQL (`recruiters.company_id`); stale JWT claims are ignored.
  * Application must exist and belong to a non-deleted job (`deleted_at IS NULL`) owned by the recruiter's company.
  * Cross-company access attempts return generic `404 RESOURCE_NOT_FOUND` with zero resume metadata leakage.
  * Candidate identity resolved from the application; accesses the candidate's current active resume. Client-supplied IDs (`candidateId`, `userId`, `companyId`, `recruiterId`, `resumeId`) cannot bypass authorization.
  * Physical file access is verified for path traversal and existence in private storage. Internal storage paths are never leaked.
* **Success Response (File Stream, `200 OK`):**
  * Headers: `Content-Type: application/pdf`, `Content-Disposition: inline; filename="<original_filename>"`, `Content-Length: <size>`
  * Body: Binary PDF byte stream
* **Success Response (Metadata, `200 OK` with `?metadata=true`):**
```json
{
  "success": true,
  "data": {
    "id": 25,
    "originalFilename": "jane_doe_resume.pdf",
    "contentType": "application/pdf",
    "sizeBytes": 1048576,
    "isCurrent": true,
    "createdAt": "2026-10-18T10:00:00Z",
    "updatedAt": "2026-10-18T10:00:00Z"
  },
  "message": "Resume metadata retrieved successfully"
}
```
* **Potential Errors:**
  * `401 UNAUTHENTICATED`: Missing or invalid token.
  * `403 FORBIDDEN`: Non-recruiter role (`CANDIDATE`, `ADMIN`).
  * `404 RESOURCE_NOT_FOUND`: Application not found, belongs to another company, job soft-deleted, candidate has no active resume, or physical file missing.
  * `422 COMPANY_REQUIRED`: Recruiter does not belong to an active company.

---

## 11. In-App Notifications API (Phase 7.3)

### 11.1 List Notifications
* **Method:** `GET`
* **Path:** `/api/notifications`
* **Access:** Any authenticated user (`CANDIDATE`, `RECRUITER`, `ADMIN`)
* **Description:** Retrieves a paginated list of notifications belonging strictly to the authenticated user, along with total unread count.
* **Request Headers:**
  * `Authorization: Bearer <jwt-token>`
* **Query Parameters:**
  * `page` (optional integer, default `0`): Zero-based page index.
  * `size` (optional integer, default `20`, max `100`): Page size.
  * `unread` (optional boolean): When `true`, filters to only unread notifications (`read_at IS NULL`).
* **Security & Invariants:**
  * User identity is resolved strictly from security context (`SecurityUtils.getCurrentUserId()`).
  * Client cannot supply `userId` to inspect other users' notifications.
  * Deterministic ordering: `created_at DESC, id DESC`.
* **Success Response (`200 OK`):**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 15,
        "type": "APPLICATION_STATUS_CHANGED",
        "title": "Application Status Updated",
        "body": "Your application for \"Senior Java Engineer\" has been updated to SHORTLISTED.",
        "resourceType": "APPLICATION",
        "resourceId": 101,
        "readAt": null,
        "createdAt": "2026-10-18T10:00:00Z",
        "read": false
      },
      {
        "id": 12,
        "type": "JOB_CLOSED",
        "title": "Job Listing Closed",
        "body": "The job listing \"DevOps Engineer\" has been closed.",
        "resourceType": "JOB",
        "resourceId": 45,
        "readAt": "2026-10-18T09:30:00Z",
        "createdAt": "2026-10-18T09:00:00Z",
        "read": true
      }
    ],
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 2,
      "totalPages": 1
    },
    "unreadCount": 1
  }
}
```
* **Potential Errors:**
  * `401 UNAUTHENTICATED`: Missing or invalid Bearer token.

---

### 11.2 Mark Single Notification As Read
* **Method:** `PATCH`
* **Path:** `/api/notifications/{id}/read` (or `/api/notifications/{id}`)
* **Access:** Any authenticated user (`CANDIDATE`, `RECRUITER`, `ADMIN`)
* **Description:** Marks a single notification belonging to the authenticated user as read by setting `read_at = CURRENT_TIMESTAMP(3)`. Idempotent if already read.
* **Request Headers:**
  * `Authorization: Bearer <jwt-token>`
* **Path Variables:**
  * `id`: Notification ID.
* **Security & Invariants:**
  * Notification must belong to the authenticated user (`WHERE id = ? AND user_id = ?`).
  * Accessing another user's notification returns generic `404 RESOURCE_NOT_FOUND` with zero metadata leakage.
* **Success Response (`200 OK`):**
```json
{
  "success": true,
  "data": {
    "id": 15,
    "type": "APPLICATION_STATUS_CHANGED",
    "title": "Application Status Updated",
    "body": "Your application for \"Senior Java Engineer\" has been updated to SHORTLISTED.",
    "resourceType": "APPLICATION",
    "resourceId": 101,
    "readAt": "2026-10-18T10:05:00Z",
    "createdAt": "2026-10-18T10:00:00Z",
    "read": true
  }
}
```
* **Potential Errors:**
  * `401 UNAUTHENTICATED`: Missing or invalid token.
  * `404 RESOURCE_NOT_FOUND`: Notification does not exist or belongs to another user.

---

### 11.3 Mark All Notifications As Read
* **Method:** `PATCH`
* **Path:** `/api/notifications/read-all`
* **Access:** Any authenticated user (`CANDIDATE`, `RECRUITER`, `ADMIN`)
* **Description:** Marks all unread notifications belonging to the authenticated user as read.
* **Request Headers:**
  * `Authorization: Bearer <jwt-token>`
* **Security & Invariants:**
  * Affects strictly notifications where `user_id = authenticatedUserId AND read_at IS NULL`.
  * Other users' notifications remain completely unaffected.
* **Success Response (`200 OK`):**
```json
{
  "success": true,
  "data": {
    "markedReadCount": 3
  }
}
```
* **Potential Errors:**
  * `401 UNAUTHENTICATED`: Missing or invalid token.

---

## 12. Phase 7 Security Hardening & Authorization Policies

### 12.1 Identity Resolution & Server Authority
* All user identities are derived exclusively from the authenticated Spring Security context (`SecurityUtils.getCurrentUserId()`, `SecurityUtils.getRequiredCandidateId()`, `SecurityUtils.getRequiredRecruiterId()`).
* Client-supplied identifiers in query parameters, headers, path segments, or JSON request bodies (`userId`, `candidateId`, `recruiterId`, `companyId`, `resumeId`) are ignored and never override the server-derived identity.

### 12.2 Role Boundary Matrix
| Endpoint Category | Guest | Candidate | Recruiter | Admin |
|---|---|---|---|---|
| Recruiter Applicants (`/api/recruiter/jobs/{id}/applications`, `/api/recruiter/applications/{id}`) | `401` | `403` | `200` | `403` |
| Recruiter Status Mutations (`/api/recruiter/applications/{id}/status`) | `401` | `403` | `200` | `403` |
| Recruiter Resume Access (`/api/recruiter/applications/{id}/resume*`) | `401` | `403` | `200` | `403` |
| Candidate Resume Access (`/api/candidate/resume*`) | `401` | `200` | `403` | `403` |
| Candidate Application Endpoints (`/api/candidate/applications*`) | `401` | `200` | `403` | `403` |
| Notifications (`/api/notifications*`) | `401` | `200` | `200` | `200` |

### 12.3 Cross-Company & Cross-User Isolation
* **Zero Metadata Leakage:** Cross-company requests (e.g. Recruiter from Company A accessing an application or resume belonging to Company B) return standard generic `404 RESOURCE_NOT_FOUND` or `404 JOB_NOT_FOUND`. Error envelopes disclose no company names, job titles, candidate names, resume identifiers, or storage paths.
* **Database Company Authority:** Recruiter company affiliation is resolved live from MySQL (`recruiters.company_id`). Stale JWT claims cannot establish access to an unassociated or reassigned company.
* **Notification Isolation:** Notification endpoints are scoped strictly by `user_id = authenticatedUserId`. Foreign notification updates return generic `404 RESOURCE_NOT_FOUND`. `read-all` exclusively affects unread notifications of the caller.

### 12.4 State Machine & Transactional Safeguards
* **Terminal Status Locking:** Applications in terminal states (`SELECTED`, `REJECTED`, `WITHDRAWN`) cannot transition to any other status. Attempted transitions return `422 INVALID_STATUS_TRANSITION`.
* **Recruiter Withdrawal Prohibition:** Recruiters are strictly prohibited from setting `status = WITHDRAWN` (`422 INVALID_STATUS_TRANSITION`). Self-service withdrawal is exclusively reserved for candidates via `/api/candidate/applications/{id}/withdraw`.
* **Atomic Status Execution:** Status mutations, status history audit logging, and in-app candidate notification creation execute within a single `@Transactional` boundary; failure in any operation triggers complete rollback.
* **Soft-Deleted Job Isolation:** Applications, details, status mutations, and resumes tied to soft-deleted jobs (`deleted_at IS NOT NULL`) are unreachable and return `404 JOB_NOT_FOUND` or `404 RESOURCE_NOT_FOUND`.
* **Storage Path Sanitization:** Resume download routes sanitize storage keys, strictly rejecting path traversal attempts (`../`) with generic 404 responses.

---

## 13. Admin Dashboard & Moderation Queries API (Phase 8.2 - Implemented & Verified)

### 13.1 Admin Dashboard Summary
* **Method:** `GET`
* **Path:** `/api/admin/dashboard`
* **Access:** `ROLE_ADMIN`
* **Description:** Retrieves platform-wide aggregated metrics across users, companies, jobs, and applications using database-backed SQL aggregate queries.
* **Request Headers:**
  * `Authorization: Bearer <jwt-token>`
* **Success Response (`200 OK`):**
```json
{
  "success": true,
  "data": {
    "users": {
      "total": 45,
      "candidates": 30,
      "recruiters": 14,
      "active": 43,
      "inactive": 2
    },
    "companies": {
      "total": 12,
      "active": 10,
      "inactive": 2
    },
    "jobs": {
      "total": 28,
      "active": 18,
      "draft": 4,
      "closed": 4,
      "archived": 2
    },
    "applications": {
      "total": 65,
      "applied": 25,
      "underReview": 15,
      "shortlisted": 10,
      "rejected": 8,
      "selected": 5,
      "withdrawn": 2
    }
  }
}
```
* **Potential Errors:**
  * `401 UNAUTHENTICATED`: Missing or invalid Bearer token.
  * `403 FORBIDDEN`: Non-admin role (`CANDIDATE`, `RECRUITER`).

---

### 13.2 Admin Users List
* **Method:** `GET`
* **Path:** `/api/admin/users`
* **Access:** `ROLE_ADMIN`
* **Description:** Paginated, deterministic listing of all users across the platform for moderation. Passwords, password hashes, and JWT secrets are never exposed.
* **Query Parameters:**
  * `role` (optional): `CANDIDATE`, `RECRUITER`, `ADMIN`
  * `active` (optional): `true`, `false`
  * `q` (optional): search string matching user email or candidate/recruiter full name
  * `page` (optional integer, default `0`)
  * `size` (optional integer, default `20`)
* **Success Response (`200 OK`):** Canonical paginated collection of `AdminUserDto`.

---

### 13.3 Admin Companies List
* **Method:** `GET`
* **Path:** `/api/admin/companies`
* **Access:** `ROLE_ADMIN`
* **Description:** Paginated listing of companies with status filtering and search, including computed job and recruiter counts.
* **Query Parameters:**
  * `active` (optional): `true`, `false`
  * `q` (optional): search query matching company name, industry, or location
  * `page` (optional integer, default `0`)
  * `size` (optional integer, default `20`)
* **Success Response (`200 OK`):** Canonical paginated collection of `AdminCompanyDto`.

---

### 13.4 Admin Jobs List
* **Method:** `GET`
* **Path:** `/api/admin/jobs`
* **Access:** `ROLE_ADMIN`
* **Description:** Platform-wide administrative job listing spanning all companies. Includes all statuses (`DRAFT`, `ACTIVE`, `CLOSED`, `ARCHIVED`) while excluding soft-deleted records (`deleted_at IS NULL`).
* **Query Parameters:**
  * `status` (optional): `JobStatus`
  * `companyId` (optional): company ID
  * `q` (optional): search string matching job title or company name
  * `page` (optional integer, default `0`)
  * `size` (optional integer, default `20`)
* **Success Response (`200 OK`):** Canonical paginated collection of `AdminJobDto`.

---

### 13.5 Admin Applications List
* **Method:** `GET`
* **Path:** `/api/admin/applications`
* **Access:** `ROLE_ADMIN`
* **Description:** Platform-wide administrative application listing spanning all jobs, companies, and candidates.
* **Query Parameters:**
  * `jobId` (optional)
  * `candidateId` (optional)
  * `companyId` (optional)
  * `status` (optional)
  * `page` (optional integer, default `0`)
  * `size` (optional integer, default `20`)
* **Success Response (`200 OK`):** Canonical paginated collection of `AdminApplicationSummaryDto`.

---

### 13.6 Admin Application Detail
* **Method:** `GET`
* **Path:** `/api/admin/applications/{applicationId}`
* **Access:** `ROLE_ADMIN`
* **Description:** Complete inspection view of an application for moderation, preserving immutable candidate and resume snapshots, full status transition audit trail, and job match analysis.
* **Success Response (`200 OK`):** Single `AdminApplicationDetailDto` enclosed in canonical success envelope.
* **Potential Errors:**
  * `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `404 RESOURCE_NOT_FOUND`.

---

### 13.7 Admin Protected Resume Access
* **Method:** `GET`
* **Path:** `/api/admin/applications/{applicationId}/resume` (or `/resume/download`)
* **Access:** `ROLE_ADMIN`
* **Description:** Administrative resume access providing binary PDF streaming or JSON metadata (`?metadata=true`) without recruiter company scoping restrictions. Sanitizes storage keys against path traversal.
* **Success Response (`200 OK`):** Binary stream with `Content-Type: application/pdf` or `ResumeResponse` JSON metadata.
* **Potential Errors:**
  * `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `404 RESOURCE_NOT_FOUND`.

---

### 13.8 Admin Audit Logs List
* **Method:** `GET`
* **Path:** `/api/admin/audit-logs`
* **Access:** `ROLE_ADMIN`
* **Description:** Read-only listing of immutable system audit logs ordered deterministically by `created_at DESC, id DESC`.
* **Query Parameters:**
  * `actorUserId` (optional)
  * `action` (optional)
  * `entityType` (optional)
  * `entityId` (optional)
  * `page` (optional integer, default `0`)
  * `size` (optional integer, default `20`)
* **Success Response (`200 OK`):** Canonical paginated collection of `AuditLogDto`.
* **Potential Errors:**
  * `401 UNAUTHENTICATED`, `403 FORBIDDEN`.

---

### 13.9 Admin User Status Mutation
* **Method:** `PATCH`
* **Path:** `/api/admin/users/{userId}/status`
* **Access:** `ROLE_ADMIN`
* **Description:** Moderates platform user account active status (deactivate or reactivate). Prohibits administrator self-deactivation. Records an immutable audit log (`USER_DEACTIVATED` or `USER_REACTIVATED`) atomically in the same database transaction.
* **Request Headers:** `Content-Type: application/json`
* **Request Body:**
  ```json
  {
    "isActive": false
  }
  ```
* **Success Response (`200 OK`):** Canonical single resource response containing updated `AdminUserDto`.
* **Potential Errors:**
  * `400 BAD REQUEST` (`VALIDATION_FAILED`): Missing or invalid `isActive` boolean.
  * `401 UNAUTHENTICATED`: Missing or invalid Bearer JWT.
  * `403 FORBIDDEN`: Non-admin caller.
  * `404 RESOURCE_NOT_FOUND`: Target user does not exist.
  * `422 UNPROCESSABLE_CONTENT` (`INVALID_STATUS_TRANSITION`): Self-deactivation attempt by authenticated administrator, or same-state transition (user already in requested status).

---

### 13.10 Admin Company Status Mutation
* **Method:** `PATCH`
* **Path:** `/api/admin/companies/{companyId}/status`
* **Access:** `ROLE_ADMIN`
* **Description:** Moderates company active status (deactivate or reactivate). Deactivating a company dynamically hides all its active jobs from public discovery without mutating job lifecycle statuses. Records an immutable audit log (`COMPANY_DEACTIVATED` or `COMPANY_REACTIVATED`) atomically in the same database transaction.
* **Request Headers:** `Content-Type: application/json`
* **Request Body:**
  ```json
  {
    "isActive": false
  }
  ```
* **Success Response (`200 OK`):** Canonical single resource response containing updated `AdminCompanyDto`.
* **Potential Errors:**
  * `400 BAD REQUEST` (`VALIDATION_FAILED`): Missing or invalid `isActive` boolean.
  * `401 UNAUTHENTICATED`: Missing or invalid Bearer JWT.
  * `403 FORBIDDEN`: Non-admin caller.
  * `404 RESOURCE_NOT_FOUND`: Target company does not exist.
  * `422 UNPROCESSABLE_CONTENT` (`INVALID_STATUS_TRANSITION`): Same-state transition (company already in requested status).

---

### 13.11 Admin Job Archive Mutation
* **Method:** `PATCH`
* **Path:** `/api/admin/jobs/{jobId}/archive`
* **Access:** `ROLE_ADMIN`
* **Description:** Moderates job lifecycle by archiving any job currently in `DRAFT`, `ACTIVE`, or `CLOSED`. Immediately hides the job from public discovery while preserving all historical applications, snapshots, and candidate records intact. Records an immutable audit log (`JOB_ARCHIVED`) atomically in the same database transaction.
* **Success Response (`200 OK`):** Canonical single resource response containing updated `AdminJobDto` with `status: "ARCHIVED"`.
* **Potential Errors:**
  * `401 UNAUTHENTICATED`: Missing or invalid Bearer JWT.
  * `403 FORBIDDEN`: Non-admin caller.
  * `404 RESOURCE_NOT_FOUND`: Target job does not exist or has been soft-deleted.
  * `422 UNPROCESSABLE_CONTENT` (`INVALID_STATUS_TRANSITION`): Job is already in `ARCHIVED` status.

---

### 13.12 Admin Job Restore Mutation
* **Method:** `PATCH`
* **Path:** `/api/admin/jobs/{jobId}/restore`
* **Access:** `ROLE_ADMIN`
* **Description:** Restores an archived job. Per CC-MASTER-001 specification, an `ARCHIVED` job restores strictly to `CLOSED` status (never `ACTIVE` or `DRAFT`). Records an immutable audit log (`JOB_RESTORED`) atomically in the same database transaction.
* **Success Response (`200 OK`):** Canonical single resource response containing updated `AdminJobDto` with `status: "CLOSED"`.
* **Potential Errors:**
  * `401 UNAUTHENTICATED`: Missing or invalid Bearer JWT.
  * `403 FORBIDDEN`: Non-admin caller.
  * `404 RESOURCE_NOT_FOUND`: Target job does not exist or has been soft-deleted.
  * `422 UNPROCESSABLE_CONTENT` (`INVALID_STATUS_TRANSITION`): Target job is not currently in `ARCHIVED` status (e.g. attempting to restore a `DRAFT`, `ACTIVE`, or `CLOSED` job).








