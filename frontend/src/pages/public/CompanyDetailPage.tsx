import React from 'react';
import { useParams, Link } from 'react-router-dom';
import { Breadcrumb } from '../../components/ui/Breadcrumb';
import { Button } from '../../components/ui/Button';

export const CompanyDetailPage: React.FC = () => {
  const { companyId } = useParams<{ companyId: string }>();

  return (
    <div className="layout-container py-8 sm:py-10 max-w-4xl mx-auto space-y-6">
      <Breadcrumb
        items={[
          { label: 'Home', path: '/' },
          { label: 'Companies', path: '/companies' },
          { label: `Company #${companyId}` },
        ]}
      />

      <div className="bg-white rounded border border-outline-warm p-6 sm:p-8 shadow-subtle space-y-4">
        <h1 className="font-headline font-bold text-2xl text-slate-text">Company Profile</h1>
        <p className="text-sm text-slate-muted">
          Viewing company #{companyId}. Company postings and details are verified by CareerConnect.
        </p>
        <div className="pt-4 border-t border-outline-warm flex items-center justify-between">
          <Link to="/companies">
            <Button variant="outline" size="sm">
              Back to Companies
            </Button>
          </Link>
          <Link to={`/jobs`}>
            <Button variant="primary" size="sm">
              View Active Jobs
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
};
