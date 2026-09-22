'use client';

import { Button } from './Button';

export interface ErrorStateProps {
  title?: string;
  description?: string;
  onRetry?: () => void;
}

export function ErrorState({
  title = 'Something went wrong',
  description,
  onRetry,
}: ErrorStateProps) {
  return (
    <div
      role="alert"
      className="flex animate-slide-up flex-col items-center gap-4 px-6 py-14 text-center"
    >
      <div className="relative flex h-16 w-16 items-center justify-center rounded-full bg-danger/10 ring-1 ring-inset ring-danger/20">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth={1.7}
          strokeLinecap="round"
          className="h-7 w-7 text-danger"
          aria-hidden
        >
          <path d="M12 8.5v4.8" />
          <circle cx="12" cy="16.6" r="0.6" fill="currentColor" />
          <path d="M10.3 3.9 2.8 17a2 2 0 0 0 1.7 3h15a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z" />
        </svg>
      </div>

      <div className="space-y-1.5">
        <h3 className="font-display text-lg font-semibold text-ink">{title}</h3>
        {description ? (
          <p className="mx-auto max-w-sm text-sm leading-relaxed text-ink-muted">{description}</p>
        ) : null}
      </div>

      {onRetry ? (
        <Button variant="outline" size="sm" onClick={onRetry}>
          Try again
        </Button>
      ) : null}
    </div>
  );
}
