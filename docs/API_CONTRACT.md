# CareerConnect — REST API Contract

**Document:** API Contract Specification  
**Project:** CareerConnect  
**Phase:** Phase 2 Complete  
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
