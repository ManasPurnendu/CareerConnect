import { describe, it, expect, vi, beforeEach } from 'vitest';
import { recruiterApi } from '../recruiter';
import { apiClient } from '../client';
import type {
  RecruiterCompanyResponseDto,
  RecruiterCompanyUpsertRequest,
  JobDetailDto,
  JobSummaryDto,
  RecruiterJobCreateRequest,
  RecruiterJobUpdateRequest,
  ApplicantSummaryDto,
  ApplicantDetailDto,
  NotificationSummaryResponse,
  NotificationDto,
} from '../../types/api';

describe('recruiterApi Client Layer', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  describe('Company Profile API', () => {
    it('getCompany calls GET /recruiter/company', async () => {
      const mockCompany: RecruiterCompanyResponseDto = {
        id: 10,
        name: 'TechNova Solutions',
        industry: 'Software',
        location: 'Bengaluru',
        website: 'https://technova.example.com',
        companySize: '51-200',
        createdAt: '2026-10-14T21:13:26.492Z',
      };
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockCompany);

      const result = await recruiterApi.getCompany();
      expect(getSpy).toHaveBeenCalledWith('/recruiter/company');
      expect(result).toEqual(mockCompany);
    });

    it('upsertCompany calls PUT /recruiter/company with request payload', async () => {
      const req: RecruiterCompanyUpsertRequest = {
        name: 'TechNova Solutions',
        industry: 'Software',
        location: 'Bengaluru',
        website: 'https://technova.example.com',
        companySize: '51-200',
      };
      const mockUpdated: RecruiterCompanyResponseDto = {
        id: 10,
        name: 'TechNova Solutions',
        industry: 'Software',
        location: 'Bengaluru',
        website: 'https://technova.example.com',
        companySize: '51-200',
        createdAt: '2026-10-14T21:13:26.492Z',
      };
      const putSpy = vi.spyOn(apiClient, 'put').mockResolvedValueOnce(mockUpdated);

      const result = await recruiterApi.upsertCompany(req);
      expect(putSpy).toHaveBeenCalledWith('/recruiter/company', req);
      expect(result).toEqual(mockUpdated);
    });
  });

  describe('Job Management API', () => {
    it('getJobs calls GET /recruiter/jobs with pagination parameters', async () => {
      const mockList: JobSummaryDto[] = [
        {
          id: 1,
          title: 'Java Developer',
          jobType: 'FULL_TIME',
          experienceLevel: 'FRESHER',
          workMode: 'HYBRID',
          location: 'Bengaluru',
          status: 'ACTIVE',
          skills: [{ id: 1, name: 'Java' }],
          createdAt: '2026-10-18T10:00:00Z',
        },
      ];
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce({
        data: mockList,
        items: mockList,
        pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
      });

      const result = await recruiterApi.getJobs(0, 20);
      expect(getSpy).toHaveBeenCalledWith('/recruiter/jobs', {
        params: { page: 0, size: 20 },
      });
      expect(result.data).toEqual(mockList);
    });

    it('getJobById calls GET /recruiter/jobs/{jobId}', async () => {
      const mockJob: JobDetailDto = {
        id: 1,
        title: 'Java Developer',
        company: { id: 10, name: 'TechNova Solutions' },
        jobType: 'FULL_TIME',
        experienceLevel: 'FRESHER',
        workMode: 'HYBRID',
        location: 'Bengaluru',
        status: 'DRAFT',
        skills: [{ id: 1, name: 'Java' }],
        createdAt: '2026-10-18T10:00:00Z',
      };
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockJob);

      const result = await recruiterApi.getJobById(1);
      expect(getSpy).toHaveBeenCalledWith('/recruiter/jobs/1');
      expect(result).toEqual(mockJob);
    });

    it('createJob calls POST /recruiter/jobs with valid payload', async () => {
      const createReq: RecruiterJobCreateRequest = {
        title: 'React Engineer',
        description: 'Frontend role',
        jobType: 'FULL_TIME',
        experienceLevel: 'FRESHER',
        workMode: 'REMOTE',
        applicationDeadline: '2026-12-31',
        requiredSkillIds: [2],
      };
      const mockCreated: JobDetailDto = {
        id: 2,
        title: 'React Engineer',
        company: { id: 10, name: 'TechNova' },
        jobType: 'FULL_TIME',
        experienceLevel: 'FRESHER',
        workMode: 'REMOTE',
        location: 'Remote',
        status: 'DRAFT',
        skills: [{ id: 2, name: 'React' }],
        createdAt: '2026-10-18T10:00:00Z',
      };
      const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce(mockCreated);

      const result = await recruiterApi.createJob(createReq);
      expect(postSpy).toHaveBeenCalledWith('/recruiter/jobs', createReq);
      expect(result).toEqual(mockCreated);
    });

    it('updateJob calls PUT /recruiter/jobs/{jobId} with update payload', async () => {
      const updateReq: RecruiterJobUpdateRequest = {
        title: 'React Engineer Updated',
        description: 'Updated description',
        jobType: 'FULL_TIME',
        experienceLevel: 'FRESHER',
        workMode: 'REMOTE',
        applicationDeadline: '2026-12-31',
        requiredSkillIds: [2],
      };
      const mockUpdated: JobDetailDto = {
        id: 2,
        title: 'React Engineer Updated',
        company: { id: 10, name: 'TechNova' },
        jobType: 'FULL_TIME',
        experienceLevel: 'FRESHER',
        workMode: 'REMOTE',
        location: 'Remote',
        status: 'DRAFT',
        skills: [{ id: 2, name: 'React' }],
        createdAt: '2026-10-18T10:00:00Z',
      };
      const putSpy = vi.spyOn(apiClient, 'put').mockResolvedValueOnce(mockUpdated);

      const result = await recruiterApi.updateJob(2, updateReq);
      expect(putSpy).toHaveBeenCalledWith('/recruiter/jobs/2', updateReq);
      expect(result).toEqual(mockUpdated);
    });

    it('publishJob calls PATCH /recruiter/jobs/{jobId}/publish', async () => {
      const mockPublished: JobDetailDto = {
        id: 2,
        title: 'React Engineer',
        company: { id: 10, name: 'TechNova' },
        jobType: 'FULL_TIME',
        experienceLevel: 'FRESHER',
        workMode: 'REMOTE',
        location: 'Remote',
        status: 'ACTIVE',
        skills: [{ id: 2, name: 'React' }],
        createdAt: '2026-10-18T10:00:00Z',
      };
      const patchSpy = vi.spyOn(apiClient, 'patch').mockResolvedValueOnce(mockPublished);

      const result = await recruiterApi.publishJob(2);
      expect(patchSpy).toHaveBeenCalledWith('/recruiter/jobs/2/publish');
      expect(result.status).toBe('ACTIVE');
    });

    it('closeJob calls PATCH /recruiter/jobs/{jobId}/close', async () => {
      const mockClosed: JobDetailDto = {
        id: 2,
        title: 'React Engineer',
        company: { id: 10, name: 'TechNova' },
        jobType: 'FULL_TIME',
        experienceLevel: 'FRESHER',
        workMode: 'REMOTE',
        location: 'Remote',
        status: 'CLOSED',
        skills: [{ id: 2, name: 'React' }],
        createdAt: '2026-10-18T10:00:00Z',
      };
      const patchSpy = vi.spyOn(apiClient, 'patch').mockResolvedValueOnce(mockClosed);

      const result = await recruiterApi.closeJob(2);
      expect(patchSpy).toHaveBeenCalledWith('/recruiter/jobs/2/close');
      expect(result.status).toBe('CLOSED');
    });

    it('deleteJob calls DELETE /recruiter/jobs/{jobId}', async () => {
      const deleteSpy = vi.spyOn(apiClient, 'delete').mockResolvedValueOnce(null);

      await recruiterApi.deleteJob(2);
      expect(deleteSpy).toHaveBeenCalledWith('/recruiter/jobs/2');
    });
  });

  describe('Applicant & Application Management API', () => {
    it('getJobApplicants calls GET /recruiter/jobs/{jobId}/applications', async () => {
      const mockApplicants: ApplicantSummaryDto[] = [
        {
          applicationId: 101,
          jobId: 1,
          candidateId: 50,
          candidateName: 'Alice Smith',
          candidateEmail: 'alice@example.com',
          status: 'APPLIED',
          appliedAt: '2026-10-18T11:00:00Z',
          matchPercentage: 85,
        },
      ];
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce({
        data: mockApplicants,
        items: mockApplicants,
        pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
      });

      const result = await recruiterApi.getJobApplicants(1, 'APPLIED', 0, 20);
      expect(getSpy).toHaveBeenCalledWith('/recruiter/jobs/1/applications', {
        params: { status: 'APPLIED', page: 0, size: 20 },
      });
      expect(result.data).toEqual(mockApplicants);
    });

    it('getApplicationDetail calls GET /recruiter/applications/{applicationId}', async () => {
      const mockDetail: ApplicantDetailDto = {
        applicationId: 101,
        jobId: 1,
        jobTitle: 'Java Developer',
        candidateId: 50,
        status: 'APPLIED',
        appliedAt: '2026-10-18T11:00:00Z',
        candidateSnapshot: {
          candidateId: 50,
          fullName: 'Alice Smith',
          email: 'alice@example.com',
          skills: ['Java', 'SQL'],
        },
        match: {
          percentage: 100,
          matchedSkills: ['Java'],
          missingSkills: [],
          additionalSkills: ['SQL'],
        },
      };
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockDetail);

      const result = await recruiterApi.getApplicationDetail(101);
      expect(getSpy).toHaveBeenCalledWith('/recruiter/applications/101');
      expect(result).toEqual(mockDetail);
    });

    it('updateApplicationStatus calls PATCH /recruiter/applications/{applicationId}/status', async () => {
      const mockUpdated: ApplicantDetailDto = {
        applicationId: 101,
        jobId: 1,
        jobTitle: 'Java Developer',
        candidateId: 50,
        status: 'SHORTLISTED',
        appliedAt: '2026-10-18T11:00:00Z',
      };
      const patchSpy = vi.spyOn(apiClient, 'patch').mockResolvedValueOnce(mockUpdated);

      const result = await recruiterApi.updateApplicationStatus(101, 'SHORTLISTED', 'Promising candidate');
      expect(patchSpy).toHaveBeenCalledWith('/recruiter/applications/101/status', {
        status: 'SHORTLISTED',
        note: 'Promising candidate',
      });
      expect(result.status).toBe('SHORTLISTED');
    });

    it('downloadResume calls GET /recruiter/applications/{applicationId}/resume and returns blob', async () => {
      const mockBlob = new Blob(['sample pdf content'], { type: 'application/pdf' });
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockBlob);

      const result = await recruiterApi.downloadResume(101);
      expect(getSpy).toHaveBeenCalledWith('/recruiter/applications/101/resume');
      expect(result).toEqual(mockBlob);
    });
  });

  describe('Notifications API', () => {
    it('getNotifications calls GET /notifications with pagination', async () => {
      const mockNotifs: NotificationSummaryResponse = {
        items: [
          {
            id: 1,
            type: 'APPLICATION_STATUS_CHANGED',
            title: 'Application Update',
            body: 'Status changed',
            read: false,
            createdAt: '2026-10-18T12:00:00Z',
          },
        ],
        pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
        unreadCount: 1,
      };
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockNotifs);

      const result = await recruiterApi.getNotifications(0, 20, true);
      expect(getSpy).toHaveBeenCalledWith('/notifications', {
        params: { page: 0, size: 20, unread: true },
      });
      expect(result.unreadCount).toBe(1);
    });

    it('markNotificationRead calls PATCH /notifications/{id}/read', async () => {
      const mockNotif: NotificationDto = {
        id: 1,
        type: 'APPLICATION_STATUS_CHANGED',
        title: 'Application Update',
        body: 'Status changed',
        read: true,
        createdAt: '2026-10-18T12:00:00Z',
      };
      const patchSpy = vi.spyOn(apiClient, 'patch').mockResolvedValueOnce(mockNotif);

      const result = await recruiterApi.markNotificationRead(1);
      expect(patchSpy).toHaveBeenCalledWith('/notifications/1/read');
      expect(result.read).toBe(true);
    });

    it('markAllNotificationsRead calls PATCH /notifications/read-all', async () => {
      const patchSpy = vi.spyOn(apiClient, 'patch').mockResolvedValueOnce({ markedReadCount: 3 });

      const result = await recruiterApi.markAllNotificationsRead();
      expect(patchSpy).toHaveBeenCalledWith('/notifications/read-all');
      expect(result.markedReadCount).toBe(3);
    });
  });
});
