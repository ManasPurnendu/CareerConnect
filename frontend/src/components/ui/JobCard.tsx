import React from 'react';
import type { Job } from '../../types/api';
import { Badge } from './Badge';
import { Button } from './Button';

export interface JobCardProps {
  job: Job;
  onView?: (job: Job) => void;
  onApply?: (job: Job) => void;
  hasApplied?: boolean;
}

export const JobCard: React.FC<JobCardProps> = ({
  job,
  onView,
  onApply,
  hasApplied = false,
}) => {
  const formatSalary = (min?: number, max?: number, currency = '$') => {
    if (!min && !max) return null;
    if (min && max) return `${currency}${min.toLocaleString()} - ${currency}${max.toLocaleString()}`;
    if (min) return `From ${currency}${min.toLocaleString()}`;
    return `Up to ${currency}${max?.toLocaleString()}`;
  };

  const salaryDisplay = formatSalary(job.minSalary, job.maxSalary, job.currency);

  return (
    <div className="bg-white rounded border border-outline-warm p-4 sm:p-5 hover:border-primary/40 hover:shadow-subtle transition-all flex flex-col justify-between gap-4 group">
      <div className="flex flex-col gap-3">
        {/* Top Header: Company & Match Score */}
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded bg-[#F2F5F3] border border-outline-warm flex items-center justify-center font-bold text-slate-text text-sm shrink-0 uppercase">
              {job.companyLogoUrl ? (
                <img
                  src={job.companyLogoUrl}
                  alt={`${job.companyName} logo`}
                  className="w-full h-full object-cover rounded"
                />
              ) : (
                job.companyName.substring(0, 2)
              )}
            </div>
            <div>
              <h3 className="font-headline font-semibold text-base text-slate-text group-hover:text-primary transition-colors leading-snug">
                {job.title}
              </h3>
              <p className="text-xs text-slate-muted font-sans">{job.companyName}</p>
            </div>
          </div>
          {job.matchScore !== undefined && (
            <span className="inline-flex items-center text-xs font-semibold px-2 py-0.5 rounded bg-primary-tint text-primary border border-[#A7F3D0] shrink-0">
              {Math.round(job.matchScore)}% Match
            </span>
          )}
        </div>

        {/* Location & Salary */}
        <div className="flex flex-wrap items-center gap-y-1 gap-x-4 text-xs text-slate-muted">
          <div className="flex items-center gap-1">
            <span className="material-symbols-outlined !text-[15px]">location_on</span>
            <span>{job.location}</span>
          </div>
          {salaryDisplay && (
            <div className="flex items-center gap-1">
              <span className="material-symbols-outlined !text-[15px]">payments</span>
              <span>{salaryDisplay}</span>
            </div>
          )}
        </div>

        {/* Badges */}
        <div className="flex flex-wrap gap-1.5 pt-1">
          <Badge variant="neutral" size="sm">
            {job.jobType.replace(/_/g, ' ')}
          </Badge>
          <Badge variant="neutral" size="sm">
            {job.workMode.replace(/_/g, ' ')}
          </Badge>
          <Badge variant="neutral" size="sm">
            {job.experienceLevel.replace(/_/g, ' ')}
          </Badge>
        </div>

        {/* Required Skills preview */}
        {job.requiredSkills && job.requiredSkills.length > 0 && (
          <div className="flex flex-wrap gap-1 mt-1">
            {job.requiredSkills.slice(0, 4).map((skill) => (
              <span
                key={skill}
                className="text-[11px] font-mono bg-[#F8FAF9] text-slate-variant px-1.5 py-0.5 rounded border border-outline-warm/70"
              >
                {skill}
              </span>
            ))}
            {job.requiredSkills.length > 4 && (
              <span className="text-[11px] text-slate-muted self-center">
                +{job.requiredSkills.length - 4} more
              </span>
            )}
          </div>
        )}
      </div>

      {/* Action Footer */}
      <div className="pt-3 border-t border-outline-warm/60 flex items-center justify-between gap-2 mt-auto">
        <span className="text-[11px] text-slate-muted">
          Posted {new Date(job.createdAt).toLocaleDateString()}
        </span>
        <div className="flex items-center gap-2">
          {onView && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => onView(job)}
              aria-label={`View details for ${job.title} at ${job.companyName}`}
            >
              View
            </Button>
          )}
          {onApply && (
            <Button
              variant={hasApplied ? 'secondary' : 'primary'}
              size="sm"
              disabled={hasApplied}
              onClick={() => onApply(job)}
              aria-label={`Apply for ${job.title} at ${job.companyName}`}
            >
              {hasApplied ? 'Applied' : 'Apply Now'}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
};
