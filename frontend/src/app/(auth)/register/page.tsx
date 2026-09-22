'use client';

import { useState, type FormEvent } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { withGuest } from '@/hoc';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Chip } from '@/components/ui/Chip';
import { useAuthStore } from '@/lib/stores/authStore';
import { isApiError, messageOf } from '@/lib/api/errors';
import { humanise } from '@/lib/utils/format';
import type { Gender } from '@/lib/api/types';

const GENDERS: Gender[] = ['WOMAN', 'MAN', 'NON_BINARY', 'OTHER'];

function RegisterPage() {
  const router = useRouter();
  const register = useAuthStore((state) => state.register);

  const [form, setForm] = useState({ displayName: '', email: '', password: '', dateOfBirth: '' });
  const [gender, setGender] = useState<Gender | null>(null);
  const [interestedIn, setInterestedIn] = useState<Gender[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  const toggleInterest = (value: Gender) => {
    setInterestedIn((current) =>
      current.includes(value) ? current.filter((item) => item !== value) : [...current, value],
    );
  };

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});

    if (!gender) {
      setError('Tell us your gender to continue.');
      return;
    }
    if (interestedIn.length === 0) {
      setError('Choose who you would like to see.');
      return;
    }

    setSubmitting(true);
    try {
      await register({ ...form, gender, interestedIn });
      router.replace('/onboarding');
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
          Create your account
        </h1>
        <p className="text-[15px] text-ink-muted">Takes about a minute.</p>
      </div>

      <form onSubmit={onSubmit} className="space-y-5" noValidate>
        <Input
          label="First name"
          required
          value={form.displayName}
          error={fieldErrors.displayName}
          onChange={(event) => setForm({ ...form, displayName: event.target.value })}
        />
        <Input
          label="Email"
          type="email"
          autoComplete="email"
          required
          value={form.email}
          error={fieldErrors.email}
          onChange={(event) => setForm({ ...form, email: event.target.value })}
        />
        <Input
          label="Password"
          type="password"
          autoComplete="new-password"
          required
          value={form.password}
          error={fieldErrors.password}
          hint="At least 8 characters, with an upper case letter and a number."
          onChange={(event) => setForm({ ...form, password: event.target.value })}
        />
        <Input
          label="Date of birth"
          type="date"
          required
          value={form.dateOfBirth}
          error={fieldErrors.dateOfBirth}
          hint="You must be 18 or over."
          onChange={(event) => setForm({ ...form, dateOfBirth: event.target.value })}
        />

        <fieldset className="space-y-2.5">
          <legend className="text-[13px] font-semibold tracking-[0.01em] text-ink-muted">
            I am a
          </legend>
          <div className="flex flex-wrap gap-2">
            {GENDERS.map((value) => (
              <Chip key={value} selected={gender === value} onClick={() => setGender(value)}>
                {humanise(value)}
              </Chip>
            ))}
          </div>
        </fieldset>

        <fieldset className="space-y-2.5">
          <legend className="text-[13px] font-semibold tracking-[0.01em] text-ink-muted">
            Show me
          </legend>
          <div className="flex flex-wrap gap-2">
            {GENDERS.map((value) => (
              <Chip
                key={value}
                selected={interestedIn.includes(value)}
                onClick={() => toggleInterest(value)}
              >
                {humanise(value)}
              </Chip>
            ))}
          </div>
        </fieldset>

        {error ? (
          <p
            role="alert"
            className="animate-slide-up rounded-xl2 bg-danger/10 px-4 py-3 text-sm font-medium text-danger ring-1 ring-inset ring-danger/20"
          >
            {error}
          </p>
        ) : null}

        <Button type="submit" size="lg" fullWidth loading={submitting}>
          Continue
        </Button>

        <p className="text-center text-xs text-ink-subtle">
          By continuing you agree to our terms and privacy policy.
        </p>
      </form>

      <p className="text-center text-sm text-ink-muted">
        Already have an account?{' '}
        <Link href="/login" className="font-semibold text-accent underline-offset-4 hover:underline">
          Sign in
        </Link>
      </p>
    </div>
  );
}

export default withGuest(RegisterPage);
