'use client';

import type { ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { cn } from '@/lib/utils/cn';
import { ArrowLeftIcon } from '@/components/ui/icons';

export interface TopBarProps {
  title: string;
  subtitle?: string;
  action?: ReactNode;
  /**
   * Sits between the back button and the title - an avatar, normally.
   *
   * <p>Separate from {@code action} because the two are not interchangeable: whatever
   * identifies the screen belongs next to its name, while {@code action} is for things you
   * do to it. Putting a face in the right-hand slot leaves the name and the person it
   * belongs to at opposite ends of the bar.
   */
  leading?: ReactNode;
  showBack?: boolean;
  sticky?: boolean;
}

/**
 * The screen header.
 *
 * <p>Translucent rather than solid, so content scrolling underneath stays faintly visible -
 * that is what makes a sticky bar feel like part of the page instead of a lid on top of it.
 * The bottom edge is a gradient hairline that fades out at both ends.
 */
export function TopBar({
  title,
  subtitle,
  action,
  leading,
  showBack,
  sticky = true,
}: TopBarProps) {
  const router = useRouter();

  return (
    <header
      className={cn(
        'glass relative z-30 flex items-center gap-3 px-5 py-4',
        sticky && 'sticky top-0',
      )}
    >
      {showBack ? (
        <button
          type="button"
          onClick={() => router.back()}
          aria-label="Go back"
          className="-ml-1.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-ink-muted transition-[background-color,color,transform] hover:bg-surface-muted hover:text-ink active:scale-90"
        >
          <ArrowLeftIcon size={20} />
        </button>
      ) : null}

      {leading ? <div className="shrink-0">{leading}</div> : null}

      <div className="min-w-0 flex-1">
        <h1
          className={cn(
            'truncate font-display font-semibold leading-tight tracking-[-0.02em] text-ink',
            // A title sharing the row with an avatar is a person's name, not a page
            // heading, so it steps down to match the smaller thing beside it.
            leading ? 'text-[19px]' : 'text-[26px]',
          )}
        >
          {title}
        </h1>
        {subtitle ? (
          <p className="mt-0.5 truncate text-[13px] font-medium text-ink-subtle">{subtitle}</p>
        ) : null}
      </div>

      {action ? <div className="shrink-0">{action}</div> : null}

      <span
        aria-hidden
        className="pointer-events-none absolute inset-x-0 bottom-0 h-px bg-gradient-to-r from-transparent via-border to-transparent"
      />
    </header>
  );
}
