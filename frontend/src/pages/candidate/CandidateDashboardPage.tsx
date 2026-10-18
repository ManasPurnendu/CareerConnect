import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { candidateApi } from '../../api/candidate';
import { jobsApi } from '../../api/jobs';
import type {
  ApplicationResponseDto,
  CandidateProfileResponse,
  ProfileReadinessDto,
  ResumeResponse,
  NotificationSummaryResponse,
  JobSummaryDto,
} from '../../types/api';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Button } from '../../components/ui/Button';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { Alert } from '../../components/ui/Alert';
import { Modal } from '../../components/ui/Modal';
import { useAuth } from '../../auth/useAuth';

export const CandidateDashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuth();

  const [profile, setProfile] = useState<CandidateProfileResponse | null>(null);
  const [readiness, setReadiness] = useState<ProfileReadinessDto | null>(null);
  const [resume, setResume] = useState<ResumeResponse | null>(null);
  const [applications, setApplications] = useState<ApplicationResponseDto[]>([]);
  const [notifications, setNotifications] = useState<NotificationSummaryResponse | null>(null);
  const [recentJobs, setRecentJobs] = useState<JobSummaryDto[]>([]);

  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Withdrawal modal state
  const [withdrawingApp, setWithdrawingApp] = useState<ApplicationResponseDto | null>(null);
  const [isWithdrawing, setIsWithdrawing] = useState<boolean>(false);

  useEffect(() => {
    const fetchDashboard = async () => {
      setIsLoading(true);
      try {
        const [profRes, readyRes, resumeRes, appsRes, notifRes, jobsRes] = await Promise.allSettled([
          candidateApi.getProfile(),
          candidateApi.getReadiness(),
          candidateApi.getCurrentResume(),
          candidateApi.getApplications(0, 5),
          candidateApi.getNotifications(0, 5, true),
          jobsApi.getPublicJobs({ page: 0, size: 4 }),
        ]);

        if (profRes.status === 'fulfilled') setProfile(profRes.value);
        if (readyRes.status === 'fulfilled') setReadiness(readyRes.value);
        if (resumeRes.status === 'fulfilled') setResume(resumeRes.value);
        if (appsRes.status === 'fulfilled' && appsRes.value?.data) {
          setApplications(appsRes.value.data);
        }
        if (notifRes.status === 'fulfilled') setNotifications(notifRes.value);
        if (jobsRes.status === 'fulfilled' && jobsRes.value?.data) {
          setRecentJobs(jobsRes.value.data);
        }
      } finally {
        setIsLoading(false);
      }
    };

    fetchDashboard();
  }, []);

  const handleWithdrawConfirm = async () => {
    if (!withdrawingApp) return;
    setIsWithdrawing(true);
    setFeedback(null);
    try {
      const updated = await candidateApi.withdrawApplication(withdrawingApp.applicationId);
      setApplications((prev) =>
        prev.map((app) => (app.applicationId === updated.applicationId ? updated : app))
      );
      setFeedback({
        type: 'success',
        message: `Application #${withdrawingApp.applicationId} for "${withdrawingApp.jobTitle}" has been withdrawn.`,
      });
      setWithdrawingApp(null);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to withdraw application.';
      setFeedback({ type: 'error', message: msg });
    } finally {
      setIsWithdrawing(false);
    }
  };

  const isWithdrawable = (status: string) => {
    return ['APPLIED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW'].includes(status);
  };

  return (
    <div className="space-y-6">
      {feedback && (
        <Alert variant={feedback.type === 'success' ? 'success' : 'error'} onClose={() => setFeedback(null)}>
          {feedback.message}
        </Alert>
      )}

      {/* Greeting Banner */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 bg-white border border-outline-warm rounded p-6 shadow-subtle">
        <div className="space-y-1">
          <h1 className="font-headline font-bold text-2xl text-slate-text">
            Welcome back, {profile?.fullName || user?.fullName || 'Candidate'}
          </h1>
          <p className="text-xs text-slate-muted">
            {profile?.headline || 'Early-career professional'} {profile?.location ? `· ${profile.location}` : ''}
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Link to="/candidate/profile">
            <Button variant="outline" size="sm" className="flex items-center gap-1.5">
              <span className="material-symbols-outlined !text-[16px]">person</span>
              Edit Profile
            </Button>
          </Link>
          <Link to="/jobs">
            <Button variant="primary" size="sm" className="flex items-center gap-1.5">
              <span className="material-symbols-outlined !text-[16px]">work</span>
              Explore Jobs
            </Button>
          </Link>
        </div>
      </div>

      {/* Profile Readiness Alert */}
      {!isLoading && readiness && !readiness.readyToApply && (
        <div className="bg-[#FFFBEB] border border-[#FDE68A] rounded p-4 sm:p-5 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div className="flex items-start gap-3">
            <span className="material-symbols-outlined text-[#D97706] !text-2xl shrink-0 mt-0.5">
              warning
            </span>
            <div>
              <h3 className="font-headline font-semibold text-sm text-[#92400E]">
                Complete Required Profile Items Before Applying
              </h3>
              <p className="text-xs text-[#B45309] mt-0.5">
                Missing requirements:{' '}
                <span className="font-semibold">{readiness.missingRequirements.join(', ') || 'Requirements incomplete'}</span>.
                You must have your full name, at least one education record, at least one skill, and an active resume before submitting applications.
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
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <Card>
          <CardBody className="p-4 sm:p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-slate-muted uppercase tracking-wider">
                Total Applications
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
                Profile Readiness
              </p>
              <h2 className="font-headline font-bold text-xl text-slate-text mt-1">
                {isLoading ? (
                  <Skeleton width="50px" height="28px" />
                ) : readiness?.readyToApply ? (
                  <span className="text-primary">Ready to Apply</span>
                ) : (
                  <span className="text-[#D97706]">Action Needed</span>
                )}
              </h2>
            </div>
            <div className="w-10 h-10 rounded bg-[#F2F5F3] border border-outline-warm text-slate-text flex items-center justify-center">
              <span className="material-symbols-outlined !text-xl">verified</span>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-4 sm:p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-slate-muted uppercase tracking-wider">
                Active Resume
              </p>
              <h2 className="font-headline font-bold text-sm text-slate-text mt-2 truncate max-w-[140px]">
                {isLoading ? (
                  <Skeleton width="80px" height="20px" />
                ) : resume ? (
                  resume.originalFilename
                ) : (
                  <span className="text-slate-muted font-normal">No Resume Uploaded</span>
                )}
              </h2>
            </div>
            <div className="w-10 h-10 rounded bg-[#F2F5F3] border border-outline-warm text-slate-text flex items-center justify-center">
              <span className="material-symbols-outlined !text-xl">picture_as_pdf</span>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-4 sm:p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-slate-muted uppercase tracking-wider">
                Unread Alerts
              </p>
              <h2 className="font-headline font-bold text-2xl text-slate-text mt-1">
                {isLoading ? <Skeleton width="30px" height="28px" /> : notifications?.unreadCount ?? 0}
              </h2>
            </div>
            <div className="w-10 h-10 rounded bg-[#F2F5F3] border border-outline-warm text-slate-text flex items-center justify-center">
              <span className="material-symbols-outlined !text-xl">notifications</span>
            </div>
          </CardBody>
        </Card>
      </div>

      {/* Main Content Area */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Recent Applications (2 cols) */}
        <div className="lg:col-span-2 space-y-4">
          <Card>
            <CardHeader className="flex items-center justify-between border-b border-outline-warm py-4 px-6">
              <div>
                <h3 className="font-headline font-semibold text-base text-slate-text">
                  Recent Applications
                </h3>
                <p className="text-xs text-slate-muted">Your active and submitted application statuses</p>
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
                  <Skeleton height="40px" />
                  <Skeleton height="40px" />
                </div>
              ) : applications.length === 0 ? (
                <div className="p-8 text-center text-xs text-slate-muted">
                  You have not submitted any applications yet.{' '}
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
                        <th className="px-4 py-3 text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-outline-warm/70">
                      {applications.map((app) => (
                        <tr key={app.applicationId} className="hover:bg-[#F9FAF9]">
                          <td className="px-4 py-3">
                            <Link
                              to={`/candidate/applications/${app.applicationId}`}
                              className="font-medium text-slate-text hover:text-primary hover:underline"
                            >
                              {app.jobTitle || `Job #${app.jobId}`}
                            </Link>
                            <div className="text-xs text-slate-muted">{app.companyName || 'Company'}</div>
                          </td>
                          <td className="px-4 py-3 text-xs text-slate-muted whitespace-nowrap">
                            {new Date(app.appliedAt).toLocaleDateString()}
                          </td>
                          <td className="px-4 py-3">
                            <StatusBadge status={app.status} size="sm" />
                          </td>
                          <td className="px-4 py-3 text-right whitespace-nowrap">
                            <div className="flex items-center justify-end gap-2">
                              <Link to={`/candidate/applications/${app.applicationId}`}>
                                <Button variant="ghost" size="sm" className="text-xs">
                                  Details
                                </Button>
                              </Link>
                              {isWithdrawable(app.status) && (
                                <button
                                  type="button"
                                  onClick={() => setWithdrawingApp(app)}
                                  className="text-xs text-slate-muted hover:text-rose-600 transition-colors font-medium underline"
                                >
                                  Withdraw
                                </button>
                              )}
                            </div>
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

        {/* Discovery & Navigation (1 col) */}
        <div className="space-y-4">
          <Card>
            <CardHeader className="flex items-center justify-between border-b border-outline-warm py-4 px-6">
              <h3 className="font-headline font-semibold text-base text-slate-text">
                Explore Open Roles
              </h3>
              <Link to="/jobs" className="text-xs text-primary font-medium hover:underline">
                View All
              </Link>
            </CardHeader>
            <CardBody className="p-4 space-y-3">
              {isLoading ? (
                <div className="space-y-3">
                  <Skeleton height="56px" />
                  <Skeleton height="56px" />
                </div>
              ) : recentJobs.length === 0 ? (
                <div className="text-xs text-slate-muted text-center py-4">
                  No active job postings found.
                </div>
              ) : (
                recentJobs.map((job) => (
                  <div
                    key={job.id}
                    className="p-3 rounded border border-outline-warm hover:border-primary/40 transition-colors cursor-pointer"
                    onClick={() => navigate(`/jobs/${job.id}`)}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <h4 className="font-semibold text-xs text-slate-text leading-tight hover:text-primary">
                        {job.title}
                      </h4>
                      <span className="text-[10px] font-mono text-slate-muted uppercase shrink-0">
                        {job.jobType || job.employmentType}
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-muted mt-0.5">{job.company?.name || job.companyName}</p>
                    <div className="flex items-center gap-2 mt-2 text-[10px] text-slate-muted">
                      <span className="flex items-center gap-0.5">
                        <span className="material-symbols-outlined !text-[12px]">location_on</span>
                        {job.location}
                      </span>
                      <span>•</span>
                      <span>{job.workMode}</span>
                    </div>
                  </div>
                ))
              )}
            </CardBody>
          </Card>

          {/* Quick Links Card */}
          <Card>
            <CardBody className="p-4 space-y-2">
              <p className="text-xs font-medium text-slate-muted uppercase tracking-wider mb-2">
                Quick Actions
              </p>
              <Link
                to="/candidate/profile"
                className="flex items-center justify-between p-2 rounded hover:bg-[#F4F6F4] text-xs text-slate-text font-medium transition-colors"
              >
                <span className="flex items-center gap-2">
                  <span className="material-symbols-outlined !text-lg text-primary">school</span>
                  Manage Education & Skills
                </span>
                <span className="material-symbols-outlined !text-sm text-slate-muted">arrow_forward</span>
              </Link>
              <Link
                to="/candidate/notifications"
                className="flex items-center justify-between p-2 rounded hover:bg-[#F4F6F4] text-xs text-slate-text font-medium transition-colors"
              >
                <span className="flex items-center gap-2">
                  <span className="material-symbols-outlined !text-lg text-primary">notifications</span>
                  View Notifications
                </span>
                <span className="material-symbols-outlined !text-sm text-slate-muted">arrow_forward</span>
              </Link>
              <Link
                to="/companies"
                className="flex items-center justify-between p-2 rounded hover:bg-[#F4F6F4] text-xs text-slate-text font-medium transition-colors"
              >
                <span className="flex items-center gap-2">
                  <span className="material-symbols-outlined !text-lg text-primary">business</span>
                  Browse Top Employers
                </span>
                <span className="material-symbols-outlined !text-sm text-slate-muted">arrow_forward</span>
              </Link>
            </CardBody>
          </Card>
        </div>
      </div>

      {/* Withdrawal Confirmation Modal */}
      <Modal
        isOpen={!!withdrawingApp}
        onClose={() => setWithdrawingApp(null)}
        title="Confirm Application Withdrawal"
      >
        <div className="space-y-4">
          <p className="text-xs text-slate-text leading-relaxed">
            Are you sure you want to withdraw your application for{' '}
            <span className="font-semibold text-slate-text">{withdrawingApp?.jobTitle}</span> at{' '}
            <span className="font-semibold text-slate-text">{withdrawingApp?.companyName}</span>?
          </p>
          <div className="p-3 bg-amber-50 border border-amber-200 rounded text-xs text-amber-800 space-y-1">
            <p className="font-semibold">Important Notes:</p>
            <ul className="list-disc list-inside space-y-0.5 text-[11px]">
              <li>This action will permanently move your application to WITHDRAWN status.</li>
              <li>You will not be able to re-apply to this specific job posting.</li>
            </ul>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setWithdrawingApp(null)}
              disabled={isWithdrawing}
            >
              Cancel
            </Button>
            <Button
              variant="primary"
              size="sm"
              onClick={handleWithdrawConfirm}
              isLoading={isWithdrawing}
              className="bg-rose-600 hover:bg-rose-700 text-white border-none"
            >
              Confirm Withdrawal
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};
