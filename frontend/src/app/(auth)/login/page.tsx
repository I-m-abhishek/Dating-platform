'use client';

import { useState, type FormEvent } from 'react';
import Link from 'next/link';
import { withGuest } from '@/hoc';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { useAuthStore } from '@/lib/stores/authStore';
import { isApiError, messageOf } from '@/lib/api/errors';

function LoginPage() {
  const login = useAuthStore((state) => state.login);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    setSubmitting(true);
    try {
      await login(email, password);
      // withGuest handles the redirect once the session lands.
    } catch (caught) {
      if (isApiError(caught) && caught.details.length > 0) {
        setFieldErrors(caught.fieldErrors);
      }
      setError(messageOf(caught));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="animate-slide-up space-y-7">
      <div className="space-y-1.5">
        <h1 className="font-display text-[32px] font-semibold leading-tight tracking-[-0.025em] text-ink">
          Welcome back
        </h1>
        <p className="text-[15px] text-ink-muted">Sign in to pick up where you left off.</p>
      </div>

      <form onSubmit={onSubmit} className="space-y-5" noValidate>
        <Input
          label="Email"
          type="email"
          autoComplete="email"
          inputMode="email"
          required
          value={email}
          error={fieldErrors.email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="you@example.com"
        />
        <Input
          label="Password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          error={fieldErrors.password}
          onChange={(event) => setPassword(event.target.value)}
        />

        {error ? (
          <p
            role="alert"
            className="animate-slide-up rounded-xl2 bg-danger/10 px-4 py-3 text-sm font-medium text-danger ring-1 ring-inset ring-danger/20"
          >
            {error}
          </p>
        ) : null}

        <Button type="submit" size="lg" fullWidth loading={submitting}>
          Sign in
        </Button>
      </form>

      <p className="text-center text-sm text-ink-muted">
        New here?{' '}
        <Link
          href="/register"
          className="font-semibold text-accent underline-offset-4 hover:underline"
        >
          Create an account
        </Link>
      </p>
    </div>
  );
}

export default withGuest(LoginPage);
