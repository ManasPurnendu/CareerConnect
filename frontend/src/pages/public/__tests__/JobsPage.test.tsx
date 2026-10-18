import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { JobsPage } from '../JobsPage';
import { jobsApi } from '../../../api/jobs';
import type { JobSummaryDto, PagedResponse } from '../../../types/api';

const mockJobs: JobSummaryDto[] = [
  {
    id: 1,
    title: 'Junior React Engineer',
    company: {
      id: 101,
      name: 'Alpha Systems',
      industry: 'Software',
      logoUrl: null,
    },
    location: 'Bangalore, India',
    employmentType: 'FULL_TIME',
    experienceLevel: 'ZERO_TO_ONE_YEARS',
    workMode: 'REMOTE',
    skills: ['React', 'TypeScript', 'Tailwind CSS'],
    salaryMinLpa: 6,
    salaryMaxLpa: 9,
    status: 'ACTIVE',
    applicationDeadline: '2026-11-30T23:59:59Z',
    createdAt: '2026-10-18T10:00:00Z',
  },
];

const mockPagedResponse: PagedResponse<JobSummaryDto> = {
  data: mockJobs,
  pagination: {
    page: 0,
    size: 9,
    totalElements: 1,
    totalPages: 1,
  },
};

import { AuthContext, type AuthContextType } from '../../../auth/AuthContext';

const renderJobsPage = (initialEntry = '/jobs') => {
  const mockContext: AuthContextType = {
    user: null,
    token: null,
    isAuthenticated: false,
    isLoading: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    setUser: vi.fn(),
    refreshCurrentUser: vi.fn(),
  };

  return render(
    <AuthContext.Provider value={mockContext}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <JobsPage />
      </MemoryRouter>
    </AuthContext.Provider>
  );
};

describe('JobsPage Public Discovery', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders loading state initially and then displays job cards', async () => {
    vi.spyOn(jobsApi, 'getPublicJobs').mockResolvedValue(mockPagedResponse);

    renderJobsPage('/jobs');

    expect(screen.getByText(/explore early-career opportunities/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Junior React Engineer')).toBeInTheDocument();
      expect(screen.getByText('Alpha Systems')).toBeInTheDocument();
      expect(screen.getByText('React')).toBeInTheDocument();
    });
  });

  it('displays empty state when no jobs match the search criteria', async () => {
    vi.spyOn(jobsApi, 'getPublicJobs').mockResolvedValue({
      data: [],
      pagination: { page: 0, size: 9, totalElements: 0, totalPages: 0 },
    });

    renderJobsPage('/jobs');

    await waitFor(() => {
      expect(screen.getByText(/no matching roles found/i)).toBeInTheDocument();
    });
  });

  it('displays error state when backend returns 400 or 500', async () => {
    vi.spyOn(jobsApi, 'getPublicJobs').mockRejectedValue(new Error('Backend service unavailable'));

    renderJobsPage('/jobs');

    await waitFor(() => {
      expect(screen.getByText(/unable to load jobs/i)).toBeInTheDocument();
      expect(screen.getByText(/backend service unavailable/i)).toBeInTheDocument();
    });
  });

  it('triggers search with query and filters on submit', async () => {
    const getJobsSpy = vi.spyOn(jobsApi, 'getPublicJobs').mockResolvedValue(mockPagedResponse);

    renderJobsPage('/jobs');

    await waitFor(() => {
      expect(screen.getByText('Junior React Engineer')).toBeInTheDocument();
    });

    const keywordInput = screen.getByPlaceholderText(/search by title, skill, or keyword/i);
    fireEvent.change(keywordInput, { target: { value: 'React' } });

    fireEvent.click(screen.getByRole('button', { name: /^search$/i }));

    await waitFor(() => {
      expect(getJobsSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          keyword: 'React',
        })
      );
    });
  });

  it('resets all filters when clicking Reset All', async () => {
    const getJobsSpy = vi.spyOn(jobsApi, 'getPublicJobs').mockResolvedValue(mockPagedResponse);

    renderJobsPage('/jobs?keyword=Python');

    await waitFor(() => {
      expect(screen.getByText('Junior React Engineer')).toBeInTheDocument();
    });

    const resetButton = screen.getByRole('button', { name: /reset all/i });
    fireEvent.click(resetButton);

    await waitFor(() => {
      expect(getJobsSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          keyword: undefined,
          location: undefined,
          employmentType: undefined,
          workMode: undefined,
          experienceLevel: undefined,
        })
      );
    });
  });
});
