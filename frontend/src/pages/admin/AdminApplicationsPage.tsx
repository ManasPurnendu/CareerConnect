import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { adminApi } from '../../api/admin';
import type { AdminApplicationSummaryDto, ApplicationStatus } from '../../types/api';
import { Pagination } from '../../components/ui/Pagination';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { Alert } from '../../components/ui/Alert';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Input } from '../../components/ui/Input';

export const AdminApplicationsPage: React.FC = () => {
  const [applications, setApplications] = useState<AdminApplicationSummaryDto[]>([]);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [pageSize] = useState<number>(15);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Filters
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [jobIdFilter, setJobIdFilter] = useState<string>('');
  const [candidateIdFilter, setCandidateIdFilter] = useState<string>('');

  const fetchApplications = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const parsedJobId = jobIdFilter.trim() ? parseInt(jobIdFilter.trim(), 10) : undefined;
      const parsedCandidateId = candidateIdFilter.trim() ? parseInt(candidateIdFilter.trim(), 10) : undefined;

      const response = await adminApi.getApplications({
        status: statusFilter !== 'ALL' ? (statusFilter as ApplicationStatus) : undefined,
        jobId: !isNaN(Number(parsedJobId)) ? parsedJobId : undefined,
        candidateId: !isNaN(Number(parsedCandidateId)) ? parsedCandidateId : undefined,
        page,
        size: pageSize,
      });

      const list = (response?.data && response.data.length > 0)
        ? response.data
        : (response?.items || (response as any)?.content || []);

      setApplications(list);
      setTotalPages(response?.pagination?.totalPages ?? response?.page?.totalPages ?? 1);
      setTotalElements(response?.pagination?.totalElements ?? response?.page?.totalElements ?? list.length);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to load platform applications.';
      setErrorMessage(msg);
    } finally {
      setIsLoading(false);
    }
  }, [page, pageSize, statusFilter, jobIdFilter, candidateIdFilter]);

  useEffect(() => {
    fetchApplications();
  }, [fetchApplications]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="font-headline font-bold text-2xl text-slate-text">Applications Oversight</h1>
        <p className="text-xs text-slate-muted mt-0.5">
          Inspect candidate submissions, review snapshots, and verify platform application statuses
        </p>
      </div>

      {errorMessage && (
        <Alert variant="error" onClose={() => setErrorMessage(null)}>
          {errorMessage}
        </Alert>
      )}

      {/* Filter Bar */}
      <Card>
        <CardBody className="p-4 sm:p-5">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label htmlFor="app-status-filter" className="block text-xs font-semibold text-slate-text mb-1">
                Application Status
              </label>
              <select
                id="app-status-filter"
                aria-label="Application Status Filter"
                value={statusFilter}
                onChange={(e) => {
                  setStatusFilter(e.target.value);
                  setPage(0);
                }}
                className="w-full h-11 px-3 text-xs bg-white border border-outline-warm rounded focus:outline-none focus:ring-1 focus:ring-primary"
              >
                <option value="ALL">All Statuses</option>
                <option value="APPLIED">APPLIED</option>
                <option value="UNDER_REVIEW">UNDER_REVIEW</option>
                <option value="SHORTLISTED">SHORTLISTED</option>
                <option value="INTERVIEW">INTERVIEW</option>
                <option value="SELECTED">SELECTED</option>
                <option value="REJECTED">REJECTED</option>
                <option value="WITHDRAWN">WITHDRAWN</option>
              </select>
            </div>

            <div>
              <label htmlFor="job-id-filter" className="block text-xs font-semibold text-slate-text mb-1">
                Filter by Job ID
              </label>
              <Input
                id="job-id-filter"
                type="number"
                placeholder="e.g. 14"
                value={jobIdFilter}
                onChange={(e) => {
                  setJobIdFilter(e.target.value);
                  setPage(0);
                }}
              />
            </div>

            <div>
              <label htmlFor="cand-id-filter" className="block text-xs font-semibold text-slate-text mb-1">
                Filter by Candidate ID
              </label>
              <Input
                id="cand-id-filter"
                type="number"
                placeholder="e.g. 5"
                value={candidateIdFilter}
                onChange={(e) => {
                  setCandidateIdFilter(e.target.value);
                  setPage(0);
                }}
              />
            </div>
          </div>
        </CardBody>
      </Card>

      {/* Applications Table Card */}
      <Card>
        <CardHeader className="flex items-center justify-between">
          <div>
            <h2 className="font-headline font-semibold text-base text-slate-text">Submissions Overview</h2>
            <p className="text-xs text-slate-muted">
              {totalElements} total platform {totalElements === 1 ? 'application' : 'applications'} recorded
            </p>
          </div>
        </CardHeader>
        <CardBody className="p-0">
          {isLoading ? (
            <div className="p-4 space-y-3">
              <Skeleton height="44px" />
              <Skeleton height="44px" />
              <Skeleton height="44px" />
            </div>
          ) : applications.length === 0 ? (
            <div className="p-12 text-center">
              <span className="material-symbols-outlined text-slate-muted !text-4xl mb-2">description</span>
              <p className="text-sm font-semibold text-slate-text">No applications found</p>
              <p className="text-xs text-slate-muted mt-1">Try clearing or adjusting your filter settings.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs sm:text-sm">
                <thead className="bg-[#FAFAF8] text-slate-muted text-[11px] uppercase tracking-wider border-b border-outline-warm">
                  <tr>
                    <th className="px-4 py-3">App ID</th>
                    <th className="px-4 py-3">Candidate</th>
                    <th className="px-4 py-3">Target Role & Company</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3">Submission Date</th>
                    <th className="px-4 py-3 text-right">Inspection Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-warm/70">
                  {applications.map((app) => (
                    <tr key={app.id} className="hover:bg-[#F9FAF9] transition-colors">
                      <td className="px-4 py-3.5 font-mono text-xs text-slate-muted">#{app.id}</td>
                      <td className="px-4 py-3.5">
                        <div className="font-semibold text-slate-text">{app.candidateName}</div>
                        <div className="text-xs text-slate-muted font-mono">{app.candidateEmail}</div>
                      </td>
                      <td className="px-4 py-3.5">
                        <div className="font-semibold text-slate-text">{app.jobTitle}</div>
                        <div className="text-xs text-slate-muted">{app.companyName}</div>
                      </td>
                      <td className="px-4 py-3.5">
                        <StatusBadge status={app.status} />
                      </td>
                      <td className="px-4 py-3.5 text-xs text-slate-muted whitespace-nowrap">
                        {app.appliedAt ? new Date(app.appliedAt).toLocaleDateString() : '—'}
                      </td>
                      <td className="px-4 py-3.5 text-right">
                        <Link
                          to={`/admin/applications/${app.id}`}
                          className="inline-flex items-center gap-1.5 px-3 py-2 rounded text-xs font-semibold text-primary bg-[#F2F5F3] hover:bg-primary hover:text-white transition-colors min-h-[44px]"
                          title="Inspect application details"
                        >
                          <span className="material-symbols-outlined !text-base">visibility</span>
                          <span>Inspect</span>
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
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
