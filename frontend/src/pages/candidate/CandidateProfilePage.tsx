import React, { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { candidateApi } from '../../api/candidate';
import type {
  CandidateProfileResponse,
  ProfileReadinessDto,
  EducationDto,
  CandidateSkillDto,
  ProjectDto,
  ExperienceDto,
  ResumeResponse,
} from '../../types/api';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Textarea } from '../../components/ui/Textarea';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Alert } from '../../components/ui/Alert';
import { Skeleton } from '../../components/ui/Skeleton';
import { Modal } from '../../components/ui/Modal';
import { useAuth } from '../../auth/useAuth';

export const CandidateProfilePage: React.FC = () => {
  const { user } = useAuth();
  const [searchParams] = useSearchParams();
  const initialEditMode = searchParams.get('edit') === 'true' || window.location.pathname.endsWith('/edit');

  // Core Data
  const [profile, setProfile] = useState<CandidateProfileResponse | null>(null);
  const [readiness, setReadiness] = useState<ProfileReadinessDto | null>(null);
  const [educationList, setEducationList] = useState<EducationDto[]>([]);
  const [skillsList, setSkillsList] = useState<CandidateSkillDto[]>([]);
  const [projectsList, setProjectsList] = useState<ProjectDto[]>([]);
  const [experienceList, setExperienceList] = useState<ExperienceDto[]>([]);
  const [currentResume, setCurrentResume] = useState<ResumeResponse | null>(null);

  // Loading States
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isSavingProfile, setIsSavingProfile] = useState<boolean>(false);
  const [isUploadingResume, setIsUploadingResume] = useState<boolean>(false);
  const [isDownloadingResume, setIsDownloadingResume] = useState<boolean>(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Profile Edit State
  const [isEditingProfile, setIsEditingProfile] = useState<boolean>(initialEditMode);
  const [fullName, setFullName] = useState('');
  const [headline, setHeadline] = useState('');
  const [location, setLocation] = useState('');
  const [summary, setSummary] = useState('');

  // Skill Add State
  const [newSkillName, setNewSkillName] = useState('');
  const [isAddingSkill, setIsAddingSkill] = useState(false);

  // Education Modal State
  const [isEduModalOpen, setIsEduModalOpen] = useState(false);
  const [editingEdu, setEditingEdu] = useState<EducationDto | null>(null);
  const [eduInstitution, setEduInstitution] = useState('');
  const [eduDegree, setEduDegree] = useState('');
  const [eduField, setEduField] = useState('');
  const [eduStartYear, setEduStartYear] = useState<number>(new Date().getFullYear() - 4);
  const [eduEndYear, setEduEndYear] = useState<number | ''>(new Date().getFullYear());
  const [eduGrade, setEduGrade] = useState('');
  const [isSubmittingEdu, setIsSubmittingEdu] = useState(false);

  // Project Modal State
  const [isProjModalOpen, setIsProjModalOpen] = useState(false);
  const [editingProj, setEditingProj] = useState<ProjectDto | null>(null);
  const [projTitle, setProjTitle] = useState('');
  const [projDescription, setProjDescription] = useState('');
  const [projUrl, setProjUrl] = useState('');
  const [isSubmittingProj, setIsSubmittingProj] = useState(false);

  // Experience Modal State
  const [isExpModalOpen, setIsExpModalOpen] = useState(false);
  const [editingExp, setEditingExp] = useState<ExperienceDto | null>(null);
  const [expCompany, setExpCompany] = useState('');
  const [expRole, setExpRole] = useState('');
  const [expStartDate, setExpStartDate] = useState('');
  const [expEndDate, setExpEndDate] = useState('');
  const [expIsCurrent, setExpIsCurrent] = useState(false);
  const [expDescription, setExpDescription] = useState('');
  const [isSubmittingExp, setIsSubmittingExp] = useState(false);

  const loadAllData = useCallback(async () => {
    setIsLoading(true);
    try {
      const [profRes, readyRes, eduRes, skillRes, projRes, expRes, resumeRes] =
        await Promise.allSettled([
          candidateApi.getProfile(),
          candidateApi.getReadiness(),
          candidateApi.getEducation(),
          candidateApi.getSkills(),
          candidateApi.getProjects(),
          candidateApi.getExperience(),
          candidateApi.getCurrentResume(),
        ]);

      if (profRes.status === 'fulfilled') {
        setProfile(profRes.value);
        setFullName(profRes.value.fullName || '');
        setHeadline(profRes.value.headline || '');
        setLocation(profRes.value.location || '');
        setSummary(profRes.value.summary || '');
      }
      if (readyRes.status === 'fulfilled') setReadiness(readyRes.value);
      if (eduRes.status === 'fulfilled') setEducationList(eduRes.value);
      if (skillRes.status === 'fulfilled') setSkillsList(skillRes.value);
      if (projRes.status === 'fulfilled') setProjectsList(projRes.value);
      if (expRes.status === 'fulfilled') setExperienceList(expRes.value);
      if (resumeRes.status === 'fulfilled') {
        setCurrentResume(resumeRes.value);
      } else {
        setCurrentResume(null);
      }
    } catch {
      setFeedback({ type: 'error', message: 'Failed to load profile data.' });
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAllData();
  }, [loadAllData]);

  // Profile Save
  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!fullName.trim()) {
      setFeedback({ type: 'error', message: 'Full name cannot be blank.' });
      return;
    }
    setIsSavingProfile(true);
    setFeedback(null);
    try {
      const updated = await candidateApi.updateProfile({
        fullName: fullName.trim(),
        headline: headline.trim() || null,
        location: location.trim() || null,
        summary: summary.trim() || null,
      });
      setProfile(updated);
      setIsEditingProfile(false);
      setFeedback({ type: 'success', message: 'Profile details saved successfully.' });
      const ready = await candidateApi.getReadiness();
      setReadiness(ready);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to update profile.';
      setFeedback({ type: 'error', message: msg });
    } finally {
      setIsSavingProfile(false);
    }
  };

  // Skill Handlers
  const handleAddSkill = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = newSkillName.trim();
    if (!trimmed) return;
    setIsAddingSkill(true);
    setFeedback(null);
    try {
      const added = await candidateApi.assignSkill({ name: trimmed });
      setSkillsList((prev) => [...prev, added]);
      setNewSkillName('');
      const ready = await candidateApi.getReadiness();
      setReadiness(ready);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to add skill.';
      setFeedback({ type: 'error', message: msg });
    } finally {
      setIsAddingSkill(false);
    }
  };

  const handleRemoveSkill = async (skillId: number) => {
    setFeedback(null);
    try {
      await candidateApi.removeSkill(skillId);
      setSkillsList((prev) => prev.filter((s) => s.id !== skillId));
      const ready = await candidateApi.getReadiness();
      setReadiness(ready);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to remove skill.';
      setFeedback({ type: 'error', message: msg });
    }
  };

  // Education Modal Openers
  const openAddEducation = () => {
    setEditingEdu(null);
    setEduInstitution('');
    setEduDegree('');
    setEduField('');
    setEduStartYear(new Date().getFullYear() - 4);
    setEduEndYear(new Date().getFullYear());
    setEduGrade('');
    setIsEduModalOpen(true);
  };

  const openEditEducation = (edu: EducationDto) => {
    setEditingEdu(edu);
    setEduInstitution(edu.institution);
    setEduDegree(edu.degree);
    setEduField(edu.fieldOfStudy);
    setEduStartYear(edu.startYear);
    setEduEndYear(edu.endYear ?? '');
    setEduGrade(edu.grade || '');
    setIsEduModalOpen(true);
  };

  const handleSaveEducation = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!eduInstitution.trim() || !eduDegree.trim() || !eduField.trim()) {
      setFeedback({ type: 'error', message: 'Institution, degree, and field of study are required.' });
      return;
    }
    if (eduEndYear !== '' && eduEndYear < eduStartYear) {
      setFeedback({ type: 'error', message: 'End year cannot be before start year.' });
      return;
    }

    setIsSubmittingEdu(true);
    setFeedback(null);
    try {
      const payload = {
        institution: eduInstitution.trim(),
        degree: eduDegree.trim(),
        fieldOfStudy: eduField.trim(),
        startYear: Number(eduStartYear),
        endYear: eduEndYear !== '' ? Number(eduEndYear) : null,
        grade: eduGrade.trim() || null,
        sortOrder: editingEdu ? editingEdu.sortOrder : educationList.length + 1,
      };

      if (editingEdu) {
        const updated = await candidateApi.updateEducation(editingEdu.id, payload);
        setEducationList((prev) => prev.map((item) => (item.id === updated.id ? updated : item)));
      } else {
        const created = await candidateApi.createEducation(payload);
        setEducationList((prev) => [...prev, created]);
      }
      setIsEduModalOpen(false);
      const ready = await candidateApi.getReadiness();
      setReadiness(ready);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to save education.';
      setFeedback({ type: 'error', message: msg });
    } finally {
      setIsSubmittingEdu(false);
    }
  };

  const handleDeleteEducation = async (id: number) => {
    if (!window.confirm('Are you sure you want to delete this education entry?')) return;
    setFeedback(null);
    try {
      await candidateApi.deleteEducation(id);
      setEducationList((prev) => prev.filter((item) => item.id !== id));
      const ready = await candidateApi.getReadiness();
      setReadiness(ready);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to delete education.';
      setFeedback({ type: 'error', message: msg });
    }
  };

  // Project Modal Openers
  const openAddProject = () => {
    setEditingProj(null);
    setProjTitle('');
    setProjDescription('');
    setProjUrl('');
    setIsProjModalOpen(true);
  };

  const openEditProject = (proj: ProjectDto) => {
    setEditingProj(proj);
    setProjTitle(proj.title);
    setProjDescription(proj.description);
    setProjUrl(proj.projectUrl || '');
    setIsProjModalOpen(true);
  };

  const handleSaveProject = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!projTitle.trim() || !projDescription.trim()) {
      setFeedback({ type: 'error', message: 'Project title and description are required.' });
      return;
    }
    setIsSubmittingProj(true);
    setFeedback(null);
    try {
      const payload = {
        title: projTitle.trim(),
        description: projDescription.trim(),
        projectUrl: projUrl.trim() || null,
        sortOrder: editingProj ? editingProj.sortOrder : projectsList.length + 1,
      };
      if (editingProj) {
        const updated = await candidateApi.updateProject(editingProj.id, payload);
        setProjectsList((prev) => prev.map((item) => (item.id === updated.id ? updated : item)));
      } else {
        const created = await candidateApi.createProject(payload);
        setProjectsList((prev) => [...prev, created]);
      }
      setIsProjModalOpen(false);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to save project.';
      setFeedback({ type: 'error', message: msg });
    } finally {
      setIsSubmittingProj(false);
    }
  };

  const handleDeleteProject = async (id: number) => {
    if (!window.confirm('Are you sure you want to delete this project?')) return;
    setFeedback(null);
    try {
      await candidateApi.deleteProject(id);
      setProjectsList((prev) => prev.filter((item) => item.id !== id));
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to delete project.';
      setFeedback({ type: 'error', message: msg });
    }
  };

  // Experience Modal Openers
  const openAddExperience = () => {
    setEditingExp(null);
    setExpCompany('');
    setExpRole('');
    setExpStartDate('');
    setExpEndDate('');
    setExpIsCurrent(false);
    setExpDescription('');
    setIsExpModalOpen(true);
  };

  const openEditExperience = (exp: ExperienceDto) => {
    setEditingExp(exp);
    setExpCompany(exp.companyName);
    setExpRole(exp.roleTitle);
    setExpStartDate(exp.startDate);
    setExpEndDate(exp.endDate || '');
    setExpIsCurrent(exp.isCurrent);
    setExpDescription(exp.description || '');
    setIsExpModalOpen(true);
  };

  const handleSaveExperience = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!expCompany.trim() || !expRole.trim() || !expStartDate) {
      setFeedback({ type: 'error', message: 'Company name, role title, and start date are required.' });
      return;
    }
    if (!expIsCurrent && !expEndDate) {
      setFeedback({ type: 'error', message: 'End date is required for past experience.' });
      return;
    }
    if (!expIsCurrent && expEndDate && expEndDate < expStartDate) {
      setFeedback({ type: 'error', message: 'End date cannot be before start date.' });
      return;
    }

    setIsSubmittingExp(true);
    setFeedback(null);
    try {
      const payload = {
        companyName: expCompany.trim(),
        roleTitle: expRole.trim(),
        startDate: expStartDate,
        endDate: expIsCurrent ? null : expEndDate || null,
        isCurrent: expIsCurrent,
        description: expDescription.trim() || null,
        sortOrder: editingExp ? editingExp.sortOrder : experienceList.length + 1,
      };

      if (editingExp) {
        const updated = await candidateApi.updateExperience(editingExp.id, payload);
        setExperienceList((prev) => prev.map((item) => (item.id === updated.id ? updated : item)));
      } else {
        const created = await candidateApi.createExperience(payload);
        setExperienceList((prev) => [...prev, created]);
      }
      setIsExpModalOpen(false);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to save experience.';
      setFeedback({ type: 'error', message: msg });
    } finally {
      setIsSubmittingExp(false);
    }
  };

  const handleDeleteExperience = async (id: number) => {
    if (!window.confirm('Are you sure you want to delete this experience record?')) return;
    setFeedback(null);
    try {
      await candidateApi.deleteExperience(id);
      setExperienceList((prev) => prev.filter((item) => item.id !== id));
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to delete experience.';
      setFeedback({ type: 'error', message: msg });
    }
  };

  // Resume Upload & Download
  const handleResumeFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.type !== 'application/pdf') {
      setFeedback({ type: 'error', message: 'Only PDF format (.pdf) is supported for resumes.' });
      e.target.value = '';
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setFeedback({ type: 'error', message: 'Resume file size cannot exceed 5 MB.' });
      e.target.value = '';
      return;
    }

    setIsUploadingResume(true);
    setFeedback(null);
    try {
      const uploaded = await candidateApi.uploadResume(file);
      setCurrentResume(uploaded);
      setFeedback({ type: 'success', message: 'Resume uploaded successfully!' });
      const ready = await candidateApi.getReadiness();
      setReadiness(ready);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to upload resume.';
      setFeedback({ type: 'error', message: msg });
    } finally {
      setIsUploadingResume(false);
      e.target.value = '';
    }
  };

  const handleDownloadResume = async () => {
    setIsDownloadingResume(true);
    setFeedback(null);
    try {
      const blob = await candidateApi.downloadResume();
      const objectUrl = window.URL.createObjectURL(blob);
      const tempLink = document.createElement('a');
      tempLink.href = objectUrl;
      tempLink.download = currentResume?.originalFilename || 'resume.pdf';
      document.body.appendChild(tempLink);
      tempLink.click();
      document.body.removeChild(tempLink);
      window.URL.revokeObjectURL(objectUrl);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to download resume.';
      setFeedback({ type: 'error', message: msg });
    } finally {
      setIsDownloadingResume(false);
    }
  };

  const handleDeleteResume = async () => {
    if (!window.confirm('Are you sure you want to remove your active resume? You will need an active resume to submit applications.')) {
      return;
    }
    setFeedback(null);
    try {
      await candidateApi.deleteResume();
      setCurrentResume(null);
      setFeedback({ type: 'success', message: 'Resume removed successfully.' });
      const ready = await candidateApi.getReadiness();
      setReadiness(ready);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to delete resume.';
      setFeedback({ type: 'error', message: msg });
    }
  };

  if (isLoading) {
    return (
      <div className="space-y-6 max-w-4xl mx-auto">
        <Skeleton height="36px" width="240px" />
        <Skeleton height="140px" />
        <Skeleton height="220px" />
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-4xl mx-auto pb-12">
      {feedback && (
        <Alert variant={feedback.type === 'success' ? 'success' : 'error'} onClose={() => setFeedback(null)}>
          {feedback.message}
        </Alert>
      )}

      {/* Page Title & Status */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 className="font-headline font-bold text-2xl text-slate-text">Candidate Profile</h1>
          <p className="text-xs text-slate-muted mt-0.5">
            Manage your personal profile, credentials, and verification status for applications.
          </p>
        </div>
        {!isEditingProfile && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => setIsEditingProfile(true)}
            className="flex items-center gap-1.5"
          >
            <span className="material-symbols-outlined !text-[16px]">edit</span>
            Edit Details
          </Button>
        )}
      </div>

      {/* Profile Readiness Tracker Card */}
      {readiness && (
        <Card className={`border ${readiness.readyToApply ? 'border-primary/30 bg-primary/5' : 'border-amber-200 bg-amber-50/50'}`}>
          <CardBody className="p-5">
            <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <span
                    className={`material-symbols-outlined !text-xl ${
                      readiness.readyToApply ? 'text-primary' : 'text-amber-600'
                    }`}
                  >
                    {readiness.readyToApply ? 'verified' : 'pending_actions'}
                  </span>
                  <h3 className="font-headline font-semibold text-sm text-slate-text">
                    {readiness.readyToApply ? 'Profile Application Ready' : 'Application Readiness Incomplete'}
                  </h3>
                </div>
                <p className="text-xs text-slate-muted">
                  {readiness.readyToApply
                    ? 'All prerequisites met! You are eligible to submit job applications.'
                    : 'You must satisfy all 4 criteria below before submitting job applications.'}
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-3">
                <div className="flex items-center gap-1 text-xs font-medium">
                  <span className={`material-symbols-outlined !text-base ${readiness.profileComplete ? 'text-primary' : 'text-slate-400'}`}>
                    {readiness.profileComplete ? 'check_circle' : 'radio_button_unchecked'}
                  </span>
                  <span className={readiness.profileComplete ? 'text-slate-text' : 'text-slate-muted'}>Full Name</span>
                </div>
                <div className="flex items-center gap-1 text-xs font-medium">
                  <span className={`material-symbols-outlined !text-base ${readiness.hasEducation ? 'text-primary' : 'text-slate-400'}`}>
                    {readiness.hasEducation ? 'check_circle' : 'radio_button_unchecked'}
                  </span>
                  <span className={readiness.hasEducation ? 'text-slate-text' : 'text-slate-muted'}>Education (≥1)</span>
                </div>
                <div className="flex items-center gap-1 text-xs font-medium">
                  <span className={`material-symbols-outlined !text-base ${readiness.hasSkills ? 'text-primary' : 'text-slate-400'}`}>
                    {readiness.hasSkills ? 'check_circle' : 'radio_button_unchecked'}
                  </span>
                  <span className={readiness.hasSkills ? 'text-slate-text' : 'text-slate-muted'}>Skills (≥1)</span>
                </div>
                <div className="flex items-center gap-1 text-xs font-medium">
                  <span className={`material-symbols-outlined !text-base ${readiness.hasResume ? 'text-primary' : 'text-slate-400'}`}>
                    {readiness.hasResume ? 'check_circle' : 'radio_button_unchecked'}
                  </span>
                  <span className={readiness.hasResume ? 'text-slate-text' : 'text-slate-muted'}>Current Resume</span>
                </div>
              </div>
            </div>
          </CardBody>
        </Card>
      )}

      {/* 1. Basic Details Card */}
      <Card>
        <CardHeader className="flex items-center justify-between py-4 px-6 border-b border-outline-warm">
          <div>
            <h2 className="font-headline font-semibold text-base text-slate-text">General Information</h2>
            <p className="text-xs text-slate-muted">Candidate identity and introduction.</p>
          </div>
          {isEditingProfile && (
            <Button variant="ghost" size="sm" onClick={() => setIsEditingProfile(false)}>
              Cancel
            </Button>
          )}
        </CardHeader>
        <CardBody className="p-6">
          {isEditingProfile ? (
            <form onSubmit={handleSaveProfile} className="space-y-4">
              <Input
                label="Full Name *"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="e.g. Jane Doe"
                required
              />
              <Input
                label="Professional Headline"
                value={headline}
                onChange={(e) => setHeadline(e.target.value)}
                placeholder="e.g. Full Stack Developer | Computer Science Graduate"
              />
              <Input
                label="Location"
                value={location}
                onChange={(e) => setLocation(e.target.value)}
                placeholder="e.g. Bengaluru, India"
              />
              <Textarea
                label="Summary / Bio"
                value={summary}
                onChange={(e) => setSummary(e.target.value)}
                placeholder="Brief summary of your professional background, strengths, and early career goals..."
                rows={4}
              />
              <div className="flex justify-end gap-2 pt-2">
                <Button type="button" variant="outline" size="sm" onClick={() => setIsEditingProfile(false)}>
                  Cancel
                </Button>
                <Button type="submit" variant="primary" size="sm" isLoading={isSavingProfile}>
                  Save Changes
                </Button>
              </div>
            </form>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
              <div>
                <p className="text-xs font-medium text-slate-muted uppercase tracking-wider">Full Name</p>
                <p className="font-headline font-semibold text-sm text-slate-text mt-1">{profile?.fullName || 'Not specified'}</p>
              </div>
              <div>
                <p className="text-xs font-medium text-slate-muted uppercase tracking-wider">Email (Account)</p>
                <p className="text-sm text-slate-text mt-1">{user?.email || 'N/A'}</p>
              </div>
              <div>
                <p className="text-xs font-medium text-slate-muted uppercase tracking-wider">Headline</p>
                <p className="text-sm text-slate-text mt-1">{profile?.headline || 'No headline added'}</p>
              </div>
              <div>
                <p className="text-xs font-medium text-slate-muted uppercase tracking-wider">Location</p>
                <p className="text-sm text-slate-text mt-1">{profile?.location || 'Not specified'}</p>
              </div>
              <div className="sm:col-span-2">
                <p className="text-xs font-medium text-slate-muted uppercase tracking-wider">Professional Summary</p>
                <p className="text-sm text-slate-text mt-1 whitespace-pre-line leading-relaxed">
                  {profile?.summary || 'No summary provided yet. Click "Edit Details" to add an overview.'}
                </p>
              </div>
            </div>
          )}
        </CardBody>
      </Card>

      {/* 2. Skills Management */}
      <Card>
        <CardHeader className="flex items-center justify-between py-4 px-6 border-b border-outline-warm">
          <div>
            <h2 className="font-headline font-semibold text-base text-slate-text">Skills</h2>
            <p className="text-xs text-slate-muted">Used for deterministic matching against job requirements.</p>
          </div>
        </CardHeader>
        <CardBody className="p-6 space-y-4">
          <form onSubmit={handleAddSkill} className="flex gap-2 max-w-md">
            <Input
              value={newSkillName}
              onChange={(e) => setNewSkillName(e.target.value)}
              placeholder="e.g. Java, React, SQL..."
              className="flex-1"
            />
            <Button type="submit" variant="primary" size="md" isLoading={isAddingSkill} disabled={!newSkillName.trim()}>
              Add Skill
            </Button>
          </form>

          {skillsList.length === 0 ? (
            <p className="text-xs text-slate-muted italic py-2">
              No skills added yet. Add at least one skill to satisfy application requirements.
            </p>
          ) : (
            <div className="flex flex-wrap gap-2 pt-1">
              {skillsList.map((skill) => (
                <span
                  key={skill.id}
                  className="inline-flex items-center gap-1.5 px-3 py-1 bg-slate-bg border border-outline-warm text-slate-text text-xs rounded font-medium"
                >
                  {skill.name}
                  <button
                    type="button"
                    onClick={() => handleRemoveSkill(skill.id)}
                    className="text-slate-muted hover:text-rose-600 transition-colors p-1 -mr-1 rounded hover:bg-black/5 flex items-center justify-center"
                    aria-label={`Remove ${skill.name}`}
                  >
                    <span className="material-symbols-outlined !text-sm">close</span>
                  </button>
                </span>
              ))}
            </div>
          )}
        </CardBody>
      </Card>

      {/* 3. Resume Management */}
      <Card>
        <CardHeader className="flex items-center justify-between py-4 px-6 border-b border-outline-warm">
          <div>
            <h2 className="font-headline font-semibold text-base text-slate-text">Resume Document</h2>
            <p className="text-xs text-slate-muted">PDF snapshot used when submitting applications.</p>
          </div>
        </CardHeader>
        <CardBody className="p-6 space-y-4">
          {currentResume ? (
            <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between p-4 bg-[#F9FAF8] border border-outline-warm rounded gap-4">
              <div className="flex items-center gap-3">
                <span className="material-symbols-outlined text-primary !text-3xl shrink-0">
                  picture_as_pdf
                </span>
                <div>
                  <h4 className="font-headline font-medium text-sm text-slate-text">
                    {currentResume.originalFilename}
                  </h4>
                  <p className="text-xs text-slate-muted">
                    {(currentResume.sizeBytes / 1024).toFixed(1)} KB · Uploaded {new Date(currentResume.createdAt).toLocaleDateString()}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleDownloadResume}
                  isLoading={isDownloadingResume}
                  className="flex items-center gap-1"
                >
                  <span className="material-symbols-outlined !text-[16px]">download</span>
                  Download
                </Button>
                <label className="cursor-pointer">
                  <input
                    type="file"
                    accept="application/pdf"
                    className="hidden"
                    onChange={handleResumeFileChange}
                    disabled={isUploadingResume}
                  />
                  <span
                    className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded border border-outline-warm text-slate-muted hover:text-slate-text bg-white hover:bg-slate-50 transition cursor-pointer"
                  >
                    <span className="material-symbols-outlined !text-[16px]">upload_file</span>
                    {isUploadingResume ? 'Uploading...' : 'Replace'}
                  </span>
                </label>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleDeleteResume}
                  className="text-rose-600 hover:text-rose-700 hover:bg-rose-50"
                  aria-label="Delete resume"
                >
                  <span className="material-symbols-outlined !text-[16px]">delete</span>
                </Button>
              </div>
            </div>
          ) : (
            <div className="text-center py-8 border-2 border-dashed border-outline-warm rounded-lg p-6 space-y-3">
              <span className="material-symbols-outlined text-slate-muted !text-4xl">
                upload_file
              </span>
              <div>
                <p className="text-sm font-medium text-slate-text">Upload your current resume</p>
                <p className="text-xs text-slate-muted mt-0.5">PDF format only, maximum size 5 MB.</p>
              </div>
              <label className="inline-block cursor-pointer">
                <input
                  type="file"
                  accept="application/pdf"
                  className="hidden"
                  onChange={handleResumeFileChange}
                  disabled={isUploadingResume}
                />
                <span className="inline-flex items-center justify-center px-4 py-2 text-xs font-semibold rounded bg-primary text-white hover:bg-primary-hover shadow-sm transition cursor-pointer">
                  {isUploadingResume ? 'Uploading...' : 'Select PDF File'}
                </span>
              </label>
            </div>
          )}
        </CardBody>
      </Card>

      {/* 4. Education Section */}
      <Card>
        <CardHeader className="flex items-center justify-between py-4 px-6 border-b border-outline-warm">
          <div>
            <h2 className="font-headline font-semibold text-base text-slate-text">Education</h2>
            <p className="text-xs text-slate-muted">At least one education record is required to apply.</p>
          </div>
          <Button variant="outline" size="sm" onClick={openAddEducation} className="flex items-center gap-1">
            <span className="material-symbols-outlined !text-[16px]">add</span>
            Add Education
          </Button>
        </CardHeader>
        <CardBody className="p-6">
          {educationList.length === 0 ? (
            <p className="text-xs text-slate-muted italic py-2">
              No education records listed. Click &quot;Add Education&quot; to add your degree or diploma.
            </p>
          ) : (
            <div className="space-y-4 divide-y divide-outline-warm">
              {educationList.map((edu, idx) => (
                <div key={edu.id} className={`flex items-start justify-between gap-4 ${idx > 0 ? 'pt-4' : ''}`}>
                  <div className="space-y-1">
                    <h4 className="font-headline font-semibold text-sm text-slate-text">{edu.institution}</h4>
                    <p className="text-xs font-medium text-slate-text">
                      {edu.degree} in {edu.fieldOfStudy}
                    </p>
                    <p className="text-xs text-slate-muted">
                      {edu.startYear} – {edu.endYear || 'Present'} {edu.grade ? `· Grade: ${edu.grade}` : ''}
                    </p>
                  </div>
                  <div className="flex items-center gap-1">
                    <Button variant="ghost" size="sm" onClick={() => openEditEducation(edu)}>
                      <span className="material-symbols-outlined !text-[16px]">edit</span>
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleDeleteEducation(edu.id)}
                      className="text-rose-600 hover:text-rose-700 hover:bg-rose-50"
                    >
                      <span className="material-symbols-outlined !text-[16px]">delete</span>
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardBody>
      </Card>

      {/* 5. Experience Section */}
      <Card>
        <CardHeader className="flex items-center justify-between py-4 px-6 border-b border-outline-warm">
          <div>
            <h2 className="font-headline font-semibold text-base text-slate-text">Work Experience</h2>
            <p className="text-xs text-slate-muted">Internships, apprenticeships, or full-time roles.</p>
          </div>
          <Button variant="outline" size="sm" onClick={openAddExperience} className="flex items-center gap-1">
            <span className="material-symbols-outlined !text-[16px]">add</span>
            Add Experience
          </Button>
        </CardHeader>
        <CardBody className="p-6">
          {experienceList.length === 0 ? (
            <p className="text-xs text-slate-muted italic py-2">
              No work experience added. (Optional for early-career candidates).
            </p>
          ) : (
            <div className="space-y-4 divide-y divide-outline-warm">
              {experienceList.map((exp, idx) => (
                <div key={exp.id} className={`flex items-start justify-between gap-4 ${idx > 0 ? 'pt-4' : ''}`}>
                  <div className="space-y-1">
                    <h4 className="font-headline font-semibold text-sm text-slate-text">{exp.roleTitle}</h4>
                    <p className="text-xs font-medium text-slate-text">{exp.companyName}</p>
                    <p className="text-xs text-slate-muted">
                      {exp.startDate} – {exp.isCurrent ? 'Present' : exp.endDate || 'N/A'}
                    </p>
                    {exp.description && (
                      <p className="text-xs text-slate-text mt-1 whitespace-pre-line leading-relaxed">
                        {exp.description}
                      </p>
                    )}
                  </div>
                  <div className="flex items-center gap-1">
                    <Button variant="ghost" size="sm" onClick={() => openEditExperience(exp)}>
                      <span className="material-symbols-outlined !text-[16px]">edit</span>
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleDeleteExperience(exp.id)}
                      className="text-rose-600 hover:text-rose-700 hover:bg-rose-50"
                    >
                      <span className="material-symbols-outlined !text-[16px]">delete</span>
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardBody>
      </Card>

      {/* 6. Projects Section */}
      <Card>
        <CardHeader className="flex items-center justify-between py-4 px-6 border-b border-outline-warm">
          <div>
            <h2 className="font-headline font-semibold text-base text-slate-text">Projects & Portfolio</h2>
            <p className="text-xs text-slate-muted">Highlight practical coursework, open source, or independent apps.</p>
          </div>
          <Button variant="outline" size="sm" onClick={openAddProject} className="flex items-center gap-1">
            <span className="material-symbols-outlined !text-[16px]">add</span>
            Add Project
          </Button>
        </CardHeader>
        <CardBody className="p-6">
          {projectsList.length === 0 ? (
            <p className="text-xs text-slate-muted italic py-2">
              No projects added yet. Showcase your practical work to prospective recruiters.
            </p>
          ) : (
            <div className="space-y-4 divide-y divide-outline-warm">
              {projectsList.map((proj, idx) => (
                <div key={proj.id} className={`flex items-start justify-between gap-4 ${idx > 0 ? 'pt-4' : ''}`}>
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <h4 className="font-headline font-semibold text-sm text-slate-text">{proj.title}</h4>
                      {proj.projectUrl && (
                        <a
                          href={proj.projectUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-primary hover:underline text-xs flex items-center gap-0.5"
                        >
                          <span className="material-symbols-outlined !text-sm">open_in_new</span>
                          Link
                        </a>
                      )}
                    </div>
                    <p className="text-xs text-slate-text whitespace-pre-line leading-relaxed">
                      {proj.description}
                    </p>
                  </div>
                  <div className="flex items-center gap-1">
                    <Button variant="ghost" size="sm" onClick={() => openEditProject(proj)}>
                      <span className="material-symbols-outlined !text-[16px]">edit</span>
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleDeleteProject(proj.id)}
                      className="text-rose-600 hover:text-rose-700 hover:bg-rose-50"
                    >
                      <span className="material-symbols-outlined !text-[16px]">delete</span>
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardBody>
      </Card>

      {/* Education Modal */}
      <Modal
        isOpen={isEduModalOpen}
        onClose={() => setIsEduModalOpen(false)}
        title={editingEdu ? 'Edit Education' : 'Add Education Record'}
      >
        <form onSubmit={handleSaveEducation} className="space-y-4">
          <Input
            label="Institution / University *"
            value={eduInstitution}
            onChange={(e) => setEduInstitution(e.target.value)}
            placeholder="e.g. National Institute of Technology"
            required
          />
          <Input
            label="Degree *"
            value={eduDegree}
            onChange={(e) => setEduDegree(e.target.value)}
            placeholder="e.g. Bachelor of Technology"
            required
          />
          <Input
            label="Field of Study *"
            value={eduField}
            onChange={(e) => setEduField(e.target.value)}
            placeholder="e.g. Computer Science and Engineering"
            required
          />
          <div className="grid grid-cols-2 gap-4">
            <Input
              label="Start Year *"
              type="number"
              min={1950}
              max={2100}
              value={eduStartYear}
              onChange={(e) => setEduStartYear(Number(e.target.value))}
              required
            />
            <Input
              label="End Year (Graduation)"
              type="number"
              min={1950}
              max={2100}
              value={eduEndYear}
              onChange={(e) => setEduEndYear(e.target.value ? Number(e.target.value) : '')}
              placeholder="Leave blank if ongoing"
            />
          </div>
          <Input
            label="Grade / CGPA"
            value={eduGrade}
            onChange={(e) => setEduGrade(e.target.value)}
            placeholder="e.g. 8.8 CGPA or Distinction"
          />
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" size="sm" onClick={() => setIsEduModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" size="sm" isLoading={isSubmittingEdu}>
              {editingEdu ? 'Update Education' : 'Add Education'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Project Modal */}
      <Modal
        isOpen={isProjModalOpen}
        onClose={() => setIsProjModalOpen(false)}
        title={editingProj ? 'Edit Project' : 'Add Project'}
      >
        <form onSubmit={handleSaveProject} className="space-y-4">
          <Input
            label="Project Title *"
            value={projTitle}
            onChange={(e) => setProjTitle(e.target.value)}
            placeholder="e.g. Distributed Task Queue"
            required
          />
          <Textarea
            label="Description *"
            value={projDescription}
            onChange={(e) => setProjDescription(e.target.value)}
            placeholder="Describe architecture, technologies used, and outcomes..."
            rows={3}
            required
          />
          <Input
            label="Project URL"
            value={projUrl}
            onChange={(e) => setProjUrl(e.target.value)}
            placeholder="e.g. https://github.com/username/project"
          />
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" size="sm" onClick={() => setIsProjModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" size="sm" isLoading={isSubmittingProj}>
              {editingProj ? 'Update Project' : 'Add Project'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Experience Modal */}
      <Modal
        isOpen={isExpModalOpen}
        onClose={() => setIsExpModalOpen(false)}
        title={editingExp ? 'Edit Experience' : 'Add Work Experience'}
      >
        <form onSubmit={handleSaveExperience} className="space-y-4">
          <Input
            label="Company Name *"
            value={expCompany}
            onChange={(e) => setExpCompany(e.target.value)}
            placeholder="e.g. Acme Tech Solutions"
            required
          />
          <Input
            label="Role Title *"
            value={expRole}
            onChange={(e) => setExpRole(e.target.value)}
            placeholder="e.g. Software Engineering Intern"
            required
          />
          <div className="grid grid-cols-2 gap-4">
            <Input
              label="Start Date *"
              type="date"
              value={expStartDate}
              onChange={(e) => setExpStartDate(e.target.value)}
              required
            />
            <Input
              label="End Date"
              type="date"
              value={expEndDate}
              onChange={(e) => setExpEndDate(e.target.value)}
              disabled={expIsCurrent}
              required={!expIsCurrent}
            />
          </div>
          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              id="isCurrentExp"
              checked={expIsCurrent}
              onChange={(e) => {
                setExpIsCurrent(e.target.checked);
                if (e.target.checked) setExpEndDate('');
              }}
              className="rounded border-outline-warm text-primary focus:ring-primary"
            />
            <label htmlFor="isCurrentExp" className="text-xs text-slate-text select-none cursor-pointer">
              I currently work here
            </label>
          </div>
          <Textarea
            label="Description"
            value={expDescription}
            onChange={(e) => setExpDescription(e.target.value)}
            placeholder="Responsibilities, key achievements, and technologies used..."
            rows={3}
          />
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" size="sm" onClick={() => setIsExpModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" size="sm" isLoading={isSubmittingExp}>
              {editingExp ? 'Update Experience' : 'Add Experience'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
