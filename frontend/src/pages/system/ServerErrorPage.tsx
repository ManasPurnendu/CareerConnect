import React from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/ui/Button';

export const ServerErrorPage: React.FC = () => {
  return (
    <div className="min-h-screen bg-warm-bg flex items-center justify-center p-4">
      <div className="bg-white rounded border border-outline-warm p-8 sm:p-12 max-w-md w-full text-center shadow-card">
        <div className="w-16 h-16 rounded-full bg-status-review-bg border border-status-review-border text-status-review flex items-center justify-center mx-auto mb-4">
          <span className="material-symbols-outlined !text-3xl">dns</span>
        </div>
        <span className="font-mono text-xs font-semibold uppercase text-status-review tracking-wider">
          500 Server Error
        </span>
        <h1 className="font-headline font-bold text-xl sm:text-2xl text-slate-text mt-1 mb-2">
          Service Disruption
        </h1>
        <p className="text-sm text-slate-muted mb-6 leading-relaxed">
          The server encountered an unexpected situation. Our systems have logged this event. Please try again shortly.
        </p>
        <div className="flex flex-col sm:flex-row gap-3 justify-center">
          <Button
            variant="primary"
            size="md"
            onClick={() => window.location.reload()}
            className="w-full sm:w-auto"
          >
            Reload Page
          </Button>
          <Link to="/">
            <Button variant="outline" size="md" className="w-full sm:w-auto">
              Return Home
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
};
