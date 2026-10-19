import { apiClient } from './client';
import type {
  RecruiterCompanyResponseDto,
  RecruiterCompanyUpsertRequest,
  JobSummaryDto,
  JobDetailDto,
  RecruiterJobCreateRequest,
  RecruiterJobUpdateRequest,
  ApplicantSummaryDto,
  ApplicantDetailDto,
  ApplicationStatus,
  PagedResponse,
  NotificationDto,
  NotificationSummaryResponse,
} from '../types/api';

export const recruiterApi = {
  // Company Profile
  getCompany: async (): Promise<RecruiterCompanyResponseDto> => {
    return apiClient.get<RecruiterCompanyResponseDto>('/recruiter/company');
  },

  upsertCompany: async (request: RecruiterCompanyUpsertRequest): Promise<RecruiterCompanyResponseDto> => {
    return apiClient.put<RecruiterCompanyResponseDto>('/recruiter/company', request);
  },

  updateCompany: async (request: RecruiterCompanyUpsertRequest): Promise<RecruiterCompanyResponseDto> => {
    return apiClient.put<RecruiterCompanyResponseDto>('/recruiter/company', request);
  },

  // Job Management
  getJobs: async (page = 0, size = 20): Promise<PagedResponse<JobSummaryDto>> => {
    return apiClient.get<PagedResponse<JobSummaryDto>>('/recruiter/jobs', {
      params: { page, size },
    });
  },

  getJobById: async (jobId: number | string): Promise<JobDetailDto> => {
    return apiClient.get<JobDetailDto>(`/recruiter/jobs/${jobId}`);
  },

  createJob: async (request: RecruiterJobCreateRequest): Promise<JobDetailDto> => {
    return apiClient.post<JobDetailDto>('/recruiter/jobs', request);
  },

  updateJob: async (jobId: number | string, request: RecruiterJobUpdateRequest): Promise<JobDetailDto> => {
    return apiClient.put<JobDetailDto>(`/recruiter/jobs/${jobId}`, request);
  },

  publishJob: async (jobId: number | string): Promise<JobDetailDto> => {
    return apiClient.patch<JobDetailDto>(`/recruiter/jobs/${jobId}/publish`);
  },

  closeJob: async (jobId: number | string): Promise<JobDetailDto> => {
    return apiClient.patch<JobDetailDto>(`/recruiter/jobs/${jobId}/close`);
  },

  deleteJob: async (jobId: number | string): Promise<void> => {
    return apiClient.delete<void>(`/recruiter/jobs/${jobId}`);
  },

  // Applicant & Application Management
  getJobApplicants: async (
    jobId: number | string,
    status?: ApplicationStatus,
    page = 0,
    size = 20
  ): Promise<PagedResponse<ApplicantSummaryDto>> => {
    return apiClient.get<PagedResponse<ApplicantSummaryDto>>(`/recruiter/jobs/${jobId}/applications`, {
      params: { status, page, size },
    });
  },

  getApplicationDetail: async (applicationId: number | string): Promise<ApplicantDetailDto> => {
    return apiClient.get<ApplicantDetailDto>(`/recruiter/applications/${applicationId}`);
  },

  updateApplicationStatus: async (
    applicationId: number | string,
    status: ApplicationStatus,
    note?: string
  ): Promise<ApplicantDetailDto> => {
    return apiClient.patch<ApplicantDetailDto>(`/recruiter/applications/${applicationId}/status`, {
      status,
      note,
    });
  },

  downloadResume: async (applicationId: number | string): Promise<Blob> => {
    return apiClient.get<Blob>(`/recruiter/applications/${applicationId}/resume`);
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

  // Backward compatibility alias for earlier mock callers
  getApplications: async (
    jobId?: number | string,
    page = 0,
    size = 20
  ): Promise<PagedResponse<ApplicantSummaryDto>> => {
    if (jobId) {
      return apiClient.get<PagedResponse<ApplicantSummaryDto>>(`/recruiter/jobs/${jobId}/applications`, {
        params: { page, size },
      });
    }
    return apiClient.get<PagedResponse<ApplicantSummaryDto>>('/recruiter/applications', {
      params: { page, size },
    });
  },
};
