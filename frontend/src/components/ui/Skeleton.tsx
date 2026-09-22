import { cn } from '@/lib/utils/cn';

function Box({ className }: { className?: string }) {
  return (
    <div
      aria-hidden
      className={cn('relative overflow-hidden rounded-xl bg-surface-muted', className)}
    >
      <div className="absolute inset-0 -translate-x-full animate-shimmer bg-gradient-to-r from-transparent via-ink/[0.06] to-transparent" />
    </div>
  );
}

function List({ rows = 3 }: { rows?: number }) {
  return (
    <div className="space-y-1" role="status" aria-label="Loading">
      {Array.from({ length: rows }).map((_, index) => (
        <div key={index} className="flex items-center gap-3.5 rounded-2xl px-2 py-3">
          <Box className="h-14 w-14 rounded-full" />
          <div className="flex-1 space-y-2.5">
            <Box className="h-3.5 w-1/3" />
            <Box className="h-3 w-2/3" />
          </div>
        </div>
      ))}
    </div>
  );
}

/** Placeholder for a photo grid - Likes and Standouts. */
function Cards({ count = 4 }: { count?: number }) {
  return (
    <div className="grid grid-cols-2 gap-3.5" role="status" aria-label="Loading">
      {Array.from({ length: count }).map((_, index) => (
        <div key={index} className="space-y-2.5">
          <Box className="aspect-[3/4] rounded-card" />
          <Box className="h-3.5 w-2/3" />
        </div>
      ))}
    </div>
  );
}

/**
 * Placeholder for the discovery feed.
 *
 * Shaped like the real card - header, photo, prompt, buttons - rather than a grey slab, so
 * the layout does not jump when the data lands.
 */
function Feed({ count = 2 }: { count?: number }) {
  return (
    <div className="space-y-5" role="status" aria-label="Loading">
      {Array.from({ length: count }).map((_, index) => (
        <div key={index} className="card overflow-hidden">
          <Box className="aspect-[4/5] rounded-none" />
          <div className="space-y-3 p-5">
            <Box className="h-5 w-1/2" />
            <Box className="h-3.5 w-1/3" />
            <div className="flex gap-2 pt-1">
              <Box className="h-7 w-20 rounded-pill" />
              <Box className="h-7 w-24 rounded-pill" />
            </div>
            <div className="flex gap-3 pt-3">
              <Box className="h-[52px] flex-1 rounded-pill" />
              <Box className="h-[52px] flex-1 rounded-pill" />
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

export const Skeleton = Object.assign(Box, { List, Cards, Feed });
