import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { adminApi, type AuditLogEntry } from '../../api/admin';
import type { AuthUser } from '../../types/api';
import { Button } from '../../components/ui/Button';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { Alert } from '../../components/ui/Alert';

export const AdminDashboardPage: React.FC = () => {
  const [users, setUsers] = useState<AuthUser[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLogEntry[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  useEffect(() => {
    const loadAdminData = async () => {
      setIsLoading(true);
      try {
        const [usersRes, auditRes] = await Promise.allSettled([
          adminApi.getUsers(0, 5),
          adminApi.getAuditLogs(0, 5),
        ]);

        if (usersRes.status === 'fulfilled' && usersRes.value?.content) {
          setUsers(usersRes.value.content);
        }
        if (auditRes.status === 'fulfilled' && auditRes.value?.content) {
          setAuditLogs(auditRes.value.content);
        }
      } finally {
        setIsLoading(false);
      }
    };
    loadAdminData();
  }, []);

  const handleToggleUserStatus = async (user: AuthUser) => {
    try {
      const updated = await adminApi.updateUserStatus(user.id, !user.isActive);
      setActionMessage(`User #${user.id} status updated to ${updated.isActive ? 'Active' : 'Inactive'}.`);
      setUsers((prev) => prev.map((u) => (u.id === user.id ? { ...u, isActive: updated.isActive } : u)));
    } catch {
      alert('Failed to update user status.');
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-headline font-bold text-2xl text-slate-text">System Administration</h1>
        <p className="text-xs text-slate-muted mt-0.5">
          Platform oversight, user administration, and security audit log monitoring
        </p>
      </div>

      {actionMessage && (
        <Alert variant="info" onClose={() => setActionMessage(null)}>
          {actionMessage}
        </Alert>
      )}

      {/* Metrics Row */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card>
          <CardBody className="p-4 sm:p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-slate-muted uppercase tracking-wider">
                Total Platform Users
              </p>
              <h2 className="font-headline font-bold text-2xl text-slate-text mt-1">
                {isLoading ? <Skeleton width="40px" height="28px" /> : users.length}
              </h2>
            </div>
            <div className="w-10 h-10 rounded bg-primary-tint border border-[#A7F3D0] text-primary flex items-center justify-center">
              <span className="material-symbols-outlined !text-xl">manage_accounts</span>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-4 sm:p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-slate-muted uppercase tracking-wider">
                Audit Trail Events
              </p>
              <h2 className="font-headline font-bold text-2xl text-slate-text mt-1">
                {isLoading ? <Skeleton width="40px" height="28px" /> : auditLogs.length}
              </h2>
            </div>
            <div className="w-10 h-10 rounded bg-secondary-container text-secondary-dark flex items-center justify-center">
              <span className="material-symbols-outlined !text-xl">receipt_long</span>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-4 sm:p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-slate-muted uppercase tracking-wider">
                System Health
              </p>
              <h2 className="font-headline font-bold text-xl text-primary mt-1">Operational</h2>
            </div>
            <div className="w-10 h-10 rounded bg-primary-tint border border-[#A7F3D0] text-primary flex items-center justify-center">
              <span className="material-symbols-outlined !text-xl">verified</span>
            </div>
          </CardBody>
        </Card>
      </div>

      {/* Tables: User Management & Audit Log */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Users Table */}
        <Card>
          <CardHeader className="flex items-center justify-between">
            <div>
              <h3 className="font-headline font-semibold text-base text-slate-text">User Accounts</h3>
              <p className="text-xs text-slate-muted">Candidate, Recruiter, and Admin records</p>
            </div>
            <Link to="/admin/users">
              <Button variant="ghost" size="sm">
                View All
              </Button>
            </Link>
          </CardHeader>
          <CardBody className="p-0">
            {isLoading ? (
              <div className="p-4 space-y-3">
                <Skeleton height="36px" />
                <Skeleton height="36px" />
              </div>
            ) : users.length === 0 ? (
              <div className="p-8 text-center text-xs text-slate-muted">No users found.</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs sm:text-sm">
                  <thead className="bg-[#FAFAF8] text-slate-muted text-[11px] uppercase tracking-wider border-b border-outline-warm">
                    <tr>
                      <th className="px-4 py-3">User</th>
                      <th className="px-4 py-3">Role</th>
                      <th className="px-4 py-3">Status</th>
                      <th className="px-4 py-3 text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-warm/70">
                    {users.map((u) => (
                      <tr key={u.id} className="hover:bg-[#F9FAF9]">
                        <td className="px-4 py-3">
                          <div className="font-medium text-slate-text">{u.email}</div>
                          <div className="text-[10px] text-slate-muted">ID: {u.id}</div>
                        </td>
                        <td className="px-4 py-3">
                          <span className="font-mono text-xs px-2 py-0.5 rounded bg-[#F2F5F3] text-slate-text border border-outline-warm">
                            {u.role}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={`inline-flex items-center gap-1 text-xs font-semibold px-2 py-0.5 rounded ${
                              u.isActive
                                ? 'bg-status-offered-bg text-status-offered'
                                : 'bg-status-rejected-bg text-status-rejected'
                            }`}
                          >
                            <span className="w-1.5 h-1.5 rounded-full bg-current" />
                            {u.isActive ? 'Active' : 'Disabled'}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-right">
                          <button
                            onClick={() => handleToggleUserStatus(u)}
                            className="text-xs text-slate-muted hover:text-primary underline font-medium"
                          >
                            {u.isActive ? 'Deactivate' : 'Activate'}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardBody>
        </Card>

        {/* Audit Log Table */}
        <Card>
          <CardHeader className="flex items-center justify-between">
            <div>
              <h3 className="font-headline font-semibold text-base text-slate-text">
                Audit Trail Log
              </h3>
              <p className="text-xs text-slate-muted">Immutable system mutations</p>
            </div>
            <Link to="/admin/audit">
              <Button variant="ghost" size="sm">
                View All
              </Button>
            </Link>
          </CardHeader>
          <CardBody className="p-0">
            {isLoading ? (
              <div className="p-4 space-y-3">
                <Skeleton height="36px" />
                <Skeleton height="36px" />
              </div>
            ) : auditLogs.length === 0 ? (
              <div className="p-8 text-center text-xs text-slate-muted">
                No recent audit log entries.
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs sm:text-sm">
                  <thead className="bg-[#FAFAF8] text-slate-muted text-[11px] uppercase tracking-wider border-b border-outline-warm">
                    <tr>
                      <th className="px-4 py-3">Action</th>
                      <th className="px-4 py-3">Entity</th>
                      <th className="px-4 py-3">Timestamp</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-warm/70">
                    {auditLogs.map((log) => (
                      <tr key={log.id} className="hover:bg-[#F9FAF9]">
                        <td className="px-4 py-3 font-mono text-xs font-semibold text-slate-text">
                          {log.action}
                        </td>
                        <td className="px-4 py-3 text-xs text-slate-muted">
                          {log.entityType} #{log.entityId}
                        </td>
                        <td className="px-4 py-3 text-xs text-slate-muted whitespace-nowrap">
                          {new Date(log.createdAt).toLocaleTimeString()}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardBody>
        </Card>
      </div>
    </div>
  );
};
