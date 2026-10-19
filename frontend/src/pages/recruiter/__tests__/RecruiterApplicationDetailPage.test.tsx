import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RecruiterApplicationDetailPage } from '../RecruiterApplicationDetailPage';
import { recruiterApi } from '../../../api/recruiter';
import type { ApplicantDetailDto } from '../../../types/api';

const mockApplicantDetail: ApplicantDetailDto = {
  applicationId: 501,
  jobId: 1,
  jobTitle: 'Senior Systems Engineer',
  candidateId: 201,
  status: 'APPLIED',
  appliedAt: '2026-10-18T14:00:00Z',
  candidateSnapshot: {
    candidateId: 201,
    fullName: 'Samantha Ray',
    email: 'samantha@example.com',
    headline: 'Distributed Systems & Cloud Engineer',
    summary: 'Passionate about high-throughput distributed architectures.',
    location: 'Austin, TX',
    education: [
      {
        id: 1,
        institution: 'University of Texas at Austin',
        degree: 'Bachelor of Science',
        fieldOfStudy: 'Computer Science',
        startYear: 2020,
        endYear: 2024,
      },
    ],
    skills: ['Java', 'React', 'Docker'],
    projects: [
      {
        id: 1,
        title: 'Distributed KV Store',
        description: 'Built a Raft-based distributed key-value store in Java.',
      },
    ],
    experience: [
      {
        id: 1,
        companyName: 'Cloud Corp',
        roleTitle: 'Backend Intern',
        startDate: '2023-06-01',
        endDate: '2023-08-31',
        description: 'Worked on microservices telemetry.',
      },
    ],
  },
  resumeSnapshot: {
    id: 99,
    originalFilename: 'Samantha_Ray_Resume.pdf',
    contentType: 'application/pdf',
    sizeBytes: 204800,
  },
  match: {
    percentage: 80,
    matchedSkills: ['Java', 'React'],
    missingSkills: ['Kubernetes'],
    additionalSkills: ['Docker'],
  },
  statusHistory: [
    {
      id: 1,
      applicationId: 501,
      fromStatus: null,
      toStatus: 'APPLIED',
      note: 'Initial application submitted',
      createdAt: '2026-10-18T14:00:00Z',
    },
  ],
};

const renderApplicationDetailPage = (appId: string = '501') => {
  return render(
    <MemoryRouter initialEntries={[`/recruiter/applications/${appId}`]}>
      <Routes>
        <Route
          path="/recruiter/applications/:applicationId"
          element={<RecruiterApplicationDetailPage />}
        />
      </Routes>
    </MemoryRouter>
  );
};

describe('RecruiterApplicationDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders loading skeleton initially', () => {
    vi.spyOn(recruiterApi, 'getApplicationDetail').mockReturnValue(new Promise(() => {}));
    const { container } = renderApplicationDetailPage();
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('renders candidate profile snapshot, deterministic match, and resume action', async () => {
    vi.spyOn(recruiterApi, 'getApplicationDetail').mockResolvedValue(mockApplicantDetail);

    renderApplicationDetailPage();

    await waitFor(() => {
      expect(screen.getByText('Samantha Ray')).toBeInTheDocument();
      expect(screen.getByText('Distributed Systems & Cloud Engineer')).toBeInTheDocument();
      expect(screen.getByText('University of Texas at Austin')).toBeInTheDocument();
      expect(screen.getByText('Distributed KV Store')).toBeInTheDocument();
      expect(screen.getByText(/Cloud Corp/i)).toBeInTheDocument();
    });

    // Match evaluation
    expect(screen.getByText('80% Match')).toBeInTheDocument();
    expect(screen.getByText('Kubernetes')).toBeInTheDocument(); // Missing skill

    // Resume action
    expect(screen.getByRole('button', { name: /Download Resume/i })).toBeInTheDocument();
  });

  it('downloads resume using authenticated blob endpoint', async () => {
    vi.spyOn(recruiterApi, 'getApplicationDetail').mockResolvedValue(mockApplicantDetail);
    const mockBlob = new Blob(['%PDF-1.4 test'], { type: 'application/pdf' });
    const downloadSpy = vi.spyOn(recruiterApi, 'downloadResume').mockResolvedValue(mockBlob);

    // Mock URL.createObjectURL and revokeObjectURL
    const createObjectURLSpy = vi.fn().mockReturnValue('blob:http://localhost/resume-123');
    const revokeObjectURLSpy = vi.fn();
    window.URL.createObjectURL = createObjectURLSpy;
    window.URL.revokeObjectURL = revokeObjectURLSpy;

    renderApplicationDetailPage();

    await waitFor(() => {
      expect(screen.getByText('Samantha Ray')).toBeInTheDocument();
    });

    const downloadBtn = screen.getByRole('button', { name: /Download Resume/i });
    fireEvent.click(downloadBtn);

    await waitFor(() => {
      expect(downloadSpy).toHaveBeenCalledWith('501');
      expect(createObjectURLSpy).toHaveBeenCalledWith(mockBlob);
    });
  });

  it('verifies next allowed status buttons and verifies WITHDRAWN is not offered', async () => {
    vi.spyOn(recruiterApi, 'getApplicationDetail').mockResolvedValue(mockApplicantDetail);

    renderApplicationDetailPage();

    await waitFor(() => {
      expect(screen.getByText('Samantha Ray')).toBeInTheDocument();
    });

    // Recruiter pipeline transitions available for APPLIED: UNDER REVIEW, SHORTLISTED, REJECTED
    expect(screen.getByRole('button', { name: 'UNDER REVIEW' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'SHORTLISTED' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'REJECTED' })).toBeInTheDocument();

    // Critical security constraint: WITHDRAWN must NEVER be present for recruiters!
    expect(screen.queryByRole('button', { name: /WITHDRAWN/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/WITHDRAWN/i)).not.toBeInTheDocument();
  });

  it('opens confirmation modal and updates status with recruiter note', async () => {
    vi.spyOn(recruiterApi, 'getApplicationDetail').mockResolvedValue(mockApplicantDetail);
    const updateSpy = vi.spyOn(recruiterApi, 'updateApplicationStatus').mockResolvedValue({
      ...mockApplicantDetail,
      status: 'SHORTLISTED',
    });

    renderApplicationDetailPage();

    await waitFor(() => {
      expect(screen.getByText('Samantha Ray')).toBeInTheDocument();
    });

    // Click SHORTLISTED button
    fireEvent.click(screen.getByRole('button', { name: 'SHORTLISTED' }));

    // Modal opens
    expect(screen.getByText('Transition Application to SHORTLISTED')).toBeInTheDocument();

    const notesInput = screen.getByPlaceholderText(/Cleared initial screening/i);
    fireEvent.change(notesInput, { target: { value: 'Strong systems background.' } });

    const confirmBtn = screen.getByRole('button', { name: 'Confirm Transition' });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith('501', 'SHORTLISTED', 'Strong systems background.');
      expect(screen.getByText(/Application status transitioned to SHORTLISTED/i)).toBeInTheDocument();
    });
  });
});
