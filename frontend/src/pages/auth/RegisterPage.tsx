import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { Input } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Alert';

export const RegisterPage: React.FC = () => {
  const { register, isLoading } = useAuth();
  const navigate = useNavigate();

  const [role, setRole] = useState<'CANDIDATE' | 'RECRUITER'>('CANDIDATE');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!fullName.trim()) {
      setError('Please provide your full name.');
      return;
    }

    if (!email || !password) {
      setError('Please provide an email and password.');
      return;
    }

    if (password.length < 8) {
      setError('Password must be at least 8 characters long.');
      return;
    }

    try {
      const user = await register({
        email: email.trim(),
        password,
        role,
        fullName: fullName.trim(),
      });

      if (user.role === 'CANDIDATE') {
        navigate('/candidate', { replace: true });
      } else if (user.role === 'RECRUITER') {
        navigate('/recruiter', { replace: true });
      } else {
        navigate('/', { replace: true });
      }
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Registration failed. Please verify your details.');
      }
    }
  };

  return (
    <div className="min-h-screen bg-warm-bg flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <Link to="/" className="flex items-center justify-center gap-2 mb-6">
          <div className="w-9 h-9 rounded bg-primary flex items-center justify-center text-white font-bold text-lg">
            CC
          </div>
          <span className="font-headline font-bold text-2xl text-slate-text">CareerConnect</span>
        </Link>
        <h1 className="text-center font-headline font-bold text-2xl text-slate-text tracking-tight">
          Create your account
        </h1>
        <p className="mt-2 text-center text-xs text-slate-muted">
          Already registered?{' '}
          <Link to="/login" className="font-medium text-primary hover:underline">
            Sign in
          </Link>
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md px-4">
        <div className="bg-white py-8 px-6 sm:px-10 rounded border border-outline-warm shadow-card">
          {error && (
            <Alert variant="error" className="mb-6">
              {error}
            </Alert>
          )}

          {/* Role selector */}
          <div className="mb-6">
            <label className="text-xs font-semibold text-slate-text uppercase tracking-wider block mb-2 font-sans">
              I want to
            </label>
            <div className="grid grid-cols-2 gap-3">
              <button
                type="button"
                onClick={() => setRole('CANDIDATE')}
                className={`py-2.5 px-3 rounded border text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors ${
                  role === 'CANDIDATE'
                    ? 'border-primary bg-primary-tint text-primary ring-1 ring-primary'
                    : 'border-outline-warm text-slate-text hover:bg-[#F2F5F3]'
                }`}
              >
                <span className="material-symbols-outlined !text-[18px]">school</span>
                <span>Find Jobs / Internships</span>
              </button>
              <button
                type="button"
                onClick={() => setRole('RECRUITER')}
                className={`py-2.5 px-3 rounded border text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors ${
                  role === 'RECRUITER'
                    ? 'border-primary bg-primary-tint text-primary ring-1 ring-primary'
                    : 'border-outline-warm text-slate-text hover:bg-[#F2F5F3]'
                }`}
              >
                <span className="material-symbols-outlined !text-[18px]">domain</span>
                <span>Hire Early Talent</span>
              </button>
            </div>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <Input
              label={role === 'CANDIDATE' ? 'Full Name' : 'Contact Person Name'}
              type="text"
              required
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="e.g. Alex Johnson"
              leftIcon={<span className="material-symbols-outlined !text-[18px]">person</span>}
            />

            <Input
              label="Email Address"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="alex@example.com"
              leftIcon={<span className="material-symbols-outlined !text-[18px]">mail</span>}
            />

            <Input
              label="Password"
              type="password"
              autoComplete="new-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="At least 8 characters"
              helperText="Must be at least 8 characters long"
              leftIcon={<span className="material-symbols-outlined !text-[18px]">lock</span>}
            />

            <Button
              type="submit"
              variant="primary"
              size="lg"
              isLoading={isLoading}
              className="w-full mt-2"
            >
              Create Account
            </Button>
          </form>

          <div className="mt-6 pt-6 border-t border-outline-warm text-center">
            <p className="text-[11px] text-slate-muted">
              By creating an account, you agree to the{' '}
              <Link to="/terms" className="underline hover:text-primary">
                Terms of Service
              </Link>{' '}
              and{' '}
              <Link to="/privacy" className="underline hover:text-primary">
                Privacy Policy
              </Link>
              .
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};
