import React, { useState, useEffect } from 'react';
import { candidateApi } from '../../api/candidate';
import type { CandidateProfile } from '../../types/api';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Textarea } from '../../components/ui/Textarea';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Alert } from '../../components/ui/Alert';
import { Skeleton } from '../../components/ui/Skeleton';

export const CandidateProfilePage: React.FC = () => {
  const [profile, setProfile] = useState<CandidateProfile | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Form fields
  const [fullName, setFullName] = useState('');
  const [headline, setHeadline] = useState('');
  const [phone, setPhone] = useState('');
  const [location, setLocation] = useState('');
  const [bio, setBio] = useState('');
  const [education, setEducation] = useState('');
  const [skills, setSkills] = useState<string[]>([]);
  const [skillInput, setSkillInput] = useState('');

  useEffect(() => {
    const fetchProfile = async () => {
      setIsLoading(true);
      try {
        const data = await candidateApi.getProfile();
        setProfile(data);
        setFullName(data.fullName || '');
        setHeadline(data.headline || '');
        setPhone(data.phone || '');
        setLocation(data.location || '');
        setBio(data.bio || '');
        setEducation(data.education || '');
        setSkills(data.skills || []);
      } catch {
        setFeedback({ type: 'error', message: 'Unable to load profile data.' });
      } finally {
        setIsLoading(false);
      }
    };
    fetchProfile();
  }, []);

  const handleAddSkill = (e: React.KeyboardEvent | React.MouseEvent) => {
    if ('key' in e && e.key !== 'Enter') return;
    e.preventDefault();
    const trimmed = skillInput.trim();
    if (trimmed && !skills.includes(trimmed)) {
      setSkills([...skills, trimmed]);
      setSkillInput('');
    }
  };

  const handleRemoveSkill = (skillToRemove: string) => {
    setSkills(skills.filter((s) => s !== skillToRemove));
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    setFeedback(null);
    try {
      const updated = await candidateApi.updateProfile({
        fullName,
        headline,
        phone,
        location,
        bio,
        education,
        skills,
      });
      setProfile(updated);
      setFeedback({ type: 'success', message: 'Candidate profile successfully updated!' });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to update profile.';
      setFeedback({ type: 'error', message: msg });
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return (
      <div className="space-y-4 max-w-3xl">
        <Skeleton height="32px" width="200px" />
        <Skeleton height="200px" />
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-3xl">
      <div>
        <h1 className="font-headline font-bold text-2xl text-slate-text">Candidate Profile</h1>
        <p className="text-xs text-slate-muted mt-0.5">
          Maintain your verified skills and education to unlock application eligibility
        </p>
      </div>

      {feedback && (
        <Alert variant={feedback.type} onClose={() => setFeedback(null)}>
          {feedback.message}
        </Alert>
      )}

      {/* Profile Readiness Meter */}
      <div className="bg-white rounded border border-outline-warm p-5 shadow-card flex items-center justify-between gap-4">
        <div>
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-muted">
            Profile Readiness Status
          </span>
          <h3 className="font-headline font-bold text-lg text-slate-text mt-0.5">
            {profile?.isProfileComplete ? (
              <span className="text-primary flex items-center gap-1.5">
                <span className="material-symbols-outlined !text-xl">check_circle</span>
                Ready For Job Submissions
              </span>
            ) : (
              <span className="text-[#D97706] flex items-center gap-1.5">
                <span className="material-symbols-outlined !text-xl">pending</span>
                Incomplete — Education & Skills Required
              </span>
            )}
          </h3>
        </div>
        <div className="text-right">
          <span className="font-mono text-xl font-bold text-slate-text">
            {skills.length} Skill{skills.length === 1 ? '' : 's'}
          </span>
        </div>
      </div>

      <Card>
        <CardHeader>
          <h2 className="font-headline font-semibold text-base text-slate-text">
            Personal & Educational Information
          </h2>
        </CardHeader>
        <CardBody>
          <form onSubmit={handleSave} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                label="Full Name"
                required
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
              />
              <Input
                label="Headline"
                placeholder="e.g. Computer Science Graduate | Aspiring Frontend Dev"
                value={headline}
                onChange={(e) => setHeadline(e.target.value)}
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                label="Phone Number"
                type="tel"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="+1 (555) 012-3456"
              />
              <Input
                label="Location"
                value={location}
                onChange={(e) => setLocation(e.target.value)}
                placeholder="City, Country"
              />
            </div>

            <Input
              label="Degree & Institution"
              required
              value={education}
              onChange={(e) => setEducation(e.target.value)}
              placeholder="e.g. B.Tech Computer Science, Stanford University (2025)"
              helperText="Required by recruiters to verify 0–2 years early-career eligibility"
            />

            <Textarea
              label="Bio / Summary"
              rows={3}
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              placeholder="Brief summary of your academic projects, passion areas, and career focus..."
            />

            {/* Skills Tag Input */}
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-semibold text-slate-text uppercase tracking-wider font-sans">
                Technical & Domain Skills <span className="text-status-rejected">*</span>
              </label>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={skillInput}
                  onChange={(e) => setSkillInput(e.target.value)}
                  onKeyDown={handleAddSkill}
                  placeholder="Type skill (e.g. React, Java, SQL) and press Enter"
                  className="w-full min-h-[44px] px-3.5 py-2 text-sm bg-white border border-outline-warm rounded text-slate-text focus:outline-none focus:ring-2 focus:ring-primary"
                />
                <Button
                  type="button"
                  variant="outline"
                  size="md"
                  onClick={handleAddSkill}
                  className="shrink-0"
                >
                  Add Skill
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
                      aria-label={`Remove ${skill}`}
                      className="hover:text-status-rejected"
                    >
                      <span className="material-symbols-outlined !text-[14px]">close</span>
                    </button>
                  </span>
                ))}
              </div>
            </div>

            <div className="pt-4 border-t border-outline-warm flex justify-end">
              <Button type="submit" variant="primary" size="md" isLoading={isSaving}>
                Save Profile Changes
              </Button>
            </div>
          </form>
        </CardBody>
      </Card>
    </div>
  );
};
