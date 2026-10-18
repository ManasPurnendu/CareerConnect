import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { CandidateDashboardPage } from '../CandidateDashboardPage';
import { candidateApi } from '../../../api/candidate';
import { jobsApi } from '../../../api/jobs';
import { AuthContext, type AuthContextType } from '../../../auth/AuthContext';
import type {
  CandidateProfileResponse,
  ProfileReadinessDto,
  ResumeResponse,
  ApplicationResponseDto,
  NotificationSummaryResponse,
  PagedResponse,
  JobSummaryDto,
  AuthUser,
} from '../../../types/api';

const mockProfile: CandidateProfileResponse = {
  id: 1,
  fullName: 'Alice Candidate',
  headline: 'Full Stack Engineer',
};

const mockReadiness: ProfileReadinessDto = {
  readyToApply: true,
  profileComplete: true,
  hasEducation: true,
  hasSkills: true,
  hasResume: true,
  missingRequirements: [],
};

const mockResume: ResumeResponse = {
  id: 100,
  originalFilename: 'resume.pdf',
  contentType: 'application/pdf',
  sizeBytes: 1024,
  isCurrent: true,
  createdAt: '2026-10-18T10:00:00Z',
};

const mockApplications: PagedResponse<ApplicationResponseDto> = {
  data: [
    {
      applicationId: 50,
      jobId: 1,
      candidateId: 1,
      jobTitle: 'Backend Engineer',
      companyName: 'Acme Corp',
      status: 'APPLIED',
      appliedAt: '2026-10-18T10:00:00Z',
    },
  ],
  pagination: {
    page: 0,
    size: 5,
    totalElements: 1,
    totalPages: 1,
  },
};

const mockNotifications: NotificationSummaryResponse = {
  items: [
    {
      id: 1,
      type: 'APPLICATION_STATUS_CHANGED',
      title: 'Update on application',
      body: 'Your application has been received.',
      createdAt: '2026-10-18T10:00:00Z',
      read: false,
    },
  ],
  pagination: {
    page: 0,
    size: 5,
    totalElements: 1,
    totalPages: 1,
  },
  unreadCount: 1,
};

const mockJobs: PagedResponse<JobSummaryDto> = {
  data: [
    {
      id: 1,
      title: 'Backend Engineer',
      company: {
        id: 10,
        name: 'Acme Corp',
        industry: 'Tech',
        logoUrl: null,
      },
      location: 'San Francisco, CA',
      jobType: 'FULL_TIME',
      experienceLevel: 'FRESHER',
      workMode: 'REMOTE',
      skills: [{ id: 1, name: 'Java' }],
      status: 'ACTIVE',
      createdAt: '2026-10-18T10:00:00Z',
    },
  ],
  pagination: {
    page: 0,
    size: 4,
    totalElements: 1,
    totalPages: 1,
  },
};

const renderDashboard = () => {
  const mockUser: AuthUser = {
    id: 1,
    email: 'alice@example.com',
    role: 'CANDIDATE',
    fullName: 'Alice Candidate',
    isActive: true,
  };

  const mockContext: AuthContextType = {
    user: mockUser,
    token: 'fake-jwt',
    isAuthenticated: true,
    isLoading: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    setUser: vi.fn(),
    refreshCurrentUser: vi.fn(),
  };

  return render(
    <AuthContext.Provider value={mockContext}>
      <MemoryRouter>
        <CandidateDashboardPage />
      </MemoryRouter>
    </AuthContext.Provider>
  );
};

describe('CandidateDashboardPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(candidateApi, 'getProfile').mockResolvedValue(mockProfile);
    vi.spyOn(candidateApi, 'getReadiness').mockResolvedValue(mockReadiness);
    vi.spyOn(candidateApi, 'getCurrentResume').mockResolvedValue(mockResume);
    vi.spyOn(candidateApi, 'getApplications').mockResolvedValue(mockApplications);
    vi.spyOn(candidateApi, 'getNotifications').mockResolvedValue(mockNotifications);
    vi.spyOn(jobsApi, 'getPublicJobs').mockResolvedValue(mockJobs);
    vi.spyOn(candidateApi, 'getJobMatch').mockResolvedValue({
      matchedSkills: ['Java'],
      missingSkills: [],
      additionalSkills: [],
      percentage: 100,
    });
  });

  it('renders candidate greeting, readiness card, metrics, and recent applications', async () => {
    renderDashboard();

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /welcome back/i })).toBeInTheDocument();
    });
    expect(screen.getByText(/Ready to Apply/i)).toBeInTheDocument();
    expect(screen.getAllByText('Backend Engineer').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Acme Corp').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Applied').length).toBeGreaterThan(0);
  });

  it('renders quick navigation links to profile, applications, and discover jobs', async () => {
    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText(/Recent Applications/i)).toBeInTheDocument();
      expect(screen.getByText(/Explore Jobs/i)).toBeInTheDocument();
    });
  });
});
