import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AdminCompaniesPage } from '../AdminCompaniesPage';
import { adminApi } from '../../../api/admin';
import type { AdminCompanyDto, PagedResponse } from '../../../types/api';

const mockCompanies: AdminCompanyDto[] = [
  {
    id: 10,
    name: 'Apex Cloud Technologies',
    industry: 'Cloud Computing',
    location: 'San Francisco, CA',
    website: 'https://apexcloud.example.com',
    companySize: '51-200',
    active: true,
    jobCount: 4,
    recruiterCount: 2,
    createdAt: '2026-10-18T10:00:00Z',
    updatedAt: '2026-10-18T10:00:00Z',
  },
  {
    id: 11,
    name: 'Legacy Dynamics',
    industry: 'Finance',
    location: 'New York, NY',
    active: false,
    jobCount: 0,
    recruiterCount: 1,
    createdAt: '2026-10-17T10:00:00Z',
    updatedAt: '2026-10-17T10:00:00Z',
  },
];

const mockPagedCompanies: PagedResponse<AdminCompanyDto> = {
  data: mockCompanies,
  items: mockCompanies,
  pagination: { page: 0, size: 15, totalElements: 2, totalPages: 1 },
};

describe('AdminCompaniesPage Component', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  const renderComponent = () =>
    render(
      <MemoryRouter>
        <AdminCompaniesPage />
      </MemoryRouter>
    );

  it('renders registered companies list and status badges', async () => {
    vi.spyOn(adminApi, 'getCompanies').mockResolvedValueOnce(mockPagedCompanies);

    renderComponent();

    expect(screen.getByText('Company Moderation')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Apex Cloud Technologies')).toBeInTheDocument();
      expect(screen.getByText('Legacy Dynamics')).toBeInTheDocument();
      expect(screen.getByText('Cloud Computing')).toBeInTheDocument();
      expect(screen.getByText('San Francisco, CA')).toBeInTheDocument();
    });
  });

  it('opens confirmation modal and deactivates active company', async () => {
    vi.spyOn(adminApi, 'getCompanies').mockResolvedValueOnce(mockPagedCompanies);
    const updateSpy = vi.spyOn(adminApi, 'updateCompanyStatus').mockResolvedValueOnce({
      ...mockCompanies[0],
      active: false,
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Apex Cloud Technologies')).toBeInTheDocument();
    });

    const deactivateBtn = screen.getByRole('button', { name: /deactivate/i });
    fireEvent.click(deactivateBtn);

    expect(screen.getByText('Confirm Company Deactivation')).toBeInTheDocument();

    const confirmBtn = screen.getByRole('button', { name: /confirm & deactivate/i });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith(10, false);
      expect(screen.getByText(/is now Deactivated/i)).toBeInTheDocument();
    });
  });

  it('handles activation for inactive company', async () => {
    vi.spyOn(adminApi, 'getCompanies').mockResolvedValueOnce(mockPagedCompanies);
    const updateSpy = vi.spyOn(adminApi, 'updateCompanyStatus').mockResolvedValueOnce({
      ...mockCompanies[1],
      active: true,
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Legacy Dynamics')).toBeInTheDocument();
    });

    const activateBtn = screen.getByRole('button', { name: /^activate$/i });
    fireEvent.click(activateBtn);

    expect(screen.getByText('Confirm Company Reactivation')).toBeInTheDocument();

    const confirmBtn = screen.getByRole('button', { name: /confirm & activate/i });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith(11, true);
      expect(screen.getByText(/is now Active/i)).toBeInTheDocument();
    });
  });
});
