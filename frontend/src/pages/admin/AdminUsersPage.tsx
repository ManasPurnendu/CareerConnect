import React, { useState, useEffect, useCallback } from 'react';
import { adminApi } from '../../api/admin';
import type { AdminUserDto, Role } from '../../types/api';
import { useAuth } from '../../auth/useAuth';
import { Pagination } from '../../components/ui/Pagination';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Modal } from '../../components/ui/Modal';

export const AdminUsersPage: React.FC = () => {
  const { user: currentAdmin } = useAuth();
  const [users, setUsers] = useState<AdminUserDto[]>([]);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [pageSize] = useState<number>(15);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [notification, setNotification] = useState<string | null>(null);

  // Filters
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [roleFilter, setRoleFilter] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');

  // Confirmation Modal State
  const [selectedUser, setSelectedUser] = useState<AdminUserDto | null>(null);
  const [targetAction, setTargetAction] = useState<'activate' | 'deactivate' | null>(null);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  const fetchUsers = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const response = await adminApi.getUsers({
        role: roleFilter !== 'ALL' ? (roleFilter as Role) : undefined,
        active: statusFilter === 'ACTIVE' ? true : statusFilter === 'INACTIVE' ? false : undefined,
        q: searchQuery.trim() || undefined,
        page,
        size: pageSize,
      });

      const list = (response?.data && response.data.length > 0)
        ? response.data
        : (response?.items || (response as any)?.content || []);

      setUsers(list);
      setTotalPages(response?.pagination?.totalPages ?? response?.page?.totalPages ?? 1);
      setTotalElements(response?.pagination?.totalElements ?? response?.page?.totalElements ?? list.length);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to load user accounts.';
      setErrorMessage(msg);
    } finally {
      setIsLoading(false);
    }
  }, [page, pageSize, roleFilter, statusFilter, searchQuery]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const handleOpenConfirm = (target: AdminUserDto, action: 'activate' | 'deactivate') => {
    setSelectedUser(target);
    setTargetAction(action);
  };

  const handleCloseConfirm = () => {
    setSelectedUser(null);
    setTargetAction(null);
    setIsSubmitting(false);
  };

  const handleConfirmStatusChange = async () => {
    if (!selectedUser || !targetAction) return;
    const newActiveState = targetAction === 'activate';

    setIsSubmitting(true);
    setErrorMessage(null);
    try {
      const updated = await adminApi.updateUserStatus(selectedUser.id, newActiveState);
      setNotification(
        `User ${updated.fullName || updated.email} (#${updated.id}) is now ${updated.active ? 'Active' : 'Deactivated'}.`
      );
      setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
      handleCloseConfirm();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to update user active status.';
      setErrorMessage(msg);
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="font-headline font-bold text-2xl text-slate-text">User Administration</h1>
        <p className="text-xs text-slate-muted mt-0.5">
          Inspect platform identities, verify credential associations, and moderate active status
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
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label htmlFor="user-search" className="block text-xs font-semibold text-slate-text mb-1">
                Search
              </label>
              <Input
                id="user-search"
                placeholder="Search by name or email..."
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  setPage(0);
                }}
              />
            </div>

            <div>
              <label htmlFor="role-filter" className="block text-xs font-semibold text-slate-text mb-1">
                Role
              </label>
              <select
                id="role-filter"
                aria-label="Role Filter"
                value={roleFilter}
                onChange={(e) => {
                  setRoleFilter(e.target.value);
                  setPage(0);
                }}
                className="w-full h-11 px-3 text-xs bg-white border border-outline-warm rounded focus:outline-none focus:ring-1 focus:ring-primary"
              >
                <option value="ALL">All Roles</option>
                <option value="CANDIDATE">Candidate</option>
                <option value="RECRUITER">Recruiter</option>
                <option value="ADMIN">Admin</option>
              </select>
            </div>

            <div>
              <label htmlFor="status-filter" className="block text-xs font-semibold text-slate-text mb-1">
                Account Status
              </label>
              <select
                id="status-filter"
                aria-label="Account Status Filter"
                value={statusFilter}
                onChange={(e) => {
                  setStatusFilter(e.target.value);
                  setPage(0);
                }}
                className="w-full h-11 px-3 text-xs bg-white border border-outline-warm rounded focus:outline-none focus:ring-1 focus:ring-primary"
              >
                <option value="ALL">All Statuses</option>
                <option value="ACTIVE">Active Only</option>
                <option value="INACTIVE">Deactivated Only</option>
              </select>
            </div>
          </div>
        </CardBody>
      </Card>

      {/* Users Table Card */}
      <Card>
        <CardHeader className="flex items-center justify-between">
          <div>
            <h2 className="font-headline font-semibold text-base text-slate-text">Registered Accounts</h2>
            <p className="text-xs text-slate-muted">
              {totalElements} total platform {totalElements === 1 ? 'user' : 'users'} found
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
          ) : users.length === 0 ? (
            <div className="p-12 text-center">
              <span className="material-symbols-outlined text-slate-muted !text-4xl mb-2">person_off</span>
              <p className="text-sm font-semibold text-slate-text">No user accounts found</p>
              <p className="text-xs text-slate-muted mt-1">Try adjusting your search criteria or role filters.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs sm:text-sm">
                <thead className="bg-[#FAFAF8] text-slate-muted text-[11px] uppercase tracking-wider border-b border-outline-warm">
                  <tr>
                    <th className="px-4 py-3">User ID</th>
                    <th className="px-4 py-3">Full Name & Email</th>
                    <th className="px-4 py-3">Role</th>
                    <th className="px-4 py-3">Company Affiliation</th>
                    <th className="px-4 py-3">Account Status</th>
                    <th className="px-4 py-3">Registered On</th>
                    <th className="px-4 py-3 text-right">Moderation Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-warm/70">
                  {users.map((u) => {
                    const isSelf = currentAdmin?.id === u.id;
                    return (
                      <tr key={u.id} className="hover:bg-[#F9FAF9] transition-colors">
                        <td className="px-4 py-3.5 font-mono text-xs text-slate-muted">#{u.id}</td>
                        <td className="px-4 py-3.5">
                          <div className="font-semibold text-slate-text">{u.fullName || '—'}</div>
                          <div className="text-xs text-slate-muted">{u.email}</div>
                        </td>
                        <td className="px-4 py-3.5">
                          <span
                            className={`font-mono text-[11px] font-semibold px-2 py-0.5 rounded border ${
                              u.role === 'ADMIN'
                                ? 'bg-secondary-container text-secondary-dark border-secondary/30'
                                : u.role === 'RECRUITER'
                                ? 'bg-[#E0F2FE] text-[#0284C7] border-[#BAE6FD]'
                                : 'bg-[#F2F5F3] text-primary border-primary/20'
                            }`}
                          >
                            {u.role}
                          </span>
                        </td>
                        <td className="px-4 py-3.5 text-xs text-slate-text">
                          {u.companyName ? (
                            <span className="font-medium text-slate-text">{u.companyName}</span>
                          ) : (
                            <span className="text-slate-muted">—</span>
                          )}
                        </td>
                        <td className="px-4 py-3.5">
                          <span
                            className={`inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-0.5 rounded ${
                              u.active
                                ? 'bg-status-offered-bg text-status-offered'
                                : 'bg-status-rejected-bg text-status-rejected'
                            }`}
                          >
                            <span
                              className={`w-1.5 h-1.5 rounded-full ${
                                u.active ? 'bg-status-offered' : 'bg-status-rejected'
                              }`}
                            />
                            {u.active ? 'Active' : 'Disabled'}
                          </span>
                        </td>
                        <td className="px-4 py-3.5 text-xs text-slate-muted whitespace-nowrap">
                          {u.createdAt ? new Date(u.createdAt).toLocaleDateString() : '—'}
                        </td>
                        <td className="px-4 py-3.5 text-right">
                          {u.active ? (
                            <Button
                              variant="outline"
                              size="sm"
                              disabled={isSelf}
                              onClick={() => handleOpenConfirm(u, 'deactivate')}
                              className="text-status-rejected border-status-rejected/30 hover:bg-status-rejected-bg min-h-[44px] disabled:opacity-40 disabled:cursor-not-allowed"
                              title={isSelf ? 'You cannot deactivate your own admin account' : 'Deactivate user account'}
                            >
                              Deactivate
                            </Button>
                          ) : (
                            <Button
                              variant="primary"
                              size="sm"
                              onClick={() => handleOpenConfirm(u, 'activate')}
                              className="min-h-[44px]"
                              title="Reactivate user account"
                            >
                              Activate
                            </Button>
                          )}
                          {isSelf && (
                            <p className="text-[10px] text-slate-muted mt-1 italic">
                              Self-deactivation disabled
                            </p>
                          )}
                        </td>
                      </tr>
                    );
                  })}
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
      {selectedUser && targetAction && (
        <Modal
          isOpen={true}
          onClose={handleCloseConfirm}
          title={targetAction === 'activate' ? 'Confirm User Reactivation' : 'Confirm User Deactivation'}
        >
          <div className="space-y-4">
            <p className="text-xs text-slate-text">
              Are you sure you want to {targetAction === 'activate' ? 'reactivate' : 'deactivate'} user account{' '}
              <strong>{selectedUser.fullName || selectedUser.email}</strong> (#{selectedUser.id})?
            </p>

            {targetAction === 'deactivate' && (
              <div className="p-3 bg-status-rejected-bg text-status-rejected rounded text-xs">
                <strong>Warning:</strong> Deactivated users cannot log into the platform or submit/process applications until reactivated by an administrator.
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
