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
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-xl font-semibold text-ink">Welcome back</h1>
        <p className="text-sm text-ink-muted">Sign in to pick up where you left off.</p>
      </div>

      <form onSubmit={onSubmit} className="space-y-4" noValidate>
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
          <p role="alert" className="rounded-2xl bg-danger/10 px-4 py-3 text-sm text-danger">
            {error}
          </p>
        ) : null}

        <Button type="submit" fullWidth loading={submitting}>
          Sign in
        </Button>
      </form>

      <p className="text-center text-sm text-ink-muted">
        New here?{' '}
        <Link href="/register" className="font-medium text-accent underline-offset-4 hover:underline">
          Create an account
        </Link>
      </p>
    </div>
  );
}

export default withGuest(LoginPage);
