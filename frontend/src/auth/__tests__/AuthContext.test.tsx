import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { AuthProvider, useAuth } from '../AuthContext';
import { authApi } from '../../api/auth';

const TestConsumer: React.FC = () => {
  const { user, isAuthenticated, login, logout } = useAuth();
  return (
    <div>
      <div data-testid="auth-status">{isAuthenticated ? 'LOGGED_IN' : 'LOGGED_OUT'}</div>
      <div data-testid="user-email">{user?.email || 'NONE'}</div>
      <button onClick={() => login({ email: 'test@example.com', password: 'password123' })}>
        Log In
      </button>
      <button onClick={logout}>Log Out</button>
    </div>
  );
};

describe('AuthContext and AuthProvider', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('provides unauthenticated initial state without persisting to localStorage', () => {
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    );

    expect(screen.getByTestId('auth-status')).toHaveTextContent('LOGGED_OUT');
    expect(screen.getByTestId('user-email')).toHaveTextContent('NONE');
  });

  it('updates state on successful login and clears on logout', async () => {
    vi.spyOn(authApi, 'login').mockResolvedValueOnce({
      accessToken: 'jwt-123',
      tokenType: 'Bearer',
      expiresAt: '2026-10-20T00:00:00Z',
      user: {
        id: 1,
        email: 'test@example.com',
        role: 'CANDIDATE',
        fullName: 'Test Candidate',
        isActive: true,
      },
    });
    vi.spyOn(authApi, 'logout').mockResolvedValueOnce(undefined);

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    );

    fireEvent.click(screen.getByText('Log In'));

    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('LOGGED_IN');
      expect(screen.getByTestId('user-email')).toHaveTextContent('test@example.com');
    });

    fireEvent.click(screen.getByText('Log Out'));

    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('LOGGED_OUT');
      expect(screen.getByTestId('user-email')).toHaveTextContent('NONE');
    });
  });
});
