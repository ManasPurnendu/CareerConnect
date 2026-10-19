import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { recruiterApi } from '../../api/recruiter';
import type { JobDetailDto, SkillDto } from '../../types/api';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Button } from '../../components/ui/Button';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { Alert } from '../../components/ui/Alert';
import { Modal } from '../../components/ui/Modal';

export const RecruiterJobDetailPage: React.FC = () => {
  const { jobId } = useParams<{ jobId: string }>();
  const navigate = useNavigate();

  const [job, setJob] = useState<JobDetailDto | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Modal confirmation action
  const [confirmAction, setConfirmAction] = useState<'publish' | 'close' | 'delete' | null>(null);
  const [isActionPending, setIsActionPending] = useState<boolean>(false);

  const fetchJob = async () => {
    if (!jobId) return;
    setIsLoading(true);
    try {
      const data = await recruiterApi.getJobById(jobId);
      setJob(data);
    } catch (err: unknown) {
      setFeedback({
        type: 'error',
        message: err instanceof Error ? err.message : 'Unable to load job details.',
      });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchJob();
  }, [jobId]);

  const handleAction = async () => {
    if (!confirmAction || !job) return;
    setIsActionPending(true);
    setFeedback(null);

    try {
      if (confirmAction === 'publish') {
        const updated = await recruiterApi.publishJob(job.id);
        setJob(updated);
        setFeedback({
          type: 'success',
          message: `Job opening has been published and is now accepting candidate applications.`,
        });
      } else if (confirmAction === 'close') {
        const updated = await recruiterApi.closeJob(job.id);
        setJob(updated);
        setFeedback({
          type: 'success',
          message: `Job opening has been closed. Candidates can no longer submit applications.`,
        });
      } else if (confirmAction === 'delete') {
        await recruiterApi.deleteJob(job.id);
        navigate('/recruiter/jobs');
        return;
      }
      setConfirmAction(null);
    } catch (err: unknown) {
      setFeedback({
        type: 'error',
        message: err instanceof Error ? err.message : `Failed to ${confirmAction} job.`,
      });
      setConfirmAction(null);
    } finally {
      setIsActionPending(false);
    }
  };

  if (isLoading) {
    return (
      <div className="space-y-6 max-w-4xl">
        <div className="space-y-2">
          <Skeleton height="36px" width="300px" />
          <Skeleton height="18px" width="450px" />
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <Skeleton height="80px" />
          <Skeleton height="80px" />
          <Skeleton height="80px" />
          <Skeleton height="80px" />
        </div>
        <Card>
          <CardBody className="p-6 space-y-4">
            <Skeleton height="20px" width="200px" />
            <Skeleton height="120px" />
          </CardBody>
        </Card>
      </div>
    );
  }

  if (!job) {
    return (
      <div className="space-y-4 max-w-4xl">
        <Alert variant="error">Job not found or access denied.</Alert>
        <Button variant="outline" size="md" onClick={() => navigate('/recruiter/jobs')}>
          Back to Job Postings
        </Button>
      </div>
    );
  }

  const applicantCount = job.applicantCount ?? 0;
  const hasApplications = applicantCount > 0;

  return (
    <div className="space-y-6 max-w-4xl">
      {/* Top Navigation Breadcrumb & Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-xs text-slate-muted mb-1">
            <Link to="/recruiter/jobs" className="hover:text-primary transition-colors">
              Job Postings
            </Link>
            <span>/</span>
            <span className="font-mono text-slate-text">#{job.id}</span>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="font-headline font-bold text-2xl text-slate-text">{job.title}</h1>
            <StatusBadge status={job.status} size="md" />
          </div>
          <p className="text-xs text-slate-muted mt-1">
            {job.company?.name} · Created {new Date(job.createdAt).toLocaleDateString()}
            {job.updatedAt && ` · Updated ${new Date(job.updatedAt).toLocaleDateString()}`}
          </p>
        </div>

        {/* Action Controls */}
        <div className="flex flex-wrap items-center gap-2">
          <Link to={`/recruiter/jobs/${job.id}/applicants`}>
            <Button
              variant="outline"
              size="md"
              leftIcon={<span className="material-symbols-outlined !text-base">group</span>}
            >
              Applicants ({applicantCount})
            </Button>
          </Link>

          {job.status === 'DRAFT' && (
            <>
              <Link to={`/recruiter/jobs/${job.id}/edit`}>
                <Button
                  variant="outline"
                  size="md"
                  leftIcon={<span className="material-symbols-outlined !text-base">edit</span>}
                >
                  Edit Draft
                </Button>
              </Link>
              <Button
                variant="primary"
                size="md"
                onClick={() => setConfirmAction('publish')}
                leftIcon={<span className="material-symbols-outlined !text-base">publish</span>}
              >
                Publish Job
              </Button>
              <Button
                variant="ghost"
                size="md"
                onClick={() => setConfirmAction('delete')}
                className="!text-status-rejected hover:!bg-[#FEE2E2]"
                title="Delete Draft"
              >
                <span className="material-symbols-outlined !text-lg">delete</span>
              </Button>
            </>
          )}

          {job.status === 'ACTIVE' && (
            <>
              <Link to={`/recruiter/jobs/${job.id}/edit`}>
                <Button
                  variant="outline"
                  size="md"
                  leftIcon={<span className="material-symbols-outlined !text-base">edit</span>}
                >
                  Edit Role
                </Button>
              </Link>
              <Button
                variant="outline"
                size="md"
                onClick={() => setConfirmAction('close')}
                leftIcon={<span className="material-symbols-outlined !text-base">lock</span>}
              >
                Close Role
              </Button>
            </>
          )}

          {job.status === 'CLOSED' && !hasApplications && (
            <Button
              variant="ghost"
              size="md"
              onClick={() => setConfirmAction('delete')}
              className="!text-status-rejected hover:!bg-[#FEE2E2]"
              title="Delete Closed Job (0 Applicants)"
            >
              <span className="material-symbols-outlined !text-lg">delete</span>
            </Button>
          )}
        </div>
      </div>

      {feedback && (
        <Alert variant={feedback.type} onClose={() => setFeedback(null)}>
          {feedback.message}
        </Alert>
      )}

      {/* Invariant Banner when Applications Exist */}
      {hasApplications && (
        <div className="p-4 rounded border border-[#FED7AA] bg-[#FFF7ED] text-xs text-[#9A3412] flex items-start gap-3">
          <span className="material-symbols-outlined !text-xl text-[#EA580C] shrink-0 mt-0.5">
            lock
          </span>
          <div>
            <span className="font-semibold block mb-0.5">
              Fundamental Requirements Locked ({applicantCount} Active Applicants)
            </span>
            Because candidate applications have been submitted for this job, fundamental requirements
            (Title, Job Type, Experience Level, Work Mode, Salary, and Required Skills) are immutable
            to preserve application integrity. Operational details (Description, Responsibilities,
            Location, Deadline) may still be edited.
          </div>
        </div>
      )}

      {/* Snapshot Metrics Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 sm:gap-4">
        <div className="bg-white p-4 rounded border border-outline-warm shadow-subtle">
          <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block">
            Job Type
          </span>
          <span className="text-sm font-semibold text-slate-text mt-1 block">
            {job.jobType.replace('_', ' ')}
          </span>
          <span className="text-[10px] font-mono text-slate-muted block mt-0.5">
            {job.workMode}
          </span>
        </div>

        <div className="bg-white p-4 rounded border border-outline-warm shadow-subtle">
          <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block">
            Experience Level
          </span>
          <span className="text-sm font-semibold text-slate-text mt-1 block">
            {job.experienceLevel.replace(/_/g, ' ')}
          </span>
          <span className="text-[10px] text-slate-muted block mt-0.5">0–2 Years Target</span>
        </div>

        <div className="bg-white p-4 rounded border border-outline-warm shadow-subtle">
          <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block">
            Compensation
          </span>
          <span className="text-sm font-semibold text-slate-text mt-1 block">
            {job.salaryMinLpa && job.salaryMaxLpa
              ? `₹${job.salaryMinLpa} – ₹${job.salaryMaxLpa} LPA`
              : job.salaryMinLpa
              ? `₹${job.salaryMinLpa} LPA`
              : 'Competitive'}
          </span>
          <span className="text-[10px] font-mono text-slate-muted block mt-0.5">
            {job.salaryCurrency || 'INR'}
          </span>
        </div>

        <div className="bg-white p-4 rounded border border-outline-warm shadow-subtle">
          <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block">
            Application Deadline
          </span>
          <span className="text-sm font-semibold text-slate-text mt-1 block font-mono">
            {job.applicationDeadline || 'No Deadline'}
          </span>
          <span className="text-[10px] text-slate-muted block mt-0.5">
            {job.location || 'Remote'}
          </span>
        </div>
      </div>

      {/* Required Skills Card */}
      <Card>
        <CardHeader>
          <h2 className="font-headline font-semibold text-base text-slate-text">
            Required Skills ({job.skills?.length ?? 0})
          </h2>
        </CardHeader>
        <CardBody className="p-4 sm:p-5">
          {job.skills && job.skills.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {job.skills.map((skill, idx) => {
                const name = typeof skill === 'string' ? skill : (skill as SkillDto).name;
                const id = typeof skill === 'object' && skill !== null ? (skill as SkillDto).id : idx;
                return (
                  <span
                    key={id}
                    className="inline-flex items-center gap-1.5 bg-primary-tint text-primary border border-[#A7F3D0] px-3 py-1 rounded text-xs font-mono font-medium"
                  >
                    <span>{name}</span>
                  </span>
                );
              })}
            </div>
          ) : (
            <p className="text-xs text-slate-muted italic">No specific skills configured.</p>
          )}
        </CardBody>
      </Card>

      {/* Description & Responsibilities */}
      <div className="space-y-4">
        <Card>
          <CardHeader>
            <h2 className="font-headline font-semibold text-base text-slate-text">Role Overview</h2>
          </CardHeader>
          <CardBody className="p-5 sm:p-6 text-sm text-slate-text leading-relaxed whitespace-pre-wrap">
            {job.description}
          </CardBody>
        </Card>

        {job.responsibilities && (
          <Card>
            <CardHeader>
              <h2 className="font-headline font-semibold text-base text-slate-text">Key Responsibilities</h2>
            </CardHeader>
            <CardBody className="p-5 sm:p-6 text-sm text-slate-text leading-relaxed whitespace-pre-wrap">
              {job.responsibilities}
            </CardBody>
          </Card>
        )}

        {job.requirements && (
          <Card>
            <CardHeader>
              <h2 className="font-headline font-semibold text-base text-slate-text">Candidate Qualifications & Eligibility</h2>
            </CardHeader>
            <CardBody className="p-5 sm:p-6 text-sm text-slate-text leading-relaxed whitespace-pre-wrap">
              {job.requirements}
            </CardBody>
          </Card>
        )}
      </div>

      {/* Confirmation Modal */}
      {confirmAction && (
        <Modal
          isOpen={true}
          onClose={() => !isActionPending && setConfirmAction(null)}
          title={
            confirmAction === 'publish'
              ? 'Publish Job Opening'
              : confirmAction === 'close'
              ? 'Close Job Opening'
              : 'Delete Job Posting'
          }
        >
          <div className="space-y-4">
            <p className="text-sm text-slate-text">
              {confirmAction === 'publish' && (
                <>
                  Are you ready to publish <strong className="text-slate-text">"{job.title}"</strong>?
                  It will transition from <span className="font-semibold">DRAFT</span> to{' '}
                  <span className="font-semibold text-primary">ACTIVE</span> and become immediately
                  discoverable by candidates.
                </>
              )}
              {confirmAction === 'close' && (
                <>
                  Are you sure you want to close <strong className="text-slate-text">"{job.title}"</strong>?
                  New applications will no longer be accepted. Existing applicant records and status
                  evaluations will remain intact.
                </>
              )}
              {confirmAction === 'delete' && (
                <>
                  Are you sure you want to delete <strong className="text-slate-text">"{job.title}"</strong>?
                  This action is permanent and cannot be reversed.
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
                variant={confirmAction === 'delete' ? 'danger' : 'primary'}
                size="md"
                onClick={handleAction}
                isLoading={isActionPending}
              >
                {confirmAction === 'publish'
                  ? 'Confirm & Publish'
                  : confirmAction === 'close'
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
