import React from 'react';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';

export const CandidateSettingsPage: React.FC = () => {
  return (
    <div className="space-y-6 max-w-3xl">
      <div>
        <h1 className="font-headline font-bold text-2xl text-slate-text">Account Settings</h1>
        <p className="text-xs text-slate-muted mt-0.5">Manage your preferences and security</p>
      </div>

      <Card>
        <CardHeader>
          <h2 className="font-headline font-semibold text-base text-slate-text">Security & Session</h2>
        </CardHeader>
        <CardBody className="p-6 text-sm text-slate-muted">
          Your session uses strictly in-memory authentication tokens. No credentials or refresh tokens are persisted to local storage.
        </CardBody>
      </Card>
    </div>
  );
};
