import { apiClient } from './client';
import type {
  JobSummaryDto,
  JobDetailDto,
  PagedResponse,
  JobType,
  ExperienceLevel,
  WorkMode,
} from '../types/api';

export interface JobFilterParams {
  keyword?: string;
  location?: string;
  employmentType?: JobType | string;
  experienceLevel?: ExperienceLevel | string;
  workMode?: WorkMode | string;
  skills?: string;
  minSalary?: number;
  maxSalary?: number;
  sort?: string;
  page?: number;
  size?: number;
}

export const jobsApi = {
  getPublicJobs: async (params?: JobFilterParams): Promise<PagedResponse<JobSummaryDto>> => {
    return apiClient.get<PagedResponse<JobSummaryDto>>('/jobs', {
      params: params as Record<string, string | number | boolean | undefined | null>,
    });
  },

  getJobById: async (jobId: number | string): Promise<JobDetailDto> => {
    return apiClient.get<JobDetailDto>(`/jobs/${jobId}`);
  },
};
