import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ApplicationDetailPage } from '../ApplicationDetailPage';
import { candidateApi } from '../../../api/candidate';
import type { ApplicationResponseDto } from '../../../types/api';

const mockApplicationDetail: ApplicationResponseDto = {
  applicationId: 42,
  jobId: 10,
  candidateId: 1,
  jobTitle: 'Senior Frontend Developer',
  companyName: 'Tech Giants Inc',
  status: 'APPLIED',
  coverLetter: 'I am excited to build high-performance web applications with your team.',
  appliedAt: '2026-10-18T14:30:00Z',
  candidateSnapshot: {
    candidateId: 1,
    fullName: 'Alice Candidate',
    email: 'alice@example.com',
    headline: 'Frontend Specialist',
    location: 'San Francisco, CA',
    summary: 'Experienced React engineer.',
    skills: ['TypeScript', 'React', 'CSS'],
    education: [
      {
        id: 1,
        institution: 'UC Berkeley',
        degree: 'B.S.',
        fieldOfStudy: 'Computer Science',
        startYear: 2018,
        endYear: 2022,
      },
    ],
    experience: [
      {
        id: 1,
        companyName: 'Startup Lab',
        roleTitle: 'Frontend Engineer',
        startDate: '2022-06-01',
        isCurrent: true,
      },
    ],
    projects: [
      {
        id: 1,
        title: 'Design System Kit',
        description: 'Component library',
      },
    ],
  },
  resumeSnapshot: {
    id: 100,
    originalFilename: 'alice_resume.pdf',
    contentType: 'application/pdf',
    sizeBytes: 154200,
  },
  statusHistory: [
    {
      id: 1,
      applicationId: 42,
      toStatus: 'APPLIED',
      createdAt: '2026-10-18T14:30:00Z',
    },
  ],
};

const renderApplicationDetailPage = (appId = '42') => {
  return render(
    <MemoryRouter initialEntries={[`/candidate/applications/${appId}`]}>
      <Routes>
        <Route path="/candidate/applications/:applicationId" element={<ApplicationDetailPage />} />
      </Routes>
    </MemoryRouter>
  );
};

describe('ApplicationDetailPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders application details, status timeline, and snapshots accurately', async () => {
    vi.spyOn(candidateApi, 'getApplicationById').mockResolvedValue(mockApplicationDetail);

    renderApplicationDetailPage('42');

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Senior Frontend Developer' })).toBeInTheDocument();
      expect(screen.getByText(/Tech Giants Inc/i)).toBeInTheDocument();
      expect(screen.getByText(/Status Progression/i)).toBeInTheDocument();
      expect(screen.getByText(/I am excited to build high-performance web applications/i)).toBeInTheDocument();
      expect(screen.getByText('Alice Candidate')).toBeInTheDocument();
      expect(screen.getByText('alice@example.com')).toBeInTheDocument();
      expect(screen.getByText('alice_resume.pdf')).toBeInTheDocument();
      expect(screen.getByText('UC Berkeley')).toBeInTheDocument();
      expect(screen.getByText('TypeScript')).toBeInTheDocument();
    });
  });

  it('allows withdrawing an active application from the detail page', async () => {
    vi.spyOn(candidateApi, 'getApplicationById').mockResolvedValue(mockApplicationDetail);
    const mockWithdrawn: ApplicationResponseDto = {
      ...mockApplicationDetail,
      status: 'WITHDRAWN',
      statusHistory: [
        ...(mockApplicationDetail.statusHistory || []),
        {
          id: 2,
          applicationId: 42,
          fromStatus: 'APPLIED',
          toStatus: 'WITHDRAWN',
          createdAt: '2026-10-18T16:00:00Z',
        },
      ],
    };
    const withdrawSpy = vi.spyOn(candidateApi, 'withdrawApplication').mockResolvedValue(mockWithdrawn);

    renderApplicationDetailPage('42');

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /withdraw application/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /withdraw application/i }));

    // Modal opens
    expect(screen.getByRole('heading', { name: /confirm application withdrawal/i })).toBeInTheDocument();

    const confirmBtn = screen.getByRole('button', { name: /confirm withdrawal/i });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(withdrawSpy).toHaveBeenCalledWith(42);
      expect(screen.getByText(/application successfully withdrawn/i)).toBeInTheDocument();
      expect(screen.getAllByText('Withdrawn').length).toBeGreaterThan(0);
    });
  });
});
