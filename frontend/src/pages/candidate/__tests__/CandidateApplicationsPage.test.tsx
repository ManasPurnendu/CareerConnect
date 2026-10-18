import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { CandidateApplicationsPage } from '../CandidateApplicationsPage';
import { candidateApi } from '../../../api/candidate';
import type { ApplicationResponseDto, PagedResponse } from '../../../types/api';

const mockApplications: PagedResponse<ApplicationResponseDto> = {
  data: [
    {
      applicationId: 42,
      jobId: 10,
      candidateId: 1,
      jobTitle: 'Senior Frontend Developer',
      companyName: 'Tech Giants Inc',
      status: 'APPLIED',
      appliedAt: '2026-10-18T14:30:00Z',
    },
  ],
  pagination: {
    page: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
  },
};

describe('CandidateApplicationsPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders application list with job title, company name, and status', async () => {
    vi.spyOn(candidateApi, 'getApplications').mockResolvedValue(mockApplications);

    render(
      <MemoryRouter>
        <CandidateApplicationsPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Senior Frontend Developer')).toBeInTheDocument();
      expect(screen.getByText('Tech Giants Inc')).toBeInTheDocument();
      expect(screen.getByText('Applied')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /withdraw/i })).toBeInTheDocument();
    });
  });

  it('opens confirmation modal and executes withdrawal via candidateApi', async () => {
    vi.spyOn(candidateApi, 'getApplications').mockResolvedValue(mockApplications);
    const mockWithdrawn: ApplicationResponseDto = {
      applicationId: 42,
      jobId: 10,
      candidateId: 1,
      jobTitle: 'Senior Frontend Developer',
      companyName: 'Tech Giants Inc',
      status: 'WITHDRAWN',
      appliedAt: '2026-10-18T14:30:00Z',
    };
    const withdrawSpy = vi.spyOn(candidateApi, 'withdrawApplication').mockResolvedValue(mockWithdrawn);

    render(
      <MemoryRouter>
        <CandidateApplicationsPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /withdraw/i })).toBeInTheDocument();
    });

    const withdrawBtn = screen.getByRole('button', { name: /withdraw/i });
    fireEvent.click(withdrawBtn);

    // Modal is opened
    expect(screen.getByRole('heading', { name: /confirm application withdrawal/i })).toBeInTheDocument();
    expect(screen.getByText(/are you sure you want to withdraw your application for/i)).toBeInTheDocument();

    const confirmBtn = screen.getByRole('button', { name: /confirm withdrawal/i });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(withdrawSpy).toHaveBeenCalledWith(42);
      expect(screen.getByText(/Application #42 for "Senior Frontend Developer" was successfully withdrawn/i)).toBeInTheDocument();
      expect(screen.getByText('Withdrawn')).toBeInTheDocument();
    });
  });

  it('renders empty state when candidate has no applications', async () => {
    const emptyResponse: PagedResponse<ApplicationResponseDto> = {
      data: [],
      pagination: {
        page: 0,
        size: 10,
        totalElements: 0,
        totalPages: 0,
      },
    };
    vi.spyOn(candidateApi, 'getApplications').mockResolvedValue(emptyResponse);

    render(
      <MemoryRouter>
        <CandidateApplicationsPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText(/no applications found/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /browse jobs/i })).toBeInTheDocument();
    });
  });
});
