import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { adminApi } from '../../api/admin';
import type { AdminApplicationDetailDto } from '../../types/api';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { Alert } from '../../components/ui/Alert';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Button } from '../../components/ui/Button';

export const AdminApplicationDetailPage: React.FC = () => {
  const { applicationId } = useParams<{ applicationId: string }>();
  const [detail, setDetail] = useState<AdminApplicationDetailDto | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [isDownloading, setIsDownloading] = useState<boolean>(false);

  useEffect(() => {
    const fetchDetail = async () => {
      if (!applicationId) return;
      setIsLoading(true);
      setErrorMessage(null);
      try {
        const data = await adminApi.getApplicationDetail(Number(applicationId));
        setDetail(data);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Failed to load application details.';
        setErrorMessage(msg);
      } finally {
        setIsLoading(false);
      }
    };
    fetchDetail();
  }, [applicationId]);

  const handleDownloadResume = async () => {
    if (!applicationId) return;
    setIsDownloading(true);
    setDownloadError(null);
    try {
      const blob = await adminApi.downloadResume(Number(applicationId));
      const filename = detail?.resumeSnapshot?.originalFilename || `resume_app_${applicationId}.pdf`;
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', filename);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to download resume.';
      setDownloadError(msg);
    } finally {
      setIsDownloading(false);
    }
  };

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton height="32px" width="200px" />
        <Skeleton height="160px" />
        <Skeleton height="200px" />
      </div>
    );
  }

  if (errorMessage || !detail) {
    return (
      <div className="space-y-6">
        <Link
          to="/admin/applications"
          className="inline-flex items-center gap-1.5 text-xs text-primary hover:underline font-medium min-h-[44px]"
        >
          <span className="material-symbols-outlined !text-base">arrow_back</span>
          Back to Applications List
        </Link>
        <Alert variant="error">{errorMessage || 'Application not found.'}</Alert>
      </div>
    );
  }

  const { candidateSnapshot, resumeSnapshot, statusHistory, match } = detail;

  return (
    <div className="space-y-6">
      {/* Back link */}
      <div>
        <Link
          to="/admin/applications"
          className="inline-flex items-center gap-1.5 text-xs text-primary hover:underline font-medium min-h-[44px]"
        >
          <span className="material-symbols-outlined !text-base">arrow_back</span>
          Back to Applications List
        </Link>
      </div>

      {downloadError && (
        <Alert variant="error" onClose={() => setDownloadError(null)}>
          {downloadError}
        </Alert>
      )}

      {/* Header Banner */}
      <Card>
        <CardBody className="p-6 flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div>
            <div className="flex items-center gap-2.5 flex-wrap">
              <span className="text-xs font-mono font-semibold px-2 py-0.5 rounded bg-[#F2F5F3] text-primary border border-primary/20">
                Application #{detail.applicationId}
              </span>
              <StatusBadge status={detail.status} />
            </div>
            <h1 className="font-headline font-bold text-2xl text-slate-text mt-2">
              {detail.jobTitle}
            </h1>
            <p className="text-xs text-slate-muted mt-0.5">
              Target Organization: <strong className="text-slate-text">{detail.companyName}</strong> (Company #{detail.companyId})
            </p>
            <p className="text-[11px] text-slate-muted mt-1">
              Applied on {new Date(detail.appliedAt).toLocaleString()}
            </p>
          </div>

          <div className="flex items-center gap-3">
            <span className="text-xs font-mono px-3 py-1.5 rounded bg-[#FAFAF8] text-slate-muted border border-outline-warm">
              Read-Only Inspection Mode
            </span>
          </div>
        </CardBody>
      </Card>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: Candidate Snapshot */}
        <div className="lg:col-span-2 space-y-6">
          {/* Candidate Profile Snapshot */}
          <Card>
            <CardHeader>
              <h2 className="font-headline font-semibold text-base text-slate-text">
                Candidate Snapshot
              </h2>
              <p className="text-xs text-slate-muted">
                Immutable profile data preserved at the moment of application submission
              </p>
            </CardHeader>
            <CardBody className="p-6 space-y-5">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <span className="text-[11px] font-semibold text-slate-muted uppercase">Full Name</span>
                  <p className="text-sm font-semibold text-slate-text mt-0.5">
                    {candidateSnapshot?.fullName || '—'}
                  </p>
                </div>
                <div>
                  <span className="text-[11px] font-semibold text-slate-muted uppercase">Email Address</span>
                  <p className="text-sm font-mono text-slate-text mt-0.5">
                    {candidateSnapshot?.email || '—'}
                  </p>
                </div>
                <div>
                  <span className="text-[11px] font-semibold text-slate-muted uppercase">Candidate ID</span>
                  <p className="text-sm font-mono text-slate-text mt-0.5">
                    #{candidateSnapshot?.candidateId || detail.candidateId}
                  </p>
                </div>
                <div>
                  <span className="text-[11px] font-semibold text-slate-muted uppercase">Location</span>
                  <p className="text-sm text-slate-text mt-0.5">
                    {candidateSnapshot?.location || '—'}
                  </p>
                </div>
              </div>

              {candidateSnapshot?.headline && (
                <div>
                  <span className="text-[11px] font-semibold text-slate-muted uppercase">Professional Headline</span>
                  <p className="text-xs text-slate-text mt-0.5">{candidateSnapshot.headline}</p>
                </div>
              )}

              {candidateSnapshot?.summary && (
                <div>
                  <span className="text-[11px] font-semibold text-slate-muted uppercase">Professional Summary</span>
                  <p className="text-xs text-slate-text mt-0.5 bg-[#FAFAF8] p-3 rounded border border-outline-warm">
                    {candidateSnapshot.summary}
                  </p>
                </div>
              )}

              {/* Skills */}
              {candidateSnapshot?.skills && candidateSnapshot.skills.length > 0 && (
                <div>
                  <span className="text-[11px] font-semibold text-slate-muted uppercase block mb-2">
                    Reported Skills
                  </span>
                  <div className="flex flex-wrap gap-1.5">
                    {candidateSnapshot.skills.map((skill, idx) => (
                      <span
                        key={idx}
                        className="text-xs font-mono px-2 py-0.5 rounded bg-[#F2F5F3] text-primary border border-primary/20"
                      >
                        {skill}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {/* Education History */}
              {candidateSnapshot?.education && candidateSnapshot.education.length > 0 && (
                <div>
                  <span className="text-[11px] font-semibold text-slate-muted uppercase block mb-2">
                    Education Snapshot
                  </span>
                  <div className="space-y-2">
                    {candidateSnapshot.education.map((edu, idx) => (
                      <div key={idx} className="p-3 bg-[#FAFAF8] rounded border border-outline-warm text-xs">
                        <div className="font-semibold text-slate-text">{edu.degree} in {edu.fieldOfStudy}</div>
                        <div className="text-slate-muted">{edu.institution} {edu.startYear ? `(${edu.startYear} - ${edu.endYear || 'Present'})` : ''}</div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Experience History */}
              {candidateSnapshot?.experience && candidateSnapshot.experience.length > 0 && (
                <div>
                  <span className="text-[11px] font-semibold text-slate-muted uppercase block mb-2">
                    Experience Snapshot
                  </span>
                  <div className="space-y-2">
                    {candidateSnapshot.experience.map((exp, idx) => (
                      <div key={idx} className="p-3 bg-[#FAFAF8] rounded border border-outline-warm text-xs">
                        <div className="font-semibold text-slate-text">{exp.roleTitle} at {exp.companyName}</div>
                        <div className="text-slate-muted">{exp.startDate} - {exp.isCurrent ? 'Present' : exp.endDate || '—'}</div>
                        {exp.description && <p className="text-slate-text mt-1">{exp.description}</p>}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Projects */}
              {candidateSnapshot?.projects && candidateSnapshot.projects.length > 0 && (
                <div>
                  <span className="text-[11px] font-semibold text-slate-muted uppercase block mb-2">
                    Project Portfolio Snapshot
                  </span>
                  <div className="space-y-2">
                    {candidateSnapshot.projects.map((proj, idx) => (
                      <div key={idx} className="p-3 bg-[#FAFAF8] rounded border border-outline-warm text-xs">
                        <div className="font-semibold text-slate-text">{proj.title}</div>
                        {proj.description && <p className="text-slate-text mt-1">{proj.description}</p>}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </CardBody>
          </Card>

          {/* Cover Letter */}
          {detail.coverLetter && (
            <Card>
              <CardHeader>
                <h2 className="font-headline font-semibold text-base text-slate-text">Candidate Cover Letter</h2>
              </CardHeader>
              <CardBody className="p-6">
                <p className="text-xs text-slate-text whitespace-pre-line bg-[#FAFAF8] p-4 rounded border border-outline-warm">
                  {detail.coverLetter}
                </p>
              </CardBody>
            </Card>
          )}
        </div>

        {/* Right Column: Resume, Match Assessment & Status History */}
        <div className="space-y-6">
          {/* Resume Snapshot & Authenticated Download */}
          <Card>
            <CardHeader>
              <h2 className="font-headline font-semibold text-base text-slate-text">Resume Asset</h2>
              <p className="text-xs text-slate-muted">Authenticated blob download</p>
            </CardHeader>
            <CardBody className="p-5 space-y-4">
              {resumeSnapshot ? (
                <div>
                  <div className="p-3 bg-[#FAFAF8] rounded border border-outline-warm flex items-center gap-3">
                    <span className="material-symbols-outlined text-primary !text-2xl">description</span>
                    <div className="min-w-0 flex-1">
                      <p className="text-xs font-semibold text-slate-text truncate">
                        {resumeSnapshot.originalFilename}
                      </p>
                      <p className="text-[11px] text-slate-muted font-mono">
                        {(resumeSnapshot.sizeBytes / 1024).toFixed(1)} KB · {resumeSnapshot.contentType}
                      </p>
                    </div>
                  </div>

                  <Button
                    variant="primary"
                    size="sm"
                    onClick={handleDownloadResume}
                    disabled={isDownloading}
                    className="w-full mt-3 min-h-[44px]"
                  >
                    <span className="material-symbols-outlined !text-base mr-1.5">download</span>
                    {isDownloading ? 'Downloading Asset...' : 'Download Resume (Secure)'}
                  </Button>
                </div>
              ) : (
                <p className="text-xs text-slate-muted italic">No resume attached to this application.</p>
              )}
            </CardBody>
          </Card>

          {/* Match Assessment */}
          {match && (
            <Card>
              <CardHeader>
                <h2 className="font-headline font-semibold text-base text-slate-text">Skill Match Breakdown</h2>
                <p className="text-xs text-slate-muted">Deterministic platform algorithm scoring</p>
              </CardHeader>
              <CardBody className="p-5 space-y-3">
                <div className="p-3 bg-[#FAFAF8] rounded border border-outline-warm flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-text">Match Score</span>
                  <span className="font-mono text-base font-bold text-primary">
                    {match.percentage}%
                  </span>
                </div>

                {match.matchedSkills && match.matchedSkills.length > 0 && (
                  <div>
                    <span className="text-[11px] font-semibold text-status-offered block mb-1">
                      Matched Skills ({match.matchedSkills.length})
                    </span>
                    <div className="flex flex-wrap gap-1">
                      {match.matchedSkills.map((s, idx) => (
                        <span
                          key={idx}
                          className="text-[11px] font-mono px-2 py-0.5 rounded bg-status-offered-bg text-status-offered border border-status-offered/30"
                        >
                          ✓ {s}
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                {match.missingSkills && match.missingSkills.length > 0 && (
                  <div>
                    <span className="text-[11px] font-semibold text-status-rejected block mb-1">
                      Missing Skills ({match.missingSkills.length})
                    </span>
                    <div className="flex flex-wrap gap-1">
                      {match.missingSkills.map((s, idx) => (
                        <span
                          key={idx}
                          className="text-[11px] font-mono px-2 py-0.5 rounded bg-status-rejected-bg text-status-rejected border border-status-rejected/30"
                        >
                          ✕ {s}
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                {match.additionalSkills && match.additionalSkills.length > 0 && (
                  <div>
                    <span className="text-[11px] font-semibold text-slate-muted block mb-1">
                      Additional Skills ({match.additionalSkills.length})
                    </span>
                    <div className="flex flex-wrap gap-1">
                      {match.additionalSkills.map((s, idx) => (
                        <span
                          key={idx}
                          className="text-[11px] font-mono px-2 py-0.5 rounded bg-[#FAFAF8] text-slate-text border border-outline-warm"
                        >
                          + {s}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </CardBody>
            </Card>
          )}

          {/* Status Timeline History */}
          <Card>
            <CardHeader>
              <h2 className="font-headline font-semibold text-base text-slate-text">Lifecycle History</h2>
              <p className="text-xs text-slate-muted">Sequential audit transitions for this submission</p>
            </CardHeader>
            <CardBody className="p-5">
              {statusHistory && statusHistory.length > 0 ? (
                <div className="space-y-4">
                  {statusHistory.map((step) => (
                    <div key={step.id} className="relative pl-6 pb-2 border-l-2 border-primary/30 last:border-l-0">
                      <div className="absolute -left-[5px] top-0 w-2 h-2 rounded-full bg-primary" />
                      <div className="text-xs font-semibold text-slate-text">
                        {step.fromStatus ? `${step.fromStatus} → ` : ''}{step.toStatus}
                      </div>
                      <div className="text-[10px] text-slate-muted font-mono">
                        {new Date(step.createdAt).toLocaleString()}
                      </div>
                      {step.note && (
                        <div className="text-xs text-slate-text mt-1 bg-[#FAFAF8] p-2 rounded border border-outline-warm">
                          {step.note}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-xs text-slate-muted italic">No state transitions recorded yet.</p>
              )}
            </CardBody>
          </Card>
        </div>
      </div>
    </div>
  );
};
