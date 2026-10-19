import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { RecruiterDashboardPage } from '../RecruiterDashboardPage';
import { recruiterApi } from '../../../api/recruiter';
import { AuthContext, type AuthContextType } from '../../../auth/AuthContext';
import type {
  RecruiterCompanyResponseDto,
  JobSummaryDto,
  ApplicantSummaryDto,
  NotificationSummaryResponse,
  PagedResponse,
  AuthUser,
} from '../../../types/api';

const mockCompany: RecruiterCompanyResponseDto = {
  id: 10,
  name: 'Nexus Dynamics',
  description: 'Enterprise cloud infrastructure provider.',
  website: 'https://nexusdynamics.com',
  location: 'San Francisco, CA',
  industry: 'Software',
  createdAt: '2026-10-18T10:00:00Z',
  updatedAt: '2026-10-18T10:00:00Z',
};

const jobItems: JobSummaryDto[] = [
  {
    id: 1,
    title: 'Senior Systems Engineer',
    company: { id: 10, name: 'Nexus Dynamics' },
    location: 'San Francisco, CA',
    jobType: 'FULL_TIME',
    experienceLevel: 'ONE_TO_TWO_YEARS',
    workMode: 'HYBRID',
    status: 'ACTIVE',
    applicantCount: 3,
    skills: [{ id: 1, name: 'Java' }],
    createdAt: '2026-10-18T10:00:00Z',
  },
  {
    id: 2,
    title: 'Junior Cloud Developer',
    company: { id: 10, name: 'Nexus Dynamics' },
    location: 'Remote',
    jobType: 'FULL_TIME',
    experienceLevel: 'FRESHER',
    workMode: 'REMOTE',
    status: 'DRAFT',
    applicantCount: 0,
    skills: [{ id: 2, name: 'React' }],
    createdAt: '2026-10-18T11:00:00Z',
  },
];

const mockJobs: PagedResponse<JobSummaryDto> = {
  data: jobItems,
  items: jobItems,
  pagination: {
    page: 0,
    size: 50,
    totalElements: 2,
    totalPages: 1,
  },
};

const applicantItems: ApplicantSummaryDto[] = [
  {
    applicationId: 101,
    jobId: 1,
    candidateId: 201,
    candidateName: 'Jordan Smith',
    candidateEmail: 'jordan@example.com',
    status: 'APPLIED',
    appliedAt: '2026-10-18T12:00:00Z',
    matchPercentage: 85,
  },
];

const mockApplicants: PagedResponse<ApplicantSummaryDto> = {
  data: applicantItems,
  items: applicantItems,
  pagination: {
    page: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
  },
};

const mockNotifications: NotificationSummaryResponse = {
  items: [
    {
      id: 1,
      type: 'APPLICATION_STATUS_CHANGED',
      title: 'New Applicant',
      body: 'Jordan Smith applied to Senior Systems Engineer.',
      createdAt: '2026-10-18T12:00:00Z',
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

const renderDashboard = (userOverrides?: Partial<AuthUser>) => {
  const mockUser: AuthUser = {
    id: 5,
    email: 'recruiter@nexus.com',
    role: 'RECRUITER',
    fullName: 'Robert Sterling',
    isActive: true,
    ...userOverrides,
  };

  const mockContext: AuthContextType = {
    user: mockUser,
    token: 'mock-token',
    isAuthenticated: true,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
    register: vi.fn(),
    setUser: vi.fn(),
    refreshCurrentUser: vi.fn(),
  };

  return render(
    <AuthContext.Provider value={mockContext}>
      <MemoryRouter>
        <RecruiterDashboardPage />
      </MemoryRouter>
    </AuthContext.Provider>
  );
};

describe('RecruiterDashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders loading skeleton state initially', () => {
    vi.spyOn(recruiterApi, 'getCompany').mockReturnValue(new Promise(() => {}));
    vi.spyOn(recruiterApi, 'getJobs').mockReturnValue(new Promise(() => {}));
    vi.spyOn(recruiterApi, 'getNotifications').mockReturnValue(new Promise(() => {}));

    const { container } = renderDashboard();
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('renders recruiter dashboard with company, job metrics, and applicants', async () => {
    vi.spyOn(recruiterApi, 'getCompany').mockResolvedValue(mockCompany);
    vi.spyOn(recruiterApi, 'getJobs').mockResolvedValue(mockJobs);
    vi.spyOn(recruiterApi, 'getJobApplicants').mockResolvedValue(mockApplicants);
    vi.spyOn(recruiterApi, 'getNotifications').mockResolvedValue(mockNotifications);

    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText('Robert Sterling')).toBeInTheDocument();
      expect(screen.getByText('Nexus Dynamics')).toBeInTheDocument();
      expect(screen.getByText('Senior Systems Engineer')).toBeInTheDocument();
      expect(screen.getByText(/Jordan Smith/i)).toBeInTheDocument();
    });

    // Metric cards
    expect(screen.getByText('Active Openings')).toBeInTheDocument();
    expect(screen.getByText('Draft Openings')).toBeInTheDocument();
    expect(screen.getByText('Total Applicants')).toBeInTheDocument();
  });

  it('displays warning banner when recruiter has no configured company', async () => {
    vi.spyOn(recruiterApi, 'getCompany').mockRejectedValue({
      status: 404,
      message: 'Company not found',
    });
    vi.spyOn(recruiterApi, 'getJobs').mockResolvedValue({
      data: [],
      items: [],
      pagination: { page: 0, size: 50, totalElements: 0, totalPages: 0 },
    });
    vi.spyOn(recruiterApi, 'getNotifications').mockResolvedValue({
      items: [],
      pagination: { page: 0, size: 5, totalElements: 0, totalPages: 0 },
      unreadCount: 0,
    });

    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText('Set Up Your Company Profile')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Configure Profile/i })).toBeInTheDocument();
    });
  });
});
