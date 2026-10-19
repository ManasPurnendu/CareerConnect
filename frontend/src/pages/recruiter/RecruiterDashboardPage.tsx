import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { recruiterApi } from '../../api/recruiter';
import type {
  RecruiterCompanyResponseDto,
  JobSummaryDto,
  NotificationSummaryResponse,
} from '../../types/api';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Button } from '../../components/ui/Button';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { Alert } from '../../components/ui/Alert';
import { ApiClientError } from '../../api/client';

export const RecruiterDashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuth();

  const [company, setCompany] = useState<RecruiterCompanyResponseDto | null>(null);
  const [jobs, setJobs] = useState<JobSummaryDto[]>([]);
  const [notifications, setNotifications] = useState<NotificationSummaryResponse | null>(null);

  const [hasNoCompany, setHasNoCompany] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadDashboardData = async () => {
      setIsLoading(true);
      setError(null);

      try {
        const [compRes, jobsRes, notifRes] = await Promise.allSettled([
          recruiterApi.getCompany(),
          recruiterApi.getJobs(0, 50),
          recruiterApi.getNotifications(0, 5),
        ]);

        if (compRes.status === 'fulfilled') {
          setCompany(compRes.value);
          setHasNoCompany(false);
        } else {
          const reason = compRes.reason;
          const isNotFound =
            (reason instanceof ApiClientError && (reason.status === 404 || reason.errorCode === 'RESOURCE_NOT_FOUND')) ||
            (typeof reason === 'object' && reason !== null && ((reason as any).status === 404 || (reason as any).errorCode === 'RESOURCE_NOT_FOUND' || (reason as any).message?.toLowerCase().includes('not found')));

          if (isNotFound) {
            setHasNoCompany(true);
            setCompany(null);
          }
        }

        if (jobsRes.status === 'fulfilled') {
          const list = (jobsRes.value?.data && jobsRes.value.data.length > 0)
            ? jobsRes.value.data
            : (jobsRes.value.items || (jobsRes.value as any).content || []);
          setJobs(list);
        }

        if (notifRes.status === 'fulfilled') {
          setNotifications(notifRes.value);
        }
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : 'Unable to load recruiter workspace.');
      } finally {
        setIsLoading(false);
      }
    };

    loadDashboardData();
  }, []);

  // Compute metrics from actual jobs data
  const activeJobs = jobs.filter((j) => j.status === 'ACTIVE');
  const draftJobs = jobs.filter((j) => j.status === 'DRAFT');
  const closedJobs = jobs.filter((j) => j.status === 'CLOSED');
  const totalApplicants = jobs.reduce((acc, j) => acc + (j.applicantCount ?? 0), 0);

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton height="70px" />
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <Skeleton height="90px" />
          <Skeleton height="90px" />
          <Skeleton height="90px" />
          <Skeleton height="90px" />
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <Skeleton height="240px" />
          <Skeleton height="240px" />
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Welcome Banner */}
      <div className="bg-white rounded border border-outline-warm p-5 sm:p-6 shadow-card flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div className="flex items-center gap-3.5">
          <div className="w-12 h-12 rounded bg-primary-tint border border-[#A7F3D0] text-primary flex items-center justify-center font-bold text-lg uppercase shadow-subtle shrink-0">
            {company?.name ? company.name.substring(0, 2) : 'CC'}
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="font-headline font-bold text-xl sm:text-2xl text-slate-text">
                {company?.name || 'Recruiter Workspace'}
              </h1>
              {company && (
                <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-primary bg-primary-tint px-2 py-0.5 rounded border border-[#A7F3D0]">
                  <span className="material-symbols-outlined !text-sm">verified</span>
                  Verified
                </span>
              )}
            </div>
            <p className="text-xs text-slate-muted mt-0.5">
              Welcome back, <span className="font-semibold text-slate-text">{user?.fullName || user?.email}</span> ·{' '}
              {company ? `${company.location || 'HQ'} · ${company.industry || 'Organization'}` : 'Organization Profile Pending'}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <Link to="/recruiter/jobs/new">
            <Button
              variant="primary"
              size="md"
              leftIcon={<span className="material-symbols-outlined !text-base">add</span>}
            >
              Post New Role
            </Button>
          </Link>
        </div>
      </div>

      {error && (
        <Alert variant="error" onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {/* Unconfigured Company Alert Callout */}
      {hasNoCompany && (
        <div className="p-4 rounded border border-[#FED7AA] bg-[#FFF7ED] text-xs text-[#9A3412] flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <span className="material-symbols-outlined !text-2xl text-[#EA580C] shrink-0">
              domain_add
            </span>
            <div>
              <span className="font-bold block text-sm mb-0.5">Set Up Your Company Profile</span>
              Your recruiter account is not yet associated with a company profile. Establish your organization to publish jobs and receive applicants.
            </div>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => navigate('/recruiter/company')}
            className="shrink-0 whitespace-nowrap bg-white !border-[#FDBA74] !text-[#C2410C] hover:!bg-[#FFEDD5]"
          >
            Configure Profile
          </Button>
        </div>
      )}

      {/* Metrics Row */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 sm:gap-4">
        <Card>
          <CardBody className="p-4 flex items-center justify-between">
            <div>
              <p className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider">
                Active Openings
              </p>
              <h3 className="font-headline font-bold text-2xl text-slate-text mt-1">
                {activeJobs.length}
              </h3>
            </div>
            <div className="w-10 h-10 rounded bg-primary-tint border border-[#A7F3D0] text-primary flex items-center justify-center">
              <span className="material-symbols-outlined !text-xl">work</span>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-4 flex items-center justify-between">
            <div>
              <p className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider">
                Draft Openings
              </p>
              <h3 className="font-headline font-bold text-2xl text-slate-text mt-1">
                {draftJobs.length}
              </h3>
            </div>
            <div className="w-10 h-10 rounded bg-[#FEF3C7] border border-[#FDE68A] text-[#92400E] flex items-center justify-center">
              <span className="material-symbols-outlined !text-xl">edit_note</span>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-4 flex items-center justify-between">
            <div>
              <p className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider">
                Total Applicants
              </p>
              <h3 className="font-headline font-bold text-2xl text-slate-text mt-1">
                {totalApplicants}
              </h3>
            </div>
            <div className="w-10 h-10 rounded bg-[#EDE9FE] border border-[#DDD6FE] text-[#6D28D9] flex items-center justify-center">
              <span className="material-symbols-outlined !text-xl">group</span>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-4 flex items-center justify-between">
            <div>
              <p className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider">
                Closed Openings
              </p>
              <h3 className="font-headline font-bold text-2xl text-slate-text mt-1">
                {closedJobs.length}
              </h3>
            </div>
            <div className="w-10 h-10 rounded bg-[#F3F4F6] border border-outline-warm text-slate-muted flex items-center justify-center">
              <span className="material-symbols-outlined !text-xl">lock</span>
            </div>
          </CardBody>
        </Card>
      </div>

      {/* Two Column Grid: Recent Job Openings & Quick Actions / Notifications */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: Recent Job Openings (2 spans) */}
        <div className="lg:col-span-2 space-y-6">
          <Card>
            <CardHeader className="flex items-center justify-between">
              <div>
                <h2 className="font-headline font-semibold text-base text-slate-text">
                  Recent Job Openings
                </h2>
                <p className="text-xs text-slate-muted">Track status, deadlines, and applicant funnels</p>
              </div>
              <Link to="/recruiter/jobs">
                <Button variant="ghost" size="sm">
                  View All ({jobs.length})
                </Button>
              </Link>
            </CardHeader>
            <CardBody className="p-0">
              {jobs.length === 0 ? (
                <div className="p-8 text-center text-xs text-slate-muted space-y-2">
                  <span className="material-symbols-outlined !text-3xl text-slate-muted block mx-auto">
                    work_outline
                  </span>
                  <p>No job openings created yet.</p>
                  <Link to="/recruiter/jobs/new">
                    <Button variant="outline" size="sm" className="mt-2">
                      Create First Opening
                    </Button>
                  </Link>
                </div>
              ) : (
                <div className="divide-y divide-outline-warm/70">
                  {jobs.slice(0, 5).map((job) => (
                    <div
                      key={job.id}
                      className="p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 hover:bg-[#F9FAF9] transition-colors"
                    >
                      <div>
                        <Link
                          to={`/recruiter/jobs/${job.id}`}
                          className="font-semibold text-sm text-slate-text hover:text-primary transition-colors block"
                        >
                          {job.title}
                        </Link>
                        <span className="text-xs text-slate-muted">
                          {(job.jobType || 'FULL_TIME').replace('_', ' ')} · {job.workMode} · {job.location || 'Remote'}
                        </span>
                      </div>

                      <div className="flex items-center gap-3">
                        <Link
                          to={`/recruiter/jobs/${job.id}/applicants`}
                          className="text-xs font-mono text-slate-muted hover:text-primary flex items-center gap-1"
                        >
                          <span className="material-symbols-outlined !text-sm">group</span>
                          <span>{job.applicantCount ?? 0}</span>
                        </Link>
                        <StatusBadge status={job.status} size="sm" />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardBody>
          </Card>
        </div>

        {/* Right Column: Quick Actions & Notifications (1 span) */}
        <div className="space-y-6">
          {/* Quick Actions */}
          <Card>
            <CardHeader>
              <h2 className="font-headline font-semibold text-base text-slate-text">Quick Actions</h2>
            </CardHeader>
            <CardBody className="p-4 space-y-2">
              <Link
                to="/recruiter/jobs/new"
                className="flex items-center gap-3 p-3 rounded border border-outline-warm hover:border-primary hover:bg-[#F2F5F3] text-xs font-medium text-slate-text transition-colors"
              >
                <div className="w-8 h-8 rounded bg-primary-tint text-primary flex items-center justify-center shrink-0">
                  <span className="material-symbols-outlined !text-lg">post_add</span>
                </div>
                <div>
                  <span className="font-semibold block text-slate-text">Create Job Opening</span>
                  <span className="text-slate-muted">Define early-career qualifications</span>
                </div>
              </Link>

              <Link
                to="/recruiter/applications"
                className="flex items-center gap-3 p-3 rounded border border-outline-warm hover:border-primary hover:bg-[#F2F5F3] text-xs font-medium text-slate-text transition-colors"
              >
                <div className="w-8 h-8 rounded bg-[#EDE9FE] text-[#6D28D9] flex items-center justify-center shrink-0">
                  <span className="material-symbols-outlined !text-lg">how_to_reg</span>
                </div>
                <div>
                  <span className="font-semibold block text-slate-text">Review Applicants</span>
                  <span className="text-slate-muted">Evaluate match and download resumes</span>
                </div>
              </Link>

              <Link
                to="/recruiter/company"
                className="flex items-center gap-3 p-3 rounded border border-outline-warm hover:border-primary hover:bg-[#F2F5F3] text-xs font-medium text-slate-text transition-colors"
              >
                <div className="w-8 h-8 rounded bg-[#FEF3C7] text-[#92400E] flex items-center justify-center shrink-0">
                  <span className="material-symbols-outlined !text-lg">corporate_fare</span>
                </div>
                <div>
                  <span className="font-semibold block text-slate-text">Company Profile</span>
                  <span className="text-slate-muted">Manage organization branding</span>
                </div>
              </Link>
            </CardBody>
          </Card>

          {/* Notifications Snippet */}
          <Card>
            <CardHeader className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined !text-base text-slate-muted">notifications</span>
                <h2 className="font-headline font-semibold text-base text-slate-text">
                  Notifications
                </h2>
              </div>
              <Link to="/recruiter/notifications">
                <Button variant="ghost" size="sm">
                  View All
                </Button>
              </Link>
            </CardHeader>
            <CardBody className="p-4">
              {!notifications || notifications.items.length === 0 ? (
                <p className="text-xs text-slate-muted italic text-center py-3">
                  No notifications at this time.
                </p>
              ) : (
                <div className="space-y-3">
                  {notifications.items.slice(0, 3).map((n) => (
                    <div
                      key={n.id}
                      className={`p-3 rounded text-xs border ${
                        n.read
                          ? 'bg-[#FAFAF8] border-outline-warm text-slate-muted'
                          : 'bg-primary-tint/30 border-[#A7F3D0] text-slate-text'
                      }`}
                    >
                      <div className="font-semibold text-slate-text">{n.title}</div>
                      <p className="text-[11px] mt-0.5 line-clamp-2">{n.body}</p>
                      <span className="text-[10px] font-mono text-slate-muted block mt-1">
                        {new Date(n.createdAt).toLocaleDateString()}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </CardBody>
          </Card>
        </div>
      </div>
    </div>
  );
};
