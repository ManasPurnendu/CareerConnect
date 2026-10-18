import { apiClient } from './client';
import type { Company, Job, Application, PagedResponse } from '../types/api';

export interface CreateJobPayload {
  title: string;
  description: string;
  location: string;
  jobType: string;
  workMode: string;
  experienceLevel: string;
  minSalary?: number;
  maxSalary?: number;
  currency?: string;
  requiredSkills: string[];
  openPositions?: number;
}

export const recruiterApi = {
  getCompany: async (): Promise<Company> => {
    return apiClient.get<Company>('/recruiter/company');
  },

  updateCompany: async (data: Partial<Company>): Promise<Company> => {
    return apiClient.patch<Company>('/recruiter/company', data);
  },

  getJobs: async (page = 0, size = 10): Promise<PagedResponse<Job>> => {
    return apiClient.get<PagedResponse<Job>>('/recruiter/jobs', {
      params: { page, size },
    });
  },

  createJob: async (job: CreateJobPayload): Promise<Job> => {
    return apiClient.post<Job>('/recruiter/jobs', job);
  },

  getApplications: async (jobId?: number, page = 0, size = 10): Promise<PagedResponse<Application>> => {
    return apiClient.get<PagedResponse<Application>>('/recruiter/applications', {
      params: { jobId, page, size },
    });
  },

  updateApplicationStatus: async (
    applicationId: number | string,
    status: string,
    statusNote?: string
  ): Promise<Application> => {
    return apiClient.patch<Application>(`/recruiter/applications/${applicationId}/status`, {
      status,
      statusNote,
    });
  },
};
