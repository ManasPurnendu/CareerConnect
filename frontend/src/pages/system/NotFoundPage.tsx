import React from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/ui/Button';

export const NotFoundPage: React.FC = () => {
  return (
    <div className="min-h-screen bg-warm-bg flex items-center justify-center p-4">
      <div className="bg-white rounded border border-outline-warm p-8 sm:p-12 max-w-md w-full text-center shadow-card">
        <div className="w-16 h-16 rounded-full bg-primary-tint border border-[#A7F3D0] text-primary flex items-center justify-center mx-auto mb-4">
          <span className="material-symbols-outlined !text-3xl">search_off</span>
        </div>
        <span className="font-mono text-xs font-semibold uppercase text-primary tracking-wider">
          404 Not Found
        </span>
        <h1 className="font-headline font-bold text-xl sm:text-2xl text-slate-text mt-1 mb-2">
          Page Not Located
        </h1>
        <p className="text-sm text-slate-muted mb-6 leading-relaxed">
          The requested page or resource could not be found. It may have been moved, expired, or the link may be incorrect.
        </p>
        <div className="flex flex-col sm:flex-row gap-3 justify-center">
          <Link to="/">
            <Button variant="primary" size="md" className="w-full sm:w-auto">
              Back to Home
            </Button>
          </Link>
          <Link to="/jobs">
            <Button variant="outline" size="md" className="w-full sm:w-auto">
              Browse Jobs
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
};
