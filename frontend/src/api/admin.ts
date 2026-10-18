import { apiClient } from './client';
import type { AuthUser, PagedResponse } from '../types/api';

export interface AuditLogEntry {
  id: number;
  actorId: number;
  action: string;
  entityType: string;
  entityId: number;
  details?: string;
  createdAt: string;
}

export const adminApi = {
  getUsers: async (page = 0, size = 10): Promise<PagedResponse<AuthUser>> => {
    return apiClient.get<PagedResponse<AuthUser>>('/admin/users', {
      params: { page, size },
    });
  },

  updateUserStatus: async (userId: number, isActive: boolean): Promise<AuthUser> => {
    return apiClient.patch<AuthUser>(`/admin/users/${userId}/status`, { isActive });
  },

  getAuditLogs: async (page = 0, size = 20): Promise<PagedResponse<AuditLogEntry>> => {
    return apiClient.get<PagedResponse<AuditLogEntry>>('/admin/audit-logs', {
      params: { page, size },
    });
  },
};
