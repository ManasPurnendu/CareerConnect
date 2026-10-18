import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { jobsApi } from '../../api/jobs';
import type { JobSummaryDto } from '../../types/api';
import { JobCard } from '../../components/ui/JobCard';
import { Skeleton } from '../../components/ui/Skeleton';

export const LandingPage: React.FC = () => {
  const [keyword, setKeyword] = useState('');
  const [location, setLocation] = useState('');
  const [featuredJobs, setFeaturedJobs] = useState<JobSummaryDto[]>([]);
  const [isLoadingJobs, setIsLoadingJobs] = useState<boolean>(true);
  const navigate = useNavigate();

  useEffect(() => {
    const loadFeatured = async () => {
      try {
        const response = await jobsApi.getPublicJobs({ size: 3 });
        if (response && response.data) {
          setFeaturedJobs(response.data);
        }
      } catch {
        // Non-blocking for landing page
      } finally {
        setIsLoadingJobs(false);
      }
    };
    loadFeatured();
  }, []);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const params = new URLSearchParams();
    if (keyword.trim()) params.set('keyword', keyword.trim());
    if (location.trim()) params.set('location', location.trim());
    const qString = params.toString();
    navigate(qString ? `/jobs?${qString}` : '/jobs');
  };

  return (
    <div className="flex flex-col gap-12 sm:gap-16 pb-16">
      {/* Hero Section */}
      <section className="pt-12 sm:pt-20 pb-8 border-b border-outline-warm bg-gradient-to-b from-white to-warm-bg">
        <div className="layout-container flex flex-col items-center text-center max-w-4xl mx-auto">
          <Badge variant="default" size="md" className="mb-4">
            Designed for 0–2 Years Experience & Freshers
          </Badge>

          <h1 className="font-headline font-bold text-3xl sm:text-5xl lg:text-6xl text-slate-text tracking-tight leading-[1.15] mb-6">
            Launch Your Career with Direct,{' '}
            <span className="text-primary underline decoration-[#A7F3D0] decoration-wavy underline-offset-8">
              Verified
            </span>{' '}
            Opportunities.
          </h1>

          <p className="font-body text-base sm:text-lg text-slate-muted max-w-2xl mb-8 leading-relaxed">
            CareerConnect bridges early-career talent with vetted employers through transparent matching, immutable applications, and guaranteed status feedback.
          </p>

          {/* Hero Search Box */}
          <form
            onSubmit={handleSearch}
            className="w-full max-w-3xl bg-white p-2 rounded-lg border border-outline-warm shadow-dropdown flex flex-col sm:flex-row gap-2"
          >
            <div className="flex-1 flex items-center px-3 gap-2 min-h-[44px]">
              <span className="material-symbols-outlined text-slate-muted !text-xl">search</span>
              <input
                type="text"
                placeholder="Job title, skills, or keywords"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                className="w-full bg-transparent text-sm text-slate-text placeholder:text-slate-muted focus:outline-none"
              />
            </div>

            <div className="hidden sm:block w-px bg-outline-warm my-1" />

            <div className="flex-1 flex items-center px-3 gap-2 min-h-[44px]">
              <span className="material-symbols-outlined text-slate-muted !text-xl">location_on</span>
              <input
                type="text"
                placeholder="City, State, or 'Remote'"
                value={location}
                onChange={(e) => setLocation(e.target.value)}
                className="w-full bg-transparent text-sm text-slate-text placeholder:text-slate-muted focus:outline-none"
              />
            </div>

            <Button type="submit" variant="primary" size="md" className="shrink-0 sm:w-auto">
              Find Jobs
            </Button>
          </form>

          {/* Quick tags */}
          <div className="flex flex-wrap items-center justify-center gap-2 mt-4 text-xs text-slate-muted">
            <span className="font-medium text-slate-text">Popular Searches:</span>
            <button
              type="button"
              onClick={() => navigate('/jobs?skills=React')}
              className="px-2 py-0.5 rounded bg-white border border-outline-warm hover:border-primary transition-colors cursor-pointer"
            >
              React
            </button>
            <button
              type="button"
              onClick={() => navigate('/jobs?skills=Java')}
              className="px-2 py-0.5 rounded bg-white border border-outline-warm hover:border-primary transition-colors cursor-pointer"
            >
              Java
            </button>
            <button
              type="button"
              onClick={() => navigate('/jobs?workMode=REMOTE')}
              className="px-2 py-0.5 rounded bg-white border border-outline-warm hover:border-primary transition-colors cursor-pointer"
            >
              Remote
            </button>
            <button
              type="button"
              onClick={() => navigate('/jobs?employmentType=INTERNSHIP')}
              className="px-2 py-0.5 rounded bg-white border border-outline-warm hover:border-primary transition-colors cursor-pointer"
            >
              Internships
            </button>
          </div>
        </div>
      </section>

      {/* Trust & Principles Section */}
      <section className="layout-container">
        <div className="text-center max-w-2xl mx-auto mb-10">
          <h2 className="font-headline font-bold text-2xl sm:text-3xl text-slate-text tracking-tight mb-2">
            Built Specifically for Early-Career Integrity
          </h2>
          <p className="text-xs sm:text-sm text-slate-muted">
            Traditional job boards let early talent get lost in black holes. CareerConnect enforces transparency.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="bg-white p-6 rounded border border-outline-warm shadow-subtle flex flex-col gap-3">
            <div className="w-10 h-10 rounded bg-primary-tint border border-[#A7F3D0] text-primary flex items-center justify-center">
              <span className="material-symbols-outlined !text-2xl">verified_user</span>
            </div>
            <h3 className="font-headline font-semibold text-lg text-slate-text">
              100% Verified Employers
            </h3>
            <p className="text-xs sm:text-sm text-slate-muted leading-relaxed">
              Every employer and recruiter is vetted. No fake postings, no spam recruitment, and zero multi-level schemes.
            </p>
          </div>

          <div className="bg-white p-6 rounded border border-outline-warm shadow-subtle flex flex-col gap-3">
            <div className="w-10 h-10 rounded bg-primary-tint border border-[#A7F3D0] text-primary flex items-center justify-center">
              <span className="material-symbols-outlined !text-2xl">rule</span>
            </div>
            <h3 className="font-headline font-semibold text-lg text-slate-text">
              Profile Readiness Gate
            </h3>
            <p className="text-xs sm:text-sm text-slate-muted leading-relaxed">
              Applications require complete education, verified skills, and resume readiness, ensuring recruiters evaluate serious profiles.
            </p>
          </div>

          <div className="bg-white p-6 rounded border border-outline-warm shadow-subtle flex flex-col gap-3">
            <div className="w-10 h-10 rounded bg-primary-tint border border-[#A7F3D0] text-primary flex items-center justify-center">
              <span className="material-symbols-outlined !text-2xl">history</span>
            </div>
            <h3 className="font-headline font-semibold text-lg text-slate-text">
              Transparent Status Tracking
            </h3>
            <p className="text-xs sm:text-sm text-slate-muted leading-relaxed">
              Watch your application progress through each stage: Applied, Under Review, Shortlisted, Interview, or Offered.
            </p>
          </div>
        </div>
      </section>

      {/* Featured Roles Preview */}
      <section className="layout-container">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h2 className="font-headline font-bold text-xl sm:text-2xl text-slate-text">
              Featured Early-Career Openings
            </h2>
            <p className="text-xs text-slate-muted">Freshly published roles welcoming 0–2 years experience</p>
          </div>
          <Link to="/jobs">
            <Button
              variant="outline"
              size="sm"
              rightIcon={<span className="material-symbols-outlined !text-base">arrow_forward</span>}
            >
              View All Jobs
            </Button>
          </Link>
        </div>

        {isLoadingJobs ? (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <Skeleton variant="card" />
            <Skeleton variant="card" />
            <Skeleton variant="card" />
          </div>
        ) : featuredJobs.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {featuredJobs.map((job) => (
              <JobCard
                key={job.id}
                job={job}
                onView={(j) => navigate(`/jobs/${j.id}`)}
                onApply={(j) => navigate(`/jobs/${j.id}`)}
              />
            ))}
          </div>
        ) : (
          <div className="bg-white rounded border border-outline-warm p-8 text-center text-xs text-slate-muted">
            No featured jobs found at the moment.{' '}
            <Link to="/jobs" className="text-primary underline">
              Browse all jobs
            </Link>
          </div>
        )}
      </section>

      {/* Call to action */}
      <section className="layout-container">
        <div className="bg-primary rounded-lg text-white p-8 sm:p-12 text-center flex flex-col items-center justify-center shadow-card">
          <h2 className="font-headline font-bold text-2xl sm:text-3xl max-w-xl mb-3">
            Ready to Take Your First Big Step?
          </h2>
          <p className="text-xs sm:text-sm text-primary-tint max-w-lg mb-6 leading-relaxed">
            Create your candidate profile today, build your verified readiness score, and get matched with companies hiring right now.
          </p>
          <div className="flex flex-col sm:flex-row gap-3">
            <Link to="/register">
              <Button variant="secondary" size="lg">
                Create Free Candidate Account
              </Button>
            </Link>
            <Link to="/register">
              <Button
                variant="outline"
                size="lg"
                className="bg-transparent text-white border-white/60 hover:bg-white/10"
              >
                Employers: Post a Job
              </Button>
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
};
