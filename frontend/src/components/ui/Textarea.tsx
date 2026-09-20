'use client';

import { forwardRef, useId, type TextareaHTMLAttributes } from 'react';
import { cn } from '@/lib/utils/cn';

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
  /** Shows a live "42/300" counter; set it to the same value as maxLength. */
  counterMax?: number;
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { label, error, counterMax, className, id, value, ...props },
  ref,
) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  const length = typeof value === 'string' ? value.length : 0;
  const nearLimit = counterMax !== undefined && length > counterMax * 0.9;

  return (
    <div className="space-y-1.5">
      <div className="flex items-baseline justify-between">
        {label ? (
          <label htmlFor={fieldId} className="text-sm font-medium text-ink-muted">
            {label}
          </label>
        ) : null}
        {counterMax !== undefined ? (
          <span className={cn('text-xs tabular-nums', nearLimit ? 'text-danger' : 'text-ink-subtle')}>
            {length}/{counterMax}
          </span>
        ) : null}
      </div>

      <textarea
        ref={ref}
        id={fieldId}
        value={value}
        aria-invalid={error ? true : undefined}
        maxLength={counterMax}
        className={cn(
          'w-full resize-none rounded-2xl border bg-surface px-3.5 py-3 text-[15px] text-ink',
          'placeholder:text-ink-subtle focus:outline-none',
          error ? 'border-danger' : 'border-border focus:border-accent',
          className,
        )}
        {...props}
      />

      {error ? (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
});
