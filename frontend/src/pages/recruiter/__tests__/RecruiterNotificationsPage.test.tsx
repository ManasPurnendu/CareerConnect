import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { RecruiterNotificationsPage } from '../RecruiterNotificationsPage';
import { recruiterApi } from '../../../api/recruiter';
import type { NotificationSummaryResponse } from '../../../types/api';

const mockNotifications: NotificationSummaryResponse = {
  items: [
    {
      id: 10,
      type: 'APPLICATION_STATUS_CHANGED',
      title: 'New Applicant Received',
      body: 'Samantha Ray applied for Senior Systems Engineer.',
      resourceType: 'APPLICATION',
      resourceId: 99,
      createdAt: '2026-10-18T14:00:00Z',
      read: false,
    },
    {
      id: 11,
      type: 'JOB_CLOSED',
      title: 'Job Closed Automatically',
      body: 'Application deadline has passed for Junior Cloud Developer.',
      resourceType: 'JOB',
      resourceId: 30,
      createdAt: '2026-10-18T12:00:00Z',
      read: true,
    },
  ],
  pagination: { page: 0, size: 20, totalElements: 2, totalPages: 1 },
  unreadCount: 1,
};

const renderNotificationsPage = () => {
  return render(
    <MemoryRouter>
      <RecruiterNotificationsPage />
    </MemoryRouter>
  );
};

describe('RecruiterNotificationsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders loading skeleton initially', () => {
    vi.spyOn(recruiterApi, 'getNotifications').mockReturnValue(new Promise(() => {}));
    const { container } = renderNotificationsPage();
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('renders notifications with read/unread indicators', async () => {
    vi.spyOn(recruiterApi, 'getNotifications').mockResolvedValue(mockNotifications);
    renderNotificationsPage();

    await waitFor(() => {
      expect(screen.getByText('New Applicant Received')).toBeInTheDocument();
      expect(screen.getByText('Job Closed Automatically')).toBeInTheDocument();
    });
  });

  it('marks individual notification as read', async () => {
    vi.spyOn(recruiterApi, 'getNotifications').mockResolvedValue(mockNotifications);
    const markReadSpy = vi.spyOn(recruiterApi, 'markNotificationRead').mockResolvedValue(mockNotifications.items[0]);

    renderNotificationsPage();

    await waitFor(() => {
      expect(screen.getByText('New Applicant Received')).toBeInTheDocument();
    });

    const markReadBtn = screen.getByRole('button', { name: 'Mark as read' });
    fireEvent.click(markReadBtn);

    await waitFor(() => {
      expect(markReadSpy).toHaveBeenCalledWith(10);
    });
  });

  it('marks all notifications as read', async () => {
    vi.spyOn(recruiterApi, 'getNotifications').mockResolvedValue(mockNotifications);
    const markAllSpy = vi.spyOn(recruiterApi, 'markAllNotificationsRead').mockResolvedValue({ markedReadCount: 1 });

    renderNotificationsPage();

    await waitFor(() => {
      expect(screen.getByText('New Applicant Received')).toBeInTheDocument();
    });

    const markAllBtn = screen.getByText(/Mark All as Read/i).closest('button')!;
    fireEvent.click(markAllBtn);

    await waitFor(() => {
      expect(markAllSpy).toHaveBeenCalled();
    });
  });

  it('renders direct links to application and job resources', async () => {
    vi.spyOn(recruiterApi, 'getNotifications').mockResolvedValue(mockNotifications);
    renderNotificationsPage();

    await waitFor(() => {
      expect(screen.getByText('View Application Details')).toBeInTheDocument();
      expect(screen.getByText('View Job Details')).toBeInTheDocument();
    });

    const appLink = screen.getByText('View Application Details').closest('a');
    expect(appLink).toHaveAttribute('href', '/recruiter/applications/99');

    const jobLink = screen.getByText('View Job Details').closest('a');
    expect(jobLink).toHaveAttribute('href', '/recruiter/jobs/30');
  });

  it('filters notifications by unread tab', async () => {
    const fetchSpy = vi.spyOn(recruiterApi, 'getNotifications').mockResolvedValue(mockNotifications);
    renderNotificationsPage();

    await waitFor(() => {
      expect(screen.getByText('New Applicant Received')).toBeInTheDocument();
    });

    const unreadTab = screen.getByRole('tab', { name: /unread/i });
    fireEvent.click(unreadTab);

    await waitFor(() => {
      expect(fetchSpy).toHaveBeenCalledWith(0, 20, true);
    });
  });
});
