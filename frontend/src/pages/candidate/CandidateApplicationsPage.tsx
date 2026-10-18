import React, { useState, useEffect } from 'react';
import { candidateApi } from '../../api/candidate';
import type { Application } from '../../types/api';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Pagination } from '../../components/ui/Pagination';
import { Skeleton } from '../../components/ui/Skeleton';
import { EmptyState } from '../../components/ui/EmptyState';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Alert } from '../../components/ui/Alert';

export const CandidateApplicationsPage: React.FC = () => {
  const [applications, setApplications] = useState<Application[]>([]);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [notification, setNotification] = useState<string | null>(null);

  useEffect(() => {
    const fetchApps = async () => {
      setIsLoading(true);
      try {
        const response = await candidateApi.getApplications(page, 10);
        if (response?.content) {
          setApplications(response.content);
          setTotalPages(response.page?.totalPages ?? 1);
        }
      } finally {
        setIsLoading(false);
      }
    };
    fetchApps();
  }, [page]);

  const handleWithdraw = async (appId: number) => {
    if (!window.confirm('Are you sure you want to withdraw this application?')) return;
    try {
      await candidateApi.withdrawApplication(appId);
      setNotification(`Application #${appId} withdrawn successfully.`);
      setApplications((prev) =>
        prev.map((a) => (a.id === appId ? { ...a, status: 'WITHDRAWN' } : a))
      );
    } catch {
      alert('Unable to withdraw application.');
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-headline font-bold text-2xl text-slate-text">My Applications</h1>
        <p className="text-xs text-slate-muted mt-0.5">
          Track the lifecycle and status of your submitted applications
        </p>
      </div>

      {notification && (
        <Alert variant="info" onClose={() => setNotification(null)}>
          {notification}
        </Alert>
      )}

      <Card>
        <CardHeader>
          <h2 className="font-headline font-semibold text-base text-slate-text">All Submissions</h2>
        </CardHeader>
        <CardBody className="p-0">
          {isLoading ? (
            <div className="p-4 space-y-3">
              <Skeleton height="40px" />
              <Skeleton height="40px" />
            </div>
          ) : applications.length === 0 ? (
            <EmptyState
              icon="description"
              title="No applications found"
              description="You have not submitted any applications yet."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs sm:text-sm">
                <thead className="bg-[#FAFAF8] text-slate-muted text-[11px] uppercase tracking-wider border-b border-outline-warm">
                  <tr>
                    <th className="px-4 py-3">Role & Company</th>
                    <th className="px-4 py-3">Match Score</th>
                    <th className="px-4 py-3">Applied Date</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-warm/70">
                  {applications.map((app) => (
                    <tr key={app.id} className="hover:bg-[#F9FAF9]">
                      <td className="px-4 py-3.5">
                        <div className="font-semibold text-slate-text">{app.jobTitle}</div>
                        <div className="text-xs text-slate-muted">{app.companyName}</div>
                      </td>
                      <td className="px-4 py-3.5 font-mono text-xs">
                        {app.matchScore !== undefined ? `${Math.round(app.matchScore)}%` : '—'}
                      </td>
                      <td className="px-4 py-3.5 text-xs text-slate-muted whitespace-nowrap">
                        {new Date(app.appliedAt).toLocaleDateString()}
                      </td>
                      <td className="px-4 py-3.5">
                        <StatusBadge status={app.status} size="sm" />
                      </td>
                      <td className="px-4 py-3.5 text-right">
                        {app.status !== 'WITHDRAWN' && app.status !== 'REJECTED' && (
                          <button
                            onClick={() => handleWithdraw(app.id)}
                            className="text-xs text-slate-muted hover:text-status-rejected underline font-medium transition-colors"
                          >
                            Withdraw
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardBody>
      </Card>

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
