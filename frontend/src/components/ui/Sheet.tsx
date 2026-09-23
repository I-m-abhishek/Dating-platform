'use client';

import { useEffect, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { cn } from '@/lib/utils/cn';
import { CloseIcon } from './icons';

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

  if (!open || typeof document === 'undefined') return null;

  /*
   * Portalled to <body>. Pages render inside <main>, which is its own stacking context
   * (z-10), so a sheet left in place sat BELOW the bottom nav bar no matter its own z-index
   * - the nav covered the sheet's bottom buttons.
   */
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center">
      <button
        type="button"
        aria-label="Close"
        onClick={onClose}
        className="absolute inset-0 animate-fade-in bg-ink/45 backdrop-blur-[3px]"
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={cn(
          'relative w-full max-w-lg animate-sheet-in rounded-t-[2rem] border border-b-0 border-border',
          'bg-surface p-6 pb-8 shadow-sheet',
          'sm:animate-scale-in sm:rounded-[1.75rem] sm:border-b sm:pb-6',
          size === 'tall' && 'max-h-[86vh] overflow-y-auto',
        )}
      >
        <div aria-hidden className="mx-auto mb-5 h-1.5 w-11 rounded-full bg-border sm:hidden" />

        {title ? (
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0">
              <h2 className="font-display text-[22px] font-semibold leading-tight text-ink">
                {title}
              </h2>
              {description ? (
                <p className="mt-1.5 text-sm leading-relaxed text-ink-muted">{description}</p>
              ) : null}
            </div>
            <button
              type="button"
              onClick={onClose}
              aria-label="Close"
              className="-mr-1 -mt-1 hidden h-9 w-9 shrink-0 items-center justify-center rounded-full text-ink-subtle transition-colors hover:bg-surface-muted hover:text-ink sm:flex"
            >
              <CloseIcon size={18} />
            </button>
          </div>
        ) : description ? (
          <p className="text-sm leading-relaxed text-ink-muted">{description}</p>
        ) : null}

        <div className={cn(title || description ? 'mt-5' : '')}>{children}</div>
      </div>
    </div>,
    document.body,
  );
}
