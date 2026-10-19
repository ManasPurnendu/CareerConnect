import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AdminAuditPage } from '../AdminAuditPage';
import { adminApi } from '../../../api/admin';
import type { AuditLogDto, PagedResponse } from '../../../types/api';

const mockLogs: AuditLogDto[] = [
  {
    id: 1,
    actorUserId: 1,
    action: 'USER_DEACTIVATED',
    entityType: 'USER',
    entityId: 45,
    metadata: '{"previousState":"ACTIVE","newState":"INACTIVE"}',
    createdAt: '2026-10-18T16:00:00Z',
  },
  {
    id: 2,
    actorUserId: 1,
    action: 'JOB_ARCHIVED',
    entityType: 'JOB',
    entityId: 102,
    metadata: '{"previousStatus":"ACTIVE","newStatus":"ARCHIVED"}',
    createdAt: '2026-10-18T16:30:00Z',
  },
];

const mockPagedLogs: PagedResponse<AuditLogDto> = {
  data: mockLogs,
  items: mockLogs,
  pagination: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
};

describe('AdminAuditPage Component', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  const renderComponent = () =>
    render(
      <MemoryRouter>
        <AdminAuditPage />
      </MemoryRouter>
    );

  it('renders immutable audit log events with actor, action badge, and metadata', async () => {
    vi.spyOn(adminApi, 'getAuditLogs').mockResolvedValueOnce(mockPagedLogs);

    renderComponent();

    expect(screen.getByText('Security Audit Trail')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getAllByText('USER_DEACTIVATED').length).toBeGreaterThan(0);
      expect(screen.getAllByText('JOB_ARCHIVED').length).toBeGreaterThan(0);
      expect(screen.getAllByText('Admin #1').length).toBe(2);
      expect(screen.getAllByText('USER').length).toBeGreaterThan(0);
      expect(screen.getAllByText('JOB').length).toBeGreaterThan(0);
    });

    // Verify metadata was parsed into structured key-value view
    expect(screen.getByText('previousState:')).toBeInTheDocument();
    expect(screen.getByText('newStatus:')).toBeInTheDocument();

    // Verify audit logs are strictly read-only: no edit or delete buttons exist
    expect(screen.queryByRole('button', { name: /delete/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /edit/i })).not.toBeInTheDocument();
  });

  it('displays empty state when no events exist', async () => {
    vi.spyOn(adminApi, 'getAuditLogs').mockResolvedValueOnce({
      data: [],
      items: [],
      pagination: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('No audit log records found')).toBeInTheDocument();
    });
  });
});
