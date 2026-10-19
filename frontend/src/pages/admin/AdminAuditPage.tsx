import React, { useState, useEffect, useCallback } from 'react';
import { adminApi } from '../../api/admin';
import type { AuditLogDto } from '../../types/api';
import { Pagination } from '../../components/ui/Pagination';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { Alert } from '../../components/ui/Alert';
import { Input } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';

export const AdminAuditPage: React.FC = () => {
  const [logs, setLogs] = useState<AuditLogDto[]>([]);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [pageSize] = useState<number>(20);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Filters
  const [actionFilter, setActionFilter] = useState<string>('ALL');
  const [entityTypeFilter, setEntityTypeFilter] = useState<string>('ALL');
  const [actorUserIdFilter, setActorUserIdFilter] = useState<string>('');
  const [entityIdFilter, setEntityIdFilter] = useState<string>('');

  const fetchLogs = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const parsedActorId = actorUserIdFilter.trim() ? parseInt(actorUserIdFilter.trim(), 10) : undefined;
      const parsedEntityId = entityIdFilter.trim() ? parseInt(entityIdFilter.trim(), 10) : undefined;

      const response = await adminApi.getAuditLogs({
        action: actionFilter !== 'ALL' ? actionFilter : undefined,
        entityType: entityTypeFilter !== 'ALL' ? entityTypeFilter : undefined,
        actorUserId: !isNaN(Number(parsedActorId)) ? parsedActorId : undefined,
        entityId: !isNaN(Number(parsedEntityId)) ? parsedEntityId : undefined,
        page,
        size: pageSize,
      });

      const list = (response?.data && response.data.length > 0)
        ? response.data
        : (response?.items || (response as any)?.content || []);

      setLogs(list);
      setTotalPages(response?.pagination?.totalPages ?? response?.page?.totalPages ?? 1);
      setTotalElements(response?.pagination?.totalElements ?? response?.page?.totalElements ?? list.length);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to load audit logs.';
      setErrorMessage(msg);
    } finally {
      setIsLoading(false);
    }
  }, [page, pageSize, actionFilter, entityTypeFilter, actorUserIdFilter, entityIdFilter]);

  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  const renderMetadata = (metadataStr?: string | null) => {
    if (!metadataStr) return <span className="text-slate-muted italic text-[11px]">None</span>;
    try {
      const parsed = JSON.parse(metadataStr);
      return (
        <div className="font-mono text-[11px] text-slate-text bg-[#FAFAF8] p-1.5 rounded border border-outline-warm max-w-xs break-all">
          {Object.entries(parsed).map(([k, v]) => (
            <div key={k}>
              <span className="text-slate-muted">{k}:</span> <span className="font-semibold">{String(v)}</span>
            </div>
          ))}
        </div>
      );
    } catch {
      return (
        <span className="font-mono text-[11px] text-slate-text bg-[#FAFAF8] px-1.5 py-0.5 rounded border border-outline-warm">
          {metadataStr}
        </span>
      );
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="font-headline font-bold text-2xl text-slate-text">Security Audit Trail</h1>
          <p className="text-xs text-slate-muted mt-0.5">
            Immutable append-only ledger tracking all administrative actions, status transitions, and governance decisions
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={fetchLogs}
          disabled={isLoading}
          className="self-start sm:self-auto min-h-[44px]"
        >
          <span className="material-symbols-outlined !text-base mr-1.5">refresh</span>
          Refresh Ledger
        </Button>
      </div>

      {errorMessage && (
        <Alert variant="error" onClose={() => setErrorMessage(null)}>
          {errorMessage}
        </Alert>
      )}

      {/* Filters Card */}
      <Card>
        <CardBody className="p-4 sm:p-5">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            <div>
              <label htmlFor="audit-action-filter" className="block text-xs font-semibold text-slate-text mb-1">
                Action Type
              </label>
              <select
                id="audit-action-filter"
                aria-label="Action Type Filter"
                value={actionFilter}
                onChange={(e) => {
                  setActionFilter(e.target.value);
                  setPage(0);
                }}
                className="w-full h-11 px-3 text-xs bg-white border border-outline-warm rounded focus:outline-none focus:ring-1 focus:ring-primary"
              >
                <option value="ALL">All Actions</option>
                <option value="USER_DEACTIVATED">USER_DEACTIVATED</option>
                <option value="USER_REACTIVATED">USER_REACTIVATED</option>
                <option value="COMPANY_DEACTIVATED">COMPANY_DEACTIVATED</option>
                <option value="COMPANY_REACTIVATED">COMPANY_REACTIVATED</option>
                <option value="JOB_ARCHIVED">JOB_ARCHIVED</option>
                <option value="JOB_RESTORED">JOB_RESTORED</option>
              </select>
            </div>

            <div>
              <label htmlFor="audit-entity-type-filter" className="block text-xs font-semibold text-slate-text mb-1">
                Target Entity Type
              </label>
              <select
                id="audit-entity-type-filter"
                aria-label="Target Entity Type Filter"
                value={entityTypeFilter}
                onChange={(e) => {
                  setEntityTypeFilter(e.target.value);
                  setPage(0);
                }}
                className="w-full h-11 px-3 text-xs bg-white border border-outline-warm rounded focus:outline-none focus:ring-1 focus:ring-primary"
              >
                <option value="ALL">All Entities</option>
                <option value="USER">USER</option>
                <option value="COMPANY">COMPANY</option>
                <option value="JOB">JOB</option>
              </select>
            </div>

            <div>
              <label htmlFor="audit-actor-id-filter" className="block text-xs font-semibold text-slate-text mb-1">
                Actor User ID
              </label>
              <Input
                id="audit-actor-id-filter"
                type="number"
                placeholder="e.g. 1"
                value={actorUserIdFilter}
                onChange={(e) => {
                  setActorUserIdFilter(e.target.value);
                  setPage(0);
                }}
              />
            </div>

            <div>
              <label htmlFor="audit-entity-id-filter" className="block text-xs font-semibold text-slate-text mb-1">
                Target Entity ID
              </label>
              <Input
                id="audit-entity-id-filter"
                type="number"
                placeholder="e.g. 42"
                value={entityIdFilter}
                onChange={(e) => {
                  setEntityIdFilter(e.target.value);
                  setPage(0);
                }}
              />
            </div>
          </div>
        </CardBody>
      </Card>

      {/* Audit Log Table Card */}
      <Card>
        <CardHeader className="flex items-center justify-between">
          <div>
            <h2 className="font-headline font-semibold text-base text-slate-text">Audit Log Events</h2>
            <p className="text-xs text-slate-muted">
              {totalElements} total {totalElements === 1 ? 'event' : 'events'} recorded in immutable ledger
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
          ) : logs.length === 0 ? (
            <div className="p-12 text-center">
              <span className="material-symbols-outlined text-slate-muted !text-4xl mb-2">receipt_long</span>
              <p className="text-sm font-semibold text-slate-text">No audit log records found</p>
              <p className="text-xs text-slate-muted mt-1">
                Audit entries are generated automatically by the backend whenever administrative actions occur.
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs sm:text-sm">
                <thead className="bg-[#FAFAF8] text-slate-muted text-[11px] uppercase tracking-wider border-b border-outline-warm">
                  <tr>
                    <th className="px-4 py-3">Event ID</th>
                    <th className="px-4 py-3">Actor Admin</th>
                    <th className="px-4 py-3">Action Recorded</th>
                    <th className="px-4 py-3">Target Entity</th>
                    <th className="px-4 py-3">Context Metadata</th>
                    <th className="px-4 py-3">Timestamp (UTC / Local)</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-warm/70">
                  {logs.map((log) => (
                    <tr key={log.id} className="hover:bg-[#F9FAF9] transition-colors">
                      <td className="px-4 py-3.5 font-mono text-xs text-slate-muted">#{log.id}</td>
                      <td className="px-4 py-3.5 font-mono text-xs font-semibold text-slate-text">
                        Admin #{log.actorUserId}
                      </td>
                      <td className="px-4 py-3.5">
                        <span
                          className={`font-mono text-[11px] font-semibold px-2 py-0.5 rounded border ${
                            log.action.includes('DEACTIVAT') || log.action.includes('ARCHIV')
                              ? 'bg-status-rejected-bg text-status-rejected border-status-rejected/30'
                              : 'bg-status-offered-bg text-status-offered border-status-offered/30'
                          }`}
                        >
                          {log.action}
                        </span>
                      </td>
                      <td className="px-4 py-3.5">
                        <span className="font-semibold text-slate-text">{log.entityType}</span>{' '}
                        <span className="font-mono text-slate-muted text-xs">#{log.entityId}</span>
                      </td>
                      <td className="px-4 py-3.5">
                        {renderMetadata(log.metadata)}
                      </td>
                      <td className="px-4 py-3.5 text-xs text-slate-muted whitespace-nowrap">
                        {log.createdAt ? new Date(log.createdAt).toLocaleString() : '—'}
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
    </div>
  );
};
