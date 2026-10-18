/**
 * Canonical CareerConnect API Types & Data Contracts
 * Matched strictly to Spring Boot backend P1-P8 contracts
 */

export type Role = 'CANDIDATE' | 'RECRUITER' | 'ADMIN';

export type JobType =
  | 'INTERNSHIP'
  | 'FULL_TIME'
  | 'PART_TIME'
  | 'APPRENTICESHIP'
  | 'TRAINEE';

export type WorkMode = 'ONSITE' | 'HYBRID' | 'REMOTE';

export type ExperienceLevel =
  | 'INTERNSHIP'
  | 'FRESHER'
  | 'ZERO_TO_ONE_YEARS'
  | 'ONE_TO_TWO_YEARS';

export type JobStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED' | 'ARCHIVED';

export type ApplicationStatus =
  | 'APPLIED'
  | 'UNDER_REVIEW'
  | 'SHORTLISTED'
  | 'INTERVIEW_SCHEDULED'
  | 'OFFERED'
  | 'REJECTED'
  | 'WITHDRAWN';

export interface PaginationMetadata {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  pagination?: PaginationMetadata;
  message: string | null;
}

export interface ApiErrorResponse {
  success: false;
  data: null;
  message: string;
  errorCode: string;
}

export interface PageMetadataCompat {
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}

export interface PagedResponse<T> {
  data: T[];
  pagination?: PaginationMetadata;
  // Aliases for compatibility
  content?: T[];
  page?: PageMetadataCompat;
}

export interface AuthUser {
  id: number;
  email: string;
  role: Role;
  fullName?: string;
  candidateId?: number;
  recruiterId?: number;
  companyId?: number;
  isActive?: boolean;
}

export interface AuthResponseData {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: AuthUser;
}

export interface SkillDto {
  id?: number;
  name: string;
}

export interface CompanySummaryDto {
  id: number;
  name: string;
  industry?: string;
  location?: string;
  logoUrl?: string | null;
  website?: string;
  description?: string;
  companySize?: string;
  isVerified?: boolean;
  activeJobCount?: number;
}

export interface CompanyDetailDto {
  id: number;
  name: string;
  industry?: string;
  location?: string;
  logoUrl?: string | null;
  website?: string;
  description?: string;
  companySize?: string;
  isVerified?: boolean;
  activeJobCount?: number;
  activeJobs: JobSummaryDto[];
  createdAt?: string;
}

export interface JobSummaryDto {
  id: number;
  title: string;
  company?: CompanySummaryDto;
  companyName?: string;
  jobType?: JobType;
  employmentType?: JobType;
  experienceLevel: ExperienceLevel;
  workMode: WorkMode;
  location: string;
  salaryMinLpa?: number;
  salaryMaxLpa?: number;
  salaryCurrency?: string;
  applicationDeadline?: string;
  status: JobStatus;
  skills: (string | SkillDto)[];
  createdAt: string;
  matchScore?: number;
}

export interface JobDetailDto {
  id: number;
  title: string;
  description?: string;
  responsibilities?: string;
  requirements?: string;
  company: CompanySummaryDto;
  jobType: JobType;
  employmentType?: JobType;
  experienceLevel: ExperienceLevel;
  workMode: WorkMode;
  location: string;
  salaryMinLpa?: number;
  salaryMaxLpa?: number;
  salaryCurrency?: string;
  applicationDeadline?: string;
  status: JobStatus;
  skills: (string | SkillDto)[];
  createdAt: string;
  updatedAt?: string;
  applicationCount?: number;
}

// Aliases for component convenience
export type Job = JobSummaryDto;
export type Company = CompanySummaryDto;

export interface CandidateProfileResponse {
  id: number;
  fullName: string;
  headline?: string | null;
  location?: string | null;
  summary?: string | null;
}

export interface UpdateProfileRequest {
  fullName: string;
  headline?: string | null;
  location?: string | null;
  summary?: string | null;
}

export interface ProfileReadinessDto {
  readyToApply: boolean;
  profileComplete: boolean;
  hasEducation: boolean;
  hasSkills: boolean;
  hasResume: boolean;
  missingRequirements: string[];
}

