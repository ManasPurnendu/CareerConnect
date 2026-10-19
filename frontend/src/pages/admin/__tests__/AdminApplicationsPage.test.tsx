import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AdminApplicationsPage } from '../AdminApplicationsPage';
import { adminApi } from '../../../api/admin';
import type { AdminApplicationSummaryDto, PagedResponse } from '../../../types/api';

const mockApplications: AdminApplicationSummaryDto[] = [
  {
    id: 501,
    jobId: 101,
    jobTitle: 'Junior Cloud Engineer',
    companyId: 10,
    companyName: 'Apex Cloud Technologies',
    candidateId: 15,
    candidateName: 'Michael Brown',
    candidateEmail: 'michael.brown@example.com',
    status: 'APPLIED',
    appliedAt: '2026-10-18T14:30:00Z',
    createdAt: '2026-10-18T14:30:00Z',
    updatedAt: '2026-10-18T14:30:00Z',
  },
  {
    id: 502,
    jobId: 101,
    jobTitle: 'Junior Cloud Engineer',
    companyId: 10,
    companyName: 'Apex Cloud Technologies',
    candidateId: 16,
    candidateName: 'Emily Watson',
    candidateEmail: 'emily.watson@example.com',
    status: 'UNDER_REVIEW',
    appliedAt: '2026-10-18T15:00:00Z',
    createdAt: '2026-10-18T15:00:00Z',
    updatedAt: '2026-10-18T15:00:00Z',
  },
];

const mockPagedApps: PagedResponse<AdminApplicationSummaryDto> = {
  data: mockApplications,
  items: mockApplications,
  pagination: { page: 0, size: 15, totalElements: 2, totalPages: 1 },
};

describe('AdminApplicationsPage Component', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  const renderComponent = () =>
    render(
      <MemoryRouter>
        <AdminApplicationsPage />
      </MemoryRouter>
    );

  it('renders applications list and navigation inspect links', async () => {
    vi.spyOn(adminApi, 'getApplications').mockResolvedValueOnce(mockPagedApps);

    renderComponent();

    expect(screen.getByText('Applications Oversight')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Michael Brown')).toBeInTheDocument();
      expect(screen.getByText('Emily Watson')).toBeInTheDocument();
      expect(screen.getByText('michael.brown@example.com')).toBeInTheDocument();
      expect(screen.getAllByText('Junior Cloud Engineer').length).toBeGreaterThan(0);
    });

    const inspectLinks = screen.getAllByRole('link', { name: /inspect/i });
    expect(inspectLinks.length).toBe(2);
    expect(inspectLinks[0]).toHaveAttribute('href', '/admin/applications/501');
  });

  it('handles empty applications state', async () => {
    vi.spyOn(adminApi, 'getApplications').mockResolvedValueOnce({
      data: [],
      items: [],
      pagination: { page: 0, size: 15, totalElements: 0, totalPages: 0 },
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('No applications found')).toBeInTheDocument();
    });
  });
});
