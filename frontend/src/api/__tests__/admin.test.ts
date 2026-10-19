import { describe, it, expect, vi, beforeEach } from 'vitest';
import { adminApi } from '../admin';
import { apiClient } from '../client';
import type {
  AdminDashboardMetrics,
  AdminUserDto,
  AdminCompanyDto,
  AdminJobDto,
  AdminApplicationSummaryDto,
  AdminApplicationDetailDto,
  AuditLogDto,
  PagedResponse,
} from '../../types/api';

describe('adminApi Client Suite', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  describe('Dashboard Metrics API', () => {
    it('getDashboard calls GET /admin/dashboard', async () => {
      const mockMetrics: AdminDashboardMetrics = {
        users: { total: 100, candidates: 70, recruiters: 25, active: 90, inactive: 10 },
        companies: { total: 20, active: 18, inactive: 2 },
        jobs: { total: 45, active: 30, draft: 5, closed: 8, archived: 2 },
        applications: { total: 150, applied: 50, underReview: 40, shortlisted: 20, rejected: 15, selected: 15, withdrawn: 10 },
      };
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockMetrics);

      const result = await adminApi.getDashboard();
      expect(getSpy).toHaveBeenCalledWith('/admin/dashboard');
      expect(result).toEqual(mockMetrics);
    });
  });

  describe('User Moderation API', () => {
    it('getUsers handles default pagination and query options', async () => {
      const mockResponse: PagedResponse<AdminUserDto> = {
        data: [
          {
            id: 1,
            email: 'admin@careerconnect.com',
            role: 'ADMIN',
            fullName: 'Platform Admin',
            active: true,
            createdAt: '2026-10-14T21:13:26.492Z',
            updatedAt: '2026-10-14T21:13:26.492Z',
          },
        ],
        pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
      };
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockResponse);

      const result = await adminApi.getUsers({ role: 'CANDIDATE', active: true, q: 'john', page: 1, size: 10 });
      expect(getSpy).toHaveBeenCalledWith('/admin/users', {
        params: { role: 'CANDIDATE', active: true, q: 'john', page: 1, size: 10 },
      });
      expect(result).toEqual(mockResponse);
    });

    it('updateUserStatus calls PATCH /admin/users/:userId/status with isActive', async () => {
      const mockUpdated: AdminUserDto = {
        id: 42,
        email: 'user@example.com',
        role: 'CANDIDATE',
        fullName: 'Jane Doe',
        active: false,
        createdAt: '2026-10-14T21:13:26.492Z',
        updatedAt: '2026-10-14T21:13:26.492Z',
      };
      const patchSpy = vi.spyOn(apiClient, 'patch').mockResolvedValueOnce(mockUpdated);

      const result = await adminApi.updateUserStatus(42, false);
      expect(patchSpy).toHaveBeenCalledWith('/admin/users/42/status', { isActive: false });
      expect(result).toEqual(mockUpdated);
    });
  });

  describe('Company Moderation API', () => {
    it('getCompanies passes filters and pagination parameters', async () => {
      const mockResponse: PagedResponse<AdminCompanyDto> = {
        data: [
          {
            id: 5,
            name: 'Acme Corp',
            industry: 'Technology',
            location: 'Bangalore',
            active: true,
            jobCount: 12,
            recruiterCount: 3,
            createdAt: '2026-10-14T21:13:26.492Z',
            updatedAt: '2026-10-14T21:13:26.492Z',
          },
        ],
        pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
      };
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockResponse);

      const result = await adminApi.getCompanies({ active: true, q: 'Acme', page: 0, size: 15 });
      expect(getSpy).toHaveBeenCalledWith('/admin/companies', {
        params: { active: true, q: 'Acme', page: 0, size: 15 },
      });
      expect(result).toEqual(mockResponse);
    });

    it('updateCompanyStatus calls PATCH /admin/companies/:companyId/status with isActive', async () => {
      const mockUpdated: AdminCompanyDto = {
        id: 5,
        name: 'Acme Corp',
        active: false,
        jobCount: 12,
        recruiterCount: 3,
        createdAt: '2026-10-14T21:13:26.492Z',
        updatedAt: '2026-10-14T21:13:26.492Z',
      };
      const patchSpy = vi.spyOn(apiClient, 'patch').mockResolvedValueOnce(mockUpdated);

      const result = await adminApi.updateCompanyStatus(5, false);
      expect(patchSpy).toHaveBeenCalledWith('/admin/companies/5/status', { isActive: false });
      expect(result).toEqual(mockUpdated);
    });
  });

  describe('Job Moderation API', () => {
    it('getJobs passes status, companyId, and search query', async () => {
      const mockResponse: PagedResponse<AdminJobDto> = {
        data: [],
        pagination: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
      };
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockResponse);

      const result = await adminApi.getJobs({ status: 'ACTIVE', companyId: 10, q: 'Dev', page: 0, size: 20 });
      expect(getSpy).toHaveBeenCalledWith('/admin/jobs', {
        params: { status: 'ACTIVE', companyId: 10, q: 'Dev', page: 0, size: 20 },
      });
      expect(result).toEqual(mockResponse);
    });

    it('archiveJob calls PATCH /admin/jobs/:jobId/archive', async () => {
      const mockJob: AdminJobDto = {
        id: 77,
        companyId: 10,
        companyName: 'Acme Corp',
        title: 'Junior Engineer',
        description: 'Role description',
        jobType: 'FULL_TIME',
        experienceLevel: 'ZERO_TO_ONE_YEARS',
        workMode: 'REMOTE',
        applicationDeadline: '2026-12-31',
        status: 'ARCHIVED',
        applicationCount: 5,
        requiredSkills: ['Java', 'SQL'],
        createdAt: '2026-10-14T21:13:26.492Z',
        updatedAt: '2026-10-14T21:13:26.492Z',
      };
      const patchSpy = vi.spyOn(apiClient, 'patch').mockResolvedValueOnce(mockJob);

      const result = await adminApi.archiveJob(77);
      expect(patchSpy).toHaveBeenCalledWith('/admin/jobs/77/archive');
      expect(result).toEqual(mockJob);
    });

    it('restoreJob calls PATCH /admin/jobs/:jobId/restore', async () => {
      const mockJob: AdminJobDto = {
        id: 77,
        companyId: 10,
        companyName: 'Acme Corp',
        title: 'Junior Engineer',
        description: 'Role description',
        jobType: 'FULL_TIME',
        experienceLevel: 'ZERO_TO_ONE_YEARS',
        workMode: 'REMOTE',
        applicationDeadline: '2026-12-31',
        status: 'CLOSED',
        applicationCount: 5,
        requiredSkills: ['Java', 'SQL'],
        createdAt: '2026-10-14T21:13:26.492Z',
        updatedAt: '2026-10-14T21:13:26.492Z',
      };
      const patchSpy = vi.spyOn(apiClient, 'patch').mockResolvedValueOnce(mockJob);

      const result = await adminApi.restoreJob(77);
      expect(patchSpy).toHaveBeenCalledWith('/admin/jobs/77/restore');
      expect(result).toEqual(mockJob);
    });
  });

  describe('Application Inspection & Resume Download API', () => {
    it('getApplications calls GET /admin/applications with filters', async () => {
      const mockResponse: PagedResponse<AdminApplicationSummaryDto> = {
        data: [
          {
            id: 101,
            jobId: 77,
            jobTitle: 'Junior Engineer',
            companyId: 10,
            companyName: 'Acme Corp',
            candidateId: 15,
            candidateName: 'John Candidate',
            candidateEmail: 'john@example.com',
            status: 'APPLIED',
            appliedAt: '2026-10-14T21:13:26.492Z',
            createdAt: '2026-10-14T21:13:26.492Z',
            updatedAt: '2026-10-14T21:13:26.492Z',
          },
        ],
        pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
      };
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockResponse);

      const result = await adminApi.getApplications({ jobId: 77, status: 'APPLIED', page: 0, size: 20 });
      expect(getSpy).toHaveBeenCalledWith('/admin/applications', {
        params: { jobId: 77, status: 'APPLIED', page: 0, size: 20 },
      });
      expect(result).toEqual(mockResponse);
    });

    it('getApplicationDetail calls GET /admin/applications/:applicationId', async () => {
      const mockDetail: AdminApplicationDetailDto = {
        applicationId: 101,
        jobId: 77,
        jobTitle: 'Junior Engineer',
        companyId: 10,
        companyName: 'Acme Corp',
        candidateId: 15,
        status: 'UNDER_REVIEW',
        appliedAt: '2026-10-14T21:13:26.492Z',
      };
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockDetail);

      const result = await adminApi.getApplicationDetail(101);
      expect(getSpy).toHaveBeenCalledWith('/admin/applications/101');
      expect(result).toEqual(mockDetail);
    });

    it('downloadResume calls GET /admin/applications/:applicationId/resume and returns Blob', async () => {
      const mockBlob = new Blob(['%PDF-1.4 test'], { type: 'application/pdf' });
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockBlob);

      const result = await adminApi.downloadResume(101);
      expect(getSpy).toHaveBeenCalledWith('/admin/applications/101/resume');
      expect(result).toBe(mockBlob);
    });
  });

  describe('Audit Logs API', () => {
    it('getAuditLogs calls GET /admin/audit-logs with filters', async () => {
      const mockResponse: PagedResponse<AuditLogDto> = {
        data: [
          {
            id: 1,
            actorUserId: 2,
            action: 'USER_DEACTIVATED',
            entityType: 'USER',
            entityId: 45,
            metadata: '{"newState":"INACTIVE"}',
            createdAt: '2026-10-14T21:13:26.492Z',
          },
        ],
        pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
      };
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockResponse);

      const result = await adminApi.getAuditLogs({
        action: 'USER_DEACTIVATED',
        entityType: 'USER',
        page: 0,
        size: 20,
      });
      expect(getSpy).toHaveBeenCalledWith('/admin/audit-logs', {
        params: { action: 'USER_DEACTIVATED', entityType: 'USER', page: 0, size: 20 },
      });
      expect(result).toEqual(mockResponse);
    });
  });
});
