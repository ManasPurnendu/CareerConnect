import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { RecruiterApplicationsPage } from '../RecruiterApplicationsPage';
import { recruiterApi } from '../../../api/recruiter';
import type { JobSummaryDto, ApplicantSummaryDto, PagedResponse } from '../../../types/api';

const jobItems: JobSummaryDto[] = [
  {
    id: 1,
    title: 'Full Stack Engineer',
    company: { id: 10, name: 'Nexus Dynamics' },
    location: 'San Francisco, CA',
    jobType: 'FULL_TIME',
    experienceLevel: 'ZERO_TO_ONE_YEARS',
    workMode: 'HYBRID',
    status: 'ACTIVE',
    applicantCount: 2,
    skills: [{ id: 1, name: 'Java' }],
    createdAt: '2026-10-18T10:00:00Z',
  },
];

const mockJobs: PagedResponse<JobSummaryDto> = {
  data: jobItems,
  items: jobItems,
  pagination: { page: 0, size: 50, totalElements: 1, totalPages: 1 },
};

const applicantItems: ApplicantSummaryDto[] = [
  {
    applicationId: 501,
    jobId: 1,
    candidateId: 201,
    candidateName: 'Samantha Ray',
    candidateEmail: 'samantha@example.com',
    candidateHeadline: 'Systems Engineer',
    status: 'APPLIED',
    appliedAt: '2026-10-18T14:00:00Z',
    matchPercentage: 92,
  },
  {
    applicationId: 502,
    jobId: 1,
    candidateId: 202,
    candidateName: 'Michael Chang',
    candidateEmail: 'michael@example.com',
    candidateHeadline: 'Cloud Developer',
    status: 'UNDER_REVIEW',
    appliedAt: '2026-10-18T15:00:00Z',
    matchPercentage: 65,
  },
];

const mockApplicants: PagedResponse<ApplicantSummaryDto> = {
  data: applicantItems,
  items: applicantItems,
  pagination: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
};

const renderApplicationsPage = () => {
  return render(
    <MemoryRouter>
      <RecruiterApplicationsPage />
    </MemoryRouter>
  );
};

describe('RecruiterApplicationsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders loading skeleton initially', () => {
    vi.spyOn(recruiterApi, 'getJobs').mockReturnValue(new Promise(() => {}));
    const { container } = renderApplicationsPage();
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('renders applicant cards with name, status, match score, and review button', async () => {
    vi.spyOn(recruiterApi, 'getJobs').mockResolvedValue(mockJobs);
    vi.spyOn(recruiterApi, 'getJobApplicants').mockResolvedValue(mockApplicants);

    renderApplicationsPage();

    await waitFor(() => {
      expect(screen.getByText('Samantha Ray')).toBeInTheDocument();
      expect(screen.getByText('Michael Chang')).toBeInTheDocument();
      expect(screen.getByText('92% Match')).toBeInTheDocument();
      expect(screen.getByText('65% Match')).toBeInTheDocument();
    });

    const reviewButtons = screen.getAllByRole('button', { name: /Review/i });
    expect(reviewButtons.length).toBeGreaterThanOrEqual(2);
  });

  it('filters applicants by status', async () => {
    vi.spyOn(recruiterApi, 'getJobs').mockResolvedValue(mockJobs);
    vi.spyOn(recruiterApi, 'getJobApplicants').mockResolvedValue(mockApplicants);

    renderApplicationsPage();

    await waitFor(() => {
      expect(screen.getByText('Samantha Ray')).toBeInTheDocument();
    });

    const statusFilter = screen.getByDisplayValue(/All Application Statuses/i);
    fireEvent.change(statusFilter, { target: { value: 'UNDER_REVIEW' } });

    await waitFor(() => {
      expect(screen.getByText('Michael Chang')).toBeInTheDocument();
    });
  });

  it('displays empty state when no applicants match', async () => {
    vi.spyOn(recruiterApi, 'getJobs').mockResolvedValue(mockJobs);
    vi.spyOn(recruiterApi, 'getJobApplicants').mockResolvedValue({
      data: [],
      items: [],
      pagination: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
    });

    renderApplicationsPage();

    await waitFor(() => {
      expect(screen.getByText('No Applicants Found')).toBeInTheDocument();
    });
  });
});
