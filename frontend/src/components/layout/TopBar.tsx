'use client';

import type { ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { cn } from '@/lib/utils/cn';

export interface TopBarProps {
  title: string;
  subtitle?: string;
  action?: ReactNode;
  showBack?: boolean;
  sticky?: boolean;
}

export function TopBar({ title, subtitle, action, showBack, sticky = true }: TopBarProps) {
  const router = useRouter();

  return (
    <header
      className={cn(
        'z-30 flex items-center gap-3 border-b border-border bg-bg/90 px-4 py-3 backdrop-blur-md',
        sticky && 'sticky top-0',
      )}
    >
      {showBack ? (
        <button
          type="button"
          onClick={() => router.back()}
          aria-label="Go back"
          className="-ml-1 flex h-9 w-9 items-center justify-center rounded-full text-ink-muted hover:bg-surface-muted"
        >
          ←
        </button>
      ) : null}

      <div className="min-w-0 flex-1">
        <h1 className="truncate text-[17px] font-semibold text-ink">{title}</h1>
        {subtitle ? <p className="truncate text-xs text-ink-subtle">{subtitle}</p> : null}
      </div>

      {action}
    </header>
  );
}
