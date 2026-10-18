import React from 'react';

export const PrivacyPage: React.FC = () => {
  return (
    <div className="layout-container py-12 max-w-3xl mx-auto space-y-6">
      <h1 className="font-headline font-bold text-3xl text-slate-text">Privacy Policy</h1>
      <p className="text-sm text-slate-muted">Last Updated: October 2026</p>
      <div className="space-y-4 pt-4 border-t border-outline-warm text-sm text-slate-variant leading-relaxed">
        <h2 className="font-headline font-semibold text-lg text-slate-text">Data Protection & Resume Access</h2>
        <p>
          Candidate resumes and sensitive contact details are protected. Only authorized recruiters of companies where a candidate actively applied can access the immutable resume snapshot.
        </p>
      </div>
    </div>
  );
};
