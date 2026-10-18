import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RegisterPage } from '../RegisterPage';
import { AuthContext, type AuthContextType } from '../../../auth/AuthContext';
import type { AuthUser } from '../../../types/api';

const renderRegisterPage = (mockRegister: ReturnType<typeof vi.fn>) => {
  const mockContext: AuthContextType = {
    user: null,
    token: null,
    isAuthenticated: false,
    isLoading: false,
    login: vi.fn(),
    register: mockRegister as unknown as AuthContextType['register'],
    logout: vi.fn(),
    setUser: vi.fn(),
    refreshCurrentUser: vi.fn(),
  };

  return render(
    <AuthContext.Provider value={mockContext}>
      <MemoryRouter initialEntries={['/register']}>
        <Routes>
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/candidate" element={<div>Candidate Dashboard</div>} />
          <Route path="/recruiter" element={<div>Recruiter Dashboard</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  );
};

describe('RegisterPage Integration', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders registration form with role selector and inputs', () => {
    renderRegisterPage(vi.fn());

    expect(screen.getByRole('heading', { name: /create your account/i })).toBeInTheDocument();
    expect(screen.getByText(/find jobs \/ internships/i)).toBeInTheDocument();
    expect(screen.getByText(/hire early talent/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/full name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument();
  });

  it('validates empty name or short password', async () => {
    const mockRegister = vi.fn();
    renderRegisterPage(mockRegister);

    fireEvent.click(screen.getByRole('button', { name: /create account/i }));
    expect(screen.getByText(/please provide your full name/i)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/full name/i), { target: { value: 'Alex Johnson' } });
    fireEvent.change(screen.getByLabelText(/email address/i), { target: { value: 'alex@test.com' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'short' } });
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    expect(screen.getByText(/password must be at least 8 characters long/i)).toBeInTheDocument();
    expect(mockRegister).not.toHaveBeenCalled();
  });

  it('successfully registers candidate and navigates to candidate dashboard', async () => {
    const candidateUser: AuthUser = {
      id: 20,
      email: 'cand@test.com',
      role: 'CANDIDATE',
      fullName: 'New Candidate',
      isActive: true,
    };
    const mockRegister = vi.fn().mockResolvedValueOnce(candidateUser);

    renderRegisterPage(mockRegister);

    fireEvent.change(screen.getByLabelText(/full name/i), { target: { value: 'New Candidate' } });
    fireEvent.change(screen.getByLabelText(/email address/i), { target: { value: 'cand@test.com' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    expect(mockRegister).toHaveBeenCalledWith({
      email: 'cand@test.com',
      password: 'password123',
      role: 'CANDIDATE',
      fullName: 'New Candidate',
    });

    await waitFor(() => {
      expect(screen.getByText('Candidate Dashboard')).toBeInTheDocument();
    });
  });

  it('successfully registers recruiter and navigates to recruiter dashboard', async () => {
    const recruiterUser: AuthUser = {
      id: 21,
      email: 'recruiter@test.com',
      role: 'RECRUITER',
      fullName: 'Hiring Lead',
      isActive: true,
    };
    const mockRegister = vi.fn().mockResolvedValueOnce(recruiterUser);

    renderRegisterPage(mockRegister);

    fireEvent.click(screen.getByText(/hire early talent/i));
    fireEvent.change(screen.getByLabelText(/contact person name/i), { target: { value: 'Hiring Lead' } });
    fireEvent.change(screen.getByLabelText(/email address/i), { target: { value: 'recruiter@test.com' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    expect(mockRegister).toHaveBeenCalledWith({
      email: 'recruiter@test.com',
      password: 'password123',
      role: 'RECRUITER',
      fullName: 'Hiring Lead',
    });

    await waitFor(() => {
      expect(screen.getByText('Recruiter Dashboard')).toBeInTheDocument();
    });
  });

  it('displays conflict error message when registration fails', async () => {
    const mockRegister = vi.fn().mockRejectedValueOnce(new Error('An account with this email address already exists.'));

    renderRegisterPage(mockRegister);

    fireEvent.change(screen.getByLabelText(/full name/i), { target: { value: 'Jane Doe' } });
    fireEvent.change(screen.getByLabelText(/email address/i), { target: { value: 'existing@test.com' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    await waitFor(() => {
      expect(screen.getByText('An account with this email address already exists.')).toBeInTheDocument();
    });
  });
});
