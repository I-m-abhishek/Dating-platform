'use client';

import type { ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

export interface ChipProps {
  children: ReactNode;
  selected?: boolean;
  onClick?: () => void;
  icon?: ReactNode;
  size?: 'sm' | 'md';
  className?: string;
}

/** The interest / quality pill. Renders as a button only when it is interactive. */
export function Chip({ children, selected, onClick, icon, size = 'md', className }: ChipProps) {
  const classes = cn(
    'inline-flex items-center gap-1.5 rounded-pill border transition-colors',
    size === 'sm' ? 'px-2.5 py-1 text-xs' : 'px-3.5 py-1.5 text-sm',
    selected
      ? 'border-accent bg-accent-soft text-accent'
      : 'border-border bg-surface text-ink-muted',
    onClick && 'cursor-pointer hover:border-ink-subtle',
    className,
  );

  if (!onClick) {
    return <span className={classes}>{icon}{children}</span>;
  }
  return (
    <button type="button" onClick={onClick} aria-pressed={selected} className={classes}>
      {icon}
      {children}
    </button>
  );
}
