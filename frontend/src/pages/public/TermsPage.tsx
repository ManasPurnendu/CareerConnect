import React from 'react';

export const TermsPage: React.FC = () => {
  return (
    <div className="layout-container py-12 max-w-3xl mx-auto space-y-6">
      <h1 className="font-headline font-bold text-3xl text-slate-text">Terms of Service</h1>
      <p className="text-sm text-slate-muted">Last Updated: October 2026</p>
      <div className="space-y-4 pt-4 border-t border-outline-warm text-sm text-slate-variant leading-relaxed">
        <h2 className="font-headline font-semibold text-lg text-slate-text">Candidate & Recruiter Obligations</h2>
        <p>
          Recruiters agree only to post genuine early-career openings with transparent salary and location requirements. Candidates agree to maintain truthful academic and skill representations.
        </p>
      </div>
    </div>
  );
};
