import { apiClient, setAuthToken } from './client';
import type { AuthResponseData, AuthUser, Role } from '../types/api';

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RegisterPayload {
  email: string;
  password: string;
  role: Role;
  fullName: string;
}

export const authApi = {
  login: async (credentials: LoginPayload): Promise<AuthResponseData> => {
    const data = await apiClient.post<AuthResponseData>('/auth/login', credentials);
    if (data?.accessToken) {
      setAuthToken(data.accessToken);
    }
    return data;
  },

  register: async (payload: RegisterPayload): Promise<AuthResponseData> => {
    const data = await apiClient.post<AuthResponseData>('/auth/register', payload);
    if (data?.accessToken) {
      setAuthToken(data.accessToken);
    }
    return data;
  },

  getCurrentUser: async (): Promise<AuthUser> => {
    return apiClient.get<AuthUser>('/auth/me');
  },

  logout: async (): Promise<void> => {
    try {
      await apiClient.post<void>('/auth/logout');
    } finally {
      setAuthToken(null);
    }
  },
};
