import React from 'react';
import { Link } from 'react-router-dom';

export const Footer: React.FC = () => {
  return (
    <footer className="bg-white border-t border-outline-warm mt-auto">
      <div className="layout-container py-12">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
          {/* Brand info */}
          <div className="md:col-span-1 flex flex-col gap-3">
            <Link to="/" className="flex items-center gap-2">
              <div className="w-7 h-7 rounded bg-primary flex items-center justify-center text-white font-bold text-sm">
                CC
              </div>
              <span className="font-headline font-bold text-base text-slate-text">CareerConnect</span>
            </Link>
            <p className="text-xs text-slate-muted leading-relaxed">
              Transparent, high-trust early-career recruitment platform connecting students, freshers, and early graduates with trusted employers.
            </p>
          </div>

          {/* Candidates */}
          <div>
            <h4 className="font-semibold text-xs text-slate-text uppercase tracking-wider mb-3">
              For Candidates
            </h4>
            <ul className="space-y-2 text-xs text-slate-muted">
              <li>
                <Link to="/jobs" className="hover:text-primary transition-colors">
                  Browse Jobs
                </Link>
              </li>
              <li>
                <Link to="/candidate/profile" className="hover:text-primary transition-colors">
                  Profile Readiness
                </Link>
              </li>
              <li>
                <Link to="/candidate/applications" className="hover:text-primary transition-colors">
                  Track Applications
                </Link>
              </li>
            </ul>
          </div>

          {/* Recruiters */}
          <div>
            <h4 className="font-semibold text-xs text-slate-text uppercase tracking-wider mb-3">
              For Employers
            </h4>
            <ul className="space-y-2 text-xs text-slate-muted">
              <li>
                <Link to="/recruiter/jobs/new" className="hover:text-primary transition-colors">
                  Post an Opening
                </Link>
              </li>
              <li>
                <Link to="/recruiter/applications" className="hover:text-primary transition-colors">
                  Review Applicants
                </Link>
              </li>
              <li>
                <Link to="/recruiter/company" className="hover:text-primary transition-colors">
                  Company Profile
                </Link>
              </li>
            </ul>
          </div>

          {/* Legal & Platform */}
          <div>
            <h4 className="font-semibold text-xs text-slate-text uppercase tracking-wider mb-3">
              Platform
            </h4>
            <ul className="space-y-2 text-xs text-slate-muted">
              <li>
                <Link to="/about" className="hover:text-primary transition-colors">
                  About Us
                </Link>
              </li>
              <li>
                <Link to="/privacy" className="hover:text-primary transition-colors">
                  Privacy Policy
                </Link>
              </li>
              <li>
                <Link to="/terms" className="hover:text-primary transition-colors">
                  Terms of Service
                </Link>
              </li>
            </ul>
          </div>
        </div>

        <div className="mt-8 pt-6 border-t border-outline-warm/60 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-slate-muted">
          <p>© {new Date().getFullYear()} CareerConnect. All rights reserved.</p>
          <p className="font-mono text-[11px]">V1 Production Baseline · Phase 9.1</p>
        </div>
      </div>
    </footer>
  );
};
