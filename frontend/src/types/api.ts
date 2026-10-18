/**
 * Canonical CareerConnect API Types & Data Contracts
 * Matched strictly to Spring Boot backend P1-P8 contracts
 */

export type Role = 'CANDIDATE' | 'RECRUITER' | 'ADMIN';

export type JobType = 'FULL_TIME' | 'PART_TIME' | 'INTERNSHIP' | 'CONTRACT';

export type WorkMode = 'REMOTE' | 'HYBRID' | 'ON_SITE';

export type ExperienceLevel = 'FRESHER' | 'INTERN' | 'JUNIOR' | 'MID';

export type JobStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'ARCHIVED';

export type ApplicationStatus =
  | 'APPLIED'
  | 'UNDER_REVIEW'
  | 'SHORTLISTED'
  | 'INTERVIEW_SCHEDULED'
  | 'OFFERED'
  | 'REJECTED'
  | 'WITHDRAWN';

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string | null;
}

export interface ApiErrorResponse {
  success: false;
  data: null;
  message: string;
  errorCode: string;
}

export interface PageMetadata {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface PagedResponse<T> {
  content: T[];
  page: PageMetadata;
}

export interface AuthUser {
  id: number;
  email: string;
  role: Role;
  isActive: boolean;
  fullName?: string;
}

export interface AuthResponseData {
  token: string;
  user: AuthUser;
}

export interface CandidateProfile {
  id: number;
  userId: number;
  fullName: string;
  headline: string;
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

export interface Company {
  id: number;
  name: string;
  description?: string;
  website?: string;
  location?: string;
  logoUrl?: string;
  industry?: string;
  size?: string;
  isVerified: boolean;
  createdAt?: string;
}

export interface Job {
  id: number;
  title: string;
  companyId: number;
  companyName: string;
  companyLogoUrl?: string;
  description: string;
  location: string;
  jobType: JobType;
  workMode: WorkMode;
  experienceLevel: ExperienceLevel;
  minSalary?: number;
  maxSalary?: number;
  currency?: string;
  requiredSkills: string[];
  status: JobStatus;
  openPositions?: number;
  expiresAt?: string;
  createdAt: string;
  updatedAt: string;
  matchScore?: number;
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
