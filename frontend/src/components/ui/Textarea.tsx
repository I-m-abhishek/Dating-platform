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
    <div className="space-y-2">
      <div className="flex items-baseline justify-between">
        {label ? (
          <label
            htmlFor={fieldId}
            className="text-[13px] font-semibold tracking-[0.01em] text-ink-muted"
          >
            {label}
          </label>
        ) : null}
        {counterMax !== undefined ? (
          <span
            className={cn(
              'text-xs font-medium tabular-nums transition-colors',
              nearLimit ? 'text-danger' : 'text-ink-subtle',
            )}
          >
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
          'w-full resize-none rounded-xl2 border bg-surface px-4 py-3.5 text-[15px] leading-relaxed text-ink',
          'transition-[border-color,box-shadow] duration-200',
          'placeholder:text-ink-subtle focus:outline-none',
          error
            ? 'border-danger shadow-[0_0_0_4px_rgb(var(--danger)/0.12)]'
            : 'border-border focus:border-accent focus:shadow-[0_0_0_4px_rgb(var(--accent)/0.13)]',
          className,
        )}
        {...props}
      />

      {error ? (
        <p role="alert" className="text-[13px] font-medium text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
});
