import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { JobDetailPage } from '../JobDetailPage';
import { jobsApi } from '../../../api/jobs';
import { AuthContext, type AuthContextType } from '../../../auth/AuthContext';
import type { JobDetailDto, AuthUser } from '../../../types/api';

const mockJobDetail: JobDetailDto = {
  id: 1,
  title: 'Full Stack Trainee',
  description: 'Join our early-career rotation program building modern cloud microservices and reactive frontends.',
  company: {
    id: 101,
    name: 'InnoTech Labs',
    industry: 'Enterprise Software',
    logoUrl: null,
    website: 'https://innotech.example.com',
    location: 'Hyderabad, India',
    description: 'Pioneering next-gen enterprise tools.',
  },
  location: 'Hyderabad, India',
  jobType: 'TRAINEE',
  employmentType: 'TRAINEE',
  experienceLevel: 'FRESHER',
  workMode: 'HYBRID',
  skills: [
    { id: 1, name: 'Java' },
    { id: 2, name: 'Spring Boot' },
    { id: 3, name: 'React' },
  ],
  salaryMinLpa: 5,
  salaryMaxLpa: 7,
  status: 'ACTIVE',
  applicationDeadline: '2026-12-31T23:59:59Z',
  createdAt: '2026-10-18T10:00:00Z',
  applicationCount: 4,
};

const renderJobDetailPage = (user: AuthUser | null = null, jobId = '1') => {
  const mockContext: AuthContextType = {
    user,
    token: user ? 'token-123' : null,
    isAuthenticated: !!user,
    isLoading: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    setUser: vi.fn(),
    refreshCurrentUser: vi.fn(),
  };

  return render(
    <AuthContext.Provider value={mockContext}>
      <MemoryRouter initialEntries={[`/jobs/${jobId}`]}>
        <Routes>
          <Route path="/jobs/:jobId" element={<JobDetailPage />} />
          <Route path="/login" element={<div>Login Page Target</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
};

describe('JobDetailPage Public Discovery', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders job details accurately when job exists', async () => {
    vi.spyOn(jobsApi, 'getJobById').mockResolvedValueOnce(mockJobDetail);

    renderJobDetailPage(null, '1');

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Full Stack Trainee' })).toBeInTheDocument();
      expect(screen.getByText('InnoTech Labs')).toBeInTheDocument();
      expect(screen.getByText(/join our early-career rotation program/i)).toBeInTheDocument();
      expect(screen.getByText('Java')).toBeInTheDocument();
      expect(screen.getByText('Spring Boot')).toBeInTheDocument();
      expect(screen.getByText('React')).toBeInTheDocument();
    });
  });

  it('directs guest users to login with next parameter when unauthenticated', async () => {
    vi.spyOn(jobsApi, 'getJobById').mockResolvedValue(mockJobDetail);

    renderJobDetailPage(null, '1');

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Full Stack Trainee' })).toBeInTheDocument();
    });

    const applyButton = screen.getAllByRole('button', { name: /apply/i })[0];
    fireEvent.click(applyButton);

    await waitFor(() => {
      expect(screen.getByText('Login Page Target')).toBeInTheDocument();
    });
  });

  it('displays candidate application action when authenticated as CANDIDATE', async () => {
    const candidateUser: AuthUser = {
      id: 5,
      email: 'cand@test.com',
      role: 'CANDIDATE',
      fullName: 'Alex Candidate',
      isActive: true,
    };
    vi.spyOn(jobsApi, 'getJobById').mockResolvedValueOnce(mockJobDetail);

    renderJobDetailPage(candidateUser, '1');

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Full Stack Trainee' })).toBeInTheDocument();
    });

    expect(screen.getByRole('button', { name: /apply for role/i })).toBeInTheDocument();
  });

  it('displays error and back button when job is not found', async () => {
    vi.spyOn(jobsApi, 'getJobById').mockRejectedValueOnce(new Error('Job not found with id 9999'));

    renderJobDetailPage(null, '9999');

    await waitFor(() => {
      expect(screen.getByText(/job posting unavailable/i)).toBeInTheDocument();
      expect(screen.getByText(/job not found with id 9999/i)).toBeInTheDocument();
    });
  });
});
