import type { ReactNode } from 'react';

export interface EmptyStateProps {
  title: string;
  description?: string;
  icon?: ReactNode;
  action?: ReactNode;
}

/**
 * Every empty list uses this, so "nothing here" always looks intentional.
 *
 * <p>The halo behind the icon is the point: an empty screen with a single soft-lit object on
 * it reads as a designed state, whereas one line of grey text reads as a bug.
 */
export function EmptyState({ title, description, icon, action }: EmptyStateProps) {
  return (
    <div className="flex animate-slide-up flex-col items-center justify-center gap-4 px-6 py-16 text-center">
      <div className="relative flex h-[76px] w-[76px] items-center justify-center">
        <span
          aria-hidden
          className="absolute inset-0 rounded-full bg-accent-gradient opacity-[0.14] blur-xl"
        />
        <span
          aria-hidden
          className="absolute inset-0 rounded-full bg-accent-gradient-soft ring-1 ring-inset ring-border"
        />
        <span className="relative animate-float text-accent [&>svg]:h-8 [&>svg]:w-8">
          {icon ?? <DefaultGlyph />}
        </span>
      </div>

      <div className="space-y-2">
        <h3 className="font-display text-xl font-semibold text-ink">{title}</h3>
        {description ? (
          <p className="mx-auto max-w-[19rem] text-sm leading-relaxed text-ink-muted">
            {description}
          </p>
        ) : null}
      </div>

      {action ? <div className="pt-1">{action}</div> : null}
    </div>
  );
}

function DefaultGlyph() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} aria-hidden>
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M12 20.3s-7.6-4.6-7.6-9.6a4.3 4.3 0 0 1 7.6-2.7 4.3 4.3 0 0 1 7.6 2.7c0 5-7.6 9.6-7.6 9.6Z"
      />
    </svg>
  );
}
