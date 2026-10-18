import React, { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { jobsApi } from '../../api/jobs';
import type { Job } from '../../types/api';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { LoadingState } from '../../components/ui/LoadingState';
import { ErrorState } from '../../components/ui/ErrorState';

export const JobDetailPage: React.FC = () => {
  const { jobId } = useParams<{ jobId: string }>();
  const navigate = useNavigate();

  const [job, setJob] = useState<Job | null>(null);
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
      } catch {
        setError('The requested job posting could not be found or has been closed.');
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
              {job.companyLogoUrl ? (
                <img
                  src={job.companyLogoUrl}
                  alt={job.companyName}
                  className="w-full h-full object-cover rounded"
                />
              ) : (
                job.companyName.substring(0, 2)
              )}
            </div>
            <div>
              <h1 className="font-headline font-bold text-xl sm:text-2xl text-slate-text">
                {job.title}
              </h1>
              <p className="text-sm font-medium text-slate-muted mt-0.5">{job.companyName}</p>
              <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-muted mt-2">
                <span className="flex items-center gap-1">
                  <span className="material-symbols-outlined !text-[16px]">location_on</span>
                  {job.location}
                </span>
                <span className="flex items-center gap-1">
                  <span className="material-symbols-outlined !text-[16px]">schedule</span>
                  Posted {new Date(job.createdAt).toLocaleDateString()}
                </span>
                {job.openPositions && (
                  <span className="flex items-center gap-1">
                    <span className="material-symbols-outlined !text-[16px]">group</span>
                    {job.openPositions} opening{job.openPositions > 1 ? 's' : ''}
                  </span>
                )}
              </div>
            </div>
          </div>

          <Link to={`/candidate/jobs/${job.id}`}>
            <Button variant="primary" size="lg" className="w-full sm:w-auto">
              Apply For Role
            </Button>
          </Link>
        </div>

        {/* Badges Bar */}
        <div className="flex flex-wrap gap-2 pt-2 border-t border-outline-warm/70">
          <Badge variant="neutral" size="md">
            {job.jobType.replace(/_/g, ' ')}
          </Badge>
          <Badge variant="neutral" size="md">
            {job.workMode.replace(/_/g, ' ')}
          </Badge>
          <Badge variant="neutral" size="md">
            {job.experienceLevel.replace(/_/g, ' ')}
          </Badge>
          {job.minSalary && (
            <Badge variant="success" size="md">
              Salary: {job.currency || '$'}{job.minSalary.toLocaleString()}
              {job.maxSalary ? ` - ${job.currency || '$'}${job.maxSalary.toLocaleString()}` : '+'}
            </Badge>
          )}
        </div>

        {/* Required Skills */}
        {job.requiredSkills && job.requiredSkills.length > 0 && (
          <div className="pt-2">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-text mb-2">
              Required Skills
            </h3>
            <div className="flex flex-wrap gap-1.5">
              {job.requiredSkills.map((skill) => (
                <span
                  key={skill}
                  className="text-xs font-mono bg-[#F2F5F3] text-slate-text px-2.5 py-1 rounded border border-outline-warm"
                >
                  {skill}
                </span>
              ))}
            </div>
          </div>
        )}

        {/* Job Description */}
        <div className="pt-4 border-t border-outline-warm/70 space-y-3">
          <h2 className="font-headline font-semibold text-base sm:text-lg text-slate-text">
            Job Description & Responsibilities
          </h2>
          <div className="text-sm text-slate-variant leading-relaxed whitespace-pre-line font-sans">
            {job.description}
          </div>
        </div>

        {/* Bottom Apply CTA */}
        <div className="pt-6 border-t border-outline-warm flex items-center justify-between">
          <Link to="/jobs" className="text-xs text-primary font-medium hover:underline flex items-center gap-1">
            <span className="material-symbols-outlined !text-base">arrow_back</span>
            Back to All Openings
          </Link>
          <Link to={`/candidate/jobs/${job.id}`}>
            <Button variant="primary" size="md">
              Apply Now
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
};
