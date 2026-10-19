import React, { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { Input } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';
import { Alert } from '../../components/ui/Alert';

export const LoginPage: React.FC = () => {
  const { login, isLoading } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const nextPath = searchParams.get('next');

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!email || !password) {
      setError('Please provide both email address and password.');
      return;
    }

    try {
      const user = await login({ email, password });
      if (nextPath) {
        navigate(decodeURIComponent(nextPath), { replace: true });
      } else {
        // Redirect to role workspace
        switch (user.role) {
          case 'CANDIDATE':
            navigate('/candidate', { replace: true });
            break;
          case 'RECRUITER':
            navigate('/recruiter', { replace: true });
            break;
          case 'ADMIN':
            navigate('/admin', { replace: true });
            break;
          default:
            navigate('/', { replace: true });
        }
      }
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Login failed. Please check your credentials.');
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
          Sign in to your account
        </h1>
        <p className="mt-2 text-center text-xs text-slate-muted">
          Or{' '}
          <Link to="/register" className="font-medium text-primary hover:underline">
            create a new account
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

          <form onSubmit={handleSubmit} className="space-y-5" noValidate>
            <Input
              label="Email Address"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="name@example.com"
              leftIcon={<span className="material-symbols-outlined !text-[18px]">mail</span>}
            />

            <Input
              label="Password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              leftIcon={<span className="material-symbols-outlined !text-[18px]">lock</span>}
            />

            <Button
              type="submit"
              variant="primary"
              size="lg"
              isLoading={isLoading}
              className="w-full mt-2"
            >
              Sign In
            </Button>
          </form>

          <div className="mt-6 pt-6 border-t border-outline-warm text-center">
            <p className="text-xs text-slate-muted leading-relaxed">
              Early-career recruitment with strict data integrity.
              <br />
              Candidate profile readiness verified prior to submission.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};
