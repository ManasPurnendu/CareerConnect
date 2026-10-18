import React, { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { candidateApi } from '../../api/candidate';
import type { ApplicationResponseDto, ApplicationStatus } from '../../types/api';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Button } from '../../components/ui/Button';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { Alert } from '../../components/ui/Alert';
import { Modal } from '../../components/ui/Modal';

export const ApplicationDetailPage: React.FC = () => {
  const { applicationId } = useParams<{ applicationId: string }>();
  const navigate = useNavigate();

  const [application, setApplication] = useState<ApplicationResponseDto | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Withdrawal modal
  const [isWithdrawModalOpen, setIsWithdrawModalOpen] = useState<boolean>(false);
  const [isWithdrawing, setIsWithdrawing] = useState<boolean>(false);

  useEffect(() => {
    const fetchDetail = async () => {
      if (!applicationId) return;
      setIsLoading(true);
      setError(null);
      try {
        const data = await candidateApi.getApplicationById(applicationId);
        setApplication(data);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Application not found or unauthorized.';
        setError(msg);
      } finally {
        setIsLoading(false);
      }
    };

    fetchDetail();
  }, [applicationId]);

  const handleWithdraw = async () => {
    if (!application) return;
    setIsWithdrawing(true);
    setFeedback(null);
    try {
      // NOTE: uses PATCH per backend specification!
      const updated = await candidateApi.withdrawApplication(application.applicationId);
      setApplication(updated);
      setIsWithdrawModalOpen(false);
      setFeedback({ type: 'success', message: 'Application successfully withdrawn.' });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Unable to withdraw application.';
      setFeedback({ type: 'error', message: msg });
    } finally {
      setIsWithdrawing(false);
    }
  };

  const isWithdrawable = (status: ApplicationStatus) => {
    return (
      status === 'APPLIED' ||
      status === 'UNDER_REVIEW' ||
      status === 'SHORTLISTED' ||
      status === 'INTERVIEW_SCHEDULED' ||
      (status as string) === 'INTERVIEW'
    );
  };

  if (isLoading) {
    return (
      <div className="space-y-6 max-w-4xl mx-auto py-8">
        <Skeleton height="32px" width="300px" />
        <Skeleton height="160px" />
        <Skeleton height="280px" />
      </div>
    );
  }

  if (error || !application) {
    return (
      <div className="space-y-6 max-w-4xl mx-auto py-8">
        <Alert variant="error">{error || 'Application could not be found.'}</Alert>
        <div className="pt-4">
          <Button variant="outline" size="sm" onClick={() => navigate('/candidate/applications')}>
            Back to My Applications
          </Button>
        </div>
      </div>
    );
  }

  const { candidateSnapshot, resumeSnapshot, statusHistory } = application;

  return (
    <div className="space-y-6 max-w-4xl mx-auto pb-12">
      <Breadcrumb
        items={[
          { label: 'Dashboard', path: '/candidate' },
          { label: 'Applications', path: '/candidate/applications' },
          { label: `Application #${application.applicationId}` },
        ]}
      />

      {feedback && (
        <Alert variant={feedback.type === 'success' ? 'success' : 'error'} onClose={() => setFeedback(null)}>
          {feedback.message}
        </Alert>
      )}

      {/* Main Application Header Card */}
      <Card>
        <CardBody className="p-6 sm:p-8 space-y-6">
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
            <div className="space-y-1">
              <div className="flex items-center gap-3">
                <h1 className="font-headline font-bold text-xl sm:text-2xl text-slate-text">
                  {application.jobTitle || `Job #${application.jobId}`}
                </h1>
                <StatusBadge status={application.status} size="md" />
              </div>
              <p className="text-sm font-medium text-slate-muted">
                {application.companyName || 'Company'} · Applied on{' '}
                {new Date(application.appliedAt).toLocaleDateString()}
              </p>
            </div>
            <div className="flex items-center gap-3">
              <Link to={`/jobs/${application.jobId}`}>
                <Button variant="outline" size="sm" className="flex items-center gap-1">
                  <span className="material-symbols-outlined !text-[16px]">visibility</span>
                  View Job
                </Button>
              </Link>
              {isWithdrawable(application.status) && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setIsWithdrawModalOpen(true)}
                  className="text-rose-600 hover:text-rose-700 hover:bg-rose-50 border-rose-200"
                >
                  Withdraw Application
                </Button>
              )}
            </div>
          </div>
        </CardBody>
      </Card>

      {/* Application Status Progression Timeline */}
      <Card>
        <CardHeader className="py-4 px-6 border-b border-outline-warm">
          <h2 className="font-headline font-semibold text-base text-slate-text">Status Progression</h2>
          <p className="text-xs text-slate-muted">Full chronological review history from employer</p>
        </CardHeader>
        <CardBody className="p-6">
          {(!statusHistory || statusHistory.length === 0) ? (
            <div className="flex items-center gap-3 p-4 bg-[#F9FAF8] rounded border border-outline-warm">
              <span className="material-symbols-outlined text-primary !text-2xl">check_circle</span>
              <div>
                <p className="text-xs font-semibold text-slate-text uppercase">Initial Submission (APPLIED)</p>
                <p className="text-xs text-slate-muted">{new Date(application.appliedAt).toLocaleString()}</p>
              </div>
            </div>
          ) : (
            <div className="relative pl-6 space-y-6 before:absolute before:left-2 before:top-2 before:bottom-2 before:w-0.5 before:bg-outline-warm">
              {statusHistory.map((item, idx) => (
                <div key={item.id || idx} className="relative flex items-start gap-3">
                  <div className="absolute -left-6 mt-1 w-4 h-4 rounded-full bg-primary border-2 border-white flex items-center justify-center">
                    <div className="w-1.5 h-1.5 rounded-full bg-white" />
                  </div>
                  <div className="space-y-0.5">
                    <div className="flex items-center gap-2">
                      <StatusBadge status={item.toStatus} size="sm" />
                      {item.fromStatus && (
                        <span className="text-xs text-slate-muted">
                          (Transitioned from {item.fromStatus})
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-slate-muted">
                      {new Date(item.createdAt).toLocaleString()}
                    </p>
                    {item.note && (
                      <p className="text-xs text-slate-text bg-[#F9FAF8] p-2 rounded mt-1 border border-outline-warm">
                        {item.note}
                      </p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardBody>
      </Card>

      {/* Cover Letter (if submitted) */}
      {application.coverLetter && (
        <Card>
          <CardHeader className="py-4 px-6 border-b border-outline-warm">
            <h2 className="font-headline font-semibold text-base text-slate-text">Submitted Cover Letter</h2>
          </CardHeader>
          <CardBody className="p-6">
            <p className="text-xs text-slate-text whitespace-pre-line leading-relaxed">
              {application.coverLetter}
            </p>
          </CardBody>
        </Card>
      )}

      {/* Immutable Application Snapshot Card */}
      <Card>
        <CardHeader className="py-4 px-6 border-b border-outline-warm">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-slate-muted !text-xl">lock</span>
            <div>
              <h2 className="font-headline font-semibold text-base text-slate-text">
                Immutable Application Snapshot
              </h2>
              <p className="text-xs text-slate-muted">
                Historical snapshot preserved at the exact time of application
              </p>
            </div>
          </div>
        </CardHeader>
        <CardBody className="p-6 space-y-6">
          <div className="p-3 bg-[#F9FAF8] border border-outline-warm rounded text-xs text-slate-muted leading-relaxed">
            <span className="font-semibold text-slate-text">Historical Record Notice:</span> This snapshot represents your verified credentials at the time this application was lodged. Any subsequent changes you make to your live profile will not alter this snapshot.
          </div>

          {/* Candidate Bio Snapshot */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 border-b border-outline-warm pb-6">
            <div>
              <p className="text-xs font-medium text-slate-muted uppercase">Candidate Name</p>
              <p className="text-sm font-semibold text-slate-text mt-0.5">
                {candidateSnapshot?.fullName || 'Not available'}
              </p>
            </div>
            <div>
              <p className="text-xs font-medium text-slate-muted uppercase">Contact Email</p>
              <p className="text-sm text-slate-text mt-0.5">{candidateSnapshot?.email || 'Not available'}</p>
            </div>
            <div>
              <p className="text-xs font-medium text-slate-muted uppercase">Headline</p>
              <p className="text-sm text-slate-text mt-0.5">
                {candidateSnapshot?.headline || 'No headline in snapshot'}
              </p>
            </div>
            <div>
              <p className="text-xs font-medium text-slate-muted uppercase">Location</p>
              <p className="text-sm text-slate-text mt-0.5">
                {candidateSnapshot?.location || 'Not specified'}
              </p>
            </div>
          </div>

          {/* Resume Snapshot */}
          <div className="border-b border-outline-warm pb-6 space-y-2">
            <p className="text-xs font-medium text-slate-muted uppercase">Resume Snapshot</p>
            {resumeSnapshot ? (
              <div className="flex items-center gap-3 p-3 bg-[#F9FAF8] border border-outline-warm rounded max-w-md">
                <span className="material-symbols-outlined text-primary !text-2xl">picture_as_pdf</span>
                <div>
                  <p className="text-xs font-semibold text-slate-text">{resumeSnapshot.originalFilename}</p>
                  <p className="text-[11px] text-slate-muted">
                    {(resumeSnapshot.sizeBytes / 1024).toFixed(1)} KB · Locked Snapshot
                  </p>
                </div>
              </div>
            ) : (
              <p className="text-xs text-slate-muted italic">No resume snapshot found.</p>
            )}
          </div>

          {/* Skills Snapshot */}
          <div className="border-b border-outline-warm pb-6 space-y-2">
            <p className="text-xs font-medium text-slate-muted uppercase">Skills Snapshot</p>
            {candidateSnapshot?.skills && candidateSnapshot.skills.length > 0 ? (
              <div className="flex flex-wrap gap-1.5">
                {candidateSnapshot.skills.map((skill, i) => (
                  <span
                    key={i}
                    className="px-2.5 py-0.5 bg-slate-bg border border-outline-warm text-slate-text text-xs rounded font-medium"
                  >
                    {skill}
                  </span>
                ))}
              </div>
            ) : (
              <p className="text-xs text-slate-muted italic">No skills recorded in snapshot.</p>
            )}
          </div>

          {/* Education Snapshot */}
          <div className="border-b border-outline-warm pb-6 space-y-3">
            <p className="text-xs font-medium text-slate-muted uppercase">Education Snapshot</p>
            {candidateSnapshot?.education && candidateSnapshot.education.length > 0 ? (
              <div className="space-y-3">
                {candidateSnapshot.education.map((edu, i) => (
                  <div key={i} className="text-xs space-y-0.5">
                    <p className="font-semibold text-slate-text">{edu.institution}</p>
                    <p className="text-slate-text">
                      {edu.degree} in {edu.fieldOfStudy} ({edu.startYear} – {edu.endYear || 'Present'})
                    </p>
                    {edu.grade && <p className="text-slate-muted">Grade: {edu.grade}</p>}
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-xs text-slate-muted italic">No education recorded in snapshot.</p>
            )}
          </div>

          {/* Experience Snapshot (if any) */}
          {candidateSnapshot?.experience && candidateSnapshot.experience.length > 0 && (
            <div className="border-b border-outline-warm pb-6 space-y-3">
              <p className="text-xs font-medium text-slate-muted uppercase">Experience Snapshot</p>
              <div className="space-y-3">
                {candidateSnapshot.experience.map((exp, i) => (
                  <div key={i} className="text-xs space-y-0.5">
                    <p className="font-semibold text-slate-text">{exp.roleTitle} at {exp.companyName}</p>
                    <p className="text-slate-muted">
                      {exp.startDate} – {exp.isCurrent ? 'Present' : exp.endDate || 'N/A'}
                    </p>
                    {exp.description && <p className="text-slate-text whitespace-pre-line">{exp.description}</p>}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Projects Snapshot (if any) */}
          {candidateSnapshot?.projects && candidateSnapshot.projects.length > 0 && (
            <div className="space-y-3">
              <p className="text-xs font-medium text-slate-muted uppercase">Projects Snapshot</p>
              <div className="space-y-3">
                {candidateSnapshot.projects.map((proj, i) => (
                  <div key={i} className="text-xs space-y-0.5">
                    <div className="flex items-center gap-2">
                      <p className="font-semibold text-slate-text">{proj.title}</p>
                      {proj.projectUrl && (
                        <a
                          href={proj.projectUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-primary hover:underline text-[11px]"
                        >
                          External Link
                        </a>
                      )}
                    </div>
                    <p className="text-slate-text whitespace-pre-line">{proj.description}</p>
                  </div>
                ))}
              </div>
            </div>
          )}
        </CardBody>
      </Card>

      {/* Withdrawal Confirmation Modal */}
      <Modal
        isOpen={isWithdrawModalOpen}
        onClose={() => setIsWithdrawModalOpen(false)}
        title="Confirm Application Withdrawal"
      >
        <div className="space-y-4">
          <p className="text-xs text-slate-text leading-relaxed">
            Are you sure you want to withdraw this application for{' '}
            <span className="font-semibold">{application.jobTitle}</span>?
          </p>
          <div className="p-3 bg-amber-50 border border-amber-200 rounded text-xs text-amber-800 space-y-1">
            <p className="font-semibold">Permanent Action:</p>
            <ul className="list-disc list-inside space-y-0.5 text-[11px]">
              <li>Your application status will be updated to WITHDRAWN.</li>
              <li>You will not be able to re-submit an application for this specific job.</li>
            </ul>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setIsWithdrawModalOpen(false)}
              disabled={isWithdrawing}
            >
              Cancel
            </Button>
            <Button
              variant="primary"
              size="sm"
              onClick={handleWithdraw}
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
