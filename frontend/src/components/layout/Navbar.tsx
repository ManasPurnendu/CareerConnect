import React, { useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { Button } from '../ui/Button';

export const Navbar: React.FC = () => {
  const { user, isAuthenticated, logout } = useAuth();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const navLinks = [
    { label: 'Find Jobs', path: '/jobs' },
    { label: 'Companies', path: '/companies' },
    { label: 'About', path: '/about' },
  ];

  const getDashboardPath = () => {
    if (!user) return '/login';
    switch (user.role) {
      case 'CANDIDATE':
        return '/candidate';
      case 'RECRUITER':
        return '/recruiter';
      case 'ADMIN':
        return '/admin';
      default:
        return '/';
    }
  };

  return (
    <header className="sticky top-0 z-40 bg-white/95 backdrop-blur border-b border-outline-warm">
      <div className="layout-container flex items-center justify-between h-16">
        {/* Brand Logo */}
        <Link to="/" className="flex items-center gap-2.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded">
          <div className="w-8 h-8 rounded bg-primary flex items-center justify-center text-white font-bold text-base shadow-subtle">
            CC
          </div>
          <div className="flex flex-col">
            <span className="font-headline font-bold text-lg leading-tight text-slate-text tracking-tight">
              CareerConnect
            </span>
            <span className="text-[10px] text-slate-muted font-mono leading-none -mt-0.5">
              Early-Career Hiring
            </span>
          </div>
        </Link>

        {/* Desktop Navigation */}
        <nav className="hidden md:flex items-center gap-6" aria-label="Main Navigation">
          {navLinks.map((link) => (
            <NavLink
              key={link.path}
              to={link.path}
              className={({ isActive }) =>
                `text-sm font-medium transition-colors hover:text-primary ${
                  isActive ? 'text-primary font-semibold' : 'text-slate-text'
                }`
              }
            >
              {link.label}
            </NavLink>
          ))}
        </nav>

        {/* Action Controls / Auth Status */}
        <div className="hidden md:flex items-center gap-3">
          {isAuthenticated && user ? (
            <div className="flex items-center gap-3">
              <Link
                to={getDashboardPath()}
                className="text-xs font-semibold px-3 py-1.5 rounded bg-primary-tint text-primary border border-[#A7F3D0] hover:bg-[#D1FAE5] transition-colors"
              >
                {user.role} Dashboard
              </Link>
              <Button variant="ghost" size="sm" onClick={handleLogout}>
                Sign Out
              </Button>
            </div>
          ) : (
            <>
              <Link to="/login">
                <Button variant="ghost" size="sm">
                  Sign In
                </Button>
              </Link>
              <Link to="/register">
                <Button variant="primary" size="sm">
                  Get Started
                </Button>
              </Link>
            </>
          )}
        </div>

        {/* Mobile Hamburger Button */}
        <button
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          className="md:hidden p-2 rounded text-slate-text hover:bg-[#F2F5F3]"
          aria-label={mobileMenuOpen ? 'Close Menu' : 'Open Menu'}
          aria-expanded={mobileMenuOpen}
        >
          <span className="material-symbols-outlined !text-2xl">
            {mobileMenuOpen ? 'close' : 'menu'}
          </span>
        </button>
      </div>

      {/* Mobile Drawer */}
      {mobileMenuOpen && (
        <div className="md:hidden border-b border-outline-warm bg-white px-4 pt-2 pb-6 space-y-4">
          <nav className="flex flex-col space-y-2">
            {navLinks.map((link) => (
              <NavLink
                key={link.path}
                to={link.path}
                onClick={() => setMobileMenuOpen(false)}
                className={({ isActive }) =>
                  `text-sm font-medium py-2 px-3 rounded ${
                    isActive ? 'bg-primary-tint text-primary font-semibold' : 'text-slate-text hover:bg-[#F2F5F3]'
                  }`
                }
              >
                {link.label}
              </NavLink>
            ))}
          </nav>
          <div className="pt-3 border-t border-outline-warm flex flex-col gap-2">
            {isAuthenticated && user ? (
              <>
                <Link
                  to={getDashboardPath()}
                  onClick={() => setMobileMenuOpen(false)}
                  className="w-full text-center text-sm font-semibold py-2 px-4 rounded bg-primary-tint text-primary border border-[#A7F3D0]"
                >
                  Go to {user.role} Dashboard
                </Link>
                <Button
                  variant="outline"
                  size="md"
                  onClick={() => {
                    setMobileMenuOpen(false);
                    handleLogout();
                  }}
                  className="w-full"
                >
                  Sign Out
                </Button>
              </>
            ) : (
              <>
                <Link to="/login" onClick={() => setMobileMenuOpen(false)} className="w-full">
                  <Button variant="outline" size="md" className="w-full">
                    Sign In
                  </Button>
                </Link>
                <Link to="/register" onClick={() => setMobileMenuOpen(false)} className="w-full">
                  <Button variant="primary" size="md" className="w-full">
                    Get Started
                  </Button>
                </Link>
              </>
            )}
          </div>
        </div>
      )}
    </header>
  );
};
