'use client';

import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';
import { Spinner } from './Spinner';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'outline' | 'premium' | 'glass';
type Size = 'sm' | 'md' | 'lg' | 'xl' | 'icon' | 'icon-sm';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  leftIcon?: ReactNode;
  rightIcon?: ReactNode;
  fullWidth?: boolean;
}

/*
 * The primary is the only element in the app that carries the full accent gradient, which is
 * what makes it read as *the* action on a screen. Everything else steps down from it: a
 * tinted surface, then an outline, then plain text.
 */
const VARIANTS: Record<Variant, string> = {
  primary:
    'sheen bg-accent-gradient text-white shadow-glow hover:shadow-glow-lg hover:brightness-[1.06]',
  secondary: 'bg-surface-muted text-ink hover:bg-border/70',
  ghost: 'bg-transparent text-ink-muted hover:bg-surface-muted hover:text-ink',
  danger: 'bg-danger text-white shadow-card hover:brightness-105',
  outline: 'border border-border bg-surface text-ink hover:border-border-strong hover:bg-surface-muted',
  premium: 'sheen bg-gold-gradient text-[#2a1c06] shadow-card hover:brightness-105',
  glass: 'glass-dark border border-white/15 text-white hover:bg-black/55',
};

const SIZES: Record<Size, string> = {
  sm: 'h-9 px-4 text-[13px] gap-1.5',
  md: 'h-11 px-5 text-[15px] gap-2',
  lg: 'h-[52px] px-6 text-base gap-2',
  xl: 'h-14 px-8 text-[17px] gap-2.5',
  // shrink-0 because an icon button is square by definition. As a flex child next to a
  // fullWidth sibling it otherwise gets squeezed - in the Likes grid a 36px button was
  // rendering 23px wide, which is both misshapen and under the minimum touch target.
  icon: 'h-11 w-11 shrink-0 p-0',
  'icon-sm': 'h-9 w-9 shrink-0 p-0',
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = 'primary',
    size = 'md',
    loading,
    leftIcon,
    rightIcon,
    fullWidth,
    className,
    children,
    disabled,
    ...props
  },
  ref,
) {
  return (
    <button
      ref={ref}
      // A loading button stays focusable but rejects clicks - disabling it would move focus.
      aria-busy={loading || undefined}
      disabled={disabled || loading}
      className={cn(
        'group/btn inline-flex select-none items-center justify-center rounded-pill font-semibold tracking-[-0.01em]',
        'transition-[transform,filter,background-color,box-shadow,border-color,opacity] duration-200 ease-snap',
        // A button that dips under the finger feels connected to the tap; one that does not
        // feels like a picture of a button.
        'active:scale-[0.97]',
        'disabled:cursor-not-allowed disabled:opacity-50 disabled:shadow-none disabled:active:scale-100',
        VARIANTS[variant],
        SIZES[size],
        fullWidth && 'w-full',
        className,
      )}
      {...props}
    >
      {loading ? <Spinner className="h-4 w-4" /> : leftIcon}
      {children}
      {!loading && rightIcon}
    </button>
  );
});
