import React from 'react';
import type { JobSummaryDto } from '../../types/api';
import { Badge } from './Badge';
import { Button } from './Button';

export interface JobCardProps {
  job: JobSummaryDto;
  onView?: (job: JobSummaryDto) => void;
  onApply?: (job: JobSummaryDto) => void;
  hasApplied?: boolean;
}

export const JobCard: React.FC<JobCardProps> = ({
  job,
  onView,
  onApply,
  hasApplied = false,
}) => {
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
        return 'Fresher';
      case 'ZERO_TO_ONE_YEARS':
        return '0–1 Years';
      case 'ONE_TO_TWO_YEARS':
        return '1–2 Years';
      default:
        return level;
    }
  };

  const formatJobType = (type?: string) => {
    if (!type) return 'Full Time';
    return type.replace(/_/g, ' ');
  };

  return (
    <div className="bg-white rounded border border-outline-warm p-4 sm:p-5 hover:border-primary/40 hover:shadow-subtle transition-all flex flex-col justify-between gap-4 group">
      <div className="flex flex-col gap-3">
        {/* Top Header: Company & Match Score */}
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded bg-[#F2F5F3] border border-outline-warm flex items-center justify-center font-bold text-slate-text text-sm shrink-0 uppercase">
              {companyName.substring(0, 2)}
            </div>
            <div>
              <h3 className="font-headline font-semibold text-base text-slate-text group-hover:text-primary transition-colors leading-snug">
                {job.title}
              </h3>
              <p className="text-xs text-slate-muted font-sans">{companyName}</p>
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
            {formatJobType(job.employmentType || job.jobType)}
          </Badge>
          <Badge variant="neutral" size="sm">
            {formatWorkMode(job.workMode)}
          </Badge>
          <Badge variant="neutral" size="sm">
            {formatExpLevel(job.experienceLevel)}
          </Badge>
        </div>

        {/* Required Skills preview */}
        {job.skills && job.skills.length > 0 && (
          <div className="flex flex-wrap gap-1 mt-1">
            {job.skills.slice(0, 4).map((skill, idx) => {
              const skillName = typeof skill === 'string' ? skill : skill?.name || '';
              const skillKey = typeof skill === 'string' ? `${skill}-${idx}` : (skill?.id ?? idx);
              return (
                <span
                  key={skillKey}
                  className="text-[11px] font-mono bg-[#F8FAF9] text-slate-variant px-1.5 py-0.5 rounded border border-outline-warm/70"
                >
                  {skillName}
                </span>
              );
            })}
            {job.skills.length > 4 && (
              <span className="text-[11px] text-slate-muted self-center">
                +{job.skills.length - 4} more
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
              aria-label={`View details for ${job.title} at ${companyName}`}
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
              aria-label={`Apply for ${job.title} at ${companyName}`}
            >
              {hasApplied ? 'Applied' : 'Apply Now'}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
};
