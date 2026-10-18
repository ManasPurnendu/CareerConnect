import { apiClient } from './client';
import type { Job, PagedResponse } from '../types/api';

export interface JobFilterParams {
  keyword?: string;
  jobType?: string;
  workMode?: string;
  experienceLevel?: string;
  location?: string;
  page?: number;
  size?: number;
}

export const jobsApi = {
  getPublicJobs: async (params?: JobFilterParams): Promise<PagedResponse<Job>> => {
    return apiClient.get<PagedResponse<Job>>('/jobs', {
      params: params as Record<string, string | number | boolean | undefined | null>,
    });
  },

  getJobById: async (jobId: number | string): Promise<Job> => {
    return apiClient.get<Job>(`/jobs/${jobId}`);
  },
};
