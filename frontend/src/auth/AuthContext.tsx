import React, { createContext, useContext, useState, useCallback, type ReactNode } from 'react';
import type { AuthUser } from '../types/api';
import { authApi, type LoginPayload, type RegisterPayload } from '../api/auth';
import { setAuthToken } from '../api/client';

export interface AuthContextType {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (credentials: LoginPayload) => Promise<AuthUser>;
  register: (payload: RegisterPayload) => Promise<AuthUser>;
  logout: () => Promise<void>;
  setUser: React.Dispatch<React.SetStateAction<AuthUser | null>>;
  refreshCurrentUser: () => Promise<AuthUser | null>;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  // In-memory state only per frozen security architecture
  const [user, setUser] = useState<AuthUser | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);

  const login = useCallback(async (credentials: LoginPayload): Promise<AuthUser> => {
    setIsLoading(true);
    try {
      const response = await authApi.login(credentials);
      const jwt = response.accessToken;
      setToken(jwt);
      setAuthToken(jwt);
      setUser(response.user);
      return response.user;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const register = useCallback(async (payload: RegisterPayload): Promise<AuthUser> => {
    setIsLoading(true);
    try {
      const response = await authApi.register(payload);
      const jwt = response.accessToken;
      setToken(jwt);
      setAuthToken(jwt);
      setUser(response.user);
      return response.user;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setToken(null);
      setAuthToken(null);
      setUser(null);
    }
  }, []);

  const refreshCurrentUser = useCallback(async (): Promise<AuthUser | null> => {
    if (!token) return null;
    try {
      const currentUser = await authApi.getCurrentUser();
      setUser(currentUser);
      return currentUser;
    } catch {
      // If /me fails, clear in-memory state
      setToken(null);
      setAuthToken(null);
      setUser(null);
      return null;
    }
  }, [token]);

  const value: AuthContextType = {
    user,
    token,
    isAuthenticated: !!user && !!token,
    isLoading,
    login,
    register,
    logout,
    setUser,
    refreshCurrentUser,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
