import React, { useState, useEffect, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { jobsApi } from '../../api/jobs';
import type { Job } from '../../types/api';
import { JobCard } from '../../components/ui/JobCard';
import { Button } from '../../components/ui/Button';
import { Pagination } from '../../components/ui/Pagination';
import { Skeleton } from '../../components/ui/Skeleton';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';

export const JobsPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();

  const [jobs, setJobs] = useState<Job[]>([]);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Filters state from query params
  const keywordParam = searchParams.get('keyword') || '';
  const locationParam = searchParams.get('location') || '';
  const jobTypeParam = searchParams.get('jobType') || '';
  const workModeParam = searchParams.get('workMode') || '';
  const expLevelParam = searchParams.get('experienceLevel') || '';

  const [keyword, setKeyword] = useState(keywordParam);
  const [location, setLocation] = useState(locationParam);
  const [jobType, setJobType] = useState(jobTypeParam);
  const [workMode, setWorkMode] = useState(workModeParam);
  const [experienceLevel, setExperienceLevel] = useState(expLevelParam);

  const fetchJobs = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await jobsApi.getPublicJobs({
        keyword: keywordParam || undefined,
        location: locationParam || undefined,
        jobType: jobTypeParam || undefined,
        workMode: workModeParam || undefined,
        experienceLevel: expLevelParam || undefined,
        page,
        size: 9,
      });

      if (response && response.content) {
        setJobs(response.content);
        setTotalElements(response.page.totalElements);
        setTotalPages(response.page.totalPages);
      } else {
        setJobs([]);
        setTotalElements(0);
        setTotalPages(0);
      }
    } catch {
      // If endpoint returns empty or backend error, display friendly message
      setError('Unable to fetch jobs. Please verify your connection or try again later.');
    } finally {
      setIsLoading(false);
    }
  }, [keywordParam, locationParam, jobTypeParam, workModeParam, expLevelParam, page]);

  useEffect(() => {
    fetchJobs();
  }, [fetchJobs]);

  const handleApplyFilter = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const newParams = new URLSearchParams();
    if (keyword) newParams.set('keyword', keyword);
    if (location) newParams.set('location', location);
    if (jobType) newParams.set('jobType', jobType);
    if (workMode) newParams.set('workMode', workMode);
    if (experienceLevel) newParams.set('experienceLevel', experienceLevel);
    setPage(0);
    setSearchParams(newParams);
  };

  const clearFilters = () => {
    setKeyword('');
    setLocation('');
    setJobType('');
    setWorkMode('');
    setExperienceLevel('');
    setPage(0);
    setSearchParams(new URLSearchParams());
  };

  return (
    <div className="layout-container py-8 sm:py-10">
      {/* Header */}
      <div className="mb-6">
        <h1 className="font-headline font-bold text-2xl sm:text-3xl text-slate-text">
          Explore Early-Career Opportunities
        </h1>
        <p className="text-xs sm:text-sm text-slate-muted mt-1">
          Showing verified openings tailored for interns, freshers, and early professionals.
        </p>
      </div>

      {/* Top Search Bar */}
      <form
        onSubmit={handleApplyFilter}
        className="bg-white p-2.5 rounded border border-outline-warm shadow-subtle flex flex-col md:flex-row gap-2.5 mb-8"
      >
        <div className="flex-1 flex items-center px-3 gap-2 min-h-[44px]">
          <span className="material-symbols-outlined text-slate-muted !text-xl">search</span>
          <input
            type="text"
            placeholder="Search by title, skill, or keyword..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="w-full bg-transparent text-sm text-slate-text placeholder:text-slate-muted focus:outline-none"
          />
        </div>

        <div className="hidden md:block w-px bg-outline-warm my-1" />

        <div className="flex-1 flex items-center px-3 gap-2 min-h-[44px]">
          <span className="material-symbols-outlined text-slate-muted !text-xl">location_on</span>
          <input
            type="text"
            placeholder="City, region, or 'Remote'..."
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            className="w-full bg-transparent text-sm text-slate-text placeholder:text-slate-muted focus:outline-none"
          />
        </div>

        <Button type="submit" variant="primary" size="md" className="shrink-0">
          Search
        </Button>
      </form>

      {/* Main Grid: Filters + Job Cards */}
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-8">
        {/* Filter Sidebar */}
        <div className="lg:col-span-1 space-y-6">
          <div className="bg-white p-5 rounded border border-outline-warm shadow-subtle space-y-5">
            <div className="flex items-center justify-between border-b border-outline-warm pb-3">
              <h3 className="font-headline font-semibold text-sm text-slate-text">Filters</h3>
              <button
                type="button"
                onClick={clearFilters}
                className="text-xs text-primary hover:underline font-medium"
              >
                Reset
              </button>
            </div>

            {/* Job Type */}
            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-text block mb-2 font-sans">
                Job Type
              </label>
              <select
                value={jobType}
                onChange={(e) => {
                  setJobType(e.target.value);
                  const p = new URLSearchParams(searchParams);
                  if (e.target.value) p.set('jobType', e.target.value);
                  else p.delete('jobType');
                  setSearchParams(p);
                }}
                className="w-full text-xs p-2.5 rounded border border-outline-warm bg-white text-slate-text focus:ring-1 focus:ring-primary"
              >
                <option value="">All Types</option>
                <option value="INTERNSHIP">Internship</option>
                <option value="FULL_TIME">Full Time</option>
                <option value="PART_TIME">Part Time</option>
                <option value="CONTRACT">Contract</option>
              </select>
            </div>

            {/* Work Mode */}
            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-text block mb-2 font-sans">
                Work Mode
              </label>
              <select
                value={workMode}
                onChange={(e) => {
                  setWorkMode(e.target.value);
                  const p = new URLSearchParams(searchParams);
                  if (e.target.value) p.set('workMode', e.target.value);
                  else p.delete('workMode');
                  setSearchParams(p);
                }}
                className="w-full text-xs p-2.5 rounded border border-outline-warm bg-white text-slate-text focus:ring-1 focus:ring-primary"
              >
                <option value="">All Modes</option>
                <option value="REMOTE">Remote</option>
                <option value="HYBRID">Hybrid</option>
                <option value="ON_SITE">On-Site</option>
              </select>
            </div>

            {/* Experience Level */}
            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-text block mb-2 font-sans">
                Experience Level
              </label>
              <select
                value={experienceLevel}
                onChange={(e) => {
                  setExperienceLevel(e.target.value);
                  const p = new URLSearchParams(searchParams);
                  if (e.target.value) p.set('experienceLevel', e.target.value);
                  else p.delete('experienceLevel');
                  setSearchParams(p);
                }}
                className="w-full text-xs p-2.5 rounded border border-outline-warm bg-white text-slate-text focus:ring-1 focus:ring-primary"
              >
                <option value="">All Levels</option>
                <option value="INTERN">Intern</option>
                <option value="FRESHER">Fresher (0 years)</option>
                <option value="JUNIOR">Junior (1–2 years)</option>
              </select>
            </div>
          </div>
        </div>

        {/* Jobs List Section */}
        <div className="lg:col-span-3 space-y-4">
          <div className="flex items-center justify-between text-xs text-slate-muted">
            <span>
              {isLoading ? 'Searching...' : `Found ${totalElements} open position${totalElements === 1 ? '' : 's'}`}
            </span>
          </div>

          {isLoading ? (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Skeleton variant="card" />
              <Skeleton variant="card" />
              <Skeleton variant="card" />
              <Skeleton variant="card" />
            </div>
          ) : error ? (
            <ErrorState
              title="Unable to load jobs"
              message={error}
              onRetry={fetchJobs}
            />
          ) : jobs.length === 0 ? (
            <EmptyState
              icon="search_off"
              title="No matching roles found"
              description="Try adjusting your keywords, work mode, or removing specific filter constraints to see more opportunities."
              actionLabel="Clear All Filters"
              onAction={clearFilters}
            />
          ) : (
            <>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {jobs.map((job) => (
                  <JobCard
                    key={job.id}
                    job={job}
                    onView={(j) => navigate(`/jobs/${j.id}`)}
                    onApply={(j) => navigate(`/candidate/jobs/${j.id}`)}
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
    </div>
  );
};
