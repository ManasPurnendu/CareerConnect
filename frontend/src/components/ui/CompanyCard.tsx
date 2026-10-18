import React from 'react';
import type { Company } from '../../types/api';
import { Button } from './Button';

export interface CompanyCardProps {
  company: Company;
  onView?: (company: Company) => void;
}

export const CompanyCard: React.FC<CompanyCardProps> = ({ company, onView }) => {
  return (
    <div className="bg-white rounded border border-outline-warm p-5 hover:border-primary/40 hover:shadow-subtle transition-all flex flex-col justify-between gap-4 group">
      <div className="flex flex-col gap-3">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 rounded bg-[#F2F5F3] border border-outline-warm flex items-center justify-center font-bold text-slate-text text-base shrink-0 uppercase">
              {company.logoUrl ? (
                <img
                  src={company.logoUrl}
                  alt={`${company.name} logo`}
                  className="w-full h-full object-cover rounded"
                />
              ) : (
                company.name.substring(0, 2)
              )}
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <h3 className="font-headline font-semibold text-base text-slate-text group-hover:text-primary transition-colors leading-snug">
                  {company.name}
                </h3>
                {company.isVerified && (
                  <span
                    className="material-symbols-outlined !text-[16px] text-primary"
                    title="Verified Company"
                  >
                    verified
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-muted">{company.industry || 'Technology & Innovation'}</p>
            </div>
          </div>
        </div>

        {company.description && (
          <p className="text-xs text-slate-variant line-clamp-2 leading-relaxed">
            {company.description}
          </p>
        )}

        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-muted pt-1">
          {company.location && (
            <div className="flex items-center gap-1">
              <span className="material-symbols-outlined !text-[15px]">location_on</span>
              <span>{company.location}</span>
            </div>
          )}
          {company.size && (
            <div className="flex items-center gap-1">
              <span className="material-symbols-outlined !text-[15px]">group</span>
              <span>{company.size} employees</span>
            </div>
          )}
        </div>
      </div>

      <div className="pt-3 border-t border-outline-warm/60 flex items-center justify-end">
        {onView && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => onView(company)}
            aria-label={`View ${company.name} profile and jobs`}
          >
            View Company
          </Button>
        )}
      </div>
    </div>
  );
};
