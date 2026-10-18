import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { companiesApi } from '../../api/companies';
import type { CompanySummaryDto } from '../../types/api';
import { CompanyCard } from '../../components/ui/CompanyCard';
import { Button } from '../../components/ui/Button';
import { Pagination } from '../../components/ui/Pagination';
import { Skeleton } from '../../components/ui/Skeleton';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';

export const CompaniesPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const queryParam = searchParams.get('q') || '';
  const [query, setQuery] = useState(queryParam);
  const [companies, setCompanies] = useState<CompanySummaryDto[]>([]);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchCompanies = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await companiesApi.getPublicCompanies(queryParam || undefined, page, 12);
      if (response && response.data) {
        setCompanies(response.data);
        if (response.pagination) {
          setTotalElements(response.pagination.totalElements);
          setTotalPages(response.pagination.totalPages);
        } else {
          setTotalElements(response.data.length);
          setTotalPages(1);
        }
      } else {
        setCompanies([]);
        setTotalElements(0);
        setTotalPages(0);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Unable to load companies.';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  }, [queryParam, page]);

  useEffect(() => {
    fetchCompanies();
  }, [fetchCompanies]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const p = new URLSearchParams();
    if (query.trim()) p.set('q', query.trim());
    setPage(0);
    setSearchParams(p);
  };

  const handleClear = () => {
    setQuery('');
    setPage(0);
    setSearchParams(new URLSearchParams());
  };

  return (
    <div className="layout-container py-8 sm:py-10 space-y-6">
      <div>
        <h1 className="font-headline font-bold text-2xl sm:text-3xl text-slate-text">
          Featured & Verified Companies
        </h1>
        <p className="text-xs sm:text-sm text-slate-muted mt-1">
          Explore vetted employers actively hiring and supporting early-career talent.
        </p>
      </div>

      {/* Search Bar */}
      <form
        onSubmit={handleSearch}
        className="bg-white p-2.5 rounded border border-outline-warm shadow-subtle flex gap-2 max-w-xl"
      >
        <div className="flex-1 flex items-center px-3 gap-2 min-h-[44px]">
          <span className="material-symbols-outlined text-slate-muted !text-xl">search</span>
          <input
            type="text"
            placeholder="Search companies by name..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="w-full bg-transparent text-sm text-slate-text placeholder:text-slate-muted focus:outline-none"
          />
        </div>
        <Button type="submit" variant="primary" size="md">
          Search
        </Button>
        {queryParam && (
          <Button type="button" variant="outline" size="md" onClick={handleClear}>
            Clear
          </Button>
        )}
      </form>

      {/* Companies Grid */}
      <div className="space-y-4 pt-2">
        <div className="flex items-center justify-between text-xs text-slate-muted">
          <span>
            {isLoading
              ? 'Searching...'
              : `Found ${totalElements} compan${totalElements === 1 ? 'y' : 'ies'}`}
          </span>
        </div>

        {isLoading ? (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <Skeleton variant="card" />
            <Skeleton variant="card" />
            <Skeleton variant="card" />
          </div>
        ) : error ? (
          <ErrorState
            title="Unable to load companies"
            message={error}
            onRetry={fetchCompanies}
          />
        ) : companies.length === 0 ? (
          <EmptyState
            icon="domain_disabled"
            title="No companies found"
            description="Try modifying your search query to discover verified employers."
            actionLabel="View All Companies"
            onAction={handleClear}
          />
        ) : (
          <>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              {companies.map((comp) => (
                <CompanyCard
                  key={comp.id}
                  company={comp}
                  onView={(c) => navigate(`/companies/${c.id}`)}
                />
              ))}
            </div>

            {totalPages > 1 && (
              <div className="pt-6">
                <Pagination
                  currentPage={page}
                  totalPages={totalPages}
                  onPageChange={(newPage) => setPage(newPage)}
                />
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};
