import React from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/ui/Button';

export const ForbiddenPage: React.FC = () => {
  return (
    <div className="min-h-screen bg-warm-bg flex items-center justify-center p-4">
      <div className="bg-white rounded border border-outline-warm p-8 sm:p-12 max-w-md w-full text-center shadow-card">
        <div className="w-16 h-16 rounded-full bg-status-rejected-bg border border-status-rejected-border text-status-rejected flex items-center justify-center mx-auto mb-4">
          <span className="material-symbols-outlined !text-3xl">lock</span>
        </div>
        <span className="font-mono text-xs font-semibold uppercase text-status-rejected tracking-wider">
          403 Access Forbidden
        </span>
        <h1 className="font-headline font-bold text-xl sm:text-2xl text-slate-text mt-1 mb-2">
          Restricted Permission
        </h1>
        <p className="text-sm text-slate-muted mb-6 leading-relaxed">
          You do not have the required permissions or role to view this workspace. Please return to your designated dashboard or sign in with an authorized account.
        </p>
        <div className="flex flex-col sm:flex-row gap-3 justify-center">
          <Link to="/">
            <Button variant="primary" size="md" className="w-full sm:w-auto">
              Return Home
            </Button>
          </Link>
          <Link to="/login">
            <Button variant="outline" size="md" className="w-full sm:w-auto">
              Switch Account
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
};
