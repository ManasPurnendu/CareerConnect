import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { RecruiterJobsPage } from '../RecruiterJobsPage';
import { recruiterApi } from '../../../api/recruiter';
import type { JobSummaryDto, PagedResponse } from '../../../types/api';

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
    applicantCount: 5,
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
    size: 20,
    totalElements: 2,
    totalPages: 1,
  },
};

const renderJobsPage = () => {
  return render(
    <MemoryRouter>
      <RecruiterJobsPage />
    </MemoryRouter>
  );
};

describe('RecruiterJobsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders loading skeleton initially', () => {
    vi.spyOn(recruiterApi, 'getJobs').mockReturnValue(new Promise(() => {}));
    const { container } = renderJobsPage();
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('renders job cards with status, details, and applicant count', async () => {
    vi.spyOn(recruiterApi, 'getJobs').mockResolvedValue(mockJobs);
    renderJobsPage();

    await waitFor(() => {
      expect(screen.getByText('Senior Systems Engineer')).toBeInTheDocument();
      expect(screen.getByText('Junior Cloud Developer')).toBeInTheDocument();
      expect(screen.getByText('5')).toBeInTheDocument();
      expect(screen.getByText('0')).toBeInTheDocument();
    });
  });

  it('filters jobs by status tab', async () => {
    vi.spyOn(recruiterApi, 'getJobs').mockResolvedValue(mockJobs);
    renderJobsPage();

    await waitFor(() => {
      expect(screen.getByText('Senior Systems Engineer')).toBeInTheDocument();
    });

    // Click Draft tab
    fireEvent.click(screen.getByRole('tab', { name: /Draft/i }));

    expect(screen.queryByText('Senior Systems Engineer')).not.toBeInTheDocument();
    expect(screen.getByText('Junior Cloud Developer')).toBeInTheDocument();
  });

  it('opens confirmation modal and publishes a draft job', async () => {
    vi.spyOn(recruiterApi, 'getJobs').mockResolvedValue(mockJobs);
    const publishSpy = vi.spyOn(recruiterApi, 'publishJob').mockResolvedValue({
      ...mockJobs.data[1],
      status: 'ACTIVE',
    } as any);

    renderJobsPage();

    await waitFor(() => {
      expect(screen.getByText('Junior Cloud Developer')).toBeInTheDocument();
    });

    const publishButtons = screen.getAllByRole('button', { name: /Publish/i });
    fireEvent.click(publishButtons[0]);

    // Modal opens
    expect(screen.getByText('Publish Job Opening')).toBeInTheDocument();

    const confirmPublish = screen.getByRole('button', { name: /Confirm & Publish/i });
    fireEvent.click(confirmPublish);

    await waitFor(() => {
      expect(publishSpy).toHaveBeenCalledWith(2);
    });
  });
});
