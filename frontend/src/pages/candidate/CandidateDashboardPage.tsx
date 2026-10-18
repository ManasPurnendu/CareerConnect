import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { candidateApi } from '../../api/candidate';
import type { Application, Job, CandidateProfile } from '../../types/api';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Button } from '../../components/ui/Button';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { Alert } from '../../components/ui/Alert';

export const CandidateDashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const [profile, setProfile] = useState<CandidateProfile | null>(null);
  const [applications, setApplications] = useState<Application[]>([]);
  const [matchedJobs, setMatchedJobs] = useState<Job[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [withdrawMessage, setWithdrawMessage] = useState<string | null>(null);

  useEffect(() => {
    const loadDashboardData = async () => {
      setIsLoading(true);
      try {
        const [profileRes, appRes, matchRes] = await Promise.allSettled([
          candidateApi.getProfile(),
          candidateApi.getApplications(0, 5),
          candidateApi.getMatchedJobs(0, 4),
        ]);

        if (profileRes.status === 'fulfilled') setProfile(profileRes.value);
        if (appRes.status === 'fulfilled' && appRes.value?.content) {
          setApplications(appRes.value.content);
        }
        if (matchRes.status === 'fulfilled' && matchRes.value?.content) {
          setMatchedJobs(matchRes.value.content);
        }
      } finally {
        setIsLoading(false);
      }
    };

    loadDashboardData();
  }, []);

  const handleWithdraw = async (appId: number) => {
    if (!window.confirm('Are you sure you want to withdraw this application? This action cannot be undone.')) {
      return;
    }
    try {
      // NOTE: uses PATCH per backend specification!
      await candidateApi.withdrawApplication(appId);
      setWithdrawMessage(`Application #${appId} successfully withdrawn.`);
      setApplications((prev) =>
        prev.map((app) => (app.id === appId ? { ...app, status: 'WITHDRAWN' } : app))
      );
    } catch {
      alert('Failed to withdraw application.');
    }
  };

  const isProfileComplete = profile?.isProfileComplete ?? false;

  return (
    <div className="space-y-6">
      {withdrawMessage && (
        <Alert variant="info" onClose={() => setWithdrawMessage(null)}>
          {withdrawMessage}
        </Alert>
      )}

      {/* Profile Readiness Banner (from Stitch Candidate Dashboard) */}
      {!isLoading && !isProfileComplete && (
        <div className="bg-[#FFFBEB] border border-[#FDE68A] rounded p-4 sm:p-5 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div className="flex items-start gap-3">
            <span className="material-symbols-outlined text-[#D97706] !text-2xl shrink-0 mt-0.5">
              warning
            </span>
            <div>
              <h3 className="font-headline font-semibold text-sm text-[#92400E]">
                Complete Your Profile to Unlock Verified Applications
              </h3>
              <p className="text-xs text-[#B45309] mt-0.5">
                Employers require verified education, at least 3 skills, and a uploaded resume snapshot before accepting submissions.
              </p>
            </div>
          </div>
          <Link to="/candidate/profile" className="shrink-0">
            <Button variant="primary" size="sm" className="bg-[#D97706] hover:bg-[#B45309] border-none text-white">
              Complete Profile
            </Button>
          </Link>
        </div>
      )}

      {/* Metrics Row */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card>
          <CardBody className="p-4 sm:p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-slate-muted uppercase tracking-wider">
                Active Applications
              </p>
              <h2 className="font-headline font-bold text-2xl text-slate-text mt-1">
                {isLoading ? <Skeleton width="40px" height="28px" /> : applications.length}
              </h2>
            </div>
            <div className="w-10 h-10 rounded bg-primary-tint border border-[#A7F3D0] text-primary flex items-center justify-center">
              <span className="material-symbols-outlined !text-xl">description</span>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-4 sm:p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-slate-muted uppercase tracking-wider">
                Matched Openings
              </p>
              <h2 className="font-headline font-bold text-2xl text-slate-text mt-1">
                {isLoading ? <Skeleton width="40px" height="28px" /> : matchedJobs.length}
              </h2>
            </div>
            <div className="w-10 h-10 rounded bg-secondary-container text-secondary-dark flex items-center justify-center">
              <span className="material-symbols-outlined !text-xl">auto_awesome</span>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-4 sm:p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-slate-muted uppercase tracking-wider">
                Profile Readiness
              </p>
              <h2 className="font-headline font-bold text-2xl text-slate-text mt-1">
                {isLoading ? (
                  <Skeleton width="50px" height="28px" />
                ) : isProfileComplete ? (
                  <span className="text-primary text-xl">100% Ready</span>
                ) : (
                  <span className="text-[#D97706] text-xl">Action Needed</span>
                )}
              </h2>
            </div>
            <div className="w-10 h-10 rounded bg-[#F2F5F3] border border-outline-warm text-slate-text flex items-center justify-center">
              <span className="material-symbols-outlined !text-xl">verified</span>
            </div>
          </CardBody>
        </Card>
      </div>

      {/* Main Content: Recent Applications & Matched Roles */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left 2 Cols: Applications */}
        <div className="lg:col-span-2 space-y-4">
          <Card>
            <CardHeader className="flex items-center justify-between">
              <div>
                <h3 className="font-headline font-semibold text-base text-slate-text">
                  Recent Applications
                </h3>
                <p className="text-xs text-slate-muted">Real-time status updates from employers</p>
              </div>
              <Link to="/candidate/applications">
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
                  You haven't submitted any applications yet.{' '}
                  <Link to="/jobs" className="text-primary underline">
                    Browse open roles
                  </Link>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs sm:text-sm">
                    <thead className="bg-[#FAFAF8] text-slate-muted text-[11px] uppercase tracking-wider border-b border-outline-warm">
                      <tr>
                        <th className="px-4 py-3">Role & Company</th>
                        <th className="px-4 py-3">Applied</th>
                        <th className="px-4 py-3">Status</th>
                        <th className="px-4 py-3 text-right">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-outline-warm/70">
                      {applications.map((app) => (
                        <tr key={app.id} className="hover:bg-[#F9FAF9]">
                          <td className="px-4 py-3">
                            <div className="font-medium text-slate-text">{app.jobTitle}</div>
                            <div className="text-xs text-slate-muted">{app.companyName}</div>
                          </td>
                          <td className="px-4 py-3 text-xs text-slate-muted whitespace-nowrap">
                            {new Date(app.appliedAt).toLocaleDateString()}
                          </td>
                          <td className="px-4 py-3">
                            <StatusBadge status={app.status} size="sm" />
                          </td>
                          <td className="px-4 py-3 text-right">
                            {app.status !== 'WITHDRAWN' && app.status !== 'REJECTED' && (
                              <button
                                onClick={() => handleWithdraw(app.id)}
                                className="text-xs text-slate-muted hover:text-status-rejected transition-colors font-medium underline"
                              >
                                Withdraw
                              </button>
                            )}
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

        {/* Right 1 Col: Matched Jobs Preview */}
        <div className="space-y-4">
          <Card>
            <CardHeader className="flex items-center justify-between">
              <h3 className="font-headline font-semibold text-base text-slate-text">
                Top Matched Jobs
              </h3>
              <Link to="/candidate/jobs" className="text-xs text-primary font-medium hover:underline">
                Explore All
              </Link>
            </CardHeader>
            <CardBody className="p-4 space-y-3">
              {isLoading ? (
                <div className="space-y-3">
                  <Skeleton height="48px" />
                  <Skeleton height="48px" />
                </div>
              ) : matchedJobs.length === 0 ? (
                <div className="text-xs text-slate-muted text-center py-4">
                  Complete your profile skills to view personalized matches.
                </div>
              ) : (
                matchedJobs.map((job) => (
                  <div
                    key={job.id}
                    className="p-3 rounded border border-outline-warm hover:border-primary/40 transition-colors cursor-pointer"
                    onClick={() => navigate(`/jobs/${job.id}`)}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <h4 className="font-semibold text-xs text-slate-text leading-tight hover:text-primary">
                        {job.title}
                      </h4>
                      {job.matchScore !== undefined && (
                        <span className="text-[10px] font-bold text-primary px-1.5 py-0.5 rounded bg-primary-tint shrink-0">
                          {Math.round(job.matchScore)}%
                        </span>
                      )}
                    </div>
                    <p className="text-[11px] text-slate-muted mt-0.5">{job.companyName}</p>
                    <div className="flex items-center gap-2 mt-2 text-[10px] text-slate-muted font-mono">
                      <span>{job.jobType}</span>
                      <span>•</span>
                      <span>{job.location}</span>
                    </div>
                  </div>
                ))
              )}
            </CardBody>
          </Card>
        </div>
      </div>
    </div>
  );
};
