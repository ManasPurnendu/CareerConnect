import React from 'react';

export const AboutPage: React.FC = () => {
  return (
    <div className="layout-container py-12 max-w-3xl mx-auto space-y-6">
      <h1 className="font-headline font-bold text-3xl text-slate-text">About CareerConnect</h1>
      <p className="text-sm text-slate-variant leading-relaxed">
        CareerConnect is an early-career recruitment platform specifically engineered for students, freshers, and professionals with 0–2 years of experience.
      </p>
      <div className="space-y-4 pt-4 border-t border-outline-warm text-sm text-slate-muted leading-relaxed">
        <h2 className="font-headline font-semibold text-lg text-slate-text">Our Mission</h2>
        <p>
          We eliminate the black-hole hiring experience for early talent by requiring complete readiness profiles, verifying employers, and enforcing immutable audit trails on every application status transition.
        </p>
      </div>
    </div>
  );
};
