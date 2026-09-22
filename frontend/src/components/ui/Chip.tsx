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
    'inline-flex items-center gap-1.5 rounded-pill border font-medium',
    'transition-[background-color,border-color,color,transform,box-shadow] duration-200 ease-snap',
    size === 'sm' ? 'px-3 py-1 text-xs' : 'px-4 py-2 text-[13px]',
    selected
      ? 'border-transparent bg-accent-gradient text-white shadow-[0_5px_16px_-8px_rgb(var(--accent)/0.95)]'
      : 'border-border bg-surface text-ink-muted',
    onClick && 'cursor-pointer active:scale-95',
    onClick && !selected && 'hover:border-accent/45 hover:bg-accent-soft hover:text-ink',
    className,
  );

  if (!onClick) {
    return (
      <span className={classes}>
        {icon}
        {children}
      </span>
    );
  }
  return (
    <button type="button" onClick={onClick} aria-pressed={selected} className={classes}>
      {icon}
      {children}
    </button>
  );
}
