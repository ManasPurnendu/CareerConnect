import React, { useState, useEffect } from 'react';
import { useSearchParams, useParams, useNavigate, Link } from 'react-router-dom';
import { recruiterApi } from '../../api/recruiter';
import type {
  ApplicantSummaryDto,
  JobSummaryDto,
  ApplicationStatus,
} from '../../types/api';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Pagination } from '../../components/ui/Pagination';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { EmptyState } from '../../components/ui/EmptyState';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';

export const RecruiterApplicationsPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const routeParams = useParams<{ jobId?: string }>();

  // Determine active jobId from route or search params
  const activeJobId = routeParams.jobId || searchParams.get('jobId') || '';
  const statusParam = searchParams.get('status') as ApplicationStatus | null;

  const [companyJobs, setCompanyJobs] = useState<JobSummaryDto[]>([]);
  const [selectedJobId, setSelectedJobId] = useState<string>(activeJobId);
  const [selectedStatus, setSelectedStatus] = useState<string>(statusParam || 'ALL');

  const [applicants, setApplicants] = useState<ApplicantSummaryDto[]>([]);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [page, setPage] = useState<number>(0);

  const [isLoadingJobs, setIsLoadingJobs] = useState<boolean>(true);
  const [isLoadingApplicants, setIsLoadingApplicants] = useState<boolean>(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // 1. Fetch company's jobs to populate selector
  useEffect(() => {
    const fetchJobs = async () => {
      setIsLoadingJobs(true);
      try {
        const response = await recruiterApi.getJobs(0, 50);
        const list = (response?.data && response.data.length > 0)
          ? response.data
          : (response?.items || (response as any)?.content || []);
        setCompanyJobs(list);

        // Auto-select first job if none specified in URL and jobs exist
        if (!activeJobId && list.length > 0) {
          setSelectedJobId(String(list[0].id));
        } else if (activeJobId) {
          setSelectedJobId(activeJobId);
        }
      } catch (err: unknown) {
        setFeedback({
          type: 'error',
          message: err instanceof Error ? err.message : 'Unable to load company job postings.',
        });
      } finally {
        setIsLoadingJobs(false);
      }
    };
    fetchJobs();
  }, [activeJobId]);

  // 2. Fetch applicants for selected job
  useEffect(() => {
    if (!selectedJobId) {
      setApplicants([]);
      return;
    }

    const fetchApplicants = async () => {
      setIsLoadingApplicants(true);
      setFeedback(null);
      try {
        const statusFilter = selectedStatus !== 'ALL' ? (selectedStatus as ApplicationStatus) : undefined;
        const response = await recruiterApi.getJobApplicants(selectedJobId, statusFilter, page, 20);
        const list = (response?.data && response.data.length > 0)
          ? response.data
          : (response?.items || (response as any)?.content || (Array.isArray(response) ? response : []));
        setApplicants(list);
        setTotalPages(response?.pagination?.totalPages || (response as any)?.page?.totalPages || 1);
      } catch (err: unknown) {
        setFeedback({
          type: 'error',
          message: err instanceof Error ? err.message : 'Failed to retrieve applicants for this job.',
        });
        setApplicants([]);
      } finally {
        setIsLoadingApplicants(false);
      }
    };

    fetchApplicants();
  }, [selectedJobId, selectedStatus, page]);

  const handleJobChange = (newJobId: string) => {
    setSelectedJobId(newJobId);
    setPage(0);
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (newJobId) {
        next.set('jobId', newJobId);
      } else {
        next.delete('jobId');
      }
      return next;
    });
  };

  const handleStatusChange = (newStatus: string) => {
    setSelectedStatus(newStatus);
    setPage(0);
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (newStatus !== 'ALL') {
        next.set('status', newStatus);
      } else {
        next.delete('status');
      }
      return next;
    });
  };

  const currentJob = companyJobs.find((j) => String(j.id) === selectedJobId);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="font-headline font-bold text-2xl text-slate-text">Applicant Review</h1>
          <p className="text-xs text-slate-muted mt-0.5">
            Evaluate candidate profiles, review deterministic match percentages, and manage application funnels.
          </p>
        </div>

        {selectedJobId && (
          <Link to={`/recruiter/jobs/${selectedJobId}`}>
            <Button
              variant="outline"
              size="md"
              leftIcon={<span className="material-symbols-outlined !text-base">visibility</span>}
            >
              View Job Spec
            </Button>
          </Link>
        )}
      </div>

      {feedback && (
        <Alert variant={feedback.type} onClose={() => setFeedback(null)}>
          {feedback.message}
        </Alert>
      )}

      {/* Filter Toolbar: Job Selector & Status Filter */}
      <div className="bg-white p-4 rounded border border-outline-warm shadow-card flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-4">
        {/* Job Dropdown */}
        <div className="flex-1 max-w-md">
          <label className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block mb-1">
            Target Job Opening
          </label>
          {isLoadingJobs ? (
            <Skeleton height="38px" />
          ) : companyJobs.length === 0 ? (
            <div className="text-xs text-slate-muted italic">No job openings created yet.</div>
          ) : (
            <select
              value={selectedJobId}
              onChange={(e) => handleJobChange(e.target.value)}
              className="w-full text-xs font-medium py-2 px-3 border border-outline-warm rounded bg-white text-slate-text focus:outline-none focus:ring-1 focus:ring-primary min-h-[40px]"
            >
              {companyJobs.map((j) => (
                <option key={j.id} value={String(j.id)}>
                  {j.title} ({j.status}) · {j.applicantCount ?? 0} applicants
                </option>
              ))}
            </select>
          )}
        </div>

        {/* Status Filter */}
        <div className="w-full sm:w-56">
          <label className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block mb-1">
            Filter by Status
          </label>
          <select
            value={selectedStatus}
            onChange={(e) => handleStatusChange(e.target.value)}
            className="w-full text-xs font-medium py-2 px-3 border border-outline-warm rounded bg-white text-slate-text focus:outline-none focus:ring-1 focus:ring-primary min-h-[40px]"
          >
            <option value="ALL">All Application Statuses</option>
            <option value="APPLIED">Applied</option>
            <option value="UNDER_REVIEW">Under Review</option>
            <option value="SHORTLISTED">Shortlisted</option>
            <option value="INTERVIEW">Interview</option>
            <option value="SELECTED">Selected (Offered)</option>
            <option value="REJECTED">Rejected</option>
            <option value="WITHDRAWN">Withdrawn by Candidate</option>
          </select>
        </div>
      </div>

      {/* Main Applicants Card */}
      <Card>
        <CardHeader className="flex items-center justify-between">
          <div>
            <h2 className="font-headline font-semibold text-base text-slate-text">
              {currentJob ? `Applicants for "${currentJob.title}"` : 'Candidates'}
            </h2>
            <p className="text-xs text-slate-muted">
              {applicants.length} candidate(s) listed · Sorted by recent submission
            </p>
          </div>
        </CardHeader>

        <CardBody className="p-0">
          {isLoadingApplicants ? (
            <div className="p-5 space-y-3">
              <Skeleton height="48px" />
              <Skeleton height="48px" />
              <Skeleton height="48px" />
            </div>
          ) : companyJobs.length === 0 ? (
            <EmptyState
              icon="work_outline"
              title="No Job Postings"
              description="Create and publish your first job opening to start receiving early-career applicants."
              actionLabel="Create Job Opening"
              onAction={() => navigate('/recruiter/jobs/new')}
            />
          ) : applicants.length === 0 ? (
            <EmptyState
              icon="group_off"
              title="No Applicants Found"
              description={
                selectedStatus !== 'ALL'
                  ? `No applications currently in status "${selectedStatus}".`
                  : 'No candidates have submitted applications for this job opening yet.'
              }
              actionLabel={selectedStatus !== 'ALL' ? 'Show All Statuses' : undefined}
              onAction={selectedStatus !== 'ALL' ? () => handleStatusChange('ALL') : undefined}
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs sm:text-sm">
                <thead className="bg-[#FAFAF8] text-slate-muted text-[11px] uppercase tracking-wider border-b border-outline-warm">
                  <tr>
                    <th className="px-4 py-3 font-semibold">Candidate</th>
                    <th className="px-4 py-3 font-semibold">Match Score</th>
                    <th className="px-4 py-3 font-semibold">Applied Date</th>
                    <th className="px-4 py-3 font-semibold">Current Status</th>
                    <th className="px-4 py-3 text-right font-semibold">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-warm/70">
                  {applicants.map((app) => (
                    <tr key={app.applicationId} className="hover:bg-[#F9FAF9] transition-colors">
                      {/* Candidate Name & Info */}
                      <td className="px-4 py-3.5">
                        <Link
                          to={`/recruiter/applications/${app.applicationId}`}
                          className="font-semibold text-slate-text hover:text-primary transition-colors block"
                        >
                          {app.candidateName || 'Candidate Profile'}
                        </Link>
                        <div className="text-xs text-slate-muted">{app.candidateEmail}</div>
                        {app.candidateHeadline && (
                          <div className="text-[11px] text-slate-muted truncate max-w-xs mt-0.5">
                            {app.candidateHeadline}
                          </div>
                        )}
                      </td>

                      {/* Deterministic Match Percentage */}
                      <td className="px-4 py-3.5">
                        {app.matchPercentage !== undefined && app.matchPercentage !== null ? (
                          <span
                            className={`inline-flex items-center gap-1 font-mono text-xs font-semibold px-2 py-0.5 rounded ${
                              app.matchPercentage >= 70
                                ? 'bg-primary-tint text-primary border border-[#A7F3D0]'
                                : app.matchPercentage >= 40
                                ? 'bg-[#FEF3C7] text-[#92400E] border border-[#FDE68A]'
                                : 'bg-[#F3F4F6] text-slate-muted border border-outline-warm'
                            }`}
                          >
                            <span className="material-symbols-outlined !text-sm">analytics</span>
                            <span>{app.matchPercentage}% Match</span>
                          </span>
                        ) : (
                          <span className="text-xs text-slate-muted">—</span>
                        )}
                      </td>

                      {/* Applied Date */}
                      <td className="px-4 py-3.5 text-xs font-mono text-slate-muted">
                        {new Date(app.appliedAt).toLocaleDateString()}
                      </td>

                      {/* Current Status */}
                      <td className="px-4 py-3.5">
                        <StatusBadge status={app.status} size="sm" />
                      </td>

                      {/* Review Action */}
                      <td className="px-4 py-3.5 text-right">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => navigate(`/recruiter/applications/${app.applicationId}`)}
                          leftIcon={<span className="material-symbols-outlined !text-[15px]">person</span>}
                        >
                          Review
                        </Button>
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
