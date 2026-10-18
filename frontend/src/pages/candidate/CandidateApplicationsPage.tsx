import React, { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { candidateApi } from '../../api/candidate';
import type { ApplicationResponseDto } from '../../types/api';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Button } from '../../components/ui/Button';
import { Pagination } from '../../components/ui/Pagination';
import { Skeleton } from '../../components/ui/Skeleton';
import { EmptyState } from '../../components/ui/EmptyState';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Alert } from '../../components/ui/Alert';
import { Modal } from '../../components/ui/Modal';

export const CandidateApplicationsPage: React.FC = () => {
  const navigate = useNavigate();
  const [applications, setApplications] = useState<ApplicationResponseDto[]>([]);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [notification, setNotification] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Withdrawal modal state
  const [withdrawingApp, setWithdrawingApp] = useState<ApplicationResponseDto | null>(null);
  const [isWithdrawing, setIsWithdrawing] = useState<boolean>(false);

  const fetchApps = useCallback(async (pageIndex: number) => {
    setIsLoading(true);
    try {
      const response = await candidateApi.getApplications(pageIndex, 10);
      if (response?.data) {
        setApplications(response.data);
        setTotalPages(response.pagination?.totalPages ?? 1);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Unable to load applications.';
      setNotification({ type: 'error', message: msg });
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchApps(page);
  }, [page, fetchApps]);

  const handleWithdrawConfirm = async () => {
    if (!withdrawingApp) return;
    setIsWithdrawing(true);
    setNotification(null);
    try {
      const updated = await candidateApi.withdrawApplication(withdrawingApp.applicationId);
      setApplications((prev) =>
        prev.map((a) => (a.applicationId === updated.applicationId ? updated : a))
      );
      setNotification({
        type: 'success',
        message: `Application #${withdrawingApp.applicationId} for "${withdrawingApp.jobTitle}" was successfully withdrawn.`,
      });
      setWithdrawingApp(null);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to withdraw application.';
      setNotification({ type: 'error', message: msg });
    } finally {
      setIsWithdrawing(false);
    }
  };

  const isWithdrawable = (status: string) => {
    return ['APPLIED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW'].includes(status);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 className="font-headline font-bold text-2xl text-slate-text">My Applications</h1>
          <p className="text-xs text-slate-muted mt-0.5">
            Track the status, timeline, and immutable snapshots of your job applications.
          </p>
        </div>
        <Link to="/jobs">
          <Button variant="primary" size="sm" className="flex items-center gap-1.5">
            <span className="material-symbols-outlined !text-[16px]">work</span>
            Find Jobs
          </Button>
        </Link>
      </div>

      {notification && (
        <Alert
          variant={notification.type === 'success' ? 'success' : 'error'}
          onClose={() => setNotification(null)}
        >
          {notification.message}
        </Alert>
      )}

      <Card>
        <CardHeader className="py-4 px-6 border-b border-outline-warm">
          <h2 className="font-headline font-semibold text-base text-slate-text">Submitted Applications</h2>
        </CardHeader>
        <CardBody className="p-0">
          {isLoading ? (
            <div className="p-6 space-y-3">
              <Skeleton height="40px" />
              <Skeleton height="40px" />
              <Skeleton height="40px" />
            </div>
          ) : applications.length === 0 ? (
            <EmptyState
              icon="description"
              title="No applications found"
              description="You have not submitted any applications yet. Browse active job listings to get started."
              actionLabel="Browse Jobs"
              onAction={() => navigate('/jobs')}
            />
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs sm:text-sm">
                  <thead className="bg-[#FAFAF8] text-slate-muted text-[11px] uppercase tracking-wider border-b border-outline-warm">
                    <tr>
                      <th className="px-6 py-3.5">Role & Company</th>
                      <th className="px-6 py-3.5">Applied Date</th>
                      <th className="px-6 py-3.5">Current Status</th>
                      <th className="px-6 py-3.5 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-warm/70">
                    {applications.map((app) => (
                      <tr key={app.applicationId} className="hover:bg-[#F9FAF9] transition-colors">
                        <td className="px-6 py-4">
                          <Link
                            to={`/candidate/applications/${app.applicationId}`}
                            className="font-semibold text-slate-text hover:text-primary hover:underline text-sm"
                          >
                            {app.jobTitle || `Job #${app.jobId}`}
                          </Link>
                          <div className="text-xs text-slate-muted mt-0.5">{app.companyName || 'Company'}</div>
                        </td>
                        <td className="px-6 py-4 text-xs text-slate-muted whitespace-nowrap">
                          {new Date(app.appliedAt).toLocaleDateString()}
                        </td>
                        <td className="px-6 py-4">
                          <StatusBadge status={app.status} size="sm" />
                        </td>
                        <td className="px-6 py-4 text-right whitespace-nowrap">
                          <div className="flex items-center justify-end gap-2">
                            <Link to={`/candidate/applications/${app.applicationId}`}>
                              <Button variant="ghost" size="sm" className="text-xs">
                                Details
                              </Button>
                            </Link>
                            {isWithdrawable(app.status) && (
                              <button
                                type="button"
                                onClick={() => setWithdrawingApp(app)}
                                className="text-xs text-slate-muted hover:text-rose-600 transition-colors font-medium underline"
                              >
                                Withdraw
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {totalPages > 1 && (
                <div className="p-4 border-t border-outline-warm flex justify-center">
                  <Pagination
                    currentPage={page + 1}
                    totalPages={totalPages}
                    onPageChange={(newPage) => setPage(newPage - 1)}
                  />
                </div>
              )}
            </>
          )}
        </CardBody>
      </Card>

      {/* Withdrawal Confirmation Modal */}
      <Modal
        isOpen={!!withdrawingApp}
        onClose={() => setWithdrawingApp(null)}
        title="Confirm Application Withdrawal"
      >
        <div className="space-y-4">
          <p className="text-xs text-slate-text leading-relaxed">
            Are you sure you want to withdraw your application for{' '}
            <span className="font-semibold text-slate-text">{withdrawingApp?.jobTitle}</span> at{' '}
            <span className="font-semibold text-slate-text">{withdrawingApp?.companyName}</span>?
          </p>
          <div className="p-3 bg-amber-50 border border-amber-200 rounded text-xs text-amber-800 space-y-1">
            <p className="font-semibold">Notice:</p>
            <ul className="list-disc list-inside space-y-0.5 text-[11px]">
              <li>Your application status will be permanently changed to WITHDRAWN.</li>
              <li>You cannot submit another application for this exact job posting.</li>
            </ul>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setWithdrawingApp(null)}
              disabled={isWithdrawing}
            >
              Cancel
            </Button>
            <Button
              variant="primary"
              size="sm"
              onClick={handleWithdrawConfirm}
              isLoading={isWithdrawing}
              className="bg-rose-600 hover:bg-rose-700 text-white border-none"
            >
              Confirm Withdrawal
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};
