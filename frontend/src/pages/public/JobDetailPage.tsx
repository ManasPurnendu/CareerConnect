import React, { useState, useEffect, useCallback } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { jobsApi } from '../../api/jobs';
import { candidateApi } from '../../api/candidate';
import type { JobDetailDto, JobMatchDto, ProfileReadinessDto } from '../../types/api';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { LoadingState } from '../../components/ui/LoadingState';
import { ErrorState } from '../../components/ui/ErrorState';
import { Modal } from '../../components/ui/Modal';
import { Textarea } from '../../components/ui/Textarea';
import { Alert } from '../../components/ui/Alert';
import { useAuth } from '../../auth/useAuth';

export const JobDetailPage: React.FC = () => {
  const { jobId } = useParams<{ jobId: string }>();
  const navigate = useNavigate();
  const { isAuthenticated, user } = useAuth();

  const [job, setJob] = useState<JobDetailDto | null>(null);
  const [matchData, setMatchData] = useState<JobMatchDto | null>(null);
  const [readiness, setReadiness] = useState<ProfileReadinessDto | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Apply Modal state
  const [isApplyModalOpen, setIsApplyModalOpen] = useState<boolean>(false);
  const [isReadinessModalOpen, setIsReadinessModalOpen] = useState<boolean>(false);
  const [coverLetter, setCoverLetter] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [applyFeedback, setApplyFeedback] = useState<{ type: 'success' | 'error'; message: string; applicationId?: number } | null>(null);

  const isCandidate = isAuthenticated && user?.role === 'CANDIDATE';

  const fetchJobData = useCallback(async () => {
    if (!jobId) return;
    setIsLoading(true);
    setError(null);
    try {
      const data = await jobsApi.getJobById(jobId);
      setJob(data);

      if (isCandidate) {
        try {
          const match = await candidateApi.getJobMatch(jobId);
          setMatchData(match);
        } catch {
          // Non-blocking if match service is unavailable
        }
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'The requested job posting could not be found or has been closed.';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  }, [jobId, isCandidate]);

  useEffect(() => {
    fetchJobData();
  }, [fetchJobData]);

  const handleApplyClick = async () => {
    if (!isAuthenticated) {
      navigate(`/login?next=${encodeURIComponent(`/jobs/${jobId}`)}`);
      return;
    }

    if (user?.role !== 'CANDIDATE') {
      alert('Only candidate accounts can submit job applications.');
      return;
    }

    // Check authoritative profile readiness from backend
    try {
      const readyState = await candidateApi.getReadiness();
      setReadiness(readyState);

      if (!readyState.readyToApply) {
        setIsReadinessModalOpen(true);
      } else {
        setIsApplyModalOpen(true);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Unable to verify profile readiness.';
      setApplyFeedback({ type: 'error', message: msg });
    }
  };

  const handleApplicationSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!jobId) return;

    setIsSubmitting(true);
    setApplyFeedback(null);
    try {
      const result = await candidateApi.applyForJob(jobId, {
        coverLetter: coverLetter.trim() || undefined,
      });

      setIsApplyModalOpen(false);
      setApplyFeedback({
        type: 'success',
        message: 'Your application has been successfully submitted!',
        applicationId: result.applicationId,
      });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to submit application.';
      setApplyFeedback({ type: 'error', message: msg });
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return (
      <div className="layout-container py-16">
        <LoadingState message="Loading job details..." />
      </div>
    );
  }

  if (error || !job) {
    return (
      <div className="layout-container py-16">
        <ErrorState
          title="Job Posting Unavailable"
          message={error || 'This job is no longer available.'}
          onRetry={() => navigate('/jobs')}
        />
      </div>
    );
  }

  const companyName = job.company?.name || 'Company';

  const formatSalary = (min?: number, max?: number, currency = 'INR') => {
    if (!min && !max) return null;
    if (min && max) return `${min} - ${max} LPA ${currency}`;
    if (min) return `From ${min} LPA ${currency}`;
    return `Up to ${max} LPA ${currency}`;
  };

  const salaryDisplay = formatSalary(job.salaryMinLpa, job.salaryMaxLpa, job.salaryCurrency);

  const formatWorkMode = (mode: string) => {
    switch (mode) {
      case 'ONSITE':
        return 'On-Site';
      case 'HYBRID':
        return 'Hybrid';
      case 'REMOTE':
        return 'Remote';
      default:
        return mode;
    }
  };

  const formatExpLevel = (level: string) => {
    switch (level) {
      case 'INTERNSHIP':
        return 'Internship';
      case 'FRESHER':
        return 'Fresher (0 years)';
      case 'ZERO_TO_ONE_YEARS':
        return '0–1 Years';
      case 'ONE_TO_TWO_YEARS':
        return '1–2 Years';
      default:
        return level;
    }
  };

  return (
    <div className="layout-container py-8 sm:py-10 max-w-4xl mx-auto space-y-6">
      <Breadcrumb
        items={[
          { label: 'Home', path: '/' },
          { label: 'Jobs', path: '/jobs' },
          { label: job.title },
        ]}
      />

      {applyFeedback && (
        <Alert
          variant={applyFeedback.type === 'success' ? 'success' : 'error'}
          onClose={() => setApplyFeedback(null)}
        >
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
            <span>{applyFeedback.message}</span>
            {applyFeedback.applicationId && (
              <Link to={`/candidate/applications/${applyFeedback.applicationId}`}>
                <Button variant="primary" size="sm" className="whitespace-nowrap">
                  View Application Details
                </Button>
              </Link>
            )}
          </div>
        </Alert>
      )}

      {/* Main Job Header Card */}
      <div className="bg-white rounded border border-outline-warm p-6 sm:p-8 shadow-subtle space-y-6">
        <div className="flex flex-col sm:flex-row items-start justify-between gap-4">
          <div className="flex items-start gap-4">
            <div className="w-14 h-14 rounded bg-[#F2F5F3] border border-outline-warm flex items-center justify-center font-bold text-slate-text text-xl shrink-0 uppercase">
              {companyName.substring(0, 2)}
            </div>
            <div>
              <h1 className="font-headline font-bold text-xl sm:text-2xl text-slate-text">
                {job.title}
              </h1>
              <p className="text-sm font-medium text-slate-muted mt-0.5">
                <Link
                  to={`/companies/${job.company?.id}`}
                  className="hover:text-primary hover:underline transition-colors"
                >
                  {companyName}
                </Link>
                {job.company?.industry ? ` · ${job.company.industry}` : ''}
              </p>
              <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-muted mt-2">
                <span className="flex items-center gap-1">
                  <span className="material-symbols-outlined !text-[16px]">location_on</span>
                  {job.location}
                </span>
                <span className="flex items-center gap-1">
                  <span className="material-symbols-outlined !text-[16px]">schedule</span>
                  Posted {new Date(job.createdAt).toLocaleDateString()}
                </span>
                {job.applicationDeadline && (
                  <span className="flex items-center gap-1 text-status-review font-medium">
                    <span className="material-symbols-outlined !text-[16px]">event</span>
                    Deadline: {new Date(job.applicationDeadline).toLocaleDateString()}
                  </span>
                )}
              </div>
            </div>
          </div>

          <Button
            variant="primary"
            size="lg"
            onClick={handleApplyClick}
            className="w-full sm:w-auto shrink-0"
          >
            Apply For Role
          </Button>
        </div>

        {/* Badges Bar */}
        <div className="flex flex-wrap gap-2 pt-2 border-t border-outline-warm/70">
          <Badge variant="neutral" size="md">
            {(job.employmentType || job.jobType || 'FULL_TIME').replace(/_/g, ' ')}
          </Badge>
          <Badge variant="neutral" size="md">
            {formatWorkMode(job.workMode)}
          </Badge>
          <Badge variant="neutral" size="md">
            {formatExpLevel(job.experienceLevel)}
          </Badge>
          {salaryDisplay && (
            <Badge variant="success" size="md">
              Salary: {salaryDisplay}
            </Badge>
          )}
        </div>

        {/* Deterministic Job Match Card (Only for authenticated Candidate) */}
        {isCandidate && matchData && (
          <div className="p-4 sm:p-5 rounded-lg border border-primary/20 bg-[#F4F8F5] space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-primary !text-2xl">
                  insights
                </span>
                <div>
                  <h3 className="font-headline font-semibold text-sm text-slate-text">
                    Deterministic Skill Match
                  </h3>
                  <p className="text-xs text-slate-muted">Authoritative comparison against candidate profile</p>
                </div>
              </div>
              <div className="text-right">
                <span className="font-headline font-bold text-2xl text-primary">
                  {matchData.percentage}%
                </span>
                <span className="block text-[10px] text-slate-muted uppercase tracking-wider font-medium">
                  Skill Match
                </span>
              </div>
            </div>

            <div className="space-y-2 pt-1 text-xs">
              {matchData.matchedSkills.length > 0 && (
                <div>
                  <span className="font-medium text-slate-muted">Matched Skills ({matchData.matchedSkills.length}): </span>
                  <span className="text-primary font-medium">{matchData.matchedSkills.join(', ')}</span>
                </div>
              )}
              {matchData.missingSkills.length > 0 && (
                <div>
                  <span className="font-medium text-slate-muted">Missing Requirements ({matchData.missingSkills.length}): </span>
                  <span className="text-amber-700 font-medium">{matchData.missingSkills.join(', ')}</span>
                </div>
              )}
              {matchData.additionalSkills.length > 0 && (
                <div>
                  <span className="font-medium text-slate-muted">Additional Skills: </span>
                  <span className="text-slate-text">{matchData.additionalSkills.join(', ')}</span>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Required Skills */}
        {job.skills && job.skills.length > 0 && (
          <div className="pt-2">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-text mb-2">
              Required Skills
            </h3>
            <div className="flex flex-wrap gap-1.5">
              {job.skills.map((skill, idx) => {
                const name = typeof skill === 'string' ? skill : skill?.name || '';
                const key = typeof skill === 'string' ? `${skill}-${idx}` : (skill?.id ?? idx);
                return (
                  <span
                    key={key}
                    className="text-xs font-mono bg-[#F2F5F3] text-slate-text px-2.5 py-1 rounded border border-outline-warm"
                  >
                    {name}
                  </span>
                );
              })}
            </div>
          </div>
        )}

        {/* Job Description */}
        {job.description && (
          <div className="pt-4 border-t border-outline-warm/70 space-y-3">
            <h2 className="font-headline font-semibold text-base sm:text-lg text-slate-text">
              Role Overview
            </h2>
            <div className="text-sm text-slate-variant leading-relaxed whitespace-pre-line font-sans">
              {job.description}
            </div>
          </div>
        )}

        {/* Responsibilities */}
        {job.responsibilities && (
          <div className="pt-4 border-t border-outline-warm/70 space-y-3">
            <h2 className="font-headline font-semibold text-base sm:text-lg text-slate-text">
              Responsibilities
            </h2>
            <div className="text-sm text-slate-variant leading-relaxed whitespace-pre-line font-sans">
              {job.responsibilities}
            </div>
          </div>
        )}

        {/* Requirements */}
        {job.requirements && (
          <div className="pt-4 border-t border-outline-warm/70 space-y-3">
            <h2 className="font-headline font-semibold text-base sm:text-lg text-slate-text">
              Requirements
            </h2>
            <div className="text-sm text-slate-variant leading-relaxed whitespace-pre-line font-sans">
              {job.requirements}
            </div>
          </div>
        )}

        {/* Bottom Apply CTA */}
        <div className="pt-6 border-t border-outline-warm flex items-center justify-between">
          <Link to="/jobs" className="text-xs text-primary font-medium hover:underline flex items-center gap-1">
            <span className="material-symbols-outlined !text-base">arrow_back</span>
            Back to All Openings
          </Link>
          <Button variant="primary" size="md" onClick={handleApplyClick}>
            Apply Now
          </Button>
        </div>
      </div>

      {/* Incomplete Readiness Modal */}
      <Modal
        isOpen={isReadinessModalOpen}
        onClose={() => setIsReadinessModalOpen(false)}
        title="Profile Incomplete — Cannot Apply"
      >
        <div className="space-y-4">
          <p className="text-xs text-slate-text leading-relaxed">
            CareerConnect requires all candidates to have a verified profile and resume snapshot before applying to roles.
          </p>

          <div className="p-4 bg-[#F9FAF8] border border-outline-warm rounded space-y-2">
            <div className="flex items-center gap-2 text-xs">
              <span
                className={`material-symbols-outlined !text-lg ${
                  readiness?.profileComplete ? 'text-primary' : 'text-rose-600'
                }`}
              >
                {readiness?.profileComplete ? 'check_circle' : 'cancel'}
              </span>
              <span className={readiness?.profileComplete ? 'text-slate-text' : 'text-rose-700 font-medium'}>
                Full Name completed
              </span>
            </div>
            <div className="flex items-center gap-2 text-xs">
              <span
                className={`material-symbols-outlined !text-lg ${
                  readiness?.hasEducation ? 'text-primary' : 'text-rose-600'
                }`}
              >
                {readiness?.hasEducation ? 'check_circle' : 'cancel'}
              </span>
              <span className={readiness?.hasEducation ? 'text-slate-text' : 'text-rose-700 font-medium'}>
                At least one education record
              </span>
            </div>
            <div className="flex items-center gap-2 text-xs">
              <span
                className={`material-symbols-outlined !text-lg ${
                  readiness?.hasSkills ? 'text-primary' : 'text-rose-600'
                }`}
              >
                {readiness?.hasSkills ? 'check_circle' : 'cancel'}
              </span>
              <span className={readiness?.hasSkills ? 'text-slate-text' : 'text-rose-700 font-medium'}>
                At least one skill assigned
              </span>
            </div>
            <div className="flex items-center gap-2 text-xs">
              <span
                className={`material-symbols-outlined !text-lg ${
                  readiness?.hasResume ? 'text-primary' : 'text-rose-600'
                }`}
              >
                {readiness?.hasResume ? 'check_circle' : 'cancel'}
              </span>
              <span className={readiness?.hasResume ? 'text-slate-text' : 'text-rose-700 font-medium'}>
                Current active resume uploaded (PDF)
              </span>
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" size="sm" onClick={() => setIsReadinessModalOpen(false)}>
              Close
            </Button>
            <Link to="/candidate/profile">
              <Button variant="primary" size="sm">
                Complete Profile Now
              </Button>
            </Link>
          </div>
        </div>
      </Modal>

      {/* Application Submission Modal */}
      <Modal
        isOpen={isApplyModalOpen}
        onClose={() => setIsApplyModalOpen(false)}
        title={`Apply for ${job.title}`}
      >
        <form onSubmit={handleApplicationSubmit} className="space-y-4">
          <div className="p-3 bg-[#F4F8F5] border border-primary/20 rounded text-xs space-y-1">
            <p className="font-semibold text-slate-text">
              Applying to {companyName} · {job.location}
            </p>
            <p className="text-slate-muted">
              Your verified profile, skills, education, and current resume will be captured as an immutable application snapshot.
            </p>
          </div>

          <Textarea
            label="Cover Letter (Optional)"
            value={coverLetter}
            onChange={(e) => setCoverLetter(e.target.value)}
            placeholder="Introduce yourself to the recruiter and highlight your relevant experience or projects..."
            rows={4}
            maxLength={5000}
          />
          <p className="text-[11px] text-slate-muted text-right">
            {coverLetter.length} / 5000 characters
          </p>

          <div className="flex justify-end gap-2 pt-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setIsApplyModalOpen(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              size="sm"
              isLoading={isSubmitting}
            >
              Submit Application
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
