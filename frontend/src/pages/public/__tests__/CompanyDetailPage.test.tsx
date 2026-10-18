import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { CompanyDetailPage } from '../CompanyDetailPage';
import { companiesApi } from '../../../api/companies';
import type { CompanyDetailDto } from '../../../types/api';

const mockCompanyDetail: CompanyDetailDto = {
  id: 101,
  name: 'InnoTech Labs',
  industry: 'Enterprise Software',
  location: 'Hyderabad, India',
  website: 'https://innotech.example.com',
  logoUrl: null,
  description: 'Pioneering next-gen enterprise tools.',
  activeJobs: [
    {
      id: 1,
      title: 'Full Stack Trainee',
      company: {
        id: 101,
        name: 'InnoTech Labs',
        industry: 'Enterprise Software',
        logoUrl: null,
      },
      location: 'Hyderabad, India',
      employmentType: 'TRAINEE',
      experienceLevel: 'FRESHER',
      workMode: 'HYBRID',
      skills: ['Java', 'Spring Boot'],
      salaryMinLpa: 5,
      salaryMaxLpa: 7,
      status: 'ACTIVE',
      applicationDeadline: '2026-12-31T23:59:59Z',
      createdAt: '2026-10-18T10:00:00Z',
    },
  ],
};

describe('CompanyDetailPage Public Discovery', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders company details and active opportunities', async () => {
    vi.spyOn(companiesApi, 'getCompanyById').mockResolvedValue(mockCompanyDetail);

    render(
      <MemoryRouter initialEntries={['/companies/101']}>
        <Routes>
          <Route path="/companies/:companyId" element={<CompanyDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'InnoTech Labs' })).toBeInTheDocument();
      expect(screen.getByText('Enterprise Software')).toBeInTheDocument();
      expect(screen.getAllByText('Hyderabad, India').length).toBeGreaterThan(0);
      expect(screen.getByText(/pioneering next-gen enterprise tools/i)).toBeInTheDocument();
      expect(screen.getByText('Full Stack Trainee')).toBeInTheDocument();
    });
  });

  it('displays 404 state when company does not exist', async () => {
    vi.spyOn(companiesApi, 'getCompanyById').mockRejectedValue(new Error('Company not found with id 999'));

    render(
      <MemoryRouter initialEntries={['/companies/999']}>
        <Routes>
          <Route path="/companies/:companyId" element={<CompanyDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /company not found/i })).toBeInTheDocument();
      expect(screen.getByText(/company not found with id 999/i)).toBeInTheDocument();
    });
  });
});
