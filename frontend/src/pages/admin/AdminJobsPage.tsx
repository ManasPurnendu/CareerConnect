import React, { useState, useEffect, useCallback } from 'react';
import { adminApi } from '../../api/admin';
import type { AdminJobDto, JobStatus } from '../../types/api';
import { Pagination } from '../../components/ui/Pagination';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Modal } from '../../components/ui/Modal';
import { StatusBadge } from '../../components/ui/StatusBadge';

export const AdminJobsPage: React.FC = () => {
  const [jobs, setJobs] = useState<AdminJobDto[]>([]);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [pageSize] = useState<number>(15);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [notification, setNotification] = useState<string | null>(null);

  // Filters
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');

  // Confirmation Modal
  const [selectedJob, setSelectedJob] = useState<AdminJobDto | null>(null);
  const [targetAction, setTargetAction] = useState<'archive' | 'restore' | null>(null);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  const fetchJobs = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const response = await adminApi.getJobs({
        status: statusFilter !== 'ALL' ? (statusFilter as JobStatus) : undefined,
        q: searchQuery.trim() || undefined,
        page,
        size: pageSize,
      });

      const list = (response?.data && response.data.length > 0)
        ? response.data
        : (response?.items || (response as any)?.content || []);

      setJobs(list);
      setTotalPages(response?.pagination?.totalPages ?? response?.page?.totalPages ?? 1);
      setTotalElements(response?.pagination?.totalElements ?? response?.page?.totalElements ?? list.length);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to load job postings.';
      setErrorMessage(msg);
    } finally {
      setIsLoading(false);
    }
  }, [page, pageSize, statusFilter, searchQuery]);

  useEffect(() => {
    fetchJobs();
  }, [fetchJobs]);

  const handleOpenConfirm = (job: AdminJobDto, action: 'archive' | 'restore') => {
    setSelectedJob(job);
    setTargetAction(action);
  };

  const handleCloseConfirm = () => {
    setSelectedJob(null);
    setTargetAction(null);
    setIsSubmitting(false);
  };

  const handleConfirmModeration = async () => {
    if (!selectedJob || !targetAction) return;

    setIsSubmitting(true);
    setErrorMessage(null);
    try {
      let updated: AdminJobDto;
      if (targetAction === 'archive') {
        updated = await adminApi.archiveJob(selectedJob.id);
        setNotification(`Job "${updated.title}" (#${updated.id}) has been archived.`);
      } else {
        updated = await adminApi.restoreJob(selectedJob.id);
        setNotification(`Job "${updated.title}" (#${updated.id}) has been restored (Status: CLOSED).`);
      }
      setJobs((prev) => prev.map((j) => (j.id === updated.id ? updated : j)));
      handleCloseConfirm();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : `Failed to ${targetAction} job.`;
      setErrorMessage(msg);
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="font-headline font-bold text-2xl text-slate-text">Job Postings Moderation</h1>
        <p className="text-xs text-slate-muted mt-0.5">
          Inspect platform-wide job listings, verify early-career criteria, and perform administrative archive or restore actions
        </p>
      </div>

      {notification && (
        <Alert variant="success" onClose={() => setNotification(null)}>
          {notification}
        </Alert>
      )}

      {errorMessage && (
        <Alert variant="error" onClose={() => setErrorMessage(null)}>
          {errorMessage}
        </Alert>
      )}

      {/* Filters Card */}
      <Card>
        <CardBody className="p-4 sm:p-5">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label htmlFor="job-search" className="block text-xs font-semibold text-slate-text mb-1">
                Search
              </label>
              <Input
                id="job-search"
                placeholder="Search by job title or company..."
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  setPage(0);
                }}
              />
            </div>

            <div>
              <label htmlFor="job-status-filter" className="block text-xs font-semibold text-slate-text mb-1">
                Posting Status
              </label>
              <select
                id="job-status-filter"
                aria-label="Posting Status Filter"
                value={statusFilter}
                onChange={(e) => {
                  setStatusFilter(e.target.value);
                  setPage(0);
                }}
                className="w-full h-11 px-3 text-xs bg-white border border-outline-warm rounded focus:outline-none focus:ring-1 focus:ring-primary"
              >
                <option value="ALL">All Statuses</option>
                <option value="ACTIVE">ACTIVE</option>
                <option value="DRAFT">DRAFT</option>
                <option value="CLOSED">CLOSED</option>
                <option value="ARCHIVED">ARCHIVED</option>
              </select>
            </div>
          </div>
        </CardBody>
      </Card>

      {/* Jobs Table Card */}
      <Card>
        <CardHeader className="flex items-center justify-between">
          <div>
            <h2 className="font-headline font-semibold text-base text-slate-text">Platform Job Postings</h2>
            <p className="text-xs text-slate-muted">
              {totalElements} total {totalElements === 1 ? 'posting' : 'postings'} across all companies
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
          ) : jobs.length === 0 ? (
            <div className="p-12 text-center">
              <span className="material-symbols-outlined text-slate-muted !text-4xl mb-2">work_off</span>
              <p className="text-sm font-semibold text-slate-text">No job postings found</p>
              <p className="text-xs text-slate-muted mt-1">Try adjusting your search criteria or status filter.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs sm:text-sm">
                <thead className="bg-[#FAFAF8] text-slate-muted text-[11px] uppercase tracking-wider border-b border-outline-warm">
                  <tr>
                    <th className="px-4 py-3">Job ID</th>
                    <th className="px-4 py-3">Title & Employer</th>
                    <th className="px-4 py-3">Type & Mode</th>
                    <th className="px-4 py-3">Experience</th>
                    <th className="px-4 py-3">Compensation</th>
                    <th className="px-4 py-3">Applicants</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3 text-right">Moderation Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-warm/70">
                  {jobs.map((j) => (
                    <tr key={j.id} className="hover:bg-[#F9FAF9] transition-colors">
                      <td className="px-4 py-3.5 font-mono text-xs text-slate-muted">#{j.id}</td>
                      <td className="px-4 py-3.5">
                        <div className="font-semibold text-slate-text">{j.title}</div>
                        <div className="text-xs text-slate-muted">{j.companyName}</div>
                      </td>
                      <td className="px-4 py-3.5 text-xs text-slate-text">
                        <div>{j.jobType}</div>
                        <div className="text-slate-muted text-[11px]">{j.workMode}</div>
                      </td>
                      <td className="px-4 py-3.5 text-xs text-slate-text font-mono">
                        {j.experienceLevel}
                      </td>
                      <td className="px-4 py-3.5 text-xs text-slate-text font-mono">
                        {j.salaryMinLpa && j.salaryMaxLpa
                          ? `₹${j.salaryMinLpa} - ₹${j.salaryMaxLpa} LPA`
                          : '—'}
                      </td>
                      <td className="px-4 py-3.5 font-mono text-xs font-semibold text-primary">
                        {j.applicationCount}
                      </td>
                      <td className="px-4 py-3.5">
                        <StatusBadge status={j.status} />
                      </td>
                      <td className="px-4 py-3.5 text-right">
                        {j.status === 'ARCHIVED' ? (
                          <Button
                            variant="primary"
                            size="sm"
                            onClick={() => handleOpenConfirm(j, 'restore')}
                            className="min-h-[44px]"
                            title="Restore archived job (restores to CLOSED)"
                          >
                            Restore
                          </Button>
                        ) : (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleOpenConfirm(j, 'archive')}
                            className="text-status-rejected border-status-rejected/30 hover:bg-status-rejected-bg min-h-[44px]"
                            title="Archive job posting"
                          >
                            Archive
                          </Button>
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

      {/* Pagination */}
      {totalPages > 1 && (
        <Pagination
          currentPage={page}
          totalPages={totalPages}
          onPageChange={(newPage) => setPage(newPage)}
        />
      )}

      {/* Confirmation Modal */}
      {selectedJob && targetAction && (
        <Modal
          isOpen={true}
          onClose={handleCloseConfirm}
          title={targetAction === 'archive' ? 'Confirm Job Archive' : 'Confirm Job Restoration'}
        >
          <div className="space-y-4">
            <p className="text-xs text-slate-text">
              Are you sure you want to {targetAction === 'archive' ? 'archive' : 'restore'} posting{' '}
              <strong>{selectedJob.title}</strong> (#{selectedJob.id}) from{' '}
              <strong>{selectedJob.companyName}</strong>?
            </p>

            {targetAction === 'archive' && (
              <div className="p-3 bg-status-rejected-bg text-status-rejected rounded text-xs">
                <strong>Moderation Notice:</strong> Archiving hides this job posting from candidates and disables new applications. This action is recorded in the administrative audit log.
              </div>
            )}

            {targetAction === 'restore' && (
              <div className="p-3 bg-[#E0F2FE] text-[#0369A1] rounded text-xs">
                <strong>Platform Policy:</strong> Restoring an archived job places it strictly in <strong>CLOSED</strong> status. The recruiter can then review and republish it if appropriate.
              </div>
            )}

            <div className="flex justify-end gap-2 pt-2">
              <Button variant="outline" size="sm" onClick={handleCloseConfirm} disabled={isSubmitting} className="min-h-[44px]">
                Cancel
              </Button>
              <Button
                variant={targetAction === 'restore' ? 'primary' : 'danger'}
                size="sm"
                onClick={handleConfirmModeration}
                disabled={isSubmitting}
                className="min-h-[44px]"
              >
                {isSubmitting
                  ? 'Processing...'
                  : targetAction === 'archive'
                  ? 'Confirm & Archive'
                  : 'Confirm & Restore'}
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
};
