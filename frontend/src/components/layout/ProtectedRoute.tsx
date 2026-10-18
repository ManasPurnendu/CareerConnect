import React from 'react';
import { Navigate, useLocation, Outlet } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import type { Role } from '../../types/api';
import { LoadingState } from '../ui/LoadingState';

export interface ProtectedRouteProps {
  allowedRoles?: Role[];
  children?: React.ReactNode;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ allowedRoles, children }) => {
  const { user, isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-warm-bg">
        <LoadingState message="Verifying session..." />
      </div>
    );
  }

  // If unauthenticated: redirect to /login with encoded ?next= parameter
  if (!isAuthenticated || !user) {
    const nextParam = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${nextParam}`} replace />;
  }

  // If role is restricted and user does not have permission: redirect to /403
  if (allowedRoles && allowedRoles.length > 0 && !allowedRoles.includes(user.role)) {
    return <Navigate to="/403" replace />;
  }

  return children ? <>{children}</> : <Outlet />;
};
