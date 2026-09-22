'use client';

import { useUiStore } from '@/lib/stores/uiStore';
import { cn } from '@/lib/utils/cn';
import { CheckIcon, CloseIcon, HeartIcon } from './icons';

const TONES = {
  default: {
    shell: 'bg-ink/95 text-bg ring-1 ring-white/10',
    icon: 'bg-white/15 text-bg',
  },
  success: {
    shell: 'bg-accent-gradient text-white shadow-glow-lg',
    icon: 'bg-white/22 text-white',
  },
  error: {
    shell: 'bg-danger text-white',
    icon: 'bg-white/20 text-white',
  },
} as const;

const ICONS = {
  default: HeartIcon,
  success: CheckIcon,
  error: CloseIcon,
} as const;

/** Transient feedback. Anything that must survive a reload belongs in notifications. */
export function Toaster() {
  const toasts = useUiStore((state) => state.toasts);
  const dismiss = useUiStore((state) => state.dismissToast);

  if (toasts.length === 0) return null;

  return (
    <div
      aria-live="polite"
      className="pointer-events-none fixed inset-x-0 bottom-28 z-[60] flex flex-col items-center gap-2.5 px-4 sm:bottom-6"
    >
      {toasts.map((toast) => {
        const tone = TONES[toast.tone];
        const Icon = ICONS[toast.tone];

        return (
          <button
            key={toast.id}
            type="button"
            onClick={() => dismiss(toast.id)}
            className={cn(
              'pointer-events-auto flex w-full max-w-sm animate-slide-up items-center gap-3 rounded-2xl px-4 py-3.5 text-left shadow-float backdrop-blur-xl',
              'transition-transform duration-200 ease-snap hover:-translate-y-0.5',
              tone.shell,
            )}
          >
            <span
              className={cn(
                'flex h-9 w-9 shrink-0 items-center justify-center rounded-full',
                tone.icon,
              )}
            >
              <Icon size={18} />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-[14px] font-semibold leading-tight">{toast.title}</span>
              {toast.description ? (
                <span className="mt-0.5 block text-xs leading-snug opacity-85">
                  {toast.description}
                </span>
              ) : null}
            </span>
          </button>
        );
      })}
    </div>
  );
}
