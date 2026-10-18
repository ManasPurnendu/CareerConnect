import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { LoginPage } from '../LoginPage';
import { AuthContext, type AuthContextType } from '../../../auth/AuthContext';
import type { AuthUser } from '../../../types/api';

const renderLoginPage = (mockLogin: ReturnType<typeof vi.fn>, initialEntry = '/login') => {
  const mockContext: AuthContextType = {
    user: null,
    token: null,
    isAuthenticated: false,
    isLoading: false,
    login: mockLogin as unknown as AuthContextType['login'],
    register: vi.fn(),
    logout: vi.fn(),
    setUser: vi.fn(),
    refreshCurrentUser: vi.fn(),
  };

  return render(
    <AuthContext.Provider value={mockContext}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/candidate" element={<div>Candidate Dashboard</div>} />
          <Route path="/recruiter" element={<div>Recruiter Dashboard</div>} />
          <Route path="/candidate/applications" element={<div>Saved Application Flow</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
};

describe('LoginPage Integration', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders login form with email and password inputs', () => {
    renderLoginPage(vi.fn());

    expect(screen.getByRole('heading', { name: /sign in to your account/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('validates empty inputs and displays error message', async () => {
    const mockLogin = vi.fn();
    renderLoginPage(mockLogin);

    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(screen.getByText(/please provide both email address and password/i)).toBeInTheDocument();
    expect(mockLogin).not.toHaveBeenCalled();
  });

  it('submits credentials and routes candidate to /candidate when next is absent', async () => {
    const candidateUser: AuthUser = {
      id: 10,
      email: 'cand@test.com',
      role: 'CANDIDATE',
      fullName: 'Alex Johnson',
      isActive: true,
    };
    const mockLogin = vi.fn().mockResolvedValueOnce(candidateUser);

    renderLoginPage(mockLogin);

    fireEvent.change(screen.getByLabelText(/email address/i), { target: { value: 'cand@test.com' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'Secret123!' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(mockLogin).toHaveBeenCalledWith({ email: 'cand@test.com', password: 'Secret123!' });

    await waitFor(() => {
      expect(screen.getByText('Candidate Dashboard')).toBeInTheDocument();
    });
  });

  it('preserves and navigates to ?next= destination upon successful login', async () => {
    const candidateUser: AuthUser = {
      id: 10,
      email: 'cand@test.com',
      role: 'CANDIDATE',
      fullName: 'Alex Johnson',
      isActive: true,
    };
    const mockLogin = vi.fn().mockResolvedValueOnce(candidateUser);

    renderLoginPage(mockLogin, '/login?next=%2Fcandidate%2Fapplications');

    fireEvent.change(screen.getByLabelText(/email address/i), { target: { value: 'cand@test.com' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'Secret123!' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByText('Saved Application Flow')).toBeInTheDocument();
    });
  });

  it('displays error banner when login fails with invalid credentials', async () => {
    const mockLogin = vi.fn().mockRejectedValueOnce(new Error('Invalid email or password.'));

    renderLoginPage(mockLogin);

    fireEvent.change(screen.getByLabelText(/email address/i), { target: { value: 'bad@test.com' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'wrongpass' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByText('Invalid email or password.')).toBeInTheDocument();
    });
  });
});
