import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { adminApi } from '../../api/admin';
import type { AdminDashboardMetrics } from '../../types/api';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';

export const AdminDashboardPage: React.FC = () => {
  const [metrics, setMetrics] = useState<AdminDashboardMetrics | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchMetrics = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await adminApi.getDashboard();
      setMetrics(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to load administrative metrics.';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchMetrics();
  }, []);

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="font-headline font-bold text-2xl text-slate-text">System Administration</h1>
          <p className="text-xs text-slate-muted mt-0.5">
            Platform governance, user moderation, entity lifecycles, and security ledger inspection
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={fetchMetrics}
          disabled={isLoading}
          className="self-start sm:self-auto min-h-[44px]"
        >
          <span className="material-symbols-outlined !text-base mr-1.5">refresh</span>
          Refresh Metrics
        </Button>
      </div>

      {error && (
        <Alert variant="error" onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {/* Top 4 Summary Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Total Users */}
        <Card className="hover:border-primary/40 transition-colors">
          <CardBody className="p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-semibold text-slate-muted uppercase tracking-wider">Total Users</p>
              <h2 className="font-headline font-bold text-2xl text-slate-text mt-1">
                {isLoading ? <Skeleton width="50px" height="28px" /> : (metrics?.users.total ?? 0)}
              </h2>
              <p className="text-[11px] text-slate-muted mt-1">
                {metrics ? `${metrics.users.candidates} candidates · ${metrics.users.recruiters} recruiters` : ''}
              </p>
            </div>
            <div className="w-11 h-11 rounded-lg bg-primary-tint border border-primary/20 text-primary flex items-center justify-center shrink-0">
              <span className="material-symbols-outlined !text-2xl">manage_accounts</span>
            </div>
          </CardBody>
        </Card>

        {/* Registered Companies */}
        <Card className="hover:border-primary/40 transition-colors">
          <CardBody className="p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-semibold text-slate-muted uppercase tracking-wider">Companies</p>
              <h2 className="font-headline font-bold text-2xl text-slate-text mt-1">
                {isLoading ? <Skeleton width="50px" height="28px" /> : (metrics?.companies.total ?? 0)}
              </h2>
              <p className="text-[11px] text-slate-muted mt-1">
                {metrics ? `${metrics.companies.active} active · ${metrics.companies.inactive} inactive` : ''}
              </p>
            </div>
            <div className="w-11 h-11 rounded-lg bg-[#E0F2FE] border border-[#BAE6FD] text-[#0284C7] flex items-center justify-center shrink-0">
              <span className="material-symbols-outlined !text-2xl">corporate_fare</span>
            </div>
          </CardBody>
        </Card>

        {/* Total Job Openings */}
        <Card className="hover:border-primary/40 transition-colors">
          <CardBody className="p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-semibold text-slate-muted uppercase tracking-wider">Job Openings</p>
              <h2 className="font-headline font-bold text-2xl text-slate-text mt-1">
                {isLoading ? <Skeleton width="50px" height="28px" /> : (metrics?.jobs.total ?? 0)}
              </h2>
              <p className="text-[11px] text-slate-muted mt-1">
                {metrics ? `${metrics.jobs.active} active · ${metrics.jobs.draft} draft · ${metrics.jobs.archived} archived` : ''}
              </p>
            </div>
            <div className="w-11 h-11 rounded-lg bg-[#FEF3C7] border border-[#FDE68A] text-[#D97706] flex items-center justify-center shrink-0">
              <span className="material-symbols-outlined !text-2xl">work</span>
            </div>
          </CardBody>
        </Card>

        {/* Total Applications */}
        <Card className="hover:border-primary/40 transition-colors">
          <CardBody className="p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-semibold text-slate-muted uppercase tracking-wider">Applications</p>
              <h2 className="font-headline font-bold text-2xl text-slate-text mt-1">
                {isLoading ? <Skeleton width="50px" height="28px" /> : (metrics?.applications.total ?? 0)}
              </h2>
              <p className="text-[11px] text-slate-muted mt-1">
                {metrics ? `${metrics.applications.underReview} reviewing · ${metrics.applications.selected} selected` : ''}
              </p>
            </div>
            <div className="w-11 h-11 rounded-lg bg-[#EDE9FE] border border-[#DDD6FE] text-[#7C3AED] flex items-center justify-center shrink-0">
              <span className="material-symbols-outlined !text-2xl">description</span>
            </div>
          </CardBody>
        </Card>
      </div>

      {/* Breakdown Details & Governance Matrix */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* User & Company Governance */}
        <Card>
          <CardHeader className="flex items-center justify-between">
            <div>
              <h2 className="font-headline font-semibold text-base text-slate-text">Account & Organization Distribution</h2>
              <p className="text-xs text-slate-muted">Breakdown by roles and moderation active state</p>
            </div>
            <Link
              to="/admin/users"
              className="text-xs font-medium text-primary hover:text-primary-dark transition-colors inline-flex items-center gap-1"
            >
              <span>Manage</span>
              <span className="material-symbols-outlined !text-sm">arrow_forward</span>
            </Link>
          </CardHeader>
          <CardBody className="p-5 space-y-4">
            {isLoading ? (
              <div className="space-y-3">
                <Skeleton height="36px" />
                <Skeleton height="36px" />
                <Skeleton height="36px" />
              </div>
            ) : (
              <div className="space-y-3">
                <div className="p-3 bg-[#FAFAF8] rounded border border-outline-warm flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <span className="material-symbols-outlined text-primary !text-lg">person</span>
                    <span className="text-xs font-medium text-slate-text">Registered Candidates</span>
                  </div>
                  <span className="font-mono text-xs font-bold text-slate-text">{metrics?.users.candidates ?? 0}</span>
                </div>

                <div className="p-3 bg-[#FAFAF8] rounded border border-outline-warm flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <span className="material-symbols-outlined text-primary !text-lg">badge</span>
                    <span className="text-xs font-medium text-slate-text">Registered Recruiters</span>
                  </div>
                  <span className="font-mono text-xs font-bold text-slate-text">{metrics?.users.recruiters ?? 0}</span>
                </div>

                <div className="p-3 bg-[#FAFAF8] rounded border border-outline-warm flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <span className="material-symbols-outlined text-status-offered !text-lg">check_circle</span>
                    <span className="text-xs font-medium text-slate-text">Active Accounts</span>
                  </div>
                  <span className="font-mono text-xs font-bold text-status-offered">{metrics?.users.active ?? 0}</span>
                </div>

                <div className="p-3 bg-[#FAFAF8] rounded border border-outline-warm flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <span className="material-symbols-outlined text-status-rejected !text-lg">block</span>
                    <span className="text-xs font-medium text-slate-text">Deactivated Accounts</span>
                  </div>
                  <span className="font-mono text-xs font-bold text-status-rejected">{metrics?.users.inactive ?? 0}</span>
                </div>
              </div>
            )}
          </CardBody>
        </Card>

        {/* Job Lifecycle & Application Breakdown */}
        <Card>
          <CardHeader className="flex items-center justify-between">
            <div>
              <h2 className="font-headline font-semibold text-base text-slate-text">Pipeline & Moderation Health</h2>
              <p className="text-xs text-slate-muted">Posting status breakdown and submission funnel</p>
            </div>
            <Link
              to="/admin/jobs"
              className="text-xs font-medium text-primary hover:text-primary-dark transition-colors inline-flex items-center gap-1"
            >
              <span>Jobs</span>
              <span className="material-symbols-outlined !text-sm">arrow_forward</span>
            </Link>
          </CardHeader>
          <CardBody className="p-5 space-y-4">
            {isLoading ? (
              <div className="space-y-3">
                <Skeleton height="36px" />
                <Skeleton height="36px" />
                <Skeleton height="36px" />
              </div>
            ) : (
              <div className="space-y-3">
                <div className="p-3 bg-[#FAFAF8] rounded border border-outline-warm flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <span className="material-symbols-outlined text-primary !text-lg">check_circle</span>
                    <span className="text-xs font-medium text-slate-text">Active Job Postings</span>
                  </div>
                  <span className="font-mono text-xs font-bold text-primary">{metrics?.jobs.active ?? 0}</span>
                </div>

                <div className="p-3 bg-[#FAFAF8] rounded border border-outline-warm flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <span className="material-symbols-outlined text-slate-muted !text-lg">archive</span>
                    <span className="text-xs font-medium text-slate-text">Archived Postings (Moderated)</span>
                  </div>
                  <span className="font-mono text-xs font-bold text-slate-text">{metrics?.jobs.archived ?? 0}</span>
                </div>

                <div className="p-3 bg-[#FAFAF8] rounded border border-outline-warm flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <span className="material-symbols-outlined text-primary !text-lg">trending_up</span>
                    <span className="text-xs font-medium text-slate-text">Applications Under Review</span>
                  </div>
                  <span className="font-mono text-xs font-bold text-slate-text">{metrics?.applications.underReview ?? 0}</span>
                </div>

                <div className="p-3 bg-[#FAFAF8] rounded border border-outline-warm flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <span className="material-symbols-outlined text-status-offered !text-lg">celebration</span>
                    <span className="text-xs font-medium text-slate-text">Selected Offers</span>
                  </div>
                  <span className="font-mono text-xs font-bold text-status-offered">{metrics?.applications.selected ?? 0}</span>
                </div>
              </div>
            )}
          </CardBody>
        </Card>
      </div>

      {/* Quick Governance Links */}
      <Card>
        <CardHeader>
          <h2 className="font-headline font-semibold text-base text-slate-text">Administrative Modules</h2>
          <p className="text-xs text-slate-muted">Direct navigation to platform inspection and moderation workspaces</p>
        </CardHeader>
        <CardBody className="p-5">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
            <Link
              to="/admin/users"
              className="p-4 rounded border border-outline-warm bg-white hover:border-primary/50 hover:bg-[#F2F5F3] transition-colors flex flex-col items-center text-center group min-h-[44px]"
            >
              <span className="material-symbols-outlined text-primary !text-3xl mb-1.5 group-hover:scale-105 transition-transform">
                manage_accounts
              </span>
              <span className="text-xs font-semibold text-slate-text">Users Moderation</span>
              <span className="text-[10px] text-slate-muted mt-0.5">Activate or deactivate platform accounts</span>
            </Link>

            <Link
              to="/admin/companies"
              className="p-4 rounded border border-outline-warm bg-white hover:border-primary/50 hover:bg-[#F2F5F3] transition-colors flex flex-col items-center text-center group min-h-[44px]"
            >
              <span className="material-symbols-outlined text-primary !text-3xl mb-1.5 group-hover:scale-105 transition-transform">
                corporate_fare
              </span>
              <span className="text-xs font-semibold text-slate-text">Companies</span>
              <span className="text-[10px] text-slate-muted mt-0.5">Moderate employer organizations</span>
            </Link>

            <Link
              to="/admin/jobs"
              className="p-4 rounded border border-outline-warm bg-white hover:border-primary/50 hover:bg-[#F2F5F3] transition-colors flex flex-col items-center text-center group min-h-[44px]"
            >
              <span className="material-symbols-outlined text-primary !text-3xl mb-1.5 group-hover:scale-105 transition-transform">
                work
              </span>
              <span className="text-xs font-semibold text-slate-text">Job Moderation</span>
              <span className="text-[10px] text-slate-muted mt-0.5">Archive and restore postings</span>
            </Link>

            <Link
              to="/admin/applications"
              className="p-4 rounded border border-outline-warm bg-white hover:border-primary/50 hover:bg-[#F2F5F3] transition-colors flex flex-col items-center text-center group min-h-[44px]"
            >
              <span className="material-symbols-outlined text-primary !text-3xl mb-1.5 group-hover:scale-105 transition-transform">
                description
              </span>
              <span className="text-xs font-semibold text-slate-text">Applications</span>
              <span className="text-[10px] text-slate-muted mt-0.5">Inspect submission snapshots & history</span>
            </Link>

            <Link
              to="/admin/audit-logs"
              className="p-4 rounded border border-outline-warm bg-white hover:border-primary/50 hover:bg-[#F2F5F3] transition-colors flex flex-col items-center text-center group min-h-[44px]"
            >
              <span className="material-symbols-outlined text-primary !text-3xl mb-1.5 group-hover:scale-105 transition-transform">
                receipt_long
              </span>
              <span className="text-xs font-semibold text-slate-text">Audit Logs</span>
              <span className="text-[10px] text-slate-muted mt-0.5">Inspect immutable security ledger</span>
            </Link>
          </div>
        </CardBody>
      </Card>
    </div>
  );
};
