import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AdminJobsPage } from '../AdminJobsPage';
import { adminApi } from '../../../api/admin';
import type { AdminJobDto, PagedResponse } from '../../../types/api';

const mockJobs: AdminJobDto[] = [
  {
    id: 101,
    companyId: 10,
    companyName: 'Apex Cloud Technologies',
    title: 'Junior Cloud Engineer',
    description: 'Cloud infrastructure role',
    jobType: 'FULL_TIME',
    experienceLevel: 'ZERO_TO_ONE_YEARS',
    workMode: 'HYBRID',
    location: 'San Francisco, CA',
    salaryMinLpa: 8,
    salaryMaxLpa: 14,
    applicationDeadline: '2026-12-31',
    status: 'ACTIVE',
    applicationCount: 4,
    requiredSkills: ['Java', 'React'],
    createdAt: '2026-10-18T10:00:00Z',
    updatedAt: '2026-10-18T10:00:00Z',
  },
  {
    id: 102,
    companyId: 10,
    companyName: 'Apex Cloud Technologies',
    title: 'Deprecated Frontend Role',
    description: 'Frontend role',
    jobType: 'INTERNSHIP',
    experienceLevel: 'INTERNSHIP',
    workMode: 'REMOTE',
    location: 'Remote',
    salaryMinLpa: 4,
    salaryMaxLpa: 6,
    applicationDeadline: '2026-12-31',
    status: 'ARCHIVED',
    applicationCount: 1,
    requiredSkills: ['JavaScript'],
    createdAt: '2026-10-17T10:00:00Z',
    updatedAt: '2026-10-17T10:00:00Z',
  },
];

const mockPagedJobs: PagedResponse<AdminJobDto> = {
  data: mockJobs,
  items: mockJobs,
  pagination: { page: 0, size: 15, totalElements: 2, totalPages: 1 },
};

describe('AdminJobsPage Component', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  const renderComponent = () =>
    render(
      <MemoryRouter>
        <AdminJobsPage />
      </MemoryRouter>
    );

  it('renders platform job postings list and attributes', async () => {
    vi.spyOn(adminApi, 'getJobs').mockResolvedValueOnce(mockPagedJobs);

    renderComponent();

    expect(screen.getByText('Job Postings Moderation')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Junior Cloud Engineer')).toBeInTheDocument();
      expect(screen.getByText('Deprecated Frontend Role')).toBeInTheDocument();
      expect(screen.getByText('₹8 - ₹14 LPA')).toBeInTheDocument();
    });
  });

  it('archives an active job upon confirmation', async () => {
    vi.spyOn(adminApi, 'getJobs').mockResolvedValueOnce(mockPagedJobs);
    const archiveSpy = vi.spyOn(adminApi, 'archiveJob').mockResolvedValueOnce({
      ...mockJobs[0],
      status: 'ARCHIVED',
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Junior Cloud Engineer')).toBeInTheDocument();
    });

    const archiveBtn = screen.getByRole('button', { name: /archive/i });
    fireEvent.click(archiveBtn);

    expect(screen.getByText('Confirm Job Archive')).toBeInTheDocument();
    expect(screen.getByText(/Archiving hides this job posting from candidates/i)).toBeInTheDocument();

    const confirmBtn = screen.getByRole('button', { name: /confirm & archive/i });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(archiveSpy).toHaveBeenCalledWith(101);
      expect(screen.getByText(/has been archived/i)).toBeInTheDocument();
    });
  });

  it('restores an archived job to CLOSED status upon confirmation', async () => {
    vi.spyOn(adminApi, 'getJobs').mockResolvedValueOnce(mockPagedJobs);
    const restoreSpy = vi.spyOn(adminApi, 'restoreJob').mockResolvedValueOnce({
      ...mockJobs[1],
      status: 'CLOSED',
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Deprecated Frontend Role')).toBeInTheDocument();
    });

    const restoreBtn = screen.getByRole('button', { name: /restore/i });
    fireEvent.click(restoreBtn);

    expect(screen.getByText('Confirm Job Restoration')).toBeInTheDocument();
    expect(screen.getByText(/Restoring an archived job places it strictly in/i)).toBeInTheDocument();

    const confirmBtn = screen.getByRole('button', { name: /confirm & restore/i });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(restoreSpy).toHaveBeenCalledWith(102);
      expect(screen.getByText(/has been restored \(Status: CLOSED\)/i)).toBeInTheDocument();
    });
  });
});
