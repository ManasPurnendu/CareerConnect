import React, { useState } from 'react';
import { NavLink, Link } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { Avatar } from '../ui/Avatar';
import { Button } from '../ui/Button';

export interface HeaderProps {
  title?: string;
  subtitle?: string;
  children?: React.ReactNode;
}

export const Header: React.FC<HeaderProps> = ({ title, subtitle, children }) => {
  const { user, logout } = useAuth();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const getNavItems = () => {
    if (!user) return [];
    switch (user.role) {
      case 'CANDIDATE':
        return [
          { label: 'Overview', path: '/candidate', icon: 'dashboard' },
          { label: 'Find Jobs', path: '/candidate/jobs', icon: 'search' },
          { label: 'Applications', path: '/candidate/applications', icon: 'description' },
          { label: 'My Profile', path: '/candidate/profile', icon: 'person' },
          { label: 'Notifications', path: '/candidate/notifications', icon: 'notifications' },
          { label: 'Settings', path: '/candidate/settings', icon: 'settings' },
        ];
      case 'RECRUITER':
        return [
          { label: 'Overview', path: '/recruiter', icon: 'dashboard' },
          { label: 'Job Postings', path: '/recruiter/jobs', icon: 'work' },
          { label: 'Applicants', path: '/recruiter/applications', icon: 'group' },
          { label: 'Company Profile', path: '/recruiter/company', icon: 'domain' },
          { label: 'Notifications', path: '/recruiter/notifications', icon: 'notifications' },
        ];
      case 'ADMIN':
        return [
          { label: 'Overview', path: '/admin', icon: 'dashboard' },
          { label: 'Users', path: '/admin/users', icon: 'manage_accounts' },
          { label: 'Companies', path: '/admin/companies', icon: 'corporate_fare' },
          { label: 'Job Postings', path: '/admin/jobs', icon: 'work' },
          { label: 'Applications', path: '/admin/applications', icon: 'description' },
          { label: 'Audit Logs', path: '/admin/audit-logs', icon: 'receipt_long' },
        ];
      default:
        return [];
    }
  };

  const navItems = getNavItems();

  return (
    <header className="bg-white border-b border-outline-warm sticky top-0 z-30">
      <div className="px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-4">
        {/* Left: Mobile Menu Trigger + Brand or Titles */}
        <div className="flex items-center gap-3">
          {user && (
            <button
              type="button"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="md:hidden min-w-[44px] min-h-[44px] flex items-center justify-center p-2 rounded text-slate-text hover:bg-[#F2F5F3] focus:outline-none focus:ring-2 focus:ring-primary"
              aria-label={mobileMenuOpen ? 'Close Menu' : 'Open Menu'}
              aria-expanded={mobileMenuOpen}
            >
              <span className="material-symbols-outlined !text-2xl">
                {mobileMenuOpen ? 'close' : 'menu'}
              </span>
            </button>
          )}

          <div>
            {title ? (
              <h1 className="font-headline font-bold text-lg sm:text-xl text-slate-text leading-tight">
                {title}
              </h1>
            ) : (
              <div className="md:hidden flex items-center gap-2">
                <div className="w-6 h-6 rounded bg-primary flex items-center justify-center text-white font-bold text-xs">
                  CC
                </div>
                <span className="font-headline font-bold text-sm text-slate-text">CareerConnect</span>
              </div>
            )}
            {subtitle && <p className="text-xs text-slate-muted">{subtitle}</p>}
          </div>
        </div>

        <div className="flex items-center gap-3">
          {children}

          {/* User representation */}
          {user && (
            <div className="flex items-center gap-2.5 pl-3 border-l border-outline-warm">
              <Avatar name={user.fullName || user.email} size="sm" />
              <div className="hidden sm:flex flex-col text-left">
                <span className="text-xs font-semibold text-slate-text leading-snug">
                  {user.fullName || user.email.split('@')[0]}
                </span>
                <span className="text-[10px] font-mono text-slate-muted uppercase">
                  {user.role}
                </span>
              </div>
              <Button
                variant="ghost"
                size="sm"
                onClick={logout}
                title="Sign out"
                aria-label="Sign out"
                className="!px-2 text-slate-muted hover:text-status-rejected min-h-[44px]"
              >
                <span className="material-symbols-outlined !text-xl">logout</span>
              </Button>
            </div>
          )}
        </div>
      </div>

      {/* Mobile Drawer */}
      {mobileMenuOpen && user && (
        <div className="md:hidden border-b border-outline-warm bg-white px-4 py-3 space-y-2 shadow-subtle animate-fadeIn">
          <div className="px-3 py-1.5 text-[10px] font-mono font-semibold tracking-wider text-slate-muted uppercase bg-[#FAFAF8] rounded">
            {user.role} Navigation
          </div>
          <nav className="flex flex-col space-y-1">
            {navItems.map((item) => (
              <NavLink
                key={item.path}
                to={item.path}
                onClick={() => setMobileMenuOpen(false)}
                end={item.path === '/candidate' || item.path === '/recruiter' || item.path === '/admin'}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-3 py-2.5 rounded text-xs font-medium min-h-[44px] transition-colors ${
                    isActive
                      ? 'bg-primary-tint text-primary font-semibold'
                      : 'text-slate-text hover:bg-[#F2F5F3]'
                  }`
                }
              >
                <span className="material-symbols-outlined !text-[18px]">{item.icon}</span>
                <span>{item.label}</span>
              </NavLink>
            ))}
          </nav>

          <div className="pt-2 border-t border-outline-warm flex items-center justify-between text-xs text-slate-muted px-2">
            <Link
              to="/"
              onClick={() => setMobileMenuOpen(false)}
              className="hover:text-primary transition-colors py-2"
            >
              View Public Site
            </Link>
            <button
              onClick={() => {
                setMobileMenuOpen(false);
                logout();
              }}
              className="text-status-rejected hover:underline py-2 min-h-[44px] flex items-center font-medium"
            >
              Sign Out
            </button>
          </div>
        </div>
      )}
    </header>
  );
};
