import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { recruiterApi } from '../../api/recruiter';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Textarea } from '../../components/ui/Textarea';
import { Select } from '../../components/ui/Select';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Alert } from '../../components/ui/Alert';

export const RecruiterNewJobPage: React.FC = () => {
  const navigate = useNavigate();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [location, setLocation] = useState('');
  const [jobType, setJobType] = useState('FULL_TIME');
  const [workMode, setWorkMode] = useState('HYBRID');
  const [experienceLevel, setExperienceLevel] = useState('FRESHER');
  const [minSalary, setMinSalary] = useState('');
  const [maxSalary, setMaxSalary] = useState('');
  const [skills, setSkills] = useState<string[]>(['React', 'TypeScript']);
  const [skillInput, setSkillInput] = useState('');

  const handleAddSkill = (e: React.KeyboardEvent | React.MouseEvent) => {
    if ('key' in e && e.key !== 'Enter') return;
    e.preventDefault();
    const trimmed = skillInput.trim();
    if (trimmed && !skills.includes(trimmed)) {
      setSkills([...skills, trimmed]);
      setSkillInput('');
    }
  };

  const handleRemoveSkill = (skill: string) => {
    setSkills(skills.filter((s) => s !== skill));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setError(null);

    if (skills.length === 0) {
      setError('Please add at least one required skill for this role.');
      setIsSubmitting(false);
      return;
    }

    try {
      await recruiterApi.createJob({
        title,
        description,
        location,
        jobType,
        workMode,
        experienceLevel,
        minSalary: minSalary ? Number(minSalary) : undefined,
        maxSalary: maxSalary ? Number(maxSalary) : undefined,
        requiredSkills: skills,
        openPositions: 1,
      });
      navigate('/recruiter/jobs');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to publish job.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6 max-w-3xl">
      <div>
        <h1 className="font-headline font-bold text-2xl text-slate-text">Create Job Opening</h1>
        <p className="text-xs text-slate-muted mt-0.5">
          Publish a new role for early-career candidates (0–2 years experience)
        </p>
      </div>

      {error && (
        <Alert variant="error" onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Card>
        <CardHeader>
          <h2 className="font-headline font-semibold text-base text-slate-text">Job Details</h2>
        </CardHeader>
        <CardBody>
          <form onSubmit={handleSubmit} className="space-y-4">
            <Input
              label="Job Title"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Junior Frontend Developer"
            />

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <Select
                label="Job Type"
                value={jobType}
                onChange={(e) => setJobType(e.target.value)}
                options={[
                  { value: 'FULL_TIME', label: 'Full Time' },
                  { value: 'PART_TIME', label: 'Part Time' },
                  { value: 'INTERNSHIP', label: 'Internship' },
                  { value: 'CONTRACT', label: 'Contract' },
                ]}
              />

              <Select
                label="Work Mode"
                value={workMode}
                onChange={(e) => setWorkMode(e.target.value)}
                options={[
                  { value: 'REMOTE', label: 'Remote' },
                  { value: 'HYBRID', label: 'Hybrid' },
                  { value: 'ON_SITE', label: 'On-Site' },
                ]}
              />

              <Select
                label="Experience Level"
                value={experienceLevel}
                onChange={(e) => setExperienceLevel(e.target.value)}
                options={[
                  { value: 'INTERN', label: 'Intern' },
                  { value: 'FRESHER', label: 'Fresher (0 yrs)' },
                  { value: 'JUNIOR', label: 'Junior (1–2 yrs)' },
                ]}
              />
            </div>

            <Input
              label="Location"
              required
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              placeholder="e.g. San Francisco, CA or Remote"
            />

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                label="Min Salary ($ or ₹)"
                type="number"
                value={minSalary}
                onChange={(e) => setMinSalary(e.target.value)}
                placeholder="75000"
              />
              <Input
                label="Max Salary ($ or ₹)"
                type="number"
                value={maxSalary}
                onChange={(e) => setMaxSalary(e.target.value)}
                placeholder="95000"
              />
            </div>

            {/* Skills */}
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-semibold text-slate-text uppercase tracking-wider font-sans">
                Required Candidate Skills <span className="text-status-rejected">*</span>
              </label>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={skillInput}
                  onChange={(e) => setSkillInput(e.target.value)}
                  onKeyDown={handleAddSkill}
                  placeholder="e.g. React, Java, Git (Press Enter)"
                  className="w-full min-h-[44px] px-3.5 py-2 text-sm bg-white border border-outline-warm rounded text-slate-text focus:outline-none focus:ring-2 focus:ring-primary"
                />
                <Button type="button" variant="outline" size="md" onClick={handleAddSkill}>
                  Add
                </Button>
              </div>
              <div className="flex flex-wrap gap-1.5 mt-2">
                {skills.map((skill) => (
                  <span
                    key={skill}
                    className="inline-flex items-center gap-1.5 bg-primary-tint text-primary border border-[#A7F3D0] px-2.5 py-1 rounded text-xs font-mono"
                  >
                    <span>{skill}</span>
                    <button
                      type="button"
                      onClick={() => handleRemoveSkill(skill)}
                      className="hover:text-status-rejected"
                    >
                      <span className="material-symbols-outlined !text-[14px]">close</span>
                    </button>
                  </span>
                ))}
              </div>
            </div>

            <Textarea
              label="Role Description"
              required
              rows={5}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Describe the responsibilities, growth opportunities, and day-to-day work..."
            />

            <div className="pt-4 border-t border-outline-warm flex items-center justify-end gap-3">
              <Button type="button" variant="outline" size="md" onClick={() => navigate('/recruiter/jobs')}>
                Cancel
              </Button>
              <Button type="submit" variant="primary" size="md" isLoading={isSubmitting}>
                Publish Opening
              </Button>
            </div>
          </form>
        </CardBody>
      </Card>
    </div>
  );
};
