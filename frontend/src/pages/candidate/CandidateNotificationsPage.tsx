import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { candidateApi } from '../../api/candidate';
import type { NotificationDto, NotificationType } from '../../types/api';
import { Button } from '../../components/ui/Button';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { EmptyState } from '../../components/ui/EmptyState';
import { Pagination } from '../../components/ui/Pagination';
import { Alert } from '../../components/ui/Alert';

export const CandidateNotificationsPage: React.FC = () => {
  const [notifications, setNotifications] = useState<NotificationDto[]>([]);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [unreadOnly, setUnreadOnly] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isMarkingAll, setIsMarkingAll] = useState<boolean>(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const fetchNotifications = useCallback(async (pageIndex: number, unreadFilter: boolean) => {
    setIsLoading(true);
    try {
      const response = await candidateApi.getNotifications(
        pageIndex,
        15,
        unreadFilter ? true : undefined
      );
      if (response) {
        setNotifications(response.items || []);
        setUnreadCount(response.unreadCount || 0);
        setTotalPages(response.pagination?.totalPages || 1);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Unable to load notifications.';
      setFeedback({ type: 'error', message: msg });
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchNotifications(page, unreadOnly);
  }, [page, unreadOnly, fetchNotifications]);

  const handleMarkAsRead = async (id: number) => {
    try {
      const updated = await candidateApi.markNotificationRead(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true, readAt: updated.readAt } : n))
      );
      setUnreadCount((prev) => Math.max(0, prev - 1));
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to mark notification as read.';
      setFeedback({ type: 'error', message: msg });
    }
  };

  const handleMarkAllAsRead = async () => {
    setIsMarkingAll(true);
    setFeedback(null);
    try {
      const res = await candidateApi.markAllNotificationsRead();
      setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
      setUnreadCount(0);
      setFeedback({
        type: 'success',
        message: `Marked ${res.markedReadCount} notifications as read.`,
      });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to mark all as read.';
      setFeedback({ type: 'error', message: msg });
    } finally {
      setIsMarkingAll(false);
    }
  };

  const getNotificationIcon = (type: NotificationType) => {
    switch (type) {
      case 'APPLICATION_STATUS_CHANGED':
        return 'assignment_turned_in';
      case 'JOB_CLOSED':
        return 'event_busy';
      case 'APPLICATION_RECEIVED':
        return 'inbox';
      case 'NEW_JOB_POSTED':
        return 'campaign';
      default:
        return 'notifications';
    }
  };

  return (
    <div className="space-y-6 max-w-3xl">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="font-headline font-bold text-2xl text-slate-text">Notifications</h1>
            {unreadCount > 0 && (
              <span className="px-2 py-0.5 rounded-full bg-primary-tint text-primary font-bold text-xs">
                {unreadCount} new
              </span>
            )}
          </div>
          <p className="text-xs text-slate-muted mt-0.5">
            Real-time updates regarding application status changes and job lifecycle alerts.
          </p>
        </div>

        {unreadCount > 0 && (
          <Button
            variant="outline"
            size="sm"
            onClick={handleMarkAllAsRead}
            isLoading={isMarkingAll}
            className="flex items-center gap-1.5 shrink-0"
          >
            <span className="material-symbols-outlined !text-[16px]">done_all</span>
            Mark All as Read
          </Button>
        )}
      </div>

      {feedback && (
        <Alert
          variant={feedback.type === 'success' ? 'success' : 'error'}
          onClose={() => setFeedback(null)}
        >
          {feedback.message}
        </Alert>
      )}

      {/* Filter Tabs */}
      <div role="tablist" aria-label="Notification filters" className="flex items-center gap-2 border-b border-outline-warm pb-2">
        <button
          type="button"
          role="tab"
          aria-selected={!unreadOnly}
          onClick={() => {
            setUnreadOnly(false);
            setPage(0);
          }}
          className={`min-h-[44px] px-3.5 py-2 text-xs font-medium rounded transition-colors ${
            !unreadOnly
              ? 'bg-primary text-white font-semibold shadow-subtle'
              : 'text-slate-muted hover:text-slate-text hover:bg-slate-bg'
          }`}
        >
          All Alerts
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={unreadOnly}
          onClick={() => {
            setUnreadOnly(true);
            setPage(0);
          }}
          className={`min-h-[44px] px-3.5 py-2 text-xs font-medium rounded transition-colors flex items-center gap-1.5 ${
            unreadOnly
              ? 'bg-primary text-white font-semibold shadow-subtle'
              : 'text-slate-muted hover:text-slate-text hover:bg-slate-bg'
          }`}
        >
          <span>Unread Only</span>
          {unreadCount > 0 && (
            <span
              className={`px-1.5 py-0.5 rounded-full text-[10px] font-bold ${
                unreadOnly ? 'bg-white text-primary' : 'bg-primary-tint text-primary'
              }`}
            >
              {unreadCount}
            </span>
          )}
        </button>
      </div>

      {/* Notification List */}
      <Card>
        <CardHeader className="py-4 px-6 border-b border-outline-warm">
          <h2 className="font-headline font-semibold text-base text-slate-text">
            {unreadOnly ? 'Unread Alerts' : 'Alert History'}
          </h2>
        </CardHeader>
        <CardBody className="p-0">
          {isLoading ? (
            <div className="p-6 space-y-3">
              <Skeleton height="56px" />
              <Skeleton height="56px" />
              <Skeleton height="56px" />
            </div>
          ) : notifications.length === 0 ? (
            <EmptyState
              icon="notifications_none"
              title={unreadOnly ? 'No unread notifications' : 'No notifications yet'}
              description={
                unreadOnly
                  ? 'You are all caught up! Switch to "All Alerts" to view historical notifications.'
                  : 'You have no notifications at this time.'
              }
            />
          ) : (
            <>
              <div className="divide-y divide-outline-warm/70">
                {notifications.map((item) => (
                  <div
                    key={item.id}
                    className={`p-4 sm:p-5 flex items-start justify-between gap-4 transition-colors ${
                      !item.read ? 'bg-[#F4F8F5]' : 'hover:bg-[#F9FAF9]'
                    }`}
                  >
                    <div className="flex items-start gap-3.5 flex-1 min-w-0">
                      <div
                        className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0 mt-0.5 ${
                          !item.read
                            ? 'bg-primary text-white'
                            : 'bg-[#F0F2F0] text-slate-muted'
                        }`}
                      >
                        <span className="material-symbols-outlined !text-lg">
                          {getNotificationIcon(item.type)}
                        </span>
                      </div>
                      <div className="space-y-1 flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <h4 className="font-headline font-semibold text-xs sm:text-sm text-slate-text">
                            {item.title}
                          </h4>
                          {!item.read && (
                            <span className="w-2 h-2 rounded-full bg-primary shrink-0" title="Unread" />
                          )}
                        </div>
                        <p className="text-xs text-slate-text leading-relaxed">{item.body}</p>

                        {item.resourceType === 'APPLICATION' && item.resourceId && (
                          <div className="pt-1">
                            <Link
                              to={`/candidate/applications/${item.resourceId}`}
                              className="inline-flex items-center gap-1 text-xs font-semibold text-primary hover:text-primary-dark hover:underline focus:outline-none focus:ring-1 focus:ring-primary rounded py-1"
                            >
                              <span>View Application Details</span>
                              <span className="material-symbols-outlined !text-sm">arrow_forward</span>
                            </Link>
                          </div>
                        )}
                        {item.resourceType === 'JOB' && item.resourceId && (
                          <div className="pt-1">
                            <Link
                              to={`/candidate/jobs/${item.resourceId}`}
                              className="inline-flex items-center gap-1 text-xs font-semibold text-primary hover:text-primary-dark hover:underline focus:outline-none focus:ring-1 focus:ring-primary rounded py-1"
                            >
                              <span>View Job Details</span>
                              <span className="material-symbols-outlined !text-sm">arrow_forward</span>
                            </Link>
                          </div>
                        )}

                        <p className="text-[11px] text-slate-muted">
                          {new Date(item.createdAt).toLocaleString()}
                        </p>
                      </div>
                    </div>

                    {!item.read && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleMarkAsRead(item.id)}
                        className="text-xs text-primary hover:text-primary-dark whitespace-nowrap shrink-0 min-h-[44px] px-3"
                      >
                        Mark Read
                      </Button>
                    )}
                  </div>
                ))}
              </div>

              {totalPages > 1 && (
                <div className="p-4 border-t border-outline-warm flex justify-center">
                  <Pagination
                    currentPage={page}
                    totalPages={totalPages}
                    onPageChange={(newPage) => setPage(newPage)}
                  />
                </div>
              )}
            </>
          )}
        </CardBody>
      </Card>
    </div>
  );
};
