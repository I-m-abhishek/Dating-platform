'use client';

import { useEffect, type ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

export interface SheetProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  description?: string;
  children: ReactNode;
  /** Sheets stack; give the taller ones more room. */
  size?: 'auto' | 'tall';
}

/**
 * Bottom sheet, used for filters, the paywall and comment threads.
 *
 * Handles the three things a modal surface always has to: lock body scroll, close on
 * Escape, and close on backdrop click while ignoring clicks inside.
 */
export function Sheet({ open, onClose, title, description, children, size = 'auto' }: SheetProps) {
  useEffect(() => {
    if (!open) return;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    window.addEventListener('keydown', onKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center">
      <button
        type="button"
        aria-label="Close"
        onClick={onClose}
        className="absolute inset-0 animate-fade-in bg-black/40 backdrop-blur-[2px]"
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={cn(
          'relative w-full max-w-lg animate-sheet-in rounded-t-3xl bg-surface p-5 shadow-sheet',
          'sm:rounded-3xl',
          size === 'tall' && 'max-h-[85vh] overflow-y-auto',
        )}
      >
        <div aria-hidden className="mx-auto mb-4 h-1 w-10 rounded-full bg-border sm:hidden" />
        {title ? <h2 className="text-lg font-semibold text-ink">{title}</h2> : null}
        {description ? <p className="mt-1 text-sm text-ink-muted">{description}</p> : null}
        <div className={cn(title || description ? 'mt-4' : '')}>{children}</div>
      </div>
    </div>
  );
}
