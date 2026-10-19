import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { recruiterApi } from '../../api/recruiter';
import type { NotificationDto } from '../../types/api';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Skeleton } from '../../components/ui/Skeleton';
import { EmptyState } from '../../components/ui/EmptyState';
import { Alert } from '../../components/ui/Alert';
import { Pagination } from '../../components/ui/Pagination';

export const RecruiterNotificationsPage: React.FC = () => {
  const [notifications, setNotifications] = useState<NotificationDto[]>([]);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [page, setPage] = useState<number>(0);
  const [filterUnread, setFilterUnread] = useState<boolean>(false);

  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isMarkingAll, setIsMarkingAll] = useState<boolean>(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const fetchNotifications = async () => {
    setIsLoading(true);
    setFeedback(null);
    try {
      const response = await recruiterApi.getNotifications(
        page,
        20,
        filterUnread ? true : undefined
      );
      setNotifications(response.items || []);
      setUnreadCount(response.unreadCount ?? 0);
      setTotalPages(response.pagination?.totalPages || 1);
    } catch (err: unknown) {
      setFeedback({
        type: 'error',
        message: err instanceof Error ? err.message : 'Unable to load recruiter notifications.',
      });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchNotifications();
  }, [page, filterUnread]);

  const handleMarkOneRead = async (id: number) => {
    try {
      await recruiterApi.markNotificationRead(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true, readAt: new Date().toISOString() } : n))
      );
      setUnreadCount((prev) => Math.max(0, prev - 1));
    } catch {
      setFeedback({ type: 'error', message: 'Failed to mark notification as read.' });
    }
  };

  const handleMarkAllRead = async () => {
    setIsMarkingAll(true);
    try {
      const result = await recruiterApi.markAllNotificationsRead();
      setNotifications((prev) =>
        prev.map((n) => ({ ...n, read: true, readAt: new Date().toISOString() }))
      );
      setUnreadCount(0);
      setFeedback({
        type: 'success',
        message: `Marked ${result.markedReadCount ?? 'all'} notifications as read.`,
      });
    } catch {
      setFeedback({ type: 'error', message: 'Failed to mark all notifications as read.' });
    } finally {
      setIsMarkingAll(false);
    }
  };

  return (
    <div className="space-y-6 max-w-3xl">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="font-headline font-bold text-2xl text-slate-text">Notifications</h1>
          <p className="text-xs text-slate-muted mt-0.5">
            Real-time candidate updates, application status changes, and system alerts
          </p>
        </div>

        {unreadCount > 0 && (
          <Button
            variant="outline"
            size="sm"
            onClick={handleMarkAllRead}
            isLoading={isMarkingAll}
            leftIcon={<span className="material-symbols-outlined !text-base">done_all</span>}
          >
            Mark All as Read ({unreadCount})
          </Button>
        )}
      </div>

      {feedback && (
        <Alert variant={feedback.type} onClose={() => setFeedback(null)}>
          {feedback.message}
        </Alert>
      )}

      {/* Filter Tabs */}
      <div role="tablist" aria-label="Notification filters" className="flex items-center gap-1 bg-white p-1 rounded border border-outline-warm shadow-subtle w-fit">
        <button
          type="button"
          role="tab"
          aria-selected={!filterUnread}
          onClick={() => {
            setFilterUnread(false);
            setPage(0);
          }}
          className={`min-h-[44px] px-3.5 py-2 rounded text-xs font-medium transition-colors ${
            !filterUnread
              ? 'bg-primary-tint text-primary font-semibold'
              : 'text-slate-muted hover:text-slate-text hover:bg-[#F2F5F3]'
          }`}
        >
          All Alerts
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={filterUnread}
          onClick={() => {
            setFilterUnread(true);
            setPage(0);
          }}
          className={`min-h-[44px] px-3.5 py-2 rounded text-xs font-medium transition-colors flex items-center gap-1.5 ${
            filterUnread
              ? 'bg-primary-tint text-primary font-semibold'
              : 'text-slate-muted hover:text-slate-text hover:bg-[#F2F5F3]'
          }`}
        >
          <span>Unread</span>
          {unreadCount > 0 && (
            <span className="w-5 h-5 rounded-full bg-primary text-white text-[10px] font-bold flex items-center justify-center">
              {unreadCount}
            </span>
          )}
        </button>
      </div>

      {/* Notifications List Card */}
      <Card>
        <CardHeader className="flex items-center justify-between">
          <h2 className="font-headline font-semibold text-base text-slate-text">
            {filterUnread ? 'Unread Alerts' : 'All Notifications'} ({notifications.length})
          </h2>
        </CardHeader>

        <CardBody className="p-0">
          {isLoading ? (
            <div className="p-4 space-y-3">
              <Skeleton height="60px" />
              <Skeleton height="60px" />
              <Skeleton height="60px" />
            </div>
          ) : notifications.length === 0 ? (
            <EmptyState
              icon="notifications_off"
              title={filterUnread ? 'No Unread Notifications' : 'No Notifications'}
              description={
                filterUnread
                  ? 'You are all caught up! There are no unread notifications.'
                  : 'You will receive notifications when candidates apply to jobs or system events occur.'
              }
              actionLabel={filterUnread ? 'Show All Notifications' : undefined}
              onAction={filterUnread ? () => setFilterUnread(false) : undefined}
            />
          ) : (
            <div className="divide-y divide-outline-warm/70">
              {notifications.map((n) => (
                <div
                  key={n.id}
                  className={`p-4 sm:p-5 flex items-start justify-between gap-4 transition-colors ${
                    n.read ? 'bg-white hover:bg-[#FAFAF8]' : 'bg-primary-tint/25 hover:bg-primary-tint/40'
                  }`}
                >
                  <div className="flex items-start gap-3 flex-1 min-w-0">
                    <div
                      className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0 mt-0.5 ${
                        n.read
                          ? 'bg-[#F3F4F6] text-slate-muted'
                          : 'bg-primary-tint border border-[#A7F3D0] text-primary'
                      }`}
                    >
                      <span className="material-symbols-outlined !text-lg">
                        {n.type === 'APPLICATION_STATUS_CHANGED'
                          ? 'published_with_changes'
                          : n.type === 'JOB_CLOSED'
                          ? 'lock'
                          : 'notifications'}
                      </span>
                    </div>

                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-sm text-slate-text">{n.title}</span>
                        {!n.read && (
                          <span className="w-2 h-2 rounded-full bg-primary shrink-0" title="Unread" />
                        )}
                      </div>
                      <p className="text-xs text-slate-text mt-1 leading-relaxed whitespace-pre-wrap">
                        {n.body}
                      </p>

                      {n.resourceType === 'APPLICATION' && n.resourceId && (
                        <div className="pt-1">
                          <Link
                            to={`/recruiter/applications/${n.resourceId}`}
                            className="inline-flex items-center gap-1 text-xs font-semibold text-primary hover:text-primary-dark hover:underline focus:outline-none focus:ring-1 focus:ring-primary rounded py-1"
                          >
                            <span>View Application Details</span>
                            <span className="material-symbols-outlined !text-sm">arrow_forward</span>
                          </Link>
                        </div>
                      )}
                      {n.resourceType === 'JOB' && n.resourceId && (
                        <div className="pt-1">
                          <Link
                            to={`/recruiter/jobs/${n.resourceId}`}
                            className="inline-flex items-center gap-1 text-xs font-semibold text-primary hover:text-primary-dark hover:underline focus:outline-none focus:ring-1 focus:ring-primary rounded py-1"
                          >
                            <span>View Job Details</span>
                            <span className="material-symbols-outlined !text-sm">arrow_forward</span>
                          </Link>
                        </div>
                      )}

                      <span className="text-[10px] font-mono text-slate-muted block mt-1.5">
                        {new Date(n.createdAt).toLocaleString()}
                      </span>
                    </div>
                  </div>

                  {!n.read && (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleMarkOneRead(n.id)}
                      title="Mark as read"
                      aria-label="Mark as read"
                      className="shrink-0 text-slate-muted hover:text-primary min-w-[44px] min-h-[44px] flex items-center justify-center p-2"
                    >
                      <span className="material-symbols-outlined !text-base">check</span>
                    </Button>
                  )}
                </div>
              ))}
            </div>
          )}
        </CardBody>
      </Card>

      {/* Pagination */}
      {totalPages > 1 && (
        <Pagination
          currentPage={page}
          totalPages={totalPages}
          onPageChange={(newPage) => setPage(newPage)}
        />
      )}
    </div>
  );
};
