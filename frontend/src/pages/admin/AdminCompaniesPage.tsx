import React, { useState, useEffect, useCallback } from 'react';
import { adminApi } from '../../api/admin';
import type { AdminCompanyDto } from '../../types/api';
import { Pagination } from '../../components/ui/Pagination';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Modal } from '../../components/ui/Modal';

export const AdminCompaniesPage: React.FC = () => {
  const [companies, setCompanies] = useState<AdminCompanyDto[]>([]);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [pageSize] = useState<number>(15);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [notification, setNotification] = useState<string | null>(null);

  // Filters
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');

  // Confirmation Modal
  const [selectedCompany, setSelectedCompany] = useState<AdminCompanyDto | null>(null);
  const [targetAction, setTargetAction] = useState<'activate' | 'deactivate' | null>(null);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  const fetchCompanies = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const response = await adminApi.getCompanies({
        active: statusFilter === 'ACTIVE' ? true : statusFilter === 'INACTIVE' ? false : undefined,
        q: searchQuery.trim() || undefined,
        page,
        size: pageSize,
      });

      const list = (response?.data && response.data.length > 0)
        ? response.data
        : (response?.items || (response as any)?.content || []);

      setCompanies(list);
      setTotalPages(response?.pagination?.totalPages ?? response?.page?.totalPages ?? 1);
      setTotalElements(response?.pagination?.totalElements ?? response?.page?.totalElements ?? list.length);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to load companies.';
      setErrorMessage(msg);
    } finally {
      setIsLoading(false);
    }
  }, [page, pageSize, statusFilter, searchQuery]);

  useEffect(() => {
    fetchCompanies();
  }, [fetchCompanies]);

  const handleOpenConfirm = (company: AdminCompanyDto, action: 'activate' | 'deactivate') => {
    setSelectedCompany(company);
    setTargetAction(action);
  };

  const handleCloseConfirm = () => {
    setSelectedCompany(null);
    setTargetAction(null);
    setIsSubmitting(false);
  };

  const handleConfirmStatusChange = async () => {
    if (!selectedCompany || !targetAction) return;
    const newActiveState = targetAction === 'activate';

    setIsSubmitting(true);
    setErrorMessage(null);
    try {
      const updated = await adminApi.updateCompanyStatus(selectedCompany.id, newActiveState);
      setNotification(
        `Company ${updated.name} (#${updated.id}) is now ${updated.active ? 'Active' : 'Deactivated'}.`
      );
      setCompanies((prev) => prev.map((c) => (c.id === updated.id ? updated : c)));
      handleCloseConfirm();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to update company active status.';
      setErrorMessage(msg);
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="font-headline font-bold text-2xl text-slate-text">Company Moderation</h1>
        <p className="text-xs text-slate-muted mt-0.5">
          Inspect registered employer organizations and moderate active operational state
        </p>
      </div>

      {notification && (
        <Alert variant="success" onClose={() => setNotification(null)}>
          {notification}
        </Alert>
      )}

      {errorMessage && (
        <Alert variant="error" onClose={() => setErrorMessage(null)}>
          {errorMessage}
        </Alert>
      )}

      {/* Filters Card */}
      <Card>
        <CardBody className="p-4 sm:p-5">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label htmlFor="company-search" className="block text-xs font-semibold text-slate-text mb-1">
                Search
              </label>
              <Input
                id="company-search"
                placeholder="Search by company name or location..."
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  setPage(0);
                }}
              />
            </div>

            <div>
              <label htmlFor="company-status-filter" className="block text-xs font-semibold text-slate-text mb-1">
                Organization Status
              </label>
              <select
                id="company-status-filter"
                aria-label="Organization Status Filter"
                value={statusFilter}
                onChange={(e) => {
                  setStatusFilter(e.target.value);
                  setPage(0);
                }}
                className="w-full h-11 px-3 text-xs bg-white border border-outline-warm rounded focus:outline-none focus:ring-1 focus:ring-primary"
              >
                <option value="ALL">All Statuses</option>
                <option value="ACTIVE">Active Organizations</option>
                <option value="INACTIVE">Deactivated Organizations</option>
              </select>
            </div>
          </div>
        </CardBody>
      </Card>

      {/* Companies Table Card */}
      <Card>
        <CardHeader className="flex items-center justify-between">
          <div>
            <h2 className="font-headline font-semibold text-base text-slate-text">Registered Companies</h2>
            <p className="text-xs text-slate-muted">
              {totalElements} total {totalElements === 1 ? 'organization' : 'organizations'} on platform
            </p>
          </div>
        </CardHeader>
        <CardBody className="p-0">
          {isLoading ? (
            <div className="p-4 space-y-3">
              <Skeleton height="44px" />
              <Skeleton height="44px" />
              <Skeleton height="44px" />
            </div>
          ) : companies.length === 0 ? (
            <div className="p-12 text-center">
              <span className="material-symbols-outlined text-slate-muted !text-4xl mb-2">corporate_fare</span>
              <p className="text-sm font-semibold text-slate-text">No companies found</p>
              <p className="text-xs text-slate-muted mt-1">Try adjusting your search criteria or status filter.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs sm:text-sm">
                <thead className="bg-[#FAFAF8] text-slate-muted text-[11px] uppercase tracking-wider border-b border-outline-warm">
                  <tr>
                    <th className="px-4 py-3">Company ID</th>
                    <th className="px-4 py-3">Name & Website</th>
                    <th className="px-4 py-3">Industry</th>
                    <th className="px-4 py-3">Location</th>
                    <th className="px-4 py-3 text-center">Jobs / Recruiters</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3">Created</th>
                    <th className="px-4 py-3 text-right">Moderation Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-warm/70">
                  {companies.map((c) => (
                    <tr key={c.id} className="hover:bg-[#F9FAF9] transition-colors">
                      <td className="px-4 py-3.5 font-mono text-xs text-slate-muted">#{c.id}</td>
                      <td className="px-4 py-3.5">
                        <div className="font-semibold text-slate-text">{c.name}</div>
                        {c.website && (
                          <a
                            href={c.website.startsWith('http') ? c.website : `https://${c.website}`}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-xs text-primary hover:underline"
                          >
                            {c.website}
                          </a>
                        )}
                      </td>
                      <td className="px-4 py-3.5 text-xs text-slate-text">{c.industry || '—'}</td>
                      <td className="px-4 py-3.5 text-xs text-slate-text">{c.location || '—'}</td>
                      <td className="px-4 py-3.5 text-center font-mono text-xs">
                        <span className="font-semibold text-primary">{c.jobCount}</span>
                        <span className="text-slate-muted"> / </span>
                        <span className="font-semibold text-slate-text">{c.recruiterCount}</span>
                      </td>
                      <td className="px-4 py-3.5">
                        <span
                          className={`inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-0.5 rounded ${
                            c.active
                              ? 'bg-status-offered-bg text-status-offered'
                              : 'bg-status-rejected-bg text-status-rejected'
                          }`}
                        >
                          <span
                            className={`w-1.5 h-1.5 rounded-full ${
                              c.active ? 'bg-status-offered' : 'bg-status-rejected'
                            }`}
                          />
                          {c.active ? 'Active' : 'Deactivated'}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-xs text-slate-muted whitespace-nowrap">
                        {c.createdAt ? new Date(c.createdAt).toLocaleDateString() : '—'}
                      </td>
                      <td className="px-4 py-3.5 text-right">
                        {c.active ? (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleOpenConfirm(c, 'deactivate')}
                            className="text-status-rejected border-status-rejected/30 hover:bg-status-rejected-bg min-h-[44px]"
                            title="Deactivate company"
                          >
                            Deactivate
                          </Button>
                        ) : (
                          <Button
                            variant="primary"
                            size="sm"
                            onClick={() => handleOpenConfirm(c, 'activate')}
                            className="min-h-[44px]"
                            title="Reactivate company"
                          >
                            Activate
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardBody>
      </Card>

      {/* Pagination */}
      {totalPages > 1 && (
        <Pagination
          currentPage={page}
          totalPages={totalPages}
          onPageChange={(newPage) => setPage(newPage)}
        />
      )}

      {/* Confirmation Modal */}
      {selectedCompany && targetAction && (
        <Modal
          isOpen={true}
          onClose={handleCloseConfirm}
          title={targetAction === 'activate' ? 'Confirm Company Reactivation' : 'Confirm Company Deactivation'}
        >
          <div className="space-y-4">
            <p className="text-xs text-slate-text">
              Are you sure you want to {targetAction === 'activate' ? 'reactivate' : 'deactivate'} organization{' '}
              <strong>{selectedCompany.name}</strong> (#{selectedCompany.id})?
            </p>

            {targetAction === 'deactivate' && (
              <div className="p-3 bg-status-rejected-bg text-status-rejected rounded text-xs">
                <strong>Moderation Notice:</strong> Deactivating an employer organization suspends recruiter activities and will be recorded in the security audit ledger.
              </div>
            )}

            <div className="flex justify-end gap-2 pt-2">
              <Button variant="outline" size="sm" onClick={handleCloseConfirm} disabled={isSubmitting} className="min-h-[44px]">
                Cancel
              </Button>
              <Button
                variant={targetAction === 'activate' ? 'primary' : 'danger'}
                size="sm"
                onClick={handleConfirmStatusChange}
                disabled={isSubmitting}
                className="min-h-[44px]"
              >
                {isSubmitting
                  ? 'Processing...'
                  : targetAction === 'activate'
                  ? 'Confirm & Activate'
                  : 'Confirm & Deactivate'}
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
};
