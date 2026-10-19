import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RecruiterJobDetailPage } from '../RecruiterJobDetailPage';
import { recruiterApi } from '../../../api/recruiter';
import type { JobDetailDto } from '../../../types/api';

const mockJobDetailWithApplicants: JobDetailDto = {
  id: 42,
  title: 'Full Stack Engineer',
  company: {
    id: 10,
    name: 'Nexus Dynamics',
    description: 'Enterprise tech company',
    location: 'San Francisco, CA',
  },
  description: 'Design and build resilient cloud systems.',
  responsibilities: 'Collaborate with cross-functional teams to deliver scale.',
  requirements: 'Experience with React and Java.',
  location: 'San Francisco, CA',
  jobType: 'FULL_TIME',
  experienceLevel: 'ZERO_TO_ONE_YEARS',
  workMode: 'HYBRID',
  salaryMinLpa: 12,
  salaryMaxLpa: 18,
  salaryCurrency: 'INR',
  applicationDeadline: '2026-12-31T23:59:59Z',
  status: 'ACTIVE',
  applicantCount: 3,
  skills: [
    { id: 1, name: 'Java' },
    { id: 2, name: 'React' },
  ],
  createdAt: '2026-10-18T10:00:00Z',
  updatedAt: '2026-10-18T10:00:00Z',
};

const renderJobDetailPage = (jobId: string = '42') => {
  return render(
    <MemoryRouter initialEntries={[`/recruiter/jobs/${jobId}`]}>
      <Routes>
        <Route path="/recruiter/jobs/:jobId" element={<RecruiterJobDetailPage />} />
      </Routes>
    </MemoryRouter>
  );
};

describe('RecruiterJobDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders loading skeleton initially', () => {
    vi.spyOn(recruiterApi, 'getJobById').mockReturnValue(new Promise(() => {}));
    const { container } = renderJobDetailPage();
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('renders complete job details', async () => {
    vi.spyOn(recruiterApi, 'getJobById').mockResolvedValue(mockJobDetailWithApplicants);

    renderJobDetailPage();

    await waitFor(() => {
      expect(screen.getByText('Full Stack Engineer')).toBeInTheDocument();
      expect(screen.getByText('Design and build resilient cloud systems.')).toBeInTheDocument();
      expect(screen.getByText('Collaborate with cross-functional teams to deliver scale.')).toBeInTheDocument();
      expect(screen.getByText('Experience with React and Java.')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Applicants/i })).toBeInTheDocument();
    });
  });

  it('displays lock notice when job has applications', async () => {
    vi.spyOn(recruiterApi, 'getJobById').mockResolvedValue(mockJobDetailWithApplicants);

    renderJobDetailPage();

    await waitFor(() => {
      expect(
        screen.getByText(/Fundamental Requirements Locked \(3 Active Applicants\)/i)
      ).toBeInTheDocument();
    });
  });

  it('allows closing an active job', async () => {
    vi.spyOn(recruiterApi, 'getJobById').mockResolvedValue(mockJobDetailWithApplicants);
    const closeSpy = vi.spyOn(recruiterApi, 'closeJob').mockResolvedValue({
      ...mockJobDetailWithApplicants,
      status: 'CLOSED',
    });

    renderJobDetailPage();

    await waitFor(() => {
      expect(screen.getByText('Full Stack Engineer')).toBeInTheDocument();
    });

    const closeBtn = screen.getByRole('button', { name: /Close Role/i });
    fireEvent.click(closeBtn);

    expect(screen.getByText('Close Job Opening')).toBeInTheDocument();

    const confirmBtn = screen.getAllByRole('button', { name: /Close Role/i })[1];
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(closeSpy).toHaveBeenCalledWith(42);
    });
  });
});
