import { describe, it, expect, vi, beforeEach } from 'vitest';
import { candidateApi } from '../candidate';
import { apiClient } from '../client';
import type {
  CandidateProfileResponse,
  ProfileReadinessDto,
  EducationDto,
  CandidateSkillDto,
  ProjectDto,
  ExperienceDto,
  ResumeResponse,
  JobMatchDto,
  ApplicationResponseDto,
  NotificationSummaryResponse,
} from '../../types/api';

describe('candidateApi Client Layer', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  describe('Profile & Readiness API', () => {
    it('getProfile calls GET /candidate/profile', async () => {
      const mockProfile: CandidateProfileResponse = {
        id: 1,
        fullName: 'Jane Doe',
        headline: 'Software Engineer',
        location: 'Remote',
        summary: 'Passionate developer',
      };
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockProfile);

      const result = await candidateApi.getProfile();
      expect(getSpy).toHaveBeenCalledWith('/candidate/profile');
      expect(result).toEqual(mockProfile);
    });

    it('updateProfile calls PUT /candidate/profile with payload', async () => {
      const mockUpdated: CandidateProfileResponse = {
        id: 1,
        fullName: 'Jane Doe Updated',
        headline: 'Lead Engineer',
      };
      const putSpy = vi.spyOn(apiClient, 'put').mockResolvedValueOnce(mockUpdated);

      const payload = { fullName: 'Jane Doe Updated', headline: 'Lead Engineer' };
      const result = await candidateApi.updateProfile(payload);
      expect(putSpy).toHaveBeenCalledWith('/candidate/profile', payload);
      expect(result).toEqual(mockUpdated);
    });

    it('getReadiness calls GET /candidate/readiness', async () => {
      const mockReadiness: ProfileReadinessDto = {
        readyToApply: true,
        profileComplete: true,
        hasEducation: true,
        hasSkills: true,
        hasResume: true,
        missingRequirements: [],
      };
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockReadiness);

      const result = await candidateApi.getReadiness();
      expect(getSpy).toHaveBeenCalledWith('/candidate/profile/readiness');
      expect(result).toEqual(mockReadiness);
    });
  });

  describe('Education CRUD API', () => {
    it('getEducation calls GET /candidate/education', async () => {
      const mockEducation: EducationDto[] = [
        {
          id: 10,
          candidateId: 1,
          institution: 'MIT',
          degree: 'B.S.',
          fieldOfStudy: 'Computer Science',
          startYear: 2018,
          endYear: 2022,
          sortOrder: 1,
        },
      ];
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockEducation);

      const result = await candidateApi.getEducation();
      expect(getSpy).toHaveBeenCalledWith('/candidate/education');
      expect(result).toEqual(mockEducation);
    });

    it('addEducation calls POST /candidate/education', async () => {
      const payload = {
        institution: 'Stanford',
        degree: 'M.S.',
        fieldOfStudy: 'AI',
        startYear: 2022,
      };
      const mockCreated = { id: 11, candidateId: 1, ...payload, sortOrder: 2 };
      const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce(mockCreated);

      const result = await candidateApi.addEducation(payload);
      expect(postSpy).toHaveBeenCalledWith('/candidate/education', payload);
      expect(result).toEqual(mockCreated);
    });

    it('updateEducation calls PUT /candidate/education/{id}', async () => {
      const payload = {
        institution: 'MIT',
        degree: 'B.S.',
        fieldOfStudy: 'CS & Math',
        startYear: 2018,
        endYear: 2022,
      };
      const mockUpdated = { id: 10, candidateId: 1, ...payload, sortOrder: 1 };
      const putSpy = vi.spyOn(apiClient, 'put').mockResolvedValueOnce(mockUpdated);

      const result = await candidateApi.updateEducation(10, payload);
      expect(putSpy).toHaveBeenCalledWith('/candidate/education/10', payload);
      expect(result).toEqual(mockUpdated);
    });

    it('deleteEducation calls DELETE /candidate/education/{id}', async () => {
      const deleteSpy = vi.spyOn(apiClient, 'delete').mockResolvedValueOnce(undefined);

      await candidateApi.deleteEducation(10);
      expect(deleteSpy).toHaveBeenCalledWith('/candidate/education/10');
    });
  });

  describe('Skills API', () => {
    it('getSkills calls GET /candidate/skills', async () => {
      const mockSkills: CandidateSkillDto[] = [
        { id: 1, name: 'TypeScript' },
        { id: 2, name: 'Java' },
      ];
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockSkills);

      const result = await candidateApi.getSkills();
      expect(getSpy).toHaveBeenCalledWith('/candidate/skills');
      expect(result).toEqual(mockSkills);
    });

    it('addSkill calls POST /candidate/skills', async () => {
      const mockSkill = { id: 3, name: 'Spring Boot' };
      const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce(mockSkill);

      const result = await candidateApi.addSkill({ name: 'Spring Boot' });
      expect(postSpy).toHaveBeenCalledWith('/candidate/skills', { name: 'Spring Boot' });
      expect(result).toEqual(mockSkill);
    });

    it('removeSkill calls DELETE /candidate/skills/{skillId}', async () => {
      const deleteSpy = vi.spyOn(apiClient, 'delete').mockResolvedValueOnce(undefined);

      await candidateApi.removeSkill(3);
      expect(deleteSpy).toHaveBeenCalledWith('/candidate/skills/3');
    });
  });

  describe('Projects CRUD API', () => {
    it('getProjects calls GET /candidate/projects', async () => {
      const mockProjects: ProjectDto[] = [
        {
          id: 5,
          candidateId: 1,
          title: 'CareerConnect',
          description: 'Job portal',
          sortOrder: 1,
        },
      ];
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockProjects);

      const result = await candidateApi.getProjects();
      expect(getSpy).toHaveBeenCalledWith('/candidate/projects');
      expect(result).toEqual(mockProjects);
    });

    it('addProject calls POST /candidate/projects', async () => {
      const payload = { title: 'Project X', description: 'Desc' };
      const mockCreated = { id: 6, candidateId: 1, ...payload, sortOrder: 2 };
      const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce(mockCreated);

      const result = await candidateApi.addProject(payload);
      expect(postSpy).toHaveBeenCalledWith('/candidate/projects', payload);
      expect(result).toEqual(mockCreated);
    });

    it('updateProject calls PUT /candidate/projects/{id}', async () => {
      const payload = { title: 'Project X Updated', description: 'New Desc' };
      const mockUpdated = { id: 6, candidateId: 1, ...payload, sortOrder: 2 };
      const putSpy = vi.spyOn(apiClient, 'put').mockResolvedValueOnce(mockUpdated);

      const result = await candidateApi.updateProject(6, payload);
      expect(putSpy).toHaveBeenCalledWith('/candidate/projects/6', payload);
      expect(result).toEqual(mockUpdated);
    });

    it('deleteProject calls DELETE /candidate/projects/{id}', async () => {
      const deleteSpy = vi.spyOn(apiClient, 'delete').mockResolvedValueOnce(undefined);

      await candidateApi.deleteProject(6);
      expect(deleteSpy).toHaveBeenCalledWith('/candidate/projects/6');
    });
  });

  describe('Experience CRUD API', () => {
    it('getExperiences calls GET /candidate/experience', async () => {
      const mockExp: ExperienceDto[] = [
        {
          id: 8,
          candidateId: 1,
          companyName: 'Acme',
          roleTitle: 'Developer',
          startDate: '2023-01-01',
          isCurrent: true,
          sortOrder: 1,
        },
      ];
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockExp);

      const result = await candidateApi.getExperiences();
      expect(getSpy).toHaveBeenCalledWith('/candidate/experience');
      expect(result).toEqual(mockExp);
    });

    it('addExperience calls POST /candidate/experience', async () => {
      const payload = {
        companyName: 'Beta Corp',
        roleTitle: 'Intern',
        startDate: '2022-01-01',
        isCurrent: false,
      };
      const mockCreated = { id: 9, candidateId: 1, ...payload, sortOrder: 2 };
      const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce(mockCreated);

      const result = await candidateApi.addExperience(payload);
      expect(postSpy).toHaveBeenCalledWith('/candidate/experience', payload);
      expect(result).toEqual(mockCreated);
    });

    it('updateExperience calls PUT /candidate/experience/{id}', async () => {
      const payload = {
        companyName: 'Beta Corp',
        roleTitle: 'Senior Intern',
        startDate: '2022-01-01',
        isCurrent: false,
      };
      const mockUpdated = { id: 9, candidateId: 1, ...payload, sortOrder: 2 };
      const putSpy = vi.spyOn(apiClient, 'put').mockResolvedValueOnce(mockUpdated);

      const result = await candidateApi.updateExperience(9, payload);
      expect(putSpy).toHaveBeenCalledWith('/candidate/experience/9', payload);
      expect(result).toEqual(mockUpdated);
    });

    it('deleteExperience calls DELETE /candidate/experience/{id}', async () => {
      const deleteSpy = vi.spyOn(apiClient, 'delete').mockResolvedValueOnce(undefined);

      await candidateApi.deleteExperience(9);
      expect(deleteSpy).toHaveBeenCalledWith('/candidate/experience/9');
    });
  });

  describe('Resume Management API', () => {
    it('getCurrentResume calls GET /candidate/resume', async () => {
      const mockResume: ResumeResponse = {
        id: 20,
        originalFilename: 'resume.pdf',
        contentType: 'application/pdf',
        sizeBytes: 1024,
        isCurrent: true,
        createdAt: '2026-10-18T10:00:00Z',
      };
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockResume);

      const result = await candidateApi.getCurrentResume();
      expect(getSpy).toHaveBeenCalledWith('/candidate/resume');
      expect(result).toEqual(mockResume);
    });

    it('uploadResume calls POST /candidate/resume with FormData', async () => {
      const mockResume: ResumeResponse = {
        id: 21,
        originalFilename: 'new_resume.pdf',
        contentType: 'application/pdf',
        sizeBytes: 2048,
        isCurrent: true,
        createdAt: '2026-10-18T12:00:00Z',
      };
      const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce(mockResume);

      const file = new File(['dummy content'], 'new_resume.pdf', { type: 'application/pdf' });
      const result = await candidateApi.uploadResume(file);

      expect(postSpy).toHaveBeenCalled();
      const [url, formData] = postSpy.mock.calls[0];
      expect(url).toBe('/candidate/resume');
      expect(formData).toBeInstanceOf(FormData);
      expect(result).toEqual(mockResume);
    });

    it('deleteResume calls DELETE /candidate/resume', async () => {
      const deleteSpy = vi.spyOn(apiClient, 'delete').mockResolvedValueOnce(undefined);

      await candidateApi.deleteResume();
      expect(deleteSpy).toHaveBeenCalledWith('/candidate/resume');
    });
  });

  describe('Applications & Match API', () => {
    it('applyForJob calls POST /jobs/{jobId}/applications', async () => {
      const mockApp: ApplicationResponseDto = {
        applicationId: 101,
        jobId: 1,
        candidateId: 1,
        status: 'APPLIED',
        coverLetter: 'Excited to apply',
        appliedAt: '2026-10-18T12:00:00Z',
      };
      const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce(mockApp);

      const result = await candidateApi.applyForJob(1, { coverLetter: 'Excited to apply' });
      expect(postSpy).toHaveBeenCalledWith('/jobs/1/applications', { coverLetter: 'Excited to apply' });
      expect(result).toEqual(mockApp);
    });

    it('withdrawApplication strictly calls PATCH /candidate/applications/{id}/withdraw', async () => {
      const mockWithdrawn: ApplicationResponseDto = {
        applicationId: 101,
        jobId: 1,
        candidateId: 1,
        status: 'WITHDRAWN',
        appliedAt: '2026-10-18T12:00:00Z',
      };
      const patchSpy = vi.spyOn(apiClient, 'patch').mockResolvedValueOnce(mockWithdrawn);

      const result = await candidateApi.withdrawApplication(101);
      expect(patchSpy).toHaveBeenCalledWith('/candidate/applications/101/withdraw');
      expect(result).toEqual(mockWithdrawn);
    });

    it('getJobMatch calls GET /jobs/{jobId}/match', async () => {
      const mockMatch: JobMatchDto = {
        matchedSkills: ['Java', 'Spring Boot'],
        missingSkills: ['Kubernetes'],
        additionalSkills: ['React'],
        percentage: 66.7,
      };
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockMatch);

      const result = await candidateApi.getJobMatch(1);
      expect(getSpy).toHaveBeenCalledWith('/jobs/1/match');
      expect(result).toEqual(mockMatch);
    });
  });

  describe('Notifications API', () => {
    it('getNotifications calls GET /candidate/notifications with pagination and unreadOnly', async () => {
      const mockSummary: NotificationSummaryResponse = {
        items: [
          {
            id: 1,
            type: 'APPLICATION_STATUS_CHANGED',
            title: 'Status Update',
            body: 'Moved to REVIEWING',
            createdAt: '2026-10-18T10:00:00Z',
            read: false,
          },
        ],
        pagination: {
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
        },
        unreadCount: 1,
      };
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce(mockSummary);

      const result = await candidateApi.getNotifications(0, 10, true);
      expect(getSpy).toHaveBeenCalledWith('/notifications', {
        params: { page: 0, size: 10, unread: true },
      });
      expect(result).toEqual(mockSummary);
    });

    it('markNotificationAsRead calls PATCH /notifications/{id}/read', async () => {
      const patchSpy = vi.spyOn(apiClient, 'patch').mockResolvedValueOnce(undefined);

      await candidateApi.markNotificationAsRead(1);
      expect(patchSpy).toHaveBeenCalledWith('/notifications/1/read');
    });

    it('markAllNotificationsAsRead calls PATCH /notifications/read-all', async () => {
      const patchSpy = vi.spyOn(apiClient, 'patch').mockResolvedValueOnce(undefined);

      await candidateApi.markAllNotificationsAsRead();
      expect(patchSpy).toHaveBeenCalledWith('/notifications/read-all');
    });
  });
});
