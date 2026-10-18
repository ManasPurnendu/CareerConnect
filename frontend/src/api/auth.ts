import { apiClient, setAuthToken } from './client';
import type { AuthResponseData, AuthUser } from '../types/api';

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RegisterPayload {
  email: string;
  password: string;
  role: 'CANDIDATE' | 'RECRUITER';
  fullName?: string;
  companyName?: string;
}

export const authApi = {
  login: async (credentials: LoginPayload): Promise<AuthResponseData> => {
    const data = await apiClient.post<AuthResponseData>('/auth/login', credentials);
    if (data?.token) {
      setAuthToken(data.token);
    }
    return data;
  },

  register: async (payload: RegisterPayload): Promise<AuthResponseData> => {
    const data = await apiClient.post<AuthResponseData>('/auth/register', payload);
    if (data?.token) {
      setAuthToken(data.token);
    }
    return data;
  },

  getCurrentUser: async (): Promise<AuthUser> => {
    return apiClient.get<AuthUser>('/auth/me');
  },

  logout: (): void => {
    setAuthToken(null);
  },
};
