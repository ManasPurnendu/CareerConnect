import React, { useState, useEffect } from 'react';
import { adminApi } from '../../api/admin';
import type { AuthUser } from '../../types/api';
import { Pagination } from '../../components/ui/Pagination';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { Alert } from '../../components/ui/Alert';

export const AdminUsersPage: React.FC = () => {
  const [users, setUsers] = useState<AuthUser[]>([]);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [notification, setNotification] = useState<string | null>(null);

  useEffect(() => {
    const fetchUsers = async () => {
      setIsLoading(true);
      try {
        const response = await adminApi.getUsers(page, 15);
        if (response?.content) {
          setUsers(response.content);
          setTotalPages(response.page.totalPages);
        }
      } finally {
        setIsLoading(false);
      }
    };
    fetchUsers();
  }, [page]);

  const handleToggle = async (user: AuthUser) => {
    try {
      const updated = await adminApi.updateUserStatus(user.id, !user.isActive);
      setNotification(`User #${user.id} status changed to ${updated.isActive ? 'Active' : 'Deactivated'}.`);
      setUsers((prev) => prev.map((u) => (u.id === user.id ? { ...u, isActive: updated.isActive } : u)));
    } catch {
      alert('Unable to toggle status.');
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-headline font-bold text-2xl text-slate-text">User Administration</h1>
        <p className="text-xs text-slate-muted mt-0.5">Manage user credentials and active statuses</p>
      </div>

      {notification && (
        <Alert variant="info" onClose={() => setNotification(null)}>
          {notification}
        </Alert>
      )}

      <Card>
        <CardHeader>
          <h2 className="font-headline font-semibold text-base text-slate-text">Platform Accounts</h2>
        </CardHeader>
        <CardBody className="p-0">
          {isLoading ? (
            <div className="p-4 space-y-3">
              <Skeleton height="40px" />
              <Skeleton height="40px" />
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs sm:text-sm">
                <thead className="bg-[#FAFAF8] text-slate-muted text-[11px] uppercase tracking-wider border-b border-outline-warm">
                  <tr>
                    <th className="px-4 py-3">ID</th>
                    <th className="px-4 py-3">Email Address</th>
                    <th className="px-4 py-3">Role</th>
                    <th className="px-4 py-3">Active Status</th>
                    <th className="px-4 py-3 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-warm/70">
                  {users.map((u) => (
                    <tr key={u.id} className="hover:bg-[#F9FAF9]">
                      <td className="px-4 py-3.5 font-mono text-xs text-slate-muted">#{u.id}</td>
                      <td className="px-4 py-3.5 font-medium text-slate-text">{u.email}</td>
                      <td className="px-4 py-3.5">
                        <span className="font-mono text-xs px-2 py-0.5 rounded bg-[#F2F5F3] text-slate-text border border-outline-warm">
                          {u.role}
                        </span>
                      </td>
                      <td className="px-4 py-3.5">
                        <span
                          className={`inline-flex items-center gap-1 text-xs font-semibold px-2 py-0.5 rounded ${
                            u.isActive
                              ? 'bg-status-offered-bg text-status-offered'
                              : 'bg-status-rejected-bg text-status-rejected'
                          }`}
                        >
                          {u.isActive ? 'Active' : 'Disabled'}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-right">
                        <button
                          onClick={() => handleToggle(u)}
                          className="text-xs text-primary underline font-medium hover:text-primary-dark"
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
