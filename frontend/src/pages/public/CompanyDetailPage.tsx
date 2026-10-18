import React, { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { companiesApi } from '../../api/companies';
import type { CompanyDetailDto } from '../../types/api';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { Button } from '../../components/ui/Button';
import { JobCard } from '../../components/ui/JobCard';
import { LoadingState } from '../../components/ui/LoadingState';
import { ErrorState } from '../../components/ui/ErrorState';

export const CompanyDetailPage: React.FC = () => {
  const { companyId } = useParams<{ companyId: string }>();
  const navigate = useNavigate();

  const [company, setCompany] = useState<CompanyDetailDto | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchCompany = async () => {
      if (!companyId) return;
      setIsLoading(true);
      setError(null);
      try {
        const data = await companiesApi.getCompanyById(companyId);
        setCompany(data);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Company not found or inactive.';
        setError(msg);
      } finally {
        setIsLoading(false);
      }
    };
    fetchCompany();
  }, [companyId]);

  if (isLoading) {
    return (
      <div className="layout-container py-16">
        <LoadingState message="Loading company profile..." />
      </div>
    );
  }

  if (error || !company) {
    return (
      <div className="layout-container py-16">
        <ErrorState
          title="Company Not Found"
          message={error || 'The requested company could not be located.'}
          onRetry={() => navigate('/companies')}
        />
      </div>
    );
  }

  return (
    <div className="layout-container py-8 sm:py-10 max-w-4xl mx-auto space-y-6">
      <Breadcrumb
        items={[
          { label: 'Home', path: '/' },
          { label: 'Companies', path: '/companies' },
          { label: company.name },
        ]}
      />

      {/* Main Company Header */}
      <div className="bg-white rounded border border-outline-warm p-6 sm:p-8 shadow-subtle space-y-5">
        <div className="flex flex-col sm:flex-row items-start justify-between gap-4">
          <div className="flex items-start gap-4">
            <div className="w-16 h-16 rounded bg-[#F2F5F3] border border-outline-warm flex items-center justify-center font-bold text-slate-text text-2xl shrink-0 uppercase">
              {company.name.substring(0, 2)}
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="font-headline font-bold text-2xl text-slate-text">
                  {company.name}
                </h1>
                <span
                  className="material-symbols-outlined !text-[20px] text-primary"
                  title="Verified Organization"
                >
                  verified
                </span>
              </div>
              <p className="text-sm text-slate-muted mt-0.5">
                {company.industry || 'Information Technology'}
              </p>
              <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-muted mt-2">
                {company.location && (
                  <span className="flex items-center gap-1">
                    <span className="material-symbols-outlined !text-[16px]">location_on</span>
                    {company.location}
                  </span>
                )}
                {company.companySize && (
                  <span className="flex items-center gap-1">
                    <span className="material-symbols-outlined !text-[16px]">group</span>
                    {company.companySize} employees
                  </span>
                )}
                {company.website && (
                  <span className="flex items-center gap-1">
                    <span className="material-symbols-outlined !text-[16px]">language</span>
                    <a
                      href={company.website}
                      target="_blank"
                      rel="noreferrer"
                      className="text-primary hover:underline"
                    >
                      {company.website.replace(/^https?:\/\//, '')}
                    </a>
                  </span>
                )}
              </div>
            </div>
          </div>

          <Link to={`/jobs?keyword=${encodeURIComponent(company.name)}`}>
            <Button variant="outline" size="md">
              Search Openings
            </Button>
          </Link>
        </div>

        {/* Description */}
        {company.description && (
          <div className="pt-4 border-t border-outline-warm/70 space-y-2">
            <h2 className="font-headline font-semibold text-base text-slate-text">About Company</h2>
            <p className="text-sm text-slate-variant leading-relaxed font-sans whitespace-pre-line">
              {company.description}
            </p>
          </div>
        )}
      </div>

      {/* Active Jobs Section */}
      <div className="space-y-4 pt-2">
        <div className="flex items-center justify-between">
          <h2 className="font-headline font-bold text-xl text-slate-text">
            Active Openings at {company.name} ({company.activeJobs ? company.activeJobs.length : 0})
          </h2>
        </div>

        {!company.activeJobs || company.activeJobs.length === 0 ? (
          <div className="bg-white rounded border border-outline-warm p-8 text-center text-xs text-slate-muted">
            There are currently no active public job openings for this company.
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {company.activeJobs.map((job) => (
              <JobCard
                key={job.id}
                job={job}
                onView={(j) => navigate(`/jobs/${j.id}`)}
                onApply={(j) => navigate(`/jobs/${j.id}`)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
