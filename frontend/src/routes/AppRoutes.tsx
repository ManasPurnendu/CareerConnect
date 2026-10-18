import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';

// Layouts
import { PublicLayout } from '../components/layout/PublicLayout';
import { CandidateLayout } from '../components/layout/CandidateLayout';
import { RecruiterLayout } from '../components/layout/RecruiterLayout';
import { AdminLayout } from '../components/layout/AdminLayout';
import { ProtectedRoute } from '../components/layout/ProtectedRoute';

// Public Pages
import { LandingPage } from '../pages/public/LandingPage';
import { JobsPage } from '../pages/public/JobsPage';
import { JobDetailPage } from '../pages/public/JobDetailPage';
import { CompaniesPage } from '../pages/public/CompaniesPage';
import { CompanyDetailPage } from '../pages/public/CompanyDetailPage';
import { AboutPage } from '../pages/public/AboutPage';
import { PrivacyPage } from '../pages/public/PrivacyPage';
import { TermsPage } from '../pages/public/TermsPage';

// Auth Pages
import { LoginPage } from '../pages/auth/LoginPage';
import { RegisterPage } from '../pages/auth/RegisterPage';

// Candidate Pages
import { CandidateDashboardPage } from '../pages/candidate/CandidateDashboardPage';
import { CandidateApplicationsPage } from '../pages/candidate/CandidateApplicationsPage';
import { CandidateProfilePage } from '../pages/candidate/CandidateProfilePage';
import { CandidateNotificationsPage } from '../pages/candidate/CandidateNotificationsPage';
import { CandidateSettingsPage } from '../pages/candidate/CandidateSettingsPage';

// Recruiter Pages
import { RecruiterDashboardPage } from '../pages/recruiter/RecruiterDashboardPage';
import { RecruiterJobsPage } from '../pages/recruiter/RecruiterJobsPage';
import { RecruiterNewJobPage } from '../pages/recruiter/RecruiterNewJobPage';
import { RecruiterApplicationsPage } from '../pages/recruiter/RecruiterApplicationsPage';
import { RecruiterCompanyPage } from '../pages/recruiter/RecruiterCompanyPage';

// Admin Pages
import { AdminDashboardPage } from '../pages/admin/AdminDashboardPage';
import { AdminUsersPage } from '../pages/admin/AdminUsersPage';
import { AdminCompaniesPage } from '../pages/admin/AdminCompaniesPage';
import { AdminJobsPage } from '../pages/admin/AdminJobsPage';
import { AdminAuditPage } from '../pages/admin/AdminAuditPage';

// System Pages
import { ForbiddenPage } from '../pages/system/ForbiddenPage';
import { NotFoundPage } from '../pages/system/NotFoundPage';
import { ServerErrorPage } from '../pages/system/ServerErrorPage';

export const AppRoutes: React.FC = () => {
  return (
    <Routes>
      {/* Public Routes Shell */}
      <Route element={<PublicLayout />}>
        <Route path="/" element={<LandingPage />} />
        <Route path="/jobs" element={<JobsPage />} />
        <Route path="/jobs/:jobId" element={<JobDetailPage />} />
        <Route path="/companies" element={<CompaniesPage />} />
        <Route path="/companies/:companyId" element={<CompanyDetailPage />} />
        <Route path="/about" element={<AboutPage />} />
        <Route path="/privacy" element={<PrivacyPage />} />
        <Route path="/terms" element={<TermsPage />} />
      </Route>

      {/* Standalone Authentication Routes */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      {/* Candidate Protected Routes */}
      <Route element={<ProtectedRoute allowedRoles={['CANDIDATE']} />}>
        <Route element={<CandidateLayout />}>
          <Route path="/candidate" element={<CandidateDashboardPage />} />
          <Route path="/candidate/jobs" element={<JobsPage />} />
          <Route path="/candidate/jobs/:jobId" element={<JobDetailPage />} />
          <Route path="/candidate/applications" element={<CandidateApplicationsPage />} />
          <Route path="/candidate/applications/:applicationId" element={<CandidateApplicationsPage />} />
          <Route path="/candidate/profile" element={<CandidateProfilePage />} />
          <Route path="/candidate/profile/edit" element={<CandidateProfilePage />} />
          <Route path="/candidate/notifications" element={<CandidateNotificationsPage />} />
          <Route path="/candidate/settings" element={<CandidateSettingsPage />} />
        </Route>
      </Route>

      {/* Recruiter Protected Routes */}
      <Route element={<ProtectedRoute allowedRoles={['RECRUITER']} />}>
        <Route element={<RecruiterLayout />}>
          <Route path="/recruiter" element={<RecruiterDashboardPage />} />
          <Route path="/recruiter/jobs" element={<RecruiterJobsPage />} />
          <Route path="/recruiter/jobs/new" element={<RecruiterNewJobPage />} />
          <Route path="/recruiter/jobs/:jobId" element={<RecruiterJobsPage />} />
          <Route path="/recruiter/jobs/:jobId/edit" element={<RecruiterNewJobPage />} />
          <Route path="/recruiter/applications" element={<RecruiterApplicationsPage />} />
          <Route path="/recruiter/applications/:applicationId" element={<RecruiterApplicationsPage />} />
          <Route path="/recruiter/company" element={<RecruiterCompanyPage />} />
          <Route path="/recruiter/company/edit" element={<RecruiterCompanyPage />} />
          <Route path="/recruiter/notifications" element={<RecruiterDashboardPage />} />
          <Route path="/recruiter/settings" element={<RecruiterDashboardPage />} />
        </Route>
      </Route>

      {/* Admin Protected Routes */}
      <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
        <Route element={<AdminLayout />}>
          <Route path="/admin" element={<AdminDashboardPage />} />
          <Route path="/admin/users" element={<AdminUsersPage />} />
          <Route path="/admin/companies" element={<AdminCompaniesPage />} />
          <Route path="/admin/jobs" element={<AdminJobsPage />} />
          <Route path="/admin/applications" element={<AdminDashboardPage />} />
          <Route path="/admin/applications/:applicationId" element={<AdminDashboardPage />} />
          <Route path="/admin/audit" element={<AdminAuditPage />} />
        </Route>
      </Route>

      {/* System Error & Status Routes */}
      <Route path="/403" element={<ForbiddenPage />} />
      <Route path="/404" element={<NotFoundPage />} />
      <Route path="/500" element={<ServerErrorPage />} />

      {/* Catch-all Wildcard -> 404 */}
      <Route path="*" element={<Navigate to="/404" replace />} />
    </Routes>
  );
};
