import React, { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { jobsApi } from '../../api/jobs';
import type { JobDetailDto } from '../../types/api';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { LoadingState } from '../../components/ui/LoadingState';
import { ErrorState } from '../../components/ui/ErrorState';
import { useAuth } from '../../auth/useAuth';

export const JobDetailPage: React.FC = () => {
  const { jobId } = useParams<{ jobId: string }>();
  const navigate = useNavigate();
  const { isAuthenticated, user } = useAuth();

  const [job, setJob] = useState<JobDetailDto | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchJob = async () => {
      if (!jobId) return;
      setIsLoading(true);
      setError(null);
      try {
        const data = await jobsApi.getJobById(jobId);
        setJob(data);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'The requested job posting could not be found or has been closed.';
        setError(msg);
      } finally {
        setIsLoading(false);
      }
    };
    fetchJob();
  }, [jobId]);

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

  const handleApplyClick = () => {
    if (!isAuthenticated) {
      navigate(`/login?next=${encodeURIComponent(`/jobs/${job.id}`)}`);
    } else if (user?.role === 'CANDIDATE') {
      navigate(`/candidate/jobs/${job.id}`);
    } else {
      alert('Only candidate accounts can submit job applications.');
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
    </div>
  );
};
