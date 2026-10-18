import React from 'react';
import { NavLink, Link } from 'react-router-dom';
import type { Role } from '../../types/api';

export interface SidebarProps {
  role: Role;
}

interface NavItem {
  label: string;
  path: string;
  icon: string;
}

export const Sidebar: React.FC<SidebarProps> = ({ role }) => {
  const getNavItems = (): NavItem[] => {
    switch (role) {
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
          { label: 'Settings', path: '/recruiter/settings', icon: 'settings' },
        ];
      case 'ADMIN':
        return [
          { label: 'Overview', path: '/admin', icon: 'dashboard' },
          { label: 'Users', path: '/admin/users', icon: 'manage_accounts' },
          { label: 'Companies', path: '/admin/companies', icon: 'corporate_fare' },
          { label: 'Job Postings', path: '/admin/jobs', icon: 'work' },
          { label: 'Audit Logs', path: '/admin/audit', icon: 'receipt_long' },
        ];
      default:
        return [];
    }
  };

  const navItems = getNavItems();

  return (
    <aside className="w-64 bg-white border-r border-outline-warm shrink-0 hidden md:flex flex-col min-h-screen">
      {/* Brand */}
      <div className="h-16 px-6 border-b border-outline-warm flex items-center justify-between">
        <Link to="/" className="flex items-center gap-2.5">
          <div className="w-7 h-7 rounded bg-primary flex items-center justify-center text-white font-bold text-xs">
            CC
          </div>
          <span className="font-headline font-bold text-base text-slate-text">CareerConnect</span>
        </Link>
      </div>

      {/* Role Indicator */}
      <div className="px-6 py-3 border-b border-outline-warm/60 bg-[#FAFAF8]">
        <span className="text-[10px] font-mono font-semibold tracking-wider text-slate-muted uppercase">
          {role} Workspace
        </span>
      </div>

      {/* Nav Menu */}
      <nav className="p-4 space-y-1 flex-1 overflow-y-auto" aria-label={`${role} Navigation`}>
        {navItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            end={item.path === '/candidate' || item.path === '/recruiter' || item.path === '/admin'}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3.5 py-2.5 rounded text-xs sm:text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-primary-tint text-primary font-semibold'
                  : 'text-slate-text hover:bg-[#F2F5F3] hover:text-primary'
              }`
            }
          >
            <span className="material-symbols-outlined !text-[20px]">{item.icon}</span>
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>

      {/* Bottom Switch/Back link */}
      <div className="p-4 border-t border-outline-warm text-xs text-slate-muted">
        <Link to="/" className="flex items-center gap-2 hover:text-primary transition-colors">
          <span className="material-symbols-outlined !text-[18px]">public</span>
          <span>View Public Site</span>
        </Link>
      </div>
    </aside>
  );
};
