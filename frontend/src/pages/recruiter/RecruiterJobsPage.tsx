import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { recruiterApi } from '../../api/recruiter';
import type { JobSummaryDto, JobStatus } from '../../types/api';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Button } from '../../components/ui/Button';
import { Pagination } from '../../components/ui/Pagination';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { EmptyState } from '../../components/ui/EmptyState';
import { Alert } from '../../components/ui/Alert';
import { Modal } from '../../components/ui/Modal';

export const RecruiterJobsPage: React.FC = () => {
  const navigate = useNavigate();
  const [jobs, setJobs] = useState<JobSummaryDto[]>([]);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [page, setPage] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Tab filter: 'ALL' | 'ACTIVE' | 'DRAFT' | 'CLOSED'
  const [statusFilter, setStatusFilter] = useState<'ALL' | JobStatus>('ALL');
  const [searchTerm, setSearchTerm] = useState<string>('');

  // Confirmation modal states
  const [confirmAction, setConfirmAction] = useState<{
    type: 'publish' | 'close' | 'delete';
    job: JobSummaryDto;
  } | null>(null);
  const [isActionPending, setIsActionPending] = useState<boolean>(false);

  const fetchJobs = async () => {
    setIsLoading(true);
    try {
      const response = await recruiterApi.getJobs(page, 20);
      const list = (response?.data && response.data.length > 0)
        ? response.data
        : (response?.items || (response as any)?.content || (Array.isArray(response) ? response : []));
      setJobs(list);
      setTotalPages(response?.pagination?.totalPages || (response as any)?.page?.totalPages || 1);
    } catch (err: unknown) {
      setFeedback({
        type: 'error',
        message: err instanceof Error ? err.message : 'Unable to load company job postings.',
      });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchJobs();
  }, [page]);

  const handleExecuteAction = async () => {
    if (!confirmAction) return;
    const { type, job } = confirmAction;
    setIsActionPending(true);
    setFeedback(null);

    try {
      if (type === 'publish') {
        await recruiterApi.publishJob(job.id);
        setFeedback({
          type: 'success',
          message: `Job "${job.title}" has been published and is now accepting candidate applications.`,
        });
      } else if (type === 'close') {
        await recruiterApi.closeJob(job.id);
        setFeedback({
          type: 'success',
          message: `Job "${job.title}" has been closed. Candidates can no longer submit new applications.`,
        });
      } else if (type === 'delete') {
        await recruiterApi.deleteJob(job.id);
        setFeedback({
          type: 'success',
          message: `Job "${job.title}" has been removed.`,
        });
      }
      setConfirmAction(null);
      await fetchJobs();
    } catch (err: unknown) {
      setFeedback({
        type: 'error',
        message: err instanceof Error ? err.message : `Failed to ${type} job.`,
      });
      setConfirmAction(null);
    } finally {
      setIsActionPending(false);
    }
  };

  const filteredJobs = jobs.filter((job) => {
    const matchesStatus = statusFilter === 'ALL' || job.status === statusFilter;
    const matchesSearch =
      !searchTerm.trim() ||
      job.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (job.location && job.location.toLowerCase().includes(searchTerm.toLowerCase()));
    return matchesStatus && matchesSearch;
  });

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="font-headline font-bold text-2xl text-slate-text">Job Postings</h1>
          <p className="text-xs text-slate-muted mt-0.5">
            Manage your company’s openings, publish draft roles, and review applicant pipelines.
          </p>
        </div>
        <Link to="/recruiter/jobs/new">
          <Button
            variant="primary"
            size="md"
            leftIcon={<span className="material-symbols-outlined !text-base">add</span>}
          >
            Create Job Opening
          </Button>
        </Link>
      </div>

      {feedback && (
        <Alert variant={feedback.type} onClose={() => setFeedback(null)}>
          {feedback.message}
        </Alert>
      )}

      {/* Filter and Search Bar */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
        {/* Status Filter Tabs */}
        <div role="tablist" aria-label="Job status filters" className="flex items-center gap-1 bg-white p-1 rounded border border-outline-warm shadow-subtle overflow-x-auto">
          {(['ALL', 'ACTIVE', 'DRAFT', 'CLOSED'] as const).map((tab) => (
            <button
              key={tab}
              type="button"
              role="tab"
              aria-selected={statusFilter === tab}
              onClick={() => setStatusFilter(tab)}
              className={`min-h-[44px] px-3.5 py-2 rounded text-xs font-medium transition-colors whitespace-nowrap ${
                statusFilter === tab
                  ? 'bg-primary-tint text-primary font-semibold'
                  : 'text-slate-muted hover:text-slate-text hover:bg-[#F2F5F3]'
              }`}
            >
              {tab === 'ALL' ? 'All Roles' : tab.charAt(0) + tab.slice(1).toLowerCase()}
            </button>
          ))}
        </div>

        {/* Search */}
        <div className="relative w-full sm:w-64">
          <span className="material-symbols-outlined !text-[18px] text-slate-muted absolute left-3 top-1/2 -translate-y-1/2">
            search
          </span>
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search by title or location..."
            className="w-full pl-9 pr-3 py-2 text-xs bg-white border border-outline-warm rounded text-slate-text placeholder:text-slate-muted focus:outline-none focus:ring-1 focus:ring-primary min-h-[38px]"
          />
        </div>
      </div>

      {/* Job Postings Table / Cards */}
      <Card>
        <CardHeader className="flex items-center justify-between">
          <h2 className="font-headline font-semibold text-base text-slate-text">
            Postings ({filteredJobs.length})
          </h2>
        </CardHeader>
        <CardBody className="p-0">
          {isLoading ? (
            <div className="p-4 space-y-3">
              <Skeleton height="44px" />
              <Skeleton height="44px" />
              <Skeleton height="44px" />
            </div>
          ) : filteredJobs.length === 0 ? (
            <EmptyState
              icon="work_outline"
              title="No Job Postings Found"
              description={
                searchTerm || statusFilter !== 'ALL'
                  ? 'No job openings match your active filters.'
                  : 'Your organization has not created any job openings yet.'
              }
              actionLabel={searchTerm || statusFilter !== 'ALL' ? 'Clear Filters' : 'Create First Opening'}
              onAction={() => {
                if (searchTerm || statusFilter !== 'ALL') {
                  setSearchTerm('');
                  setStatusFilter('ALL');
                } else {
                  navigate('/recruiter/jobs/new');
                }
              }}
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs sm:text-sm">
                <thead className="bg-[#FAFAF8] text-slate-muted text-[11px] uppercase tracking-wider border-b border-outline-warm">
                  <tr>
                    <th className="px-4 py-3 font-semibold">Job Title</th>
                    <th className="px-4 py-3 font-semibold">Type & Mode</th>
                    <th className="px-4 py-3 font-semibold">Location</th>
                    <th className="px-4 py-3 font-semibold">Deadline</th>
                    <th className="px-4 py-3 font-semibold">Applicants</th>
                    <th className="px-4 py-3 font-semibold">Status</th>
                    <th className="px-4 py-3 text-right font-semibold">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-warm/70">
                  {filteredJobs.map((job) => (
                    <tr key={job.id} className="hover:bg-[#F9FAF9] transition-colors">
                      {/* Job Title */}
                      <td className="px-4 py-3.5">
                        <Link
                          to={`/recruiter/jobs/${job.id}`}
                          className="font-semibold text-slate-text hover:text-primary transition-colors block"
                        >
                          {job.title}
                        </Link>
                        <span className="text-[11px] font-mono text-slate-muted">
                          ID: #{job.id} · Created {new Date(job.createdAt).toLocaleDateString()}
                        </span>
                      </td>

                      {/* Type & Mode */}
                      <td className="px-4 py-3.5 text-xs text-slate-muted">
                        <span className="inline-flex items-center gap-1 font-medium text-slate-text">
                          {(job.jobType || 'FULL_TIME').replace('_', ' ')}
                        </span>
                        <span className="block text-[11px] text-slate-muted font-mono">
                          {job.workMode}
                        </span>
                      </td>

                      {/* Location */}
                      <td className="px-4 py-3.5 text-xs text-slate-muted">
                        {job.location || 'Remote'}
                      </td>

                      {/* Deadline */}
                      <td className="px-4 py-3.5 text-xs font-mono text-slate-muted">
                        {job.applicationDeadline || 'No deadline'}
                      </td>

                      {/* Applicants */}
                      <td className="px-4 py-3.5">
                        <Link
                          to={`/recruiter/jobs/${job.id}/applicants`}
                          className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded bg-[#F2F5F3] hover:bg-primary-tint text-slate-text hover:text-primary transition-colors text-xs font-medium"
                          title="View applicants for this job"
                        >
                          <span className="material-symbols-outlined !text-[15px]">group</span>
                          <span>{job.applicantCount ?? 0}</span>
                        </Link>
                      </td>

                      {/* Status */}
                      <td className="px-4 py-3.5">
                        <StatusBadge status={job.status} size="sm" />
                      </td>

                      {/* Actions */}
                      <td className="px-4 py-3.5 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          {/* DRAFT actions: Edit, Publish, Delete */}
                          {job.status === 'DRAFT' && (
                            <>
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => navigate(`/recruiter/jobs/${job.id}/edit`)}
                                title="Edit Draft"
                              >
                                Edit
                              </Button>
                              <Button
                                variant="primary"
                                size="sm"
                                onClick={() => setConfirmAction({ type: 'publish', job })}
                                title="Publish Job"
                              >
                                Publish
                              </Button>
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => setConfirmAction({ type: 'delete', job })}
                                className="!text-status-rejected hover:!bg-[#FEE2E2]"
                                title="Delete Draft"
                                aria-label="Delete Draft"
                              >
                                <span className="material-symbols-outlined !text-[18px]">delete</span>
                              </Button>
                            </>
                          )}

                          {/* ACTIVE actions: Edit, Close, View Applicants */}
                          {job.status === 'ACTIVE' && (
                            <>
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => navigate(`/recruiter/jobs/${job.id}/edit`)}
                                title="Edit Job Details"
                              >
                                Edit
                              </Button>
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => setConfirmAction({ type: 'close', job })}
                                title="Close Job to Applications"
                              >
                                Close
                              </Button>
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => navigate(`/recruiter/jobs/${job.id}`)}
                                title="View Job Details"
                                aria-label="View Job Details"
                              >
                                <span className="material-symbols-outlined !text-[18px]">visibility</span>
                              </Button>
                            </>
                          )}

                          {/* CLOSED actions: View Details, Applicants */}
                          {job.status === 'CLOSED' && (
                            <>
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => navigate(`/recruiter/jobs/${job.id}`)}
                              >
                                View
                              </Button>
                              {(!job.applicantCount || job.applicantCount === 0) && (
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => setConfirmAction({ type: 'delete', job })}
                                  className="!text-status-rejected hover:!bg-[#FEE2E2]"
                                  title="Delete Closed Job (0 Applicants)"
                                  aria-label="Delete Closed Job"
                                >
                                  <span className="material-symbols-outlined !text-[18px]">delete</span>
                                </Button>
                              )}
                            </>
                          )}

                          {/* ARCHIVED: read-only */}
                          {job.status === 'ARCHIVED' && (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => navigate(`/recruiter/jobs/${job.id}`)}
                            >
                              View
                            </Button>
                          )}
                        </div>
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
      {confirmAction && (
        <Modal
          isOpen={true}
          onClose={() => !isActionPending && setConfirmAction(null)}
          title={
            confirmAction.type === 'publish'
              ? 'Publish Job Opening'
              : confirmAction.type === 'close'
              ? 'Close Job Opening'
              : 'Delete Job Posting'
          }
        >
          <div className="space-y-4">
            <p className="text-sm text-slate-text">
              {confirmAction.type === 'publish' && (
                <>
                  Are you sure you want to publish{' '}
                  <strong className="text-slate-text">"{confirmAction.job.title}"</strong>? It will
                  immediately transition from <span className="font-semibold">DRAFT</span> to{' '}
                  <span className="font-semibold text-primary">ACTIVE</span> and become visible to
                  early-career candidates.
                </>
              )}
              {confirmAction.type === 'close' && (
                <>
                  Are you sure you want to close{' '}
                  <strong className="text-slate-text">"{confirmAction.job.title}"</strong>? Candidates
                  will no longer be able to submit applications. Existing applicant profiles remain
                  accessible for review.
                </>
              )}
              {confirmAction.type === 'delete' && (
                <>
                  Are you sure you want to delete{' '}
                  <strong className="text-slate-text">"{confirmAction.job.title}"</strong>? This will
                  permanently remove this job posting from your company's active roster. This action
                  cannot be undone.
                </>
              )}
            </p>

            <div className="pt-3 border-t border-outline-warm flex items-center justify-end gap-3">
              <Button
                variant="outline"
                size="md"
                onClick={() => setConfirmAction(null)}
                disabled={isActionPending}
              >
                Cancel
              </Button>
              <Button
                variant={confirmAction.type === 'delete' ? 'danger' : 'primary'}
                size="md"
                onClick={handleExecuteAction}
                isLoading={isActionPending}
              >
                {confirmAction.type === 'publish'
                  ? 'Confirm & Publish'
                  : confirmAction.type === 'close'
                  ? 'Close Role'
                  : 'Delete Role'}
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
};
