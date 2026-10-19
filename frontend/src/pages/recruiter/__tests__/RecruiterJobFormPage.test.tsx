import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RecruiterJobFormPage } from '../RecruiterJobFormPage';
import { recruiterApi } from '../../../api/recruiter';
import { jobsApi } from '../../../api/jobs';
import type { JobDetailDto } from '../../../types/api';

const mockExistingJob: JobDetailDto = {
  id: 15,
  title: 'Cloud Architect',
  company: { id: 10, name: 'Nexus Dynamics' },
  description: 'Lead our cloud architectural transformation.',
  responsibilities: 'Build systems.',
  requirements: 'Experience with Java and TypeScript.',
  location: 'Seattle, WA',
  jobType: 'FULL_TIME',
  experienceLevel: 'ONE_TO_TWO_YEARS',
  workMode: 'REMOTE',
  salaryMinLpa: 15,
  salaryMaxLpa: 25,
  salaryCurrency: 'INR',
  applicationDeadline: '2026-11-30T23:59:59Z',
  status: 'ACTIVE',
  applicantCount: 2, // has applications!
  skills: [
    { id: 1, name: 'Java' },
    { id: 4, name: 'TypeScript' },
  ],
  createdAt: '2026-10-18T10:00:00Z',
  updatedAt: '2026-10-18T10:00:00Z',
};

const renderJobFormPage = (route: string = '/recruiter/jobs/new') => {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route path="/recruiter/jobs/new" element={<RecruiterJobFormPage />} />
        <Route path="/recruiter/jobs/:jobId/edit" element={<RecruiterJobFormPage />} />
      </Routes>
    </MemoryRouter>
  );
};

describe('RecruiterJobFormPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(jobsApi, 'getJobs').mockResolvedValue({
      data: [],
      items: [],
      pagination: { page: 0, size: 50, totalElements: 0, totalPages: 0 },
    });
  });

  it('renders create job form with empty fields', async () => {
    renderJobFormPage('/recruiter/jobs/new');

    expect(screen.getByRole('heading', { name: 'Create Job Opening' })).toBeInTheDocument();
    expect(screen.getByLabelText(/Job Title/i)).toHaveValue('');
    expect(screen.getByLabelText(/Role Overview & Description/i)).toHaveValue('');
  });

  it('validates required fields before submitting new job', async () => {
    renderJobFormPage('/recruiter/jobs/new');

    const form = screen.getByLabelText(/Job Title/i).closest('form')!;
    fireEvent.submit(form);

    await waitFor(() => {
      expect(screen.getByText('Job title is required.')).toBeInTheDocument();
    });
  });

  it('disables fundamental requirement fields when editing job with applicants', async () => {
    vi.spyOn(recruiterApi, 'getJobById').mockResolvedValue(mockExistingJob);

    renderJobFormPage('/recruiter/jobs/15/edit');

    await waitFor(() => {
      expect(screen.getByDisplayValue('Cloud Architect')).toBeInTheDocument();
    });

    // Check lock notice
    expect(
      screen.getByText(/Fundamental Job Requirements Locked \(2 Candidates Applied\)/i)
    ).toBeInTheDocument();

    // Fundamental fields should be disabled
    expect(screen.getByLabelText(/Job Title/i)).toBeDisabled();
    expect(screen.getByLabelText(/Job Type/i)).toBeDisabled();
    expect(screen.getByLabelText(/Experience Level/i)).toBeDisabled();
    expect(screen.getByLabelText(/Work Mode/i)).toBeDisabled();

    // Operational fields should remain editable
    expect(screen.getByLabelText(/Role Overview & Description/i)).not.toBeDisabled();
    expect(screen.getByLabelText(/Location \/ Office/i)).not.toBeDisabled();
  });
});
