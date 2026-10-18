import React from 'react';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';

export const AdminJobsPage: React.FC = () => {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-headline font-bold text-2xl text-slate-text">Job Postings Oversight</h1>
        <p className="text-xs text-slate-muted mt-0.5">Global audit and monitoring of public opportunities</p>
      </div>

      <Card>
        <CardHeader>
          <h2 className="font-headline font-semibold text-base text-slate-text">Published Openings</h2>
        </CardHeader>
        <CardBody className="p-8 text-center text-xs text-slate-muted">
          All postings conform to the early-career standard (0–2 years experience target).
        </CardBody>
      </Card>
    </div>
  );
};
