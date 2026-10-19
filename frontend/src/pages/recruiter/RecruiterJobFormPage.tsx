import React, { useState, useEffect } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { recruiterApi } from '../../api/recruiter';
import { jobsApi } from '../../api/jobs';
import type {
  JobType,
  ExperienceLevel,
  WorkMode,
  SkillDto,
} from '../../types/api';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Textarea } from '../../components/ui/Textarea';
import { Select } from '../../components/ui/Select';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Alert } from '../../components/ui/Alert';
import { Skeleton } from '../../components/ui/Skeleton';

// Known seeded platform skills with database IDs
const SEEDED_PLATFORM_SKILLS: SkillDto[] = [
  { id: 1, name: 'Java' },
  { id: 2, name: 'React' },
  { id: 3, name: 'SQL' },
  { id: 4, name: 'TypeScript' },
  { id: 5, name: 'Rust' },
  { id: 6, name: 'Spring Boot' },
  { id: 8, name: 'Machine Learning' },
  { id: 9, name: 'Web-Development' },
];

export const RecruiterJobFormPage: React.FC = () => {
  const navigate = useNavigate();
  const { jobId } = useParams<{ jobId: string }>();
  const isEditMode = Boolean(jobId);

  const [isLoading, setIsLoading] = useState<boolean>(isEditMode);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [applicantCount, setApplicantCount] = useState<number>(0);

  // Form fields
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [responsibilities, setResponsibilities] = useState('');
  const [requirements, setRequirements] = useState('');
  const [jobType, setJobType] = useState<JobType>('FULL_TIME');
  const [experienceLevel, setExperienceLevel] = useState<ExperienceLevel>('FRESHER');
  const [workMode, setWorkMode] = useState<WorkMode>('HYBRID');
  const [location, setLocation] = useState('');
  const [salaryMinLpa, setSalaryMinLpa] = useState('');
  const [salaryMaxLpa, setSalaryMaxLpa] = useState('');
  const [salaryCurrency] = useState('INR');

  // Application deadline defaults to 30 days in future (YYYY-MM-DD)
  const defaultDeadline = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)
    .toISOString()
    .split('T')[0];
  const [applicationDeadline, setApplicationDeadline] = useState(defaultDeadline);

  // Available skills & selected skills
  const [availableSkills, setAvailableSkills] = useState<SkillDto[]>(SEEDED_PLATFORM_SKILLS);
  const [selectedSkillIds, setSelectedSkillIds] = useState<number[]>([1, 2]);

  const hasApplications = isEditMode && applicantCount > 0;

  useEffect(() => {
    // 1. Fetch available skills from public jobs to discover dynamically active skills
    const loadPlatformSkills = async () => {
      try {
        const publicJobs = await jobsApi.getPublicJobs({ page: 0, size: 50 });
        const map = new Map<number, string>();
        SEEDED_PLATFORM_SKILLS.forEach((s) => {
          if (s.id) map.set(s.id, s.name);
        });

        const items = publicJobs?.data || publicJobs?.items || [];
        items.forEach((j) => {
          if (j.skills) {
            j.skills.forEach((s) => {
              if (typeof s === 'object' && s !== null && s.id && s.name) {
                map.set(s.id, s.name);
              }
            });
          }
        });
        const combined = Array.from(map.entries()).map(([id, name]) => ({ id, name }));
        setAvailableSkills(combined);
      } catch {
        // Fallback to seeded skills
      }
    };
    loadPlatformSkills();

    // 2. If edit mode, load existing job data
    if (isEditMode && jobId) {
      const loadJob = async () => {
        setIsLoading(true);
        try {
          const data = await recruiterApi.getJobById(jobId);
          setTitle(data.title || '');
          setDescription(data.description || '');
          setResponsibilities(data.responsibilities || '');
          setRequirements(data.requirements || '');
          setJobType(data.jobType);
          setExperienceLevel(data.experienceLevel);
          setWorkMode(data.workMode);
          setLocation(data.location || '');
          setSalaryMinLpa(data.salaryMinLpa ? String(data.salaryMinLpa) : '');
          setSalaryMaxLpa(data.salaryMaxLpa ? String(data.salaryMaxLpa) : '');
          setApplicationDeadline(data.applicationDeadline || defaultDeadline);
          setApplicantCount(data.applicantCount ?? 0);

          if (data.skills && data.skills.length > 0) {
            const skillIds: number[] = [];
            data.skills.forEach((s) => {
              if (typeof s === 'object' && s !== null && s.id) {
                skillIds.push(s.id);
              }
            });
            if (skillIds.length > 0) {
              setSelectedSkillIds(skillIds);
            }
          }
        } catch (err: unknown) {
          setError(err instanceof Error ? err.message : 'Unable to load existing job details.');
        } finally {
          setIsLoading(false);
        }
      };
      loadJob();
    }
  }, [isEditMode, jobId]);

  const handleToggleSkill = (skillId: number) => {
    if (hasApplications) return; // locked if applications exist
    if (selectedSkillIds.includes(skillId)) {
      if (selectedSkillIds.length === 1) {
        setError('At least one required skill must remain selected.');
        return;
      }
      setSelectedSkillIds(selectedSkillIds.filter((id) => id !== skillId));
      setError(null);
    } else {
      setSelectedSkillIds([...selectedSkillIds, skillId]);
      setError(null);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setError(null);

    // Frontend validations
    if (!title.trim()) {
      setError('Job title is required.');
      setIsSubmitting(false);
      return;
    }
    if (!description.trim()) {
      setError('Job description is required.');
      setIsSubmitting(false);
      return;
    }
    if (workMode !== 'REMOTE' && !location.trim()) {
      setError('Location is required for On-Site and Hybrid roles.');
      setIsSubmitting(false);
      return;
    }
    if (selectedSkillIds.length === 0) {
      setError('Please select at least one required skill for early-career matching.');
      setIsSubmitting(false);
      return;
    }
    if (!applicationDeadline) {
      setError('Application deadline is required.');
      setIsSubmitting(false);
      return;
    }

    const todayStr = new Date().toISOString().split('T')[0];
    if (applicationDeadline < todayStr) {
      setError('Application deadline cannot be in the past.');
      setIsSubmitting(false);
      return;
    }

    const minNum = salaryMinLpa ? Number(salaryMinLpa) : undefined;
    const maxNum = salaryMaxLpa ? Number(salaryMaxLpa) : undefined;

    if (minNum !== undefined && minNum <= 0) {
      setError('Minimum salary must be greater than zero.');
      setIsSubmitting(false);
      return;
    }
    if (minNum !== undefined && maxNum !== undefined && maxNum < minNum) {
      setError('Maximum salary cannot be less than minimum salary.');
      setIsSubmitting(false);
      return;
    }

    try {
      const payload = {
        title: title.trim(),
        description: description.trim(),
        responsibilities: responsibilities.trim() || undefined,
        requirements: requirements.trim() || undefined,
        jobType,
        experienceLevel,
        workMode,
        location: location.trim() || undefined,
        salaryMinLpa: minNum,
        salaryMaxLpa: maxNum,
        salaryCurrency,
        applicationDeadline,
        requiredSkillIds: selectedSkillIds,
      };

      if (isEditMode && jobId) {
        await recruiterApi.updateJob(jobId, payload);
        navigate(`/recruiter/jobs/${jobId}`);
      } else {
        const created = await recruiterApi.createJob(payload);
        navigate(`/recruiter/jobs/${created.id}`);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to save job opening.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const jobTypeOptions = [
    { value: 'INTERNSHIP', label: 'Internship' },
    { value: 'FULL_TIME', label: 'Full Time' },
    { value: 'PART_TIME', label: 'Part Time' },
    { value: 'APPRENTICESHIP', label: 'Apprenticeship' },
    { value: 'TRAINEE', label: 'Graduate Trainee' },
  ];

  const experienceLevelOptions = [
    { value: 'INTERNSHIP', label: 'Internship (Student / In-College)' },
    { value: 'FRESHER', label: 'Fresher (0 Years / Recent Graduate)' },
    { value: 'ZERO_TO_ONE_YEARS', label: '0–1 Years Experience' },
    { value: 'ONE_TO_TWO_YEARS', label: '1–2 Years Experience' },
  ];

  const workModeOptions = [
    { value: 'HYBRID', label: 'Hybrid' },
    { value: 'ONSITE', label: 'On-Site' },
    { value: 'REMOTE', label: 'Remote' },
  ];

  if (isLoading) {
    return (
      <div className="space-y-6 max-w-3xl">
        <div className="space-y-2">
          <Skeleton height="32px" width="240px" />
          <Skeleton height="16px" width="380px" />
        </div>
        <Card>
          <CardBody className="p-6 space-y-4">
            <Skeleton height="40px" />
            <Skeleton height="40px" />
            <Skeleton height="120px" />
          </CardBody>
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-3xl">
      {/* Page Header */}
      <div>
        <div className="flex items-center gap-2 text-xs text-slate-muted mb-1">
          <Link to="/recruiter/jobs" className="hover:text-primary transition-colors">
            Job Postings
          </Link>
          <span>/</span>
          <span className="font-mono text-slate-text">
            {isEditMode ? `Edit #${jobId}` : 'New Opening'}
          </span>
        </div>
        <h1 className="font-headline font-bold text-2xl text-slate-text">
          {isEditMode ? 'Edit Job Opening' : 'Create Job Opening'}
        </h1>
        <p className="text-xs text-slate-muted mt-0.5">
          {isEditMode
            ? 'Update role details, responsibilities, and operational parameters.'
            : 'Publish a structured role for candidates with 0–2 years of experience.'}
        </p>
      </div>

      {error && (
        <Alert variant="error" onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {/* Fundamental Requirements Lock Warning */}
      {hasApplications && (
        <div className="p-4 rounded border border-[#FED7AA] bg-[#FFF7ED] text-xs text-[#9A3412] flex items-start gap-3">
          <span className="material-symbols-outlined !text-xl text-[#EA580C] shrink-0 mt-0.5">
            lock
          </span>
          <div>
            <span className="font-semibold block mb-0.5">
              Fundamental Job Requirements Locked ({applicantCount} Candidates Applied)
            </span>
            Per platform data invariants, fundamental requirements (Job Title, Type, Experience Level,
            Work Mode, Salary Bounds, and Required Skills) are locked once applications exist.
            Operational details (Description, Responsibilities, Location, and Application Deadline)
            remain editable.
          </div>
        </div>
      )}

      <Card>
        <CardHeader>
          <h2 className="font-headline font-semibold text-base text-slate-text">Role Configuration</h2>
        </CardHeader>
        <CardBody className="p-5 sm:p-6">
          <form onSubmit={handleSubmit} className="space-y-5">
            {/* Job Title */}
            <Input
              label="Job Title"
              required
              disabled={hasApplications}
              maxLength={200}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Junior Backend Engineer (Java / Spring)"
              helperText={hasApplications ? 'Locked: Applications exist' : undefined}
            />

            {/* Employment Type, Experience Level, Work Mode */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <Select
                label="Job Type"
                value={jobType}
                disabled={hasApplications}
                onChange={(e) => setJobType(e.target.value as JobType)}
                options={jobTypeOptions}
              />

              <Select
                label="Experience Level"
                value={experienceLevel}
                disabled={hasApplications}
                onChange={(e) => setExperienceLevel(e.target.value as ExperienceLevel)}
                options={experienceLevelOptions}
              />

              <Select
                label="Work Mode"
                value={workMode}
                disabled={hasApplications}
                onChange={(e) => setWorkMode(e.target.value as WorkMode)}
                options={workModeOptions}
              />
            </div>

            {/* Location & Application Deadline */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                label="Location / Office"
                required={workMode !== 'REMOTE'}
                maxLength={150}
                value={location}
                onChange={(e) => setLocation(e.target.value)}
                placeholder={workMode === 'REMOTE' ? 'Optional (e.g. Remote India)' : 'e.g. Bengaluru, India'}
              />

              <Input
                label="Application Deadline"
                type="date"
                required
                min={new Date().toISOString().split('T')[0]}
                value={applicationDeadline}
                onChange={(e) => setApplicationDeadline(e.target.value)}
              />
            </div>

            {/* Salary Range */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                label="Min Salary (LPA in ₹)"
                type="number"
                step="0.1"
                min="0.1"
                disabled={hasApplications}
                value={salaryMinLpa}
                onChange={(e) => setSalaryMinLpa(e.target.value)}
                placeholder="e.g. 6.00"
                helperText={hasApplications ? 'Locked: Applications exist' : 'Lakhs Per Annum'}
              />

              <Input
                label="Max Salary (LPA in ₹)"
                type="number"
                step="0.1"
                min="0.1"
                disabled={hasApplications}
                value={salaryMaxLpa}
                onChange={(e) => setSalaryMaxLpa(e.target.value)}
                placeholder="e.g. 12.00"
                helperText={hasApplications ? 'Locked: Applications exist' : 'Must be ≥ Min Salary'}
              />
            </div>

            {/* Required Skills Selection */}
            <div className="flex flex-col gap-2">
              <label className="text-xs font-semibold text-slate-text uppercase tracking-wider font-sans">
                Required Skills for Evaluation <span className="text-status-rejected">*</span>
              </label>
              <p className="text-xs text-slate-muted -mt-1">
                Select the technical skills used by the deterministic matching engine to evaluate applicants.
              </p>

              <div className="flex flex-wrap gap-2 pt-1">
                {availableSkills.map((skill) => {
                  const isSelected = selectedSkillIds.includes(skill.id);
                  return (
                    <button
                      key={skill.id}
                      type="button"
                      disabled={hasApplications}
                      onClick={() => handleToggleSkill(skill.id)}
                      className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-mono font-medium border transition-colors ${
                        isSelected
                          ? 'bg-primary text-white border-primary shadow-subtle'
                          : 'bg-white text-slate-text border-outline-warm hover:border-primary hover:bg-[#F2F5F3]'
                      } ${hasApplications ? 'opacity-70 cursor-not-allowed' : 'cursor-pointer'}`}
                    >
                      <span className="material-symbols-outlined !text-[15px]">
                        {isSelected ? 'check_circle' : 'add'}
                      </span>
                      <span>{skill.name}</span>
                    </button>
                  );
                })}
              </div>
              <span className="text-[11px] font-mono text-slate-muted">
                {selectedSkillIds.length} skill(s) selected
              </span>
            </div>

            {/* Role Description */}
            <Textarea
              label="Role Overview & Description"
              required
              rows={5}
              maxLength={10000}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Provide a comprehensive role description covering the team context, mentorship, and tech stack..."
            />

            {/* Responsibilities */}
            <Textarea
              label="Key Responsibilities (Optional)"
              rows={4}
              maxLength={10000}
              value={responsibilities}
              onChange={(e) => setResponsibilities(e.target.value)}
              placeholder="Detail day-to-day duties, feature delivery expectations, and engineering practices..."
            />

            {/* Qualifications / Eligibility */}
            <Textarea
              label="Qualifications & Requirements"
              rows={4}
              maxLength={10000}
              disabled={hasApplications}
              value={requirements}
              onChange={(e) => setRequirements(e.target.value)}
              placeholder="List required degrees, fundamentals, programming knowledge, or minimum academic eligibility..."
              helperText={hasApplications ? 'Locked: Applications exist' : undefined}
            />

            {/* Form Actions */}
            <div className="pt-4 border-t border-outline-warm flex items-center justify-end gap-3">
              <Button
                type="button"
                variant="outline"
                size="md"
                onClick={() => navigate('/recruiter/jobs')}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button type="submit" variant="primary" size="md" isLoading={isSubmitting}>
                {isEditMode ? 'Save Job Changes' : 'Create Draft Opening'}
              </Button>
            </div>
          </form>
        </CardBody>
      </Card>
    </div>
  );
};

export const RecruiterNewJobPage = RecruiterJobFormPage;
