import React, { useState, useEffect } from 'react';
import { recruiterApi } from '../../api/recruiter';
import type { Company } from '../../types/api';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Textarea } from '../../components/ui/Textarea';
import { Alert } from '../../components/ui/Alert';
import { Skeleton } from '../../components/ui/Skeleton';

export const RecruiterCompanyPage: React.FC = () => {
  const [company, setCompany] = useState<Company | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [location, setLocation] = useState('');
  const [website, setWebsite] = useState('');
  const [industry, setIndustry] = useState('');

  useEffect(() => {
    const fetchCompany = async () => {
      setIsLoading(true);
      try {
        const data = await recruiterApi.getCompany();
        setCompany(data);
        setName(data.name || '');
        setDescription(data.description || '');
        setLocation(data.location || '');
        setWebsite(data.website || '');
        setIndustry(data.industry || '');
      } catch {
        setFeedback({ type: 'error', message: 'Unable to load company profile.' });
      } finally {
        setIsLoading(false);
      }
    };
    fetchCompany();
  }, []);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    setFeedback(null);
    try {
      const updated = await recruiterApi.updateCompany({
        name,
        description,
        location,
        website,
        industry,
      });
      setCompany(updated);
      setFeedback({ type: 'success', message: 'Company details updated successfully.' });
    } catch (err: unknown) {
      setFeedback({
        type: 'error',
        message: err instanceof Error ? err.message : 'Failed to update company.',
      });
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return (
      <div className="space-y-4 max-w-2xl">
        <Skeleton height="32px" width="200px" />
        <Skeleton height="200px" />
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-2xl">
      <div>
        <h1 className="font-headline font-bold text-2xl text-slate-text">Company Profile</h1>
        <p className="text-xs text-slate-muted mt-0.5">
          Manage your organization profile displayed to early-career candidates
        </p>
      </div>

      {feedback && (
        <Alert variant={feedback.type} onClose={() => setFeedback(null)}>
          {feedback.message}
        </Alert>
      )}

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <h2 className="font-headline font-semibold text-base text-slate-text">Organization Details</h2>
            {company?.isVerified && (
              <span className="inline-flex items-center gap-1 text-xs font-semibold text-primary bg-primary-tint px-2 py-0.5 rounded border border-[#A7F3D0]">
                <span className="material-symbols-outlined !text-base">verified</span>
                Verified Employer
              </span>
            )}
          </div>
        </CardHeader>
        <CardBody>
          <form onSubmit={handleSave} className="space-y-4">
            <Input
              label="Company Name"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
            />

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                label="Industry"
                value={industry}
                onChange={(e) => setIndustry(e.target.value)}
                placeholder="e.g. Software, Financial Tech"
              />
              <Input
                label="Location / HQ"
                value={location}
                onChange={(e) => setLocation(e.target.value)}
                placeholder="e.g. San Francisco, CA"
              />
            </div>

            <Input
              label="Website"
              type="url"
              value={website}
              onChange={(e) => setWebsite(e.target.value)}
              placeholder="https://example.com"
            />

            <Textarea
              label="About Company"
              rows={4}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Tell candidates about your company culture, early-career mentorship, and technology stack..."
            />

            <div className="pt-4 border-t border-outline-warm flex justify-end">
              <Button type="submit" variant="primary" size="md" isLoading={isSaving}>
                Save Changes
              </Button>
            </div>
          </form>
        </CardBody>
      </Card>
    </div>
  );
};
