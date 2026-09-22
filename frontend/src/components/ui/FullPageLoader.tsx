import { LogoMark } from './icons';

/**
 * The branded wait.
 *
 * <p>A bare spinner on a white page is indistinguishable from a broken app. The mark plus a
 * breathing halo tells the reader the product is loading, not stuck.
 */
export function FullPageLoader({ label }: { label?: string }) {
  return (
    <div
      role="status"
      aria-label={label ?? 'Loading'}
      className="flex min-h-[70vh] flex-col items-center justify-center gap-5"
    >
      <div className="relative flex h-16 w-16 items-center justify-center">
        <span
          aria-hidden
          className="absolute inset-0 animate-pulse-ring rounded-full bg-accent-gradient opacity-30"
        />
        <span
          aria-hidden
          className="absolute inset-0 rounded-full bg-accent-gradient opacity-[0.12] blur-lg"
        />
        <LogoMark size={40} className="relative animate-float" />
      </div>
      {label ? <p className="text-sm font-medium text-ink-subtle">{label}</p> : null}
    </div>
  );
}
