import { apiClient } from './client';
import type { CompanySummaryDto, CompanyDetailDto, PagedResponse } from '../types/api';

export const companiesApi = {
  getPublicCompanies: async (
    q?: string,
    page = 0,
    size = 20
  ): Promise<PagedResponse<CompanySummaryDto>> => {
    return apiClient.get<PagedResponse<CompanySummaryDto>>('/companies', {
      params: { q: q || undefined, page, size },
    });
  },

  getCompanyById: async (companyId: number | string): Promise<CompanyDetailDto> => {
    return apiClient.get<CompanyDetailDto>(`/companies/${companyId}`);
  },
};
