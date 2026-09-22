'use client';

import { forwardRef, useId, type InputHTMLAttributes, type ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  hint?: string;
  error?: string;
  leftAddon?: ReactNode;
  rightAddon?: ReactNode;
}

/**
 * A labelled text field.
 *
 * The error is wired with {@code aria-describedby} and {@code aria-invalid} rather than
 * just coloured red, so a screen reader announces the validation message the server sent.
 */
export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, hint, error, leftAddon, rightAddon, className, id, ...props },
  ref,
) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const describedBy = error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined;

  return (
    <div className="space-y-2">
      {label ? (
        <label
          htmlFor={inputId}
          className="block text-[13px] font-semibold tracking-[0.01em] text-ink-muted"
        >
          {label}
        </label>
      ) : null}

      <div
        className={cn(
          'flex items-center gap-2.5 rounded-xl2 border bg-surface px-4',
          'transition-[border-color,box-shadow,background-color] duration-200',
          error
            ? 'border-danger shadow-[0_0_0_4px_rgb(var(--danger)/0.12)]'
            : 'border-border focus-within:border-accent focus-within:shadow-[0_0_0_4px_rgb(var(--accent)/0.13)]',
        )}
      >
        {leftAddon ? <span className="shrink-0 text-ink-subtle">{leftAddon}</span> : null}
        <input
          ref={ref}
          id={inputId}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={cn(
            'h-[52px] w-full bg-transparent text-[15px] text-ink placeholder:text-ink-subtle',
            'focus:outline-none focus-visible:ring-0',
            className,
          )}
          {...props}
        />
        {rightAddon ? <span className="shrink-0 text-ink-subtle">{rightAddon}</span> : null}
      </div>

      {error ? (
        <p id={`${inputId}-error`} role="alert" className="text-[13px] font-medium text-danger">
          {error}
        </p>
      ) : hint ? (
        <p id={`${inputId}-hint`} className="text-[13px] text-ink-subtle">
          {hint}
        </p>
      ) : null}
    </div>
  );
});
