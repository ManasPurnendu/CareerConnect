import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { RecruiterCompanyPage } from '../RecruiterCompanyPage';
import { recruiterApi } from '../../../api/recruiter';
import type { RecruiterCompanyResponseDto } from '../../../types/api';

const mockCompany: RecruiterCompanyResponseDto = {
  id: 10,
  name: 'Nexus Dynamics',
  description: 'Enterprise cloud infrastructure provider.',
  website: 'https://nexusdynamics.com',
  location: 'San Francisco, CA',
  industry: 'Software',
  createdAt: '2026-10-18T10:00:00Z',
  updatedAt: '2026-10-18T10:00:00Z',
};

const renderCompanyPage = () => {
  return render(
    <MemoryRouter>
      <RecruiterCompanyPage />
    </MemoryRouter>
  );
};

describe('RecruiterCompanyPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders loading skeleton initially', () => {
    vi.spyOn(recruiterApi, 'getCompany').mockReturnValue(new Promise(() => {}));
    const { container } = renderCompanyPage();
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('renders company details when company exists', async () => {
    vi.spyOn(recruiterApi, 'getCompany').mockResolvedValue(mockCompany);

    renderCompanyPage();

    await waitFor(() => {
      expect(screen.getByText('Nexus Dynamics')).toBeInTheDocument();
      expect(screen.getByText('Enterprise cloud infrastructure provider.')).toBeInTheDocument();
      expect(screen.getByText('San Francisco, CA')).toBeInTheDocument();
      expect(screen.getByText('Software')).toBeInTheDocument();
    });

    expect(screen.getByRole('button', { name: /Edit Profile/i })).toBeInTheDocument();
  });

  it('opens edit form and updates company successfully', async () => {
    vi.spyOn(recruiterApi, 'getCompany').mockResolvedValue(mockCompany);
    const updateSpy = vi.spyOn(recruiterApi, 'upsertCompany').mockResolvedValue({
      ...mockCompany,
      name: 'Nexus Dynamics Global',
    });

    renderCompanyPage();

    await waitFor(() => {
      expect(screen.getByText('Nexus Dynamics')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Edit Profile/i }));

    const nameInput = screen.getByLabelText(/Company Name/i);
    fireEvent.change(nameInput, { target: { value: 'Nexus Dynamics Global' } });

    fireEvent.click(screen.getByRole('button', { name: /Save Changes/i }));

    await waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'Nexus Dynamics Global',
        })
      );
      expect(screen.getByText('Company profile updated successfully.')).toBeInTheDocument();
    });
  });

  it('renders unconfigured setup form when recruiter has no company (404)', async () => {
    vi.spyOn(recruiterApi, 'getCompany').mockRejectedValue({
      status: 404,
      message: 'Company not found',
    });

    renderCompanyPage();

    await waitFor(() => {
      expect(screen.getByText('Set Up Company Profile')).toBeInTheDocument();
      expect(screen.getByText('Company Workspace Required')).toBeInTheDocument();
      expect(screen.getByLabelText(/Company Name/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Create Company Profile/i })).toBeInTheDocument();
    });
  });
});
