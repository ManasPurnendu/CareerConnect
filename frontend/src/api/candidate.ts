import { apiClient } from './client';
import type {
  CandidateProfileResponse,
  UpdateProfileRequest,
  ProfileReadinessDto,
  EducationDto,
  CreateEducationRequest,
  UpdateEducationRequest,
  CandidateSkillDto,
  AssignSkillRequest,
  ProjectDto,
  CreateProjectRequest,
  UpdateProjectRequest,
  ExperienceDto,
  CreateExperienceRequest,
  UpdateExperienceRequest,
  ResumeResponse,
  ApplicationResponseDto,
  ApplicationSubmissionRequest,
  JobMatchDto,
  NotificationSummaryResponse,
  NotificationDto,
  PagedResponse,
} from '../types/api';

export const candidateApi = {
  // Profile
  getProfile: async (): Promise<CandidateProfileResponse> => {
    return apiClient.get<CandidateProfileResponse>('/candidate/profile');
  },

  updateProfile: async (request: UpdateProfileRequest): Promise<CandidateProfileResponse> => {
    return apiClient.put<CandidateProfileResponse>('/candidate/profile', request);
  },

  getReadiness: async (): Promise<ProfileReadinessDto> => {
    return apiClient.get<ProfileReadinessDto>('/candidate/profile/readiness');
  },

  // Education
  getEducation: async (): Promise<EducationDto[]> => {
    return apiClient.get<EducationDto[]>('/candidate/education');
  },

  createEducation: async (request: CreateEducationRequest): Promise<EducationDto> => {
    return apiClient.post<EducationDto>('/candidate/education', request);
  },

  updateEducation: async (id: number | string, request: UpdateEducationRequest): Promise<EducationDto> => {
    return apiClient.put<EducationDto>(`/candidate/education/${id}`, request);
  },

  deleteEducation: async (id: number | string): Promise<void> => {
    return apiClient.delete<void>(`/candidate/education/${id}`);
  },

  // Skills
  getSkills: async (): Promise<CandidateSkillDto[]> => {
    return apiClient.get<CandidateSkillDto[]>('/candidate/skills');
  },

  assignSkill: async (request: AssignSkillRequest): Promise<CandidateSkillDto> => {
    return apiClient.post<CandidateSkillDto>('/candidate/skills', request);
  },

  removeSkill: async (skillId: number | string): Promise<void> => {
    return apiClient.delete<void>(`/candidate/skills/${skillId}`);
  },

  // Projects
  getProjects: async (): Promise<ProjectDto[]> => {
    return apiClient.get<ProjectDto[]>('/candidate/projects');
  },

  createProject: async (request: CreateProjectRequest): Promise<ProjectDto> => {
    return apiClient.post<ProjectDto>('/candidate/projects', request);
  },

  updateProject: async (id: number | string, request: UpdateProjectRequest): Promise<ProjectDto> => {
    return apiClient.put<ProjectDto>(`/candidate/projects/${id}`, request);
  },

  deleteProject: async (id: number | string): Promise<void> => {
    return apiClient.delete<void>(`/candidate/projects/${id}`);
  },

  // Experience
  getExperience: async (): Promise<ExperienceDto[]> => {
    return apiClient.get<ExperienceDto[]>('/candidate/experience');
  },

  createExperience: async (request: CreateExperienceRequest): Promise<ExperienceDto> => {
    return apiClient.post<ExperienceDto>('/candidate/experience', request);
  },

  updateExperience: async (id: number | string, request: UpdateExperienceRequest): Promise<ExperienceDto> => {
    return apiClient.put<ExperienceDto>(`/candidate/experience/${id}`, request);
  },

  deleteExperience: async (id: number | string): Promise<void> => {
    return apiClient.delete<void>(`/candidate/experience/${id}`);
  },

  // Resume
  getCurrentResume: async (): Promise<ResumeResponse> => {
    return apiClient.get<ResumeResponse>('/candidate/resume');
  },

  uploadResume: async (file: File): Promise<ResumeResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    return apiClient.post<ResumeResponse>('/candidate/resume', formData);
  },

  downloadResume: async (): Promise<Blob> => {
    return apiClient.get<Blob>('/candidate/resume/download');
  },

  deleteResume: async (): Promise<void> => {
    return apiClient.delete<void>('/candidate/resume');
  },

  // Applications
  getApplications: async (page = 0, size = 20): Promise<PagedResponse<ApplicationResponseDto>> => {
    return apiClient.get<PagedResponse<ApplicationResponseDto>>('/candidate/applications', {
      params: { page, size },
    });
  },

  getApplicationById: async (applicationId: number | string): Promise<ApplicationResponseDto> => {
    return apiClient.get<ApplicationResponseDto>(`/candidate/applications/${applicationId}`);
  },

  applyForJob: async (jobId: number | string, request?: ApplicationSubmissionRequest): Promise<ApplicationResponseDto> => {
    return apiClient.post<ApplicationResponseDto>(`/jobs/${jobId}/applications`, request || {});
  },

  withdrawApplication: async (applicationId: number | string): Promise<ApplicationResponseDto> => {
    return apiClient.patch<ApplicationResponseDto>(`/candidate/applications/${applicationId}/withdraw`);
  },

  // Job Matching
  getJobMatch: async (jobId: number | string): Promise<JobMatchDto> => {
    return apiClient.get<JobMatchDto>(`/jobs/${jobId}/match`);
  },

  // Notifications
  getNotifications: async (page = 0, size = 20, unread?: boolean): Promise<NotificationSummaryResponse> => {
    return apiClient.get<NotificationSummaryResponse>('/notifications', {
      params: { page, size, unread },
    });
  },

  markNotificationRead: async (id: number | string): Promise<NotificationDto> => {
    return apiClient.patch<NotificationDto>(`/notifications/${id}/read`);
  },

  markAllNotificationsRead: async (): Promise<{ markedReadCount: number }> => {
    return apiClient.patch<{ markedReadCount: number }>('/notifications/read-all');
  },

  // Aliases for developer and test convenience
  addEducation: async (request: CreateEducationRequest): Promise<EducationDto> => {
    return apiClient.post<EducationDto>('/candidate/education', request);
  },
  addSkill: async (request: AssignSkillRequest): Promise<CandidateSkillDto> => {
    return apiClient.post<CandidateSkillDto>('/candidate/skills', request);
  },
  addProject: async (request: CreateProjectRequest): Promise<ProjectDto> => {
    return apiClient.post<ProjectDto>('/candidate/projects', request);
  },
  getExperiences: async (): Promise<ExperienceDto[]> => {
    return apiClient.get<ExperienceDto[]>('/candidate/experience');
  },
  addExperience: async (request: CreateExperienceRequest): Promise<ExperienceDto> => {
    return apiClient.post<ExperienceDto>('/candidate/experience', request);
  },
  markNotificationAsRead: async (id: number | string): Promise<NotificationDto> => {
    return apiClient.patch<NotificationDto>(`/notifications/${id}/read`);
  },
  markAllNotificationsAsRead: async (): Promise<{ markedReadCount: number }> => {
    return apiClient.patch<{ markedReadCount: number }>('/notifications/read-all');
  },
};
