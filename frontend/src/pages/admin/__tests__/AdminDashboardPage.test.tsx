import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AdminDashboardPage } from '../AdminDashboardPage';
import { adminApi } from '../../../api/admin';
import type { AdminDashboardMetrics } from '../../../types/api';

const mockMetrics: AdminDashboardMetrics = {
  users: { total: 121, candidates: 91, recruiters: 29, active: 111, inactive: 10 },
  companies: { total: 25, active: 22, inactive: 3 },
  jobs: { total: 64, active: 41, draft: 11, closed: 8, archived: 4 },
  applications: { total: 257, applied: 83, underReview: 62, shortlisted: 45, rejected: 31, selected: 23, withdrawn: 13 },
};

describe('AdminDashboardPage Component', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders loading skeleton and then populates platform metrics', async () => {
    vi.spyOn(adminApi, 'getDashboard').mockResolvedValueOnce(mockMetrics);

    render(
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>
    );

    expect(screen.getByText('System Administration')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('121')).toBeInTheDocument();
      expect(screen.getByText('25')).toBeInTheDocument();
      expect(screen.getByText('64')).toBeInTheDocument();
      expect(screen.getByText('257')).toBeInTheDocument();
    });

    expect(screen.getByText('91 candidates · 29 recruiters')).toBeInTheDocument();
    expect(screen.getByText('22 active · 3 inactive')).toBeInTheDocument();
    expect(screen.getByText('Users Moderation')).toBeInTheDocument();
    expect(screen.getByText('Audit Logs')).toBeInTheDocument();
  });

  it('handles API error with graceful error alert and refresh action', async () => {
    vi.spyOn(adminApi, 'getDashboard')
      .mockRejectedValueOnce(new Error('Internal Server Error'))
      .mockResolvedValueOnce(mockMetrics);

    render(
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Internal Server Error')).toBeInTheDocument();
    });

    const refreshBtn = screen.getByRole('button', { name: /refresh metrics/i });
    fireEvent.click(refreshBtn);

    await waitFor(() => {
      expect(screen.getByText('121')).toBeInTheDocument();
    });
  });
});
