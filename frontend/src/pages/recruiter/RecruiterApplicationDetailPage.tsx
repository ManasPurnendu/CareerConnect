import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { recruiterApi } from '../../api/recruiter';
import type {
  ApplicantDetailDto,
  ApplicationStatus,
} from '../../types/api';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Button } from '../../components/ui/Button';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { Alert } from '../../components/ui/Alert';
import { Modal } from '../../components/ui/Modal';
import { Textarea } from '../../components/ui/Textarea';

// Valid recruiter next transitions mapping (excluding WITHDRAWN which is candidate-controlled)
const VALID_RECRUITER_TRANSITIONS: Record<ApplicationStatus, ApplicationStatus[]> = {
  APPLIED: ['UNDER_REVIEW', 'SHORTLISTED', 'REJECTED'],
  UNDER_REVIEW: ['SHORTLISTED', 'REJECTED'],
  SHORTLISTED: ['INTERVIEW', 'REJECTED'],
  INTERVIEW: ['SELECTED', 'REJECTED'],
  INTERVIEW_SCHEDULED: ['SELECTED', 'REJECTED'],
  SELECTED: [],
  OFFERED: [],
  REJECTED: [],
  WITHDRAWN: [],
};

export const RecruiterApplicationDetailPage: React.FC = () => {
  const { applicationId } = useParams<{ applicationId: string }>();
  const navigate = useNavigate();

  const [detail, setDetail] = useState<ApplicantDetailDto | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isDownloadingResume, setIsDownloadingResume] = useState<boolean>(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Status update modal state
  const [selectedTargetStatus, setSelectedTargetStatus] = useState<ApplicationStatus | null>(null);
  const [statusNote, setStatusNote] = useState<string>('');
  const [isUpdatingStatus, setIsUpdatingStatus] = useState<boolean>(false);

  const fetchDetail = async () => {
    if (!applicationId) return;
    setIsLoading(true);
    try {
      const data = await recruiterApi.getApplicationDetail(applicationId);
      setDetail(data);
    } catch (err: unknown) {
      setFeedback({
        type: 'error',
        message: err instanceof Error ? err.message : 'Unable to load application details.',
      });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchDetail();
  }, [applicationId]);

  const handleDownloadResume = async () => {
    if (!applicationId || !detail) return;
    setIsDownloadingResume(true);
    setFeedback(null);

    try {
      const blob = await recruiterApi.downloadResume(applicationId);
      const filename = detail.resumeSnapshot?.originalFilename || `candidate_resume_${applicationId}.pdf`;

      const downloadUrl = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = downloadUrl;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      window.URL.revokeObjectURL(downloadUrl);
      document.body.removeChild(link);
    } catch (err: unknown) {
      setFeedback({
        type: 'error',
        message: err instanceof Error ? err.message : 'Failed to download candidate resume.',
      });
    } finally {
      setIsDownloadingResume(false);
    }
  };

  const handleStatusTransition = async () => {
    if (!applicationId || !selectedTargetStatus) return;
    setIsUpdatingStatus(true);
    setFeedback(null);

    try {
      const updated = await recruiterApi.updateApplicationStatus(
        applicationId,
        selectedTargetStatus,
        statusNote.trim() || undefined
      );
      setDetail(updated);
      setSelectedTargetStatus(null);
      setStatusNote('');
      setFeedback({
        type: 'success',
        message: `Application status transitioned to ${selectedTargetStatus}. Notification dispatched to candidate.`,
      });
    } catch (err: unknown) {
      setFeedback({
        type: 'error',
        message: err instanceof Error ? err.message : 'Failed to update application status.',
      });
    } finally {
      setIsUpdatingStatus(false);
    }
  };

  if (isLoading) {
    return (
      <div className="space-y-6 max-w-4xl">
        <div className="space-y-2">
          <Skeleton height="32px" width="280px" />
          <Skeleton height="16px" width="400px" />
        </div>
        <Card>
          <CardBody className="p-6 space-y-4">
            <Skeleton height="60px" />
            <Skeleton height="120px" />
          </CardBody>
        </Card>
      </div>
    );
  }

  if (!detail) {
    return (
      <div className="space-y-4 max-w-4xl">
        <Alert variant="error">
          Application record not found or does not belong to your company's job postings.
        </Alert>
        <Button variant="outline" size="md" onClick={() => navigate('/recruiter/applications')}>
          Back to Applicants
        </Button>
      </div>
    );
  }

  const snapshot = detail.candidateSnapshot;
  const match = detail.match;
  const nextAllowedStatuses = VALID_RECRUITER_TRANSITIONS[detail.status] || [];
  const isTerminal = nextAllowedStatuses.length === 0;

  return (
    <div className="space-y-6 max-w-4xl">
      {/* Top Breadcrumb & Header */}
      <div>
        <div className="flex items-center gap-2 text-xs text-slate-muted mb-1">
          <Link to="/recruiter/applications" className="hover:text-primary transition-colors">
            Applicants
          </Link>
          <span>/</span>
          <Link
            to={`/recruiter/jobs/${detail.jobId}`}
            className="hover:text-primary transition-colors truncate max-w-xs"
          >
            {detail.jobTitle}
          </Link>
          <span>/</span>
          <span className="font-mono text-slate-text">App #{detail.applicationId}</span>
        </div>

        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mt-1">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 rounded bg-primary-tint border border-[#A7F3D0] text-primary flex items-center justify-center font-bold text-lg uppercase shadow-subtle">
              {snapshot?.fullName ? snapshot.fullName.substring(0, 2) : 'CA'}
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="font-headline font-bold text-2xl text-slate-text">
                  {snapshot?.fullName || 'Candidate Profile'}
                </h1>
                <StatusBadge status={detail.status} size="md" />
              </div>
              <p className="text-xs text-slate-muted">
                Applied for <span className="font-semibold text-slate-text">{detail.jobTitle}</span> on{' '}
                {new Date(detail.appliedAt).toLocaleDateString()}
              </p>
            </div>
          </div>

          {/* Action Bar: Protected Resume Download & Status Action */}
          <div className="flex items-center gap-2">
            {detail.resumeSnapshot && (
              <Button
                variant="primary"
                size="md"
                onClick={handleDownloadResume}
                isLoading={isDownloadingResume}
                leftIcon={<span className="material-symbols-outlined !text-base">description</span>}
              >
                Download Resume
              </Button>
            )}
          </div>
        </div>
      </div>

      {feedback && (
        <Alert variant={feedback.type} onClose={() => setFeedback(null)}>
          {feedback.message}
        </Alert>
      )}

      {/* Status Transition Control Bar */}
      <Card className="border-l-4 border-l-primary">
        <CardBody className="p-4 sm:p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block">
              Application Pipeline Stage
            </span>
            <div className="flex items-center gap-2 mt-1">
              <span className="text-sm font-semibold text-slate-text">
                Current Status: {detail.status.replace(/_/g, ' ')}
              </span>
              {isTerminal && (
                <span className="text-xs text-slate-muted font-mono italic">
                  (Terminal State — No further transitions)
                </span>
              )}
            </div>
          </div>

          {/* Available Recruiter Transitions */}
          {!isTerminal && (
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs text-slate-muted font-medium mr-1">Move to:</span>
              {nextAllowedStatuses.map((st) => (
                <Button
                  key={st}
                  variant={st === 'REJECTED' ? 'outline' : 'outline'}
                  size="sm"
                  className={
                    st === 'REJECTED'
                      ? '!text-status-rejected hover:!bg-[#FEE2E2] !border-status-rejected/30'
                      : st === 'SELECTED'
                      ? '!text-primary !border-primary font-semibold hover:!bg-primary-tint'
                      : 'hover:!bg-[#F2F5F3]'
                  }
                  onClick={() => {
                    setSelectedTargetStatus(st);
                    setStatusNote('');
                  }}
                >
                  {st.replace(/_/g, ' ')}
                </Button>
              ))}
            </div>
          )}
        </CardBody>
      </Card>

      {/* Deterministic Match Evaluation Card */}
      {match && (
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined !text-xl text-primary">analytics</span>
                <h2 className="font-headline font-semibold text-base text-slate-text">
                  Early-Career Skill Match Analysis
                </h2>
              </div>
              <span
                className={`font-mono text-sm font-bold px-2.5 py-0.5 rounded ${
                  match.percentage >= 70
                    ? 'bg-primary-tint text-primary border border-[#A7F3D0]'
                    : match.percentage >= 40
                    ? 'bg-[#FEF3C7] text-[#92400E] border border-[#FDE68A]'
                    : 'bg-[#F3F4F6] text-slate-muted border border-outline-warm'
                }`}
              >
                {match.percentage}% Match
              </span>
            </div>
          </CardHeader>
          <CardBody className="p-5 space-y-4">
            {/* Progress Bar */}
            <div className="w-full bg-[#E5E7EB] h-2.5 rounded-full overflow-hidden">
              <div
                className={`h-full transition-all duration-300 ${
                  match.percentage >= 70
                    ? 'bg-primary'
                    : match.percentage >= 40
                    ? 'bg-[#F59E0B]'
                    : 'bg-slate-muted'
                }`}
                style={{ width: `${Math.min(100, Math.max(0, match.percentage))}%` }}
              />
            </div>

            {/* Matched Skills vs Missing Skills */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
              <div className="p-3.5 rounded bg-primary-tint/50 border border-[#A7F3D0]/70 space-y-2">
                <span className="text-[11px] font-semibold text-primary uppercase tracking-wider block">
                  Matched Skills ({match.matchedSkills?.length ?? 0})
                </span>
                {match.matchedSkills && match.matchedSkills.length > 0 ? (
                  <div className="flex flex-wrap gap-1.5">
                    {match.matchedSkills.map((s) => (
                      <span
                        key={s}
                        className="inline-flex items-center gap-1 bg-white px-2.5 py-0.5 rounded text-xs font-mono text-primary border border-[#A7F3D0]"
                      >
                        <span className="material-symbols-outlined !text-[13px]">check</span>
                        <span>{s}</span>
                      </span>
                    ))}
                  </div>
                ) : (
                  <span className="text-xs text-slate-muted italic">No overlapping required skills.</span>
                )}
              </div>

              <div className="p-3.5 rounded bg-[#FEF2F2] border border-[#FECACA] space-y-2">
                <span className="text-[11px] font-semibold text-[#991B1B] uppercase tracking-wider block">
                  Missing Required Skills ({match.missingSkills?.length ?? 0})
                </span>
                {match.missingSkills && match.missingSkills.length > 0 ? (
                  <div className="flex flex-wrap gap-1.5">
                    {match.missingSkills.map((s) => (
                      <span
                        key={s}
                        className="inline-flex items-center gap-1 bg-white px-2.5 py-0.5 rounded text-xs font-mono text-[#991B1B] border border-[#FECACA]"
                      >
                        <span className="material-symbols-outlined !text-[13px]">close</span>
                        <span>{s}</span>
                      </span>
                    ))}
                  </div>
                ) : (
                  <span className="text-xs text-primary font-medium">All required skills possessed!</span>
                )}
              </div>
            </div>

            {match.additionalSkills && match.additionalSkills.length > 0 && (
              <div className="pt-2">
                <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block mb-1.5">
                  Additional Candidate Skills
                </span>
                <div className="flex flex-wrap gap-1.5">
                  {match.additionalSkills.map((s) => (
                    <span
                      key={s}
                      className="bg-[#F3F4F6] text-slate-text px-2 py-0.5 rounded text-xs font-mono border border-outline-warm"
                    >
                      {s}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </CardBody>
        </Card>
      )}

      {/* Candidate Profile Snapshot */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <h2 className="font-headline font-semibold text-base text-slate-text">
              Immutable Candidate Snapshot
            </h2>
            <span className="text-[11px] font-mono text-slate-muted">Captured upon application submission</span>
          </div>
        </CardHeader>
        <CardBody className="p-5 sm:p-6 space-y-6">
          {/* Contact & Headline */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pb-4 border-b border-outline-warm">
            <div>
              <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block">
                Email
              </span>
              <span className="text-sm font-medium text-slate-text mt-0.5 block">
                {snapshot?.email || '—'}
              </span>
            </div>
            <div>
              <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block">
                Location
              </span>
              <span className="text-sm font-medium text-slate-text mt-0.5 block">
                {snapshot?.location || '—'}
              </span>
            </div>
            <div>
              <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block">
                Headline
              </span>
              <span className="text-sm font-medium text-slate-text mt-0.5 block">
                {snapshot?.headline || '—'}
              </span>
            </div>
          </div>

          {/* Bio / Summary */}
          {snapshot?.summary && (
            <div className="pb-4 border-b border-outline-warm">
              <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block mb-1.5">
                Summary
              </span>
              <p className="text-sm text-slate-text leading-relaxed whitespace-pre-wrap">
                {snapshot.summary}
              </p>
            </div>
          )}

          {/* Education History */}
          <div className="space-y-3 pb-4 border-b border-outline-warm">
            <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block">
              Education
            </span>
            {snapshot?.education && snapshot.education.length > 0 ? (
              <div className="space-y-3">
                {snapshot.education.map((edu, idx) => (
                  <div key={idx} className="p-3 rounded bg-[#FAFAF8] border border-outline-warm">
                    <div className="flex items-start justify-between gap-2">
                      <div className="font-semibold text-sm text-slate-text">{edu.institution}</div>
                      <span className="text-xs font-mono text-slate-muted">
                        {edu.startYear} – {edu.endYear || 'Present'}
                      </span>
                    </div>
                    <div className="text-xs text-slate-muted mt-0.5">
                      {edu.degree} in {edu.fieldOfStudy}
                      {edu.grade && ` · Grade: ${edu.grade}`}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-xs text-slate-muted italic">No education recorded in snapshot.</p>
            )}
          </div>

          {/* Projects */}
          {snapshot?.projects && snapshot.projects.length > 0 && (
            <div className="space-y-3 pb-4 border-b border-outline-warm">
              <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block">
                Key Projects
              </span>
              <div className="space-y-3">
                {snapshot.projects.map((proj, idx) => (
                  <div key={idx} className="p-3 rounded bg-[#FAFAF8] border border-outline-warm space-y-1">
                    <div className="flex items-center justify-between gap-2">
                      <div className="font-semibold text-sm text-slate-text">{proj.title}</div>
                      {proj.projectUrl && (
                        <a
                          href={proj.projectUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-xs text-primary font-medium hover:underline flex items-center gap-1"
                        >
                          <span>Repository / Demo</span>
                          <span className="material-symbols-outlined !text-xs">open_in_new</span>
                        </a>
                      )}
                    </div>
                    <p className="text-xs text-slate-text leading-relaxed whitespace-pre-wrap">
                      {proj.description}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Experience */}
          {snapshot?.experience && snapshot.experience.length > 0 && (
            <div className="space-y-3 pb-4 border-b border-outline-warm">
              <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block">
                Prior Experience / Internships
              </span>
              <div className="space-y-3">
                {snapshot.experience.map((exp, idx) => (
                  <div key={idx} className="p-3 rounded bg-[#FAFAF8] border border-outline-warm space-y-1">
                    <div className="flex items-start justify-between gap-2">
                      <div className="font-semibold text-sm text-slate-text">
                        {exp.roleTitle} · {exp.companyName}
                      </div>
                      <span className="text-xs font-mono text-slate-muted">
                        {exp.startDate} – {exp.isCurrent ? 'Present' : exp.endDate || '—'}
                      </span>
                    </div>
                    {exp.description && (
                      <p className="text-xs text-slate-text leading-relaxed whitespace-pre-wrap">
                        {exp.description}
                      </p>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Cover Letter */}
          {detail.coverLetter && (
            <div>
              <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block mb-1.5">
                Candidate Note / Cover Letter
              </span>
              <div className="p-4 rounded bg-[#FAFAF8] border border-outline-warm text-xs text-slate-text leading-relaxed whitespace-pre-wrap">
                {detail.coverLetter}
              </div>
            </div>
          )}
        </CardBody>
      </Card>

      {/* Application Status History Audit Trail */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined !text-base text-slate-muted">history</span>
            <h2 className="font-headline font-semibold text-base text-slate-text">
              Status Progression History
            </h2>
          </div>
        </CardHeader>
        <CardBody className="p-5">
          {detail.statusHistory && detail.statusHistory.length > 0 ? (
            <div className="space-y-4">
              {detail.statusHistory.map((h, i) => (
                <div key={h.id || i} className="flex items-start gap-3 text-xs">
                  <div className="w-6 h-6 rounded-full bg-primary-tint border border-[#A7F3D0] text-primary flex items-center justify-center shrink-0 mt-0.5">
                    <span className="material-symbols-outlined !text-[14px]">check</span>
                  </div>
                  <div className="flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-semibold text-slate-text">
                        {h.fromStatus ? `${h.fromStatus} → ${h.toStatus}` : `Initial Status: ${h.toStatus}`}
                      </span>
                      <span className="text-slate-muted font-mono text-[11px]">
                        {new Date(h.createdAt).toLocaleString()}
                      </span>
                    </div>
                    {h.note && (
                      <p className="text-slate-muted mt-1 italic bg-[#FAFAF8] p-2 rounded border border-outline-warm/60">
                        "{h.note}"
                      </p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-xs text-slate-muted italic">No status transitions recorded yet.</p>
          )}
        </CardBody>
      </Card>

      {/* Status Transition Confirmation Modal */}
      {selectedTargetStatus && (
        <Modal
          isOpen={true}
          onClose={() => !isUpdatingStatus && setSelectedTargetStatus(null)}
          title={`Transition Application to ${selectedTargetStatus}`}
        >
          <div className="space-y-4">
            <p className="text-sm text-slate-text">
              Are you sure you want to transition{' '}
              <strong className="text-slate-text">{snapshot?.fullName || 'this candidate'}</strong> from{' '}
              <span className="font-semibold">{detail.status}</span> to{' '}
              <span className="font-semibold text-primary">{selectedTargetStatus}</span>?
            </p>

            <Textarea
              label="Recruiter Decision Note (Optional)"
              maxLength={500}
              rows={3}
              value={statusNote}
              onChange={(e) => setStatusNote(e.target.value)}
              placeholder="e.g. Cleared initial screening. Scheduled technical interview round for next Tuesday..."
              helperText="This note is permanently recorded in the application audit log."
            />

            <div className="pt-3 border-t border-outline-warm flex items-center justify-end gap-3">
              <Button
                variant="outline"
                size="md"
                onClick={() => setSelectedTargetStatus(null)}
                disabled={isUpdatingStatus}
              >
                Cancel
              </Button>
              <Button
                variant={selectedTargetStatus === 'REJECTED' ? 'danger' : 'primary'}
                size="md"
                onClick={handleStatusTransition}
                isLoading={isUpdatingStatus}
              >
                Confirm Transition
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
};
