import React from 'react';
import type { CompanySummaryDto } from '../../types/api';
import { Button } from './Button';

export interface CompanyCardProps {
  company: CompanySummaryDto;
  onView?: (company: CompanySummaryDto) => void;
}

export const CompanyCard: React.FC<CompanyCardProps> = ({ company, onView }) => {
  return (
    <div className="bg-white rounded border border-outline-warm p-5 hover:border-primary/40 hover:shadow-subtle transition-all flex flex-col justify-between gap-4 group">
      <div className="flex flex-col gap-3">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 rounded bg-[#F2F5F3] border border-outline-warm flex items-center justify-center font-bold text-slate-text text-base shrink-0 uppercase">
              {company.name.substring(0, 2)}
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <h3 className="font-headline font-semibold text-base text-slate-text group-hover:text-primary transition-colors leading-snug">
                  {company.name}
                </h3>
              </div>
              <p className="text-xs text-slate-muted">{company.industry || 'Information Technology'}</p>
            </div>
          </div>
          {(company.activeJobCount ?? 0) > 0 && (
            <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-primary-tint text-primary border border-[#A7F3D0] shrink-0 font-medium">
              {company.activeJobCount} Open Role{(company.activeJobCount ?? 0) > 1 ? 's' : ''}
            </span>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-muted pt-1">
          {company.location && (
            <div className="flex items-center gap-1">
              <span className="material-symbols-outlined !text-[15px]">location_on</span>
              <span>{company.location}</span>
            </div>
          )}
          {company.companySize && (
            <div className="flex items-center gap-1">
              <span className="material-symbols-outlined !text-[15px]">group</span>
              <span>{company.companySize} employees</span>
            </div>
          )}
          {company.website && (
            <div className="flex items-center gap-1">
              <span className="material-symbols-outlined !text-[15px]">language</span>
              <a
                href={company.website}
                target="_blank"
                rel="noreferrer"
                className="hover:underline text-primary"
                onClick={(e) => e.stopPropagation()}
              >
                Website
              </a>
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
            aria-label={`View ${company.name} details and open jobs`}
          >
            View Company
          </Button>
        )}
      </div>
    </div>
  );
};
