import { apiClient } from './client';
import type {
  AdminDashboardMetrics,
  AdminUserDto,
  AdminCompanyDto,
  AdminJobDto,
  AdminApplicationSummaryDto,
  AdminApplicationDetailDto,
  AuditLogDto,
  PagedResponse,
  Role,
  JobStatus,
  ApplicationStatus,
} from '../types/api';

// Backward compatibility alias
export type AuditLogEntry = AuditLogDto;

export interface GetUsersParams {
  role?: Role;
  active?: boolean;
  q?: string;
  page?: number;
  size?: number;
}

export interface GetCompaniesParams {
  active?: boolean;
  q?: string;
  page?: number;
  size?: number;
}

export interface GetJobsParams {
  status?: JobStatus;
  companyId?: number;
  q?: string;
  page?: number;
  size?: number;
}

export interface GetApplicationsParams {
  jobId?: number;
  candidateId?: number;
  companyId?: number;
  status?: ApplicationStatus;
  page?: number;
  size?: number;
}

export interface GetAuditLogsParams {
  actorUserId?: number;
  action?: string;
  entityType?: string;
  entityId?: number;
  page?: number;
  size?: number;
}

export const adminApi = {
  // 1. Dashboard Metrics
  getDashboard: async (): Promise<AdminDashboardMetrics> => {
    return apiClient.get<AdminDashboardMetrics>('/admin/dashboard');
  },

  // 2. User Moderation
  getUsers: async (
    pageOrParams?: number | GetUsersParams,
    sizeParam?: number
  ): Promise<PagedResponse<AdminUserDto>> => {
    if (typeof pageOrParams === 'number' || pageOrParams === undefined) {
      return apiClient.get<PagedResponse<AdminUserDto>>('/admin/users', {
        params: { page: pageOrParams ?? 0, size: sizeParam ?? 20 },
      });
    }
    const { page = 0, size = 20, ...rest } = pageOrParams;
    return apiClient.get<PagedResponse<AdminUserDto>>('/admin/users', {
      params: { page, size, ...rest },
    });
  },

  updateUserStatus: async (userId: number, isActive: boolean): Promise<AdminUserDto> => {
    return apiClient.patch<AdminUserDto>(`/admin/users/${userId}/status`, { isActive });
  },

  // 3. Company Moderation
  getCompanies: async (params?: GetCompaniesParams): Promise<PagedResponse<AdminCompanyDto>> => {
    const { page = 0, size = 20, ...rest } = params || {};
    return apiClient.get<PagedResponse<AdminCompanyDto>>('/admin/companies', {
      params: { page, size, ...rest },
    });
  },

  updateCompanyStatus: async (companyId: number, isActive: boolean): Promise<AdminCompanyDto> => {
    return apiClient.patch<AdminCompanyDto>(`/admin/companies/${companyId}/status`, { isActive });
  },

  // 4. Job Moderation
  getJobs: async (params?: GetJobsParams): Promise<PagedResponse<AdminJobDto>> => {
    const { page = 0, size = 20, ...rest } = params || {};
    return apiClient.get<PagedResponse<AdminJobDto>>('/admin/jobs', {
      params: { page, size, ...rest },
    });
  },

  archiveJob: async (jobId: number): Promise<AdminJobDto> => {
    return apiClient.patch<AdminJobDto>(`/admin/jobs/${jobId}/archive`);
  },

  restoreJob: async (jobId: number): Promise<AdminJobDto> => {
    return apiClient.patch<AdminJobDto>(`/admin/jobs/${jobId}/restore`);
  },

  // 5. Application Inspection
  getApplications: async (params?: GetApplicationsParams): Promise<PagedResponse<AdminApplicationSummaryDto>> => {
    const { page = 0, size = 20, ...rest } = params || {};
    return apiClient.get<PagedResponse<AdminApplicationSummaryDto>>('/admin/applications', {
      params: { page, size, ...rest },
    });
  },

  getApplicationDetail: async (applicationId: number): Promise<AdminApplicationDetailDto> => {
    return apiClient.get<AdminApplicationDetailDto>(`/admin/applications/${applicationId}`);
  },

  // 6. Resume Download (Authenticated Blob Streaming)
  downloadResume: async (applicationId: number): Promise<Blob> => {
    return apiClient.get<Blob>(`/admin/applications/${applicationId}/resume`);
  },

  // 7. Audit Logs
  getAuditLogs: async (
    pageOrParams?: number | GetAuditLogsParams,
    sizeParam?: number
  ): Promise<PagedResponse<AuditLogDto>> => {
    if (typeof pageOrParams === 'number' || pageOrParams === undefined) {
      return apiClient.get<PagedResponse<AuditLogDto>>('/admin/audit-logs', {
        params: { page: pageOrParams ?? 0, size: sizeParam ?? 20 },
      });
    }
    const { page = 0, size = 20, ...rest } = pageOrParams;
    return apiClient.get<PagedResponse<AuditLogDto>>('/admin/audit-logs', {
      params: { page, size, ...rest },
    });
  },
};
