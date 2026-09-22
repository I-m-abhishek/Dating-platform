'use client';

import { cn } from '@/lib/utils/cn';

export interface SwitchProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
  description?: string;
  disabled?: boolean;
}

/**
 * A labelled toggle.
 *
 * <p>Built on a real checkbox rather than a styled div: it keeps the native keyboard
 * behaviour, the label association and the announced state, and the visible track is
 * drawn by sibling elements reacting to {@code peer-checked}. A div with role="switch"
 * would need all of that reimplemented by hand.
 */
export function Switch({ checked, onChange, label, description, disabled }: SwitchProps) {
  return (
    <label
      className={cn(
        'flex items-center justify-between gap-4',
        disabled ? 'cursor-not-allowed opacity-55' : 'cursor-pointer',
      )}
    >
      <span className="min-w-0">
        <span className="block text-[14px] font-medium text-ink">{label}</span>
        {description ? (
          <span className="mt-0.5 block text-xs leading-snug text-ink-subtle">{description}</span>
        ) : null}
      </span>

      <span className="relative inline-flex shrink-0">
        <input
          type="checkbox"
          role="switch"
          checked={checked}
          disabled={disabled}
          onChange={(event) => onChange(event.target.checked)}
          className="peer h-7 w-[46px] cursor-pointer appearance-none rounded-full bg-border transition-colors duration-300 checked:bg-accent-gradient focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-bg disabled:cursor-not-allowed"
        />
        <span
          aria-hidden
          className="pointer-events-none absolute left-[3px] top-[3px] h-[22px] w-[22px] rounded-full bg-white shadow-sm transition-transform duration-300 ease-snap peer-checked:translate-x-[18px]"
        />
      </span>
    </label>
  );
}
