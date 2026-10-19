import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { AdminApplicationDetailPage } from '../AdminApplicationDetailPage';
import { adminApi } from '../../../api/admin';
import type { AdminApplicationDetailDto } from '../../../types/api';

const mockDetail: AdminApplicationDetailDto = {
  applicationId: 501,
  jobId: 101,
  jobTitle: 'Junior Cloud Engineer',
  companyId: 10,
  companyName: 'Apex Cloud Technologies',
  candidateId: 15,
  status: 'UNDER_REVIEW',
  coverLetter: 'I am excited about this cloud role.',
  appliedAt: '2026-10-18T14:30:00Z',
  candidateSnapshot: {
    candidateId: 15,
    fullName: 'Michael Brown',
    email: 'michael.brown@example.com',
    headline: 'Aspiring Cloud & DevOps Engineer',
    location: 'San Francisco, CA',
    summary: 'Skilled in Linux, Docker, and Java.',
    skills: ['Java', 'Docker', 'Linux', 'SQL'],
    education: [
      {
        id: 1,
        degree: 'Bachelor of Science',
        fieldOfStudy: 'Computer Science',
        institution: 'University of California, Berkeley',
        startYear: 2020,
        endYear: 2024,
      },
    ],
    experience: [
      {
        id: 1,
        companyName: 'Tech Startups Inc',
        roleTitle: 'Software Engineering Intern',
        startDate: '2023-05-01',
        endDate: '2023-08-31',
        isCurrent: false,
        description: 'Built REST APIs in Spring Boot',
      },
    ],
    projects: [
      {
        id: 1,
        title: 'Cloud Orchestrator',
        description: 'Microservice orchestration platform',
      },
    ],
  },
  resumeSnapshot: {
    id: 88,
    originalFilename: 'michael_brown_resume.pdf',
    contentType: 'application/pdf',
    sizeBytes: 154200,
  },
  statusHistory: [
    {
      id: 1,
      applicationId: 501,
      fromStatus: null,
      toStatus: 'APPLIED',
      note: 'Initial submission',
      createdAt: '2026-10-18T14:30:00Z',
    },
    {
      id: 2,
      applicationId: 501,
      fromStatus: 'APPLIED',
      toStatus: 'UNDER_REVIEW',
      note: 'Screening candidate portfolio',
      createdAt: '2026-10-18T15:00:00Z',
    },
  ],
  match: {
    percentage: 75,
    matchedSkills: ['Java', 'SQL'],
    missingSkills: ['React', 'TypeScript'],
    additionalSkills: ['Docker', 'Linux'],
  },
};

describe('AdminApplicationDetailPage Component', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  const renderComponent = (id = '501') =>
    render(
      <MemoryRouter initialEntries={[`/admin/applications/${id}`]}>
        <Routes>
          <Route path="/admin/applications/:applicationId" element={<AdminApplicationDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

  it('renders application details, candidate snapshot, and match assessment', async () => {
    vi.spyOn(adminApi, 'getApplicationDetail').mockResolvedValueOnce(mockDetail);

    renderComponent('501');

    await waitFor(() => {
      expect(screen.getByText('Application #501')).toBeInTheDocument();
      expect(screen.getByText('Junior Cloud Engineer')).toBeInTheDocument();
      expect(screen.getByText('Apex Cloud Technologies')).toBeInTheDocument();
      expect(screen.getByText('Michael Brown')).toBeInTheDocument();
      expect(screen.getByText('michael.brown@example.com')).toBeInTheDocument();
      expect(screen.getByText('Aspiring Cloud & DevOps Engineer')).toBeInTheDocument();
    });

    expect(screen.getByText('Bachelor of Science in Computer Science')).toBeInTheDocument();
    expect(screen.getByText('Software Engineering Intern at Tech Startups Inc')).toBeInTheDocument();
    expect(screen.getByText('Cloud Orchestrator')).toBeInTheDocument();
    expect(screen.getByText('75%')).toBeInTheDocument();
    expect(screen.getByText('Read-Only Inspection Mode')).toBeInTheDocument();
  });

  it('triggers secure resume download via adminApi.downloadResume', async () => {
    vi.spyOn(adminApi, 'getApplicationDetail').mockResolvedValueOnce(mockDetail);
    const mockBlob = new Blob(['%PDF-1.4 mock content'], { type: 'application/pdf' });
    const downloadSpy = vi.spyOn(adminApi, 'downloadResume').mockResolvedValueOnce(mockBlob);

    // Mock window.URL methods
    const createObjectURLMock = vi.fn().mockReturnValue('blob:mock-url');
    const revokeObjectURLMock = vi.fn();
    window.URL.createObjectURL = createObjectURLMock;
    window.URL.revokeObjectURL = revokeObjectURLMock;

    renderComponent('501');

    await waitFor(() => {
      expect(screen.getByText('michael_brown_resume.pdf')).toBeInTheDocument();
    });

    const downloadBtn = screen.getByRole('button', { name: /download resume/i });
    fireEvent.click(downloadBtn);

    await waitFor(() => {
      expect(downloadSpy).toHaveBeenCalledWith(501);
      expect(createObjectURLMock).toHaveBeenCalledWith(mockBlob);
      expect(revokeObjectURLMock).toHaveBeenCalledWith('blob:mock-url');
    });
  });

  it('handles application not found error state', async () => {
    vi.spyOn(adminApi, 'getApplicationDetail').mockRejectedValueOnce(
      new Error('Application not found')
    );

    renderComponent('999');

    await waitFor(() => {
      expect(screen.getByText('Application not found')).toBeInTheDocument();
    });
  });
});
