import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { CompaniesPage } from '../CompaniesPage';
import { companiesApi } from '../../../api/companies';
import type { CompanySummaryDto, PagedResponse } from '../../../types/api';

const mockCompanies: CompanySummaryDto[] = [
  {
    id: 101,
    name: 'InnoTech Labs',
    industry: 'Enterprise Software',
    location: 'Hyderabad, India',
    logoUrl: null,
    website: 'https://innotech.example.com',
    description: 'Pioneering next-gen enterprise tools.',
    activeJobCount: 3,
  },
];

const mockPagedResponse: PagedResponse<CompanySummaryDto> = {
  data: mockCompanies,
  pagination: {
    page: 0,
    size: 12,
    totalElements: 1,
    totalPages: 1,
  },
};

describe('CompaniesPage Public Discovery', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders company cards when data is loaded successfully', async () => {
    vi.spyOn(companiesApi, 'getPublicCompanies').mockResolvedValue(mockPagedResponse);

    render(
      <MemoryRouter initialEntries={['/companies']}>
        <CompaniesPage />
      </MemoryRouter>
    );

    expect(screen.getByText(/featured & verified companies/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('InnoTech Labs')).toBeInTheDocument();
      expect(screen.getByText('Enterprise Software')).toBeInTheDocument();
      expect(screen.getByText(/3 open roles/i)).toBeInTheDocument();
    });
  });

  it('searches companies when query is submitted', async () => {
    const getCompaniesSpy = vi.spyOn(companiesApi, 'getPublicCompanies').mockResolvedValue(mockPagedResponse);

    render(
      <MemoryRouter initialEntries={['/companies']}>
        <CompaniesPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('InnoTech Labs')).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText(/search companies by name/i);
    fireEvent.change(searchInput, { target: { value: 'InnoTech' } });
    fireEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() => {
      expect(getCompaniesSpy).toHaveBeenCalledWith('InnoTech', 0, 12);
    });
  });

  it('displays empty state when no companies are found', async () => {
    vi.spyOn(companiesApi, 'getPublicCompanies').mockResolvedValue({
      data: [],
      pagination: { page: 0, size: 12, totalElements: 0, totalPages: 0 },
    });

    render(
      <MemoryRouter initialEntries={['/companies']}>
        <CompaniesPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText(/no companies found/i)).toBeInTheDocument();
    });
  });

  it('displays error state when API request fails', async () => {
    vi.spyOn(companiesApi, 'getPublicCompanies').mockRejectedValue(new Error('Network error'));

    render(
      <MemoryRouter initialEntries={['/companies']}>
        <CompaniesPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText(/unable to load companies/i)).toBeInTheDocument();
      expect(screen.getByText(/network error/i)).toBeInTheDocument();
    });
  });
});
