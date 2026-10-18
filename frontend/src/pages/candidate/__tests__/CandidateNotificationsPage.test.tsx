import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { CandidateNotificationsPage } from '../CandidateNotificationsPage';
import { candidateApi } from '../../../api/candidate';
import type { NotificationSummaryResponse } from '../../../types/api';

const mockNotificationsResponse: NotificationSummaryResponse = {
  items: [
    {
      id: 1,
      type: 'APPLICATION_STATUS_CHANGED',
      title: 'Status Updated to Shortlisted',
      body: 'Congratulations, your application for Frontend Engineer was shortlisted.',
      resourceType: 'APPLICATION',
      resourceId: 42,
      createdAt: '2026-10-18T10:00:00Z',
      read: false,
    },
    {
      id: 2,
      type: 'JOB_CLOSED',
      title: 'Job Position Closed',
      body: 'The position Trainee at Acme has been filled.',
      resourceType: 'JOB',
      resourceId: 5,
      createdAt: '2026-10-17T09:00:00Z',
      read: true,
      readAt: '2026-10-17T12:00:00Z',
    },
  ],
  pagination: {
    page: 0,
    size: 10,
    totalElements: 2,
    totalPages: 1,
  },
  unreadCount: 1,
};

describe('CandidateNotificationsPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders notifications list and unread count badge', async () => {
    vi.spyOn(candidateApi, 'getNotifications').mockResolvedValue(mockNotificationsResponse);

    render(
      <MemoryRouter>
        <CandidateNotificationsPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Status Updated to Shortlisted')).toBeInTheDocument();
      expect(screen.getByText('Job Position Closed')).toBeInTheDocument();
      expect(screen.getByText('1 new')).toBeInTheDocument();
    });
  });

  it('marks a single notification as read when clicking mark read', async () => {
    vi.spyOn(candidateApi, 'getNotifications').mockResolvedValue(mockNotificationsResponse);
    const markReadSpy = vi.spyOn(candidateApi, 'markNotificationRead').mockResolvedValue({
      ...mockNotificationsResponse.items[0],
      read: true,
      readAt: '2026-10-18T12:00:00Z',
    });

    render(
      <MemoryRouter>
        <CandidateNotificationsPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Status Updated to Shortlisted')).toBeInTheDocument();
    });

    const markReadBtn = screen.getByRole('button', { name: /mark read/i });
    fireEvent.click(markReadBtn);

    await waitFor(() => {
      expect(markReadSpy).toHaveBeenCalledWith(1);
    });
  });

  it('marks all notifications as read when clicking Mark all as read button', async () => {
    vi.spyOn(candidateApi, 'getNotifications').mockResolvedValue(mockNotificationsResponse);
    const markAllSpy = vi.spyOn(candidateApi, 'markAllNotificationsRead').mockResolvedValue({
      markedReadCount: 1,
    });

    render(
      <MemoryRouter>
        <CandidateNotificationsPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /mark all as read/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /mark all as read/i }));

    await waitFor(() => {
      expect(markAllSpy).toHaveBeenCalled();
    });
  });
});
