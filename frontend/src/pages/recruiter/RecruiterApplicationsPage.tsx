import React, { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { recruiterApi } from '../../api/recruiter';
import type { Application } from '../../types/api';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Pagination } from '../../components/ui/Pagination';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { EmptyState } from '../../components/ui/EmptyState';
import { Alert } from '../../components/ui/Alert';

export const RecruiterApplicationsPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const jobIdParam = searchParams.get('jobId');

  const [applications, setApplications] = useState<Application[]>([]);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [feedback, setFeedback] = useState<string | null>(null);

  useEffect(() => {
    const fetchApps = async () => {
      setIsLoading(true);
      try {
        const response = await recruiterApi.getApplications(
          jobIdParam ? Number(jobIdParam) : undefined,
          page,
          10
        );
        if (response?.content) {
          setApplications(response.content);
          setTotalPages(response.page?.totalPages ?? 1);
        }
      } finally {
        setIsLoading(false);
      }
    };
    fetchApps();
  }, [jobIdParam, page]);

  const handleUpdateStatus = async (appId: number, newStatus: string) => {
    try {
      await recruiterApi.updateApplicationStatus(appId, newStatus);
      setFeedback(`Application #${appId} moved to ${newStatus}.`);
      setApplications((prev) =>
        prev.map((a) => (a.id === appId ? { ...a, status: newStatus as any } : a))
      );
    } catch {
      alert('Failed to update status.');
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-headline font-bold text-2xl text-slate-text">Applicant Management</h1>
        <p className="text-xs text-slate-muted mt-0.5">
          {jobIdParam ? `Reviewing candidates for Job #${jobIdParam}` : 'All incoming applicants across your postings'}
        </p>
      </div>

      {feedback && (
        <Alert variant="success" onClose={() => setFeedback(null)}>
          {feedback}
        </Alert>
      )}

      <Card>
        <CardHeader>
          <h2 className="font-headline font-semibold text-base text-slate-text">Candidates</h2>
        </CardHeader>
        <CardBody className="p-0">
          {isLoading ? (
            <div className="p-4 space-y-3">
              <Skeleton height="40px" />
              <Skeleton height="40px" />
            </div>
          ) : applications.length === 0 ? (
            <EmptyState
              icon="group_off"
              title="No applicants found"
              description="No candidates have applied to this posting yet."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs sm:text-sm">
                <thead className="bg-[#FAFAF8] text-slate-muted text-[11px] uppercase tracking-wider border-b border-outline-warm">
                  <tr>
                    <th className="px-4 py-3">Applicant Name</th>
                    <th className="px-4 py-3">Target Role</th>
                    <th className="px-4 py-3">Match</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3 text-right">Review Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-warm/70">
                  {applications.map((app) => (
                    <tr key={app.id} className="hover:bg-[#F9FAF9]">
                      <td className="px-4 py-3.5">
                        <div className="font-semibold text-slate-text">{app.candidateName}</div>
                        <div className="text-xs text-slate-muted">{app.candidateEmail}</div>
                      </td>
                      <td className="px-4 py-3.5 text-xs text-slate-muted">{app.jobTitle}</td>
                      <td className="px-4 py-3.5 font-mono text-xs text-primary font-semibold">
                        {app.matchScore !== undefined ? `${Math.round(app.matchScore)}%` : '—'}
                      </td>
                      <td className="px-4 py-3.5">
                        <StatusBadge status={app.status} size="sm" />
                      </td>
                      <td className="px-4 py-3.5 text-right">
                        <select
                          value={app.status}
                          onChange={(e) => handleUpdateStatus(app.id, e.target.value)}
                          className="text-xs py-1 px-2 border border-outline-warm rounded bg-white text-slate-text focus:ring-1 focus:ring-primary"
                        >
                          <option value="APPLIED">Applied</option>
                          <option value="UNDER_REVIEW">Under Review</option>
                          <option value="SHORTLISTED">Shortlist</option>
                          <option value="INTERVIEW_SCHEDULED">Interview</option>
                          <option value="OFFERED">Offer</option>
                          <option value="REJECTED">Reject</option>
                        </select>
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
