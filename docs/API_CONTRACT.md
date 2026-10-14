# CareerConnect — REST API Contract

**Document:** API Contract Specification  
**Project:** CareerConnect  
**Phase:** Phase 4 Complete (Candidate Profile, Resources & Readiness)  
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
* `GET /api/candidate/resume` (`200 OK`): retrieves current resume metadata; returns `404 RESOURCE_NOT_FOUND` if none.
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
