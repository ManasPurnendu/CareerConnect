import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { recruiterApi } from '../../api/recruiter';
import type { Company, Job, Application } from '../../types/api';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Button } from '../../components/ui/Button';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';

export const RecruiterDashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const [company, setCompany] = useState<Company | null>(null);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [applications, setApplications] = useState<Application[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    const loadRecruiterData = async () => {
      setIsLoading(true);
      try {
        const [compRes, jobsRes, appsRes] = await Promise.allSettled([
          recruiterApi.getCompany(),
          recruiterApi.getJobs(0, 5),
          recruiterApi.getApplications(undefined, 0, 5),
        ]);

        if (compRes.status === 'fulfilled') setCompany(compRes.value);
        if (jobsRes.status === 'fulfilled' && jobsRes.value?.content) setJobs(jobsRes.value.content);
        if (appsRes.status === 'fulfilled' && appsRes.value?.content) setApplications(appsRes.value.content);
      } finally {
        setIsLoading(false);
      }
    };
    loadRecruiterData();
  }, []);

  return (
    <div className="space-y-6">
      {/* Top Banner / Company Status */}
      <div className="bg-white rounded border border-outline-warm p-5 shadow-card flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 rounded bg-primary-tint border border-[#A7F3D0] text-primary flex items-center justify-center font-bold text-lg uppercase">
            {company?.name ? company.name.substring(0, 2) : 'CO'}
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <h2 className="font-headline font-bold text-lg text-slate-text">
                {company?.name || 'Your Company Workspace'}
              </h2>
              {company?.isVerified && (
                <span
                  className="material-symbols-outlined !text-[18px] text-primary"
                  title="Verified Organization"
                >
                  verified
                </span>
              )}
            </div>
            <p className="text-xs text-slate-muted">
              {company?.location ? `${company.location} · ` : ''}Recruiter Portal
            </p>
          </div>
        </div>

        <Link to="/recruiter/jobs/new">
          <Button variant="primary" size="md" leftIcon={<span className="material-symbols-outlined !text-base">add</span>}>
            Post New Opening
          </Button>
        </Link>
      </div>

      {/* Metrics Row */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card>
          <CardBody className="p-4 sm:p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-slate-muted uppercase tracking-wider">
                Active Job Postings
              </p>
              <h3 className="font-headline font-bold text-2xl text-slate-text mt-1">
                {isLoading ? <Skeleton width="40px" height="28px" /> : jobs.length}
              </h3>
            </div>
            <div className="w-10 h-10 rounded bg-primary-tint border border-[#A7F3D0] text-primary flex items-center justify-center">
              <span className="material-symbols-outlined !text-xl">work</span>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-4 sm:p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-slate-muted uppercase tracking-wider">
                Total Applicants
              </p>
              <h3 className="font-headline font-bold text-2xl text-slate-text mt-1">
                {isLoading ? <Skeleton width="40px" height="28px" /> : applications.length}
              </h3>
            </div>
            <div className="w-10 h-10 rounded bg-secondary-container text-secondary-dark flex items-center justify-center">
              <span className="material-symbols-outlined !text-xl">group</span>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-4 sm:p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-slate-muted uppercase tracking-wider">
                Hiring Velocity
              </p>
              <h3 className="font-headline font-bold text-xl text-primary mt-1">
                Early-Career Active
              </h3>
            </div>
            <div className="w-10 h-10 rounded bg-[#F2F5F3] border border-outline-warm text-slate-text flex items-center justify-center">
              <span className="material-symbols-outlined !text-xl">speed</span>
            </div>
          </CardBody>
        </Card>
      </div>

      {/* Tables: Jobs & Applicants */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Active Jobs */}
        <Card>
          <CardHeader className="flex items-center justify-between">
            <div>
              <h3 className="font-headline font-semibold text-base text-slate-text">
                Active Job Openings
              </h3>
              <p className="text-xs text-slate-muted">Manage postings and applicants</p>
            </div>
            <Link to="/recruiter/jobs">
              <Button variant="ghost" size="sm">
                View All
              </Button>
            </Link>
          </CardHeader>
          <CardBody className="p-0">
            {isLoading ? (
              <div className="p-4 space-y-3">
                <Skeleton height="36px" />
                <Skeleton height="36px" />
              </div>
            ) : jobs.length === 0 ? (
              <div className="p-8 text-center text-xs text-slate-muted">
                No active jobs posted yet.{' '}
                <Link to="/recruiter/jobs/new" className="text-primary underline">
                  Post your first role
                </Link>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs sm:text-sm">
                  <thead className="bg-[#FAFAF8] text-slate-muted text-[11px] uppercase tracking-wider border-b border-outline-warm">
                    <tr>
                      <th className="px-4 py-3">Title</th>
                      <th className="px-4 py-3">Type</th>
                      <th className="px-4 py-3">Status</th>
                      <th className="px-4 py-3 text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-warm/70">
                    {jobs.map((job) => (
                      <tr key={job.id} className="hover:bg-[#F9FAF9]">
                        <td className="px-4 py-3 font-medium text-slate-text">{job.title}</td>
                        <td className="px-4 py-3 text-xs text-slate-muted">{job.jobType}</td>
                        <td className="px-4 py-3">
                          <StatusBadge status={job.status} size="sm" />
                        </td>
                        <td className="px-4 py-3 text-right">
                          <button
                            onClick={() => navigate(`/recruiter/applications?jobId=${job.id}`)}
                            className="text-xs text-primary font-medium hover:underline"
                          >
                            Applicants
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardBody>
        </Card>

        {/* Recent Applicants */}
        <Card>
          <CardHeader className="flex items-center justify-between">
            <div>
              <h3 className="font-headline font-semibold text-base text-slate-text">
                Recent Applicants
              </h3>
              <p className="text-xs text-slate-muted">Candidates matching requirements</p>
            </div>
            <Link to="/recruiter/applications">
              <Button variant="ghost" size="sm">
                View All
              </Button>
            </Link>
          </CardHeader>
          <CardBody className="p-0">
            {isLoading ? (
              <div className="p-4 space-y-3">
                <Skeleton height="36px" />
                <Skeleton height="36px" />
              </div>
            ) : applications.length === 0 ? (
              <div className="p-8 text-center text-xs text-slate-muted">
                No candidate applications received yet.
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs sm:text-sm">
                  <thead className="bg-[#FAFAF8] text-slate-muted text-[11px] uppercase tracking-wider border-b border-outline-warm">
                    <tr>
                      <th className="px-4 py-3">Candidate</th>
                      <th className="px-4 py-3">Applied For</th>
                      <th className="px-4 py-3">Match</th>
                      <th className="px-4 py-3">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-warm/70">
                    {applications.map((app) => (
                      <tr key={app.id} className="hover:bg-[#F9FAF9]">
                        <td className="px-4 py-3">
                          <div className="font-medium text-slate-text">{app.candidateName}</div>
                          <div className="text-[11px] text-slate-muted">{app.candidateEmail}</div>
                        </td>
                        <td className="px-4 py-3 text-xs text-slate-muted">{app.jobTitle}</td>
                        <td className="px-4 py-3 font-mono text-xs text-primary font-semibold">
                          {app.matchScore !== undefined ? `${Math.round(app.matchScore)}%` : '—'}
                        </td>
                        <td className="px-4 py-3">
                          <StatusBadge status={app.status} size="sm" />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardBody>
        </Card>
      </div>
    </div>
  );
};
