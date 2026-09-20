'use client';

import { useUiStore } from '@/lib/stores/uiStore';
import { cn } from '@/lib/utils/cn';

const TONES = {
  default: 'bg-ink text-bg',
  success: 'bg-success text-white',
  error: 'bg-danger text-white',
} as const;

/** Transient feedback. Anything that must survive a reload belongs in notifications. */
export function Toaster() {
  const toasts = useUiStore((state) => state.toasts);
  const dismiss = useUiStore((state) => state.dismissToast);

  if (toasts.length === 0) return null;

  return (
    <div
      aria-live="polite"
      className="pointer-events-none fixed inset-x-0 bottom-24 z-[60] flex flex-col items-center gap-2 px-4 sm:bottom-6"
    >
      {toasts.map((toast) => (
        <button
          key={toast.id}
          type="button"
          onClick={() => dismiss(toast.id)}
          className={cn(
            'pointer-events-auto w-full max-w-sm animate-slide-up rounded-2xl px-4 py-3 text-left shadow-card',
            TONES[toast.tone],
          )}
        >
          <p className="text-sm font-medium">{toast.title}</p>
          {toast.description ? <p className="mt-0.5 text-xs opacity-80">{toast.description}</p> : null}
        </button>
      ))}
    </div>
  );
}
