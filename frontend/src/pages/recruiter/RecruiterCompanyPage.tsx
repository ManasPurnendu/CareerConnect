import React, { useState, useEffect } from 'react';
import { recruiterApi } from '../../api/recruiter';
import type { RecruiterCompanyResponseDto } from '../../types/api';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Textarea } from '../../components/ui/Textarea';
import { Select } from '../../components/ui/Select';
import { Alert } from '../../components/ui/Alert';
import { Skeleton } from '../../components/ui/Skeleton';
import { ApiClientError } from '../../api/client';

export const RecruiterCompanyPage: React.FC = () => {
  const [company, setCompany] = useState<RecruiterCompanyResponseDto | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [isUnconfigured, setIsUnconfigured] = useState<boolean>(false);
  const [isEditing, setIsEditing] = useState<boolean>(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Form fields
  const [name, setName] = useState('');
  const [industry, setIndustry] = useState('');
  const [location, setLocation] = useState('');
  const [website, setWebsite] = useState('');
  const [companySize, setCompanySize] = useState('51-200');
  const [logo, setLogo] = useState('');
  const [description, setDescription] = useState('');

  const companySizeOptions = [
    { value: '1-10', label: '1–10 employees (Seed Startup)' },
    { value: '11-50', label: '11–50 employees (Early Growth)' },
    { value: '51-200', label: '51–200 employees (Mid-Size)' },
    { value: '201-500', label: '201–500 employees (Large)' },
    { value: '501-1000', label: '501–1,000 employees (Enterprise)' },
    { value: '1000+', label: '1,000+ employees (Global Enterprise)' },
  ];

  const fetchCompany = async () => {
    setIsLoading(true);
    setFeedback(null);
    try {
      const data = await recruiterApi.getCompany();
      setCompany(data);
      setName(data.name || '');
      setIndustry(data.industry || '');
      setLocation(data.location || '');
      setWebsite(data.website || '');
      setCompanySize(data.companySize || '51-200');
      setLogo(data.logo || '');
      setDescription(data.description || '');
      setIsUnconfigured(false);
      setIsEditing(false);
    } catch (err: unknown) {
      const isNotFound =
        (err instanceof ApiClientError && (err.status === 404 || err.errorCode === 'RESOURCE_NOT_FOUND')) ||
        (typeof err === 'object' && err !== null && ((err as any).status === 404 || (err as any).errorCode === 'RESOURCE_NOT_FOUND' || (err as any).message?.toLowerCase().includes('not found')));

      if (isNotFound) {
        setIsUnconfigured(true);
        setCompany(null);
        setIsEditing(true);
      } else {
        setFeedback({
          type: 'error',
          message: err instanceof Error ? err.message : 'Unable to load company profile.',
        });
      }
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchCompany();
  }, []);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setFeedback({ type: 'error', message: 'Company name is required.' });
      return;
    }

    setIsSaving(true);
    setFeedback(null);

    try {
      const payload = {
        name: name.trim(),
        industry: industry.trim() || undefined,
        location: location.trim() || undefined,
        website: website.trim() || undefined,
        companySize: companySize || undefined,
        logo: logo.trim() || undefined,
        description: description.trim() || undefined,
      };

      const updated = await recruiterApi.upsertCompany(payload);
      setCompany(updated);
      setIsUnconfigured(false);
      setIsEditing(false);
      setFeedback({
        type: 'success',
        message: isUnconfigured
          ? 'Company profile established successfully. You can now publish job openings.'
          : 'Company profile updated successfully.',
      });
    } catch (err: unknown) {
      setFeedback({
        type: 'error',
        message: err instanceof Error ? err.message : 'Failed to save company profile.',
      });
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return (
      <div className="space-y-6 max-w-3xl">
        <div className="space-y-2">
          <Skeleton height="32px" width="240px" />
          <Skeleton height="16px" width="360px" />
        </div>
        <Card>
          <CardBody className="p-6 space-y-4">
            <Skeleton height="40px" />
            <Skeleton height="40px" />
            <Skeleton height="100px" />
          </CardBody>
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-3xl">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="font-headline font-bold text-2xl text-slate-text">
            {isUnconfigured ? 'Set Up Company Profile' : 'Company Profile'}
          </h1>
          <p className="text-xs text-slate-muted mt-0.5">
            {isUnconfigured
              ? 'Associate your recruiter account with an organization to start posting roles.'
              : 'Manage your organization’s identity, hiring details, and early-career branding.'}
          </p>
        </div>

        {!isUnconfigured && !isEditing && (
          <Button
            variant="outline"
            size="md"
            onClick={() => setIsEditing(true)}
            leftIcon={<span className="material-symbols-outlined !text-base">edit</span>}
          >
            Edit Profile
          </Button>
        )}
      </div>

      {feedback && (
        <Alert variant={feedback.type} onClose={() => setFeedback(null)}>
          {feedback.message}
        </Alert>
      )}

      {isUnconfigured && (
        <div className="p-4 rounded border border-[#FED7AA] bg-[#FFF7ED] text-xs text-[#9A3412] flex items-start gap-3">
          <span className="material-symbols-outlined !text-xl text-[#EA580C] shrink-0 mt-0.5">
            domain_add
          </span>
          <div>
            <span className="font-semibold block mb-0.5">Company Workspace Required</span>
            To publish jobs and evaluate applicants, you must establish your company profile. Recruiters
            at your organization share this company profile.
          </div>
        </div>
      )}

      {/* Main Card: View or Edit Mode */}
      <Card>
        <CardHeader className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded bg-primary-tint border border-[#A7F3D0] text-primary flex items-center justify-center font-bold text-base uppercase">
              {name ? name.substring(0, 2) : 'CO'}
            </div>
            <div>
              <h2 className="font-headline font-semibold text-base text-slate-text">
                {name || 'New Organization'}
              </h2>
              <span className="text-[11px] font-mono text-slate-muted">
                {company?.id ? `Company ID: #${company.id}` : 'Unregistered Organization'}
              </span>
            </div>
          </div>
        </CardHeader>

        <CardBody className="p-5 sm:p-6">
          {isEditing ? (
            <form onSubmit={handleSave} className="space-y-4">
              <Input
                label="Company Name"
                required
                maxLength={150}
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Acme Technologies"
              />

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <Input
                  label="Industry"
                  maxLength={100}
                  value={industry}
                  onChange={(e) => setIndustry(e.target.value)}
                  placeholder="e.g. Enterprise Software, FinTech"
                />

                <Input
                  label="Location / Headquarters"
                  maxLength={150}
                  value={location}
                  onChange={(e) => setLocation(e.target.value)}
                  placeholder="e.g. Bengaluru, India"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <Input
                  label="Website URL"
                  type="url"
                  maxLength={255}
                  value={website}
                  onChange={(e) => setWebsite(e.target.value)}
                  placeholder="https://example.com"
                />

                <Select
                  label="Company Size"
                  value={companySize}
                  onChange={(e) => setCompanySize(e.target.value)}
                  options={companySizeOptions}
                />
              </div>

              <Input
                label="Logo Image URL (Optional)"
                type="url"
                maxLength={500}
                value={logo}
                onChange={(e) => setLogo(e.target.value)}
                placeholder="https://example.com/logo.png"
              />

              <Textarea
                label="Company Description & Culture"
                maxLength={5000}
                rows={5}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Describe your organization, early-career culture, mentorship programs, and technology stack..."
              />

              <div className="pt-4 border-t border-outline-warm flex items-center justify-end gap-3">
                {!isUnconfigured && (
                  <Button
                    type="button"
                    variant="outline"
                    size="md"
                    onClick={() => {
                      setIsEditing(false);
                      setFeedback(null);
                    }}
                    disabled={isSaving}
                  >
                    Cancel
                  </Button>
                )}
                <Button type="submit" variant="primary" size="md" isLoading={isSaving}>
                  {isUnconfigured ? 'Create Company Profile' : 'Save Changes'}
                </Button>
              </div>
            </form>
          ) : (
            <div className="space-y-6">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block">
                    Industry
                  </span>
                  <span className="text-sm font-medium text-slate-text mt-0.5 block">
                    {company?.industry || 'Not specified'}
                  </span>
                </div>

                <div>
                  <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block">
                    Headquarters
                  </span>
                  <span className="text-sm font-medium text-slate-text mt-0.5 block">
                    {company?.location || 'Not specified'}
                  </span>
                </div>

                <div>
                  <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block">
                    Website
                  </span>
                  {company?.website ? (
                    <a
                      href={company.website}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-sm font-medium text-primary hover:underline mt-0.5 flex items-center gap-1"
                    >
                      <span>{company.website}</span>
                      <span className="material-symbols-outlined !text-sm">open_in_new</span>
                    </a>
                  ) : (
                    <span className="text-sm text-slate-muted mt-0.5 block">Not specified</span>
                  )}
                </div>

                <div>
                  <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block">
                    Company Size
                  </span>
                  <span className="text-sm font-medium text-slate-text mt-0.5 block">
                    {company?.companySize
                      ? companySizeOptions.find((o) => o.value === company.companySize)?.label || company.companySize
                      : 'Not specified'}
                  </span>
                </div>
              </div>

              {company?.description && (
                <div className="pt-4 border-t border-outline-warm">
                  <span className="text-[11px] font-semibold text-slate-muted uppercase tracking-wider block mb-1.5">
                    About the Organization
                  </span>
                  <p className="text-sm text-slate-text leading-relaxed whitespace-pre-wrap">
                    {company.description}
                  </p>
                </div>
              )}

              {company?.createdAt && (
                <div className="pt-4 border-t border-outline-warm text-[11px] font-mono text-slate-muted flex items-center justify-between">
                  <span>Profile established: {new Date(company.createdAt).toLocaleDateString()}</span>
                  {company.updatedAt && (
                    <span>Last updated: {new Date(company.updatedAt).toLocaleDateString()}</span>
                  )}
                </div>
              )}
            </div>
          )}
        </CardBody>
      </Card>
    </div>
  );
};
