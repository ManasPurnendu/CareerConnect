import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AdminUsersPage } from '../AdminUsersPage';
import { adminApi } from '../../../api/admin';
import { AuthContext, type AuthContextType } from '../../../auth/AuthContext';
import type { AdminUserDto, PagedResponse, AuthUser } from '../../../types/api';

const mockCurrentAdmin: AuthUser = {
  id: 1,
  email: 'admin@careerconnect.com',
  role: 'ADMIN',
  fullName: 'Main Administrator',
  isActive: true,
};

const mockAuthContext: AuthContextType = {
  user: mockCurrentAdmin,
  token: 'mock-admin-token',
  isAuthenticated: true,
  isLoading: false,
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  setUser: vi.fn(),
  refreshCurrentUser: vi.fn(),
};

const mockUsers: AdminUserDto[] = [
  {
    id: 1,
    email: 'admin@careerconnect.com',
    role: 'ADMIN',
    fullName: 'Main Administrator',
    active: true,
    createdAt: '2026-10-14T21:13:26.492Z',
    updatedAt: '2026-10-14T21:13:26.492Z',
  },
  {
    id: 2,
    email: 'candidate@example.com',
    role: 'CANDIDATE',
    fullName: 'Sarah Candidate',
    active: true,
    candidateId: 10,
    createdAt: '2026-10-15T10:00:00Z',
    updatedAt: '2026-10-15T10:00:00Z',
  },
  {
    id: 3,
    email: 'recruiter@example.com',
    role: 'RECRUITER',
    fullName: 'Alex Recruiter',
    active: false,
    recruiterId: 5,
    companyId: 2,
    companyName: 'Acme Technologies',
    createdAt: '2026-10-16T12:00:00Z',
    updatedAt: '2026-10-16T12:00:00Z',
  },
];

const mockPagedUsers: PagedResponse<AdminUserDto> = {
  data: mockUsers,
  items: mockUsers,
  pagination: { page: 0, size: 15, totalElements: 3, totalPages: 1 },
};

describe('AdminUsersPage Component', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  const renderComponent = () =>
    render(
      <AuthContext.Provider value={mockAuthContext}>
        <MemoryRouter>
          <AdminUsersPage />
        </MemoryRouter>
      </AuthContext.Provider>
    );

  it('renders users list, role badges, and accounts', async () => {
    vi.spyOn(adminApi, 'getUsers').mockResolvedValueOnce(mockPagedUsers);

    renderComponent();

    expect(screen.getByText('User Administration')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Main Administrator')).toBeInTheDocument();
      expect(screen.getByText('Sarah Candidate')).toBeInTheDocument();
      expect(screen.getByText('Alex Recruiter')).toBeInTheDocument();
      expect(screen.getByText('Acme Technologies')).toBeInTheDocument();
    });
  });

  it('prevents self-deactivation: disables action button for currently logged-in admin', async () => {
    vi.spyOn(adminApi, 'getUsers').mockResolvedValueOnce(mockPagedUsers);

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Main Administrator')).toBeInTheDocument();
    });

    expect(screen.getByText('Self-deactivation disabled')).toBeInTheDocument();

    // The first deactivate button (for current admin) must be disabled
    const buttons = screen.getAllByRole('button', { name: /deactivate/i });
    expect(buttons[0]).toBeDisabled();

    // The second deactivate button (for Sarah Candidate) is enabled
    expect(buttons[1]).toBeEnabled();
  });

  it('opens confirmation modal and updates user status upon confirmation', async () => {
    vi.spyOn(adminApi, 'getUsers').mockResolvedValueOnce(mockPagedUsers);
    const updateSpy = vi.spyOn(adminApi, 'updateUserStatus').mockResolvedValueOnce({
      ...mockUsers[1],
      active: false,
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Sarah Candidate')).toBeInTheDocument();
    });

    const candidateDeactivateBtn = screen.getAllByRole('button', { name: /deactivate/i })[1];
    fireEvent.click(candidateDeactivateBtn);

    expect(screen.getByText('Confirm User Deactivation')).toBeInTheDocument();
    expect(screen.getByText(/Are you sure you want to deactivate user account/i)).toBeInTheDocument();

    const confirmBtn = screen.getByRole('button', { name: /confirm & deactivate/i });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith(2, false);
      expect(screen.getByText(/is now Deactivated/i)).toBeInTheDocument();
    });
  });

  it('displays real backend error message when mutation fails', async () => {
    vi.spyOn(adminApi, 'getUsers').mockResolvedValueOnce(mockPagedUsers);
    vi.spyOn(adminApi, 'updateUserStatus').mockRejectedValueOnce(
      new Error('User is already inactive')
    );

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Sarah Candidate')).toBeInTheDocument();
    });

    const candidateDeactivateBtn = screen.getAllByRole('button', { name: /deactivate/i })[1];
    fireEvent.click(candidateDeactivateBtn);

    const confirmBtn = screen.getByRole('button', { name: /confirm & deactivate/i });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(screen.getByText('User is already inactive')).toBeInTheDocument();
    });
  });
});
