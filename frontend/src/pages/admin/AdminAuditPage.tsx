import React, { useState, useEffect } from 'react';
import { adminApi, type AuditLogEntry } from '../../api/admin';
import { Pagination } from '../../components/ui/Pagination';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';

export const AdminAuditPage: React.FC = () => {
  const [logs, setLogs] = useState<AuditLogEntry[]>([]);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    const fetchLogs = async () => {
      setIsLoading(true);
      try {
        const response = await adminApi.getAuditLogs(page, 20);
        if (response?.content) {
          setLogs(response.content);
          setTotalPages(response.page?.totalPages ?? 1);
        }
      } finally {
        setIsLoading(false);
      }
    };
    fetchLogs();
  }, [page]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-headline font-bold text-2xl text-slate-text">Audit Log Trail</h1>
        <p className="text-xs text-slate-muted mt-0.5">
          Immutable ledger of security actions, user transitions, and status updates
        </p>
      </div>

      <Card>
        <CardHeader>
          <h2 className="font-headline font-semibold text-base text-slate-text">Audit Events</h2>
        </CardHeader>
        <CardBody className="p-0">
          {isLoading ? (
            <div className="p-4 space-y-3">
              <Skeleton height="40px" />
              <Skeleton height="40px" />
            </div>
          ) : logs.length === 0 ? (
            <div className="p-8 text-center text-xs text-slate-muted">No audit events recorded.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs sm:text-sm">
                <thead className="bg-[#FAFAF8] text-slate-muted text-[11px] uppercase tracking-wider border-b border-outline-warm">
                  <tr>
                    <th className="px-4 py-3">Event ID</th>
                    <th className="px-4 py-3">Actor ID</th>
                    <th className="px-4 py-3">Action</th>
                    <th className="px-4 py-3">Entity Target</th>
                    <th className="px-4 py-3">Timestamp</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-warm/70">
                  {logs.map((log) => (
                    <tr key={log.id} className="hover:bg-[#F9FAF9]">
                      <td className="px-4 py-3 font-mono text-xs text-slate-muted">#{log.id}</td>
                      <td className="px-4 py-3 font-mono text-xs">User #{log.actorId}</td>
                      <td className="px-4 py-3 font-mono text-xs font-semibold text-primary">
                        {log.action}
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-text">
                        {log.entityType} #{log.entityId}
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-muted whitespace-nowrap">
                        {new Date(log.createdAt).toLocaleString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardBody>
      </Card>

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