export interface EducationDto {
  id: number;
  candidateId: number;
  institution: string;
  degree: string;
  fieldOfStudy: string;
  startYear: number;
  endYear?: number | null;
  grade?: string | null;
  sortOrder: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateEducationRequest {
  institution: string;
  degree: string;
  fieldOfStudy: string;
  startYear: number;
  endYear?: number | null;
  grade?: string | null;
  sortOrder?: number;
}

export interface UpdateEducationRequest {
  institution: string;
  degree: string;
  fieldOfStudy: string;
  startYear: number;
  endYear?: number | null;
  grade?: string | null;
  sortOrder?: number;
}

export interface CandidateSkillDto {
  id: number;
  name: string;
}

export interface AssignSkillRequest {
  name: string;
}

export interface ProjectDto {
  id: number;
  candidateId: number;
  title: string;
  description: string;
  projectUrl?: string | null;
  sortOrder: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateProjectRequest {
  title: string;
  description: string;
  projectUrl?: string | null;
  sortOrder?: number;
}

export interface UpdateProjectRequest {
  title: string;
  description: string;
  projectUrl?: string | null;
  sortOrder?: number;
}

export interface ExperienceDto {
  id: number;
  candidateId: number;
  companyName: string;
  roleTitle: string;
  startDate: string;
  endDate?: string | null;
  isCurrent: boolean;
  description?: string | null;
  sortOrder: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateExperienceRequest {
  companyName: string;
  roleTitle: string;
  startDate: string;
  endDate?: string | null;
  isCurrent?: boolean;
  description?: string | null;
  sortOrder?: number;
}

export interface UpdateExperienceRequest {
  companyName: string;
  roleTitle: string;
  startDate: string;
  endDate?: string | null;
  isCurrent?: boolean;
  description?: string | null;
  sortOrder?: number;
}

export interface ResumeResponse {
  id: number;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  isCurrent: boolean;
  createdAt: string;
  updatedAt?: string;
}

export interface JobMatchDto {
  matchedSkills: string[];
  missingSkills: string[];
  additionalSkills: string[];
  percentage: number;
}

export interface ApplicationStatusHistoryDto {
  id: number;
  applicationId: number;
  fromStatus?: ApplicationStatus | null;
  toStatus: ApplicationStatus;
  changedByUserId?: number | null;
  note?: string | null;
  createdAt: string;
}

export interface CandidateEducationSnapshot {
  id: number;
  institution: string;
  degree: string;
  fieldOfStudy: string;
  startYear: number;
  endYear?: number | null;
  grade?: string | null;
  sortOrder?: number;
}

export interface CandidateProjectSnapshot {
  id: number;
  title: string;
  description: string;
  projectUrl?: string | null;
  sortOrder?: number;
}

export interface CandidateExperienceSnapshot {
  id: number;
  companyName: string;
  roleTitle: string;
  startDate: string;
  endDate?: string | null;
  isCurrent?: boolean;
  description?: string | null;
  sortOrder?: number;
}

export interface CandidateSnapshot {
  candidateId: number;
  fullName: string;
  email: string;
  headline?: string | null;
  location?: string | null;
  summary?: string | null;
  education?: CandidateEducationSnapshot[];
  skills?: string[];
  projects?: CandidateProjectSnapshot[];
  experience?: CandidateExperienceSnapshot[];
}

export interface ResumeSnapshot {
  id: number;
  originalFilename: string;
  storageKey?: string;
  contentType: string;
  sizeBytes: number;
}

export interface ApplicationResponseDto {
  applicationId: number;
  jobId: number;
  candidateId: number;
  status: ApplicationStatus;
  coverLetter?: string | null;
  appliedAt: string;
  jobTitle?: string;
  companyName?: string;
  candidateSnapshot?: CandidateSnapshot;
  resumeSnapshot?: ResumeSnapshot;
  statusHistory?: ApplicationStatusHistoryDto[];
}

export interface ApplicationSubmissionRequest {
  coverLetter?: string;
}

export type NotificationType =
  | 'APPLICATION_STATUS_CHANGED'
  | 'JOB_CLOSED'
  | 'APPLICATION_RECEIVED'
  | 'NEW_JOB_POSTED';

export interface NotificationDto {
  id: number;
  type: NotificationType;
  title: string;
  body: string;
  resourceType?: string | null;
  resourceId?: number | null;
  readAt?: string | null;
  createdAt: string;
  read: boolean;
}

export interface NotificationSummaryResponse {
  items: NotificationDto[];
  pagination: PaginationMetadata;
  unreadCount: number;
}

// Aliases and legacy models for recruiter component compatibility
export type CandidateProfile = CandidateProfileResponse;

export interface Application {
  id: number;
  jobId: number;
  jobTitle: string;
  companyName: string;
  candidateId: number;
  candidateName: string;
  candidateEmail: string;
  status: ApplicationStatus;
  statusNote?: string;
  matchScore?: number;
  resumeSnapshotUrl?: string;
  appliedAt: string;
  updatedAt: string;
}
