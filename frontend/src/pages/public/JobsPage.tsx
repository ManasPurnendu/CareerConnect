import React, { useState, useEffect, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { jobsApi } from '../../api/jobs';
import type { JobSummaryDto, JobType, WorkMode, ExperienceLevel } from '../../types/api';
import { JobCard } from '../../components/ui/JobCard';
import { Button } from '../../components/ui/Button';
import { Pagination } from '../../components/ui/Pagination';
import { Skeleton } from '../../components/ui/Skeleton';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { useAuth } from '../../auth/useAuth';

export const JobsPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { isAuthenticated, user } = useAuth();

  const [jobs, setJobs] = useState<JobSummaryDto[]>([]);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Filters state from query params
  const keywordParam = searchParams.get('keyword') || searchParams.get('q') || '';
  const locationParam = searchParams.get('location') || '';
  const employmentTypeParam = searchParams.get('employmentType') || searchParams.get('jobType') || '';
  const workModeParam = searchParams.get('workMode') || '';
  const expLevelParam = searchParams.get('experienceLevel') || '';
  const skillsParam = searchParams.get('skills') || '';
  const sortParam = searchParams.get('sort') || '';

  const [keyword, setKeyword] = useState(keywordParam);
  const [location, setLocation] = useState(locationParam);
  const [employmentType, setEmploymentType] = useState(employmentTypeParam);
  const [workMode, setWorkMode] = useState(workModeParam);
  const [experienceLevel, setExperienceLevel] = useState(expLevelParam);
  const [sort, setSort] = useState(sortParam);

  const fetchJobs = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await jobsApi.getPublicJobs({
        keyword: keywordParam || undefined,
        location: locationParam || undefined,
        employmentType: employmentTypeParam ? (employmentTypeParam as JobType) : undefined,
        workMode: workModeParam ? (workModeParam as WorkMode) : undefined,
        experienceLevel: expLevelParam ? (expLevelParam as ExperienceLevel) : undefined,
        skills: skillsParam || undefined,
        sort: sortParam || undefined,
        page,
        size: 9,
      });

      if (response && response.data) {
        setJobs(response.data);
        if (response.pagination) {
          setTotalElements(response.pagination.totalElements);
          setTotalPages(response.pagination.totalPages);
        } else {
          setTotalElements(response.data.length);
          setTotalPages(1);
        }
      } else {
        setJobs([]);
        setTotalElements(0);
        setTotalPages(0);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Unable to fetch jobs. Please try again.';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  }, [keywordParam, locationParam, employmentTypeParam, workModeParam, expLevelParam, skillsParam, sortParam, page]);

  useEffect(() => {
    fetchJobs();
  }, [fetchJobs]);

  const handleApplyFilter = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const newParams = new URLSearchParams();
    if (keyword) newParams.set('keyword', keyword.trim());
    if (location) newParams.set('location', location.trim());
    if (employmentType) newParams.set('employmentType', employmentType);
    if (workMode) newParams.set('workMode', workMode);
    if (experienceLevel) newParams.set('experienceLevel', experienceLevel);
    if (sort) newParams.set('sort', sort);
    setPage(0);
    setSearchParams(newParams);
  };

  const clearFilters = () => {
    setKeyword('');
    setLocation('');
    setEmploymentType('');
    setWorkMode('');
    setExperienceLevel('');
    setSort('');
    setPage(0);
    setSearchParams(new URLSearchParams());
  };

  const handleApplyAction = (job: JobSummaryDto) => {
    if (!isAuthenticated) {
      navigate(`/login?next=${encodeURIComponent(`/jobs/${job.id}`)}`);
    } else if (user?.role === 'CANDIDATE') {
      navigate(`/candidate/jobs/${job.id}`);
    } else {
      navigate(`/jobs/${job.id}`);
    }
  };

  return (
    <div className="layout-container py-8 sm:py-10">
      {/* Header */}
      <div className="mb-6">
        <h1 className="font-headline font-bold text-2xl sm:text-3xl text-slate-text">
          Explore Early-Career Opportunities
        </h1>
        <p className="text-xs sm:text-sm text-slate-muted mt-1">
          Showing verified openings tailored for interns, freshers, and early professionals (0–2 years).
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
                Reset All
              </button>
            </div>

            {/* Employment Type */}
            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-text block mb-2 font-sans">
                Employment Type
              </label>
              <select
                value={employmentType}
                onChange={(e) => {
                  setEmploymentType(e.target.value);
                  const p = new URLSearchParams(searchParams);
                  if (e.target.value) p.set('employmentType', e.target.value);
                  else p.delete('employmentType');
                  p.delete('jobType');
                  setSearchParams(p);
                }}
                className="w-full text-xs p-2.5 rounded border border-outline-warm bg-white text-slate-text focus:ring-1 focus:ring-primary min-h-[44px]"
              >
                <option value="">All Types</option>
                <option value="INTERNSHIP">Internship</option>
                <option value="FULL_TIME">Full Time</option>
                <option value="PART_TIME">Part Time</option>
                <option value="APPRENTICESHIP">Apprenticeship</option>
                <option value="TRAINEE">Trainee</option>
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
                className="w-full text-xs p-2.5 rounded border border-outline-warm bg-white text-slate-text focus:ring-1 focus:ring-primary min-h-[44px]"
              >
                <option value="">All Modes</option>
                <option value="ONSITE">On-Site</option>
                <option value="HYBRID">Hybrid</option>
                <option value="REMOTE">Remote</option>
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
                className="w-full text-xs p-2.5 rounded border border-outline-warm bg-white text-slate-text focus:ring-1 focus:ring-primary min-h-[44px]"
              >
                <option value="">All Levels</option>
                <option value="INTERNSHIP">Internship</option>
                <option value="FRESHER">Fresher (0 years)</option>
                <option value="ZERO_TO_ONE_YEARS">0–1 Years</option>
                <option value="ONE_TO_TWO_YEARS">1–2 Years</option>
              </select>
            </div>

            {/* Sort Order */}
            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-text block mb-2 font-sans">
                Sort By
              </label>
              <select
                value={sort}
                onChange={(e) => {
                  setSort(e.target.value);
                  const p = new URLSearchParams(searchParams);
                  if (e.target.value) p.set('sort', e.target.value);
                  else p.delete('sort');
                  setSearchParams(p);
                }}
                className="w-full text-xs p-2.5 rounded border border-outline-warm bg-white text-slate-text focus:ring-1 focus:ring-primary min-h-[44px]"
              >
                <option value="">Newest First</option>
                <option value="latest">Latest</option>
                <option value="oldest">Oldest</option>
                <option value="deadline,asc">Deadline (Urgent First)</option>
                <option value="salary,desc">Salary (High to Low)</option>
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
                    onApply={handleApplyAction}
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
