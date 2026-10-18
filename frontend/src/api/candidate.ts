import { apiClient } from './client';
import type { CandidateProfile, Application, PagedResponse, Job } from '../types/api';

export const candidateApi = {
  getProfile: async (): Promise<CandidateProfile> => {
    return apiClient.get<CandidateProfile>('/candidate/profile');
  },

  updateProfile: async (profile: Partial<CandidateProfile>): Promise<CandidateProfile> => {
    return apiClient.patch<CandidateProfile>('/candidate/profile', profile);
  },

  getApplications: async (page = 0, size = 10): Promise<PagedResponse<Application>> => {
    return apiClient.get<PagedResponse<Application>>('/candidate/applications', {
      params: { page, size },
    });
  },

  getApplicationById: async (applicationId: number | string): Promise<Application> => {
    return apiClient.get<Application>(`/candidate/applications/${applicationId}`);
  },

  applyForJob: async (jobId: number | string): Promise<Application> => {
    return apiClient.post<Application>(`/candidate/applications`, { jobId });
  },

  // Note: Canonical backend withdrawal endpoint is PATCH, not POST!
  withdrawApplication: async (applicationId: number | string): Promise<Application> => {
    return apiClient.patch<Application>(`/candidate/applications/${applicationId}/withdraw`);
  },

  getMatchedJobs: async (page = 0, size = 10): Promise<PagedResponse<Job>> => {
    return apiClient.get<PagedResponse<Job>>('/candidate/jobs/matched', {
      params: { page, size },
    });
  },
};
