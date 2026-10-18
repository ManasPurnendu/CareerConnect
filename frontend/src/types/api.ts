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

export interface CandidateProfile {
  id: number;
  userId: number;
  fullName: string;
  headline?: string;
  phone?: string;
  location?: string;
  bio?: string;
  skills: string[];
  experienceYears?: number;
  education?: string;
  isProfileComplete: boolean;
  resumeUrl?: string;
  createdAt: string;
  updatedAt: string;
}

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
