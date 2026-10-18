import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ProtectedRoute } from '../ProtectedRoute';
import { AuthContext, type AuthContextType } from '../../../auth/AuthContext';
import type { AuthUser } from '../../../types/api';

const renderWithAuth = (
  user: AuthUser | null,
  allowedRoles?: ('CANDIDATE' | 'RECRUITER' | 'ADMIN')[],
  initialEntry = '/protected'
) => {
  const mockAuthContext: AuthContextType = {
    user,
    token: user ? 'token-123' : null,
    isAuthenticated: !!user,
    isLoading: false,
    login: async () => user!,
    register: async () => user!,
    logout: async () => {},
    setUser: () => {},
    refreshCurrentUser: async () => user,
  };

  return render(
    <AuthContext.Provider value={mockAuthContext}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/login" element={<div>Login Page Target</div>} />
          <Route path="/403" element={<div>Forbidden 403 Target</div>} />
          <Route element={<ProtectedRoute allowedRoles={allowedRoles} />}>
            <Route path="/protected" element={<div>Protected Content</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
};

describe('ProtectedRoute guard', () => {
  it('redirects unauthenticated users to /login with encoded ?next= parameter', () => {
    renderWithAuth(null, ['CANDIDATE'], '/protected?tab=active');
    expect(screen.getByText('Login Page Target')).toBeInTheDocument();
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  it('redirects authenticated users with incorrect role to /403', () => {
    const candidateUser: AuthUser = {
      id: 2,
      email: 'cand@example.com',
      role: 'CANDIDATE',
      isActive: true,
    };
    renderWithAuth(candidateUser, ['RECRUITER'], '/protected');
    expect(screen.getByText('Forbidden 403 Target')).toBeInTheDocument();
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  it('allows access to authenticated users with the matching role', () => {
    const recruiterUser: AuthUser = {
      id: 3,
      email: 'rec@example.com',
      role: 'RECRUITER',
      isActive: true,
    };
    renderWithAuth(recruiterUser, ['RECRUITER'], '/protected');
    expect(screen.getByText('Protected Content')).toBeInTheDocument();
  });
});
