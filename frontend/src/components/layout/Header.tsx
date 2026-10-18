import React from 'react';
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

  return (
    <header className="bg-white border-b border-outline-warm sticky top-0 z-30">
      <div className="px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-4">
        <div>
          {title && (
            <h1 className="font-headline font-bold text-lg sm:text-xl text-slate-text leading-tight">
              {title}
            </h1>
          )}
          {subtitle && <p className="text-xs text-slate-muted">{subtitle}</p>}
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
                className="!px-2 text-slate-muted hover:text-status-rejected"
              >
                <span className="material-symbols-outlined !text-xl">logout</span>
              </Button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};
