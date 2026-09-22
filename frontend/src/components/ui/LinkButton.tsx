import Link from 'next/link';
import type { ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

type Variant = 'primary' | 'outline' | 'ghost' | 'premium' | 'glass';
type Size = 'sm' | 'md' | 'lg' | 'xl';

/* Mirrors the Button variants exactly - the two must be indistinguishable on screen. */
const VARIANTS: Record<Variant, string> = {
  primary:
    'sheen bg-accent-gradient text-white shadow-glow hover:shadow-glow-lg hover:brightness-[1.06]',
  outline: 'border border-border bg-surface text-ink hover:border-border-strong hover:bg-surface-muted',
  ghost: 'text-ink-muted hover:bg-surface-muted hover:text-ink',
  premium: 'sheen bg-gold-gradient text-[#2a1c06] shadow-card hover:brightness-105',
  glass: 'glass-dark border border-white/15 text-white hover:bg-black/55',
};

const SIZES: Record<Size, string> = {
  sm: 'h-9 px-4 text-[13px] gap-1.5',
  md: 'h-11 px-5 text-[15px] gap-2',
  lg: 'h-[52px] px-6 text-base gap-2',
  xl: 'h-14 px-8 text-[17px] gap-2.5',
};

/**
 * A link that looks like a button.
 *
 * Exists so we never nest an anchor inside a button element - that is invalid HTML and
 * breaks keyboard activation in ways that are hard to notice and easy to ship.
 */
export function LinkButton({
  href,
  children,
  variant = 'primary',
  size = 'md',
  fullWidth,
  className,
}: {
  href: string;
  children: ReactNode;
  variant?: Variant;
  size?: Size;
  fullWidth?: boolean;
  className?: string;
}) {
  return (
    <Link
      href={href}
      className={cn(
        'inline-flex select-none items-center justify-center rounded-pill font-semibold tracking-[-0.01em]',
        'transition-[transform,filter,background-color,box-shadow,border-color] duration-200 ease-snap',
        'active:scale-[0.97]',
        VARIANTS[variant],
        SIZES[size],
        fullWidth && 'w-full',
        className,
      )}
    >
      {children}
    </Link>
  );
}
