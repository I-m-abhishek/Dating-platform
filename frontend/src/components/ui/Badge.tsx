import type { ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

type Tone = 'neutral' | 'accent' | 'success' | 'muted' | 'gold' | 'glass' | 'danger';

const TONES: Record<Tone, string> = {
  neutral: 'bg-surface-muted text-ink-muted',
  accent: 'bg-accent-gradient text-white shadow-[0_4px_14px_-6px_rgb(var(--accent)/0.9)]',
  success: 'bg-success/12 text-success ring-1 ring-inset ring-success/25',
  muted: 'bg-surface/70 text-ink-muted ring-1 ring-inset ring-border',
  gold: 'bg-gold-gradient text-[#2a1c06] shadow-[0_4px_14px_-7px_rgb(var(--gold))]',
  // For badges sitting on top of a photo, where the backdrop is unknown.
  glass: 'glass-dark text-white ring-1 ring-inset ring-white/20',
  danger: 'bg-danger/12 text-danger ring-1 ring-inset ring-danger/25',
};

export function Badge({
  children,
  tone = 'neutral',
  className,
}: {
  children: ReactNode;
  tone?: Tone;
  className?: string;
}) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-pill px-2.5 py-1 text-[11px] font-semibold tracking-[0.01em]',
        TONES[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}

/** The small count bubble on a tab. Caps at 99+ so it never resizes the tab bar. */
export function CountBadge({ count }: { count: number }) {
  if (count <= 0) return null;
  return (
    <span className="absolute -right-2 -top-1.5 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-accent-gradient px-1 text-[10px] font-bold leading-none text-white ring-2 ring-surface">
      {count > 99 ? '99+' : count}
    </span>
  );
}
