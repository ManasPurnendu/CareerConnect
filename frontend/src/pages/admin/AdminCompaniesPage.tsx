import React from 'react';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';

export const AdminCompaniesPage: React.FC = () => {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-headline font-bold text-2xl text-slate-text">Company Oversight</h1>
        <p className="text-xs text-slate-muted mt-0.5">Verification and management of registered employers</p>
      </div>

      <Card>
        <CardHeader>
          <h2 className="font-headline font-semibold text-base text-slate-text">Registered Companies</h2>
        </CardHeader>
        <CardBody className="p-8 text-center text-xs text-slate-muted">
          All employer accounts must be verified by administrators prior to public job publishing.
        </CardBody>
      </Card>
    </div>
  );
};
