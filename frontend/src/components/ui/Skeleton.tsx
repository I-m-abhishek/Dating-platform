import { cn } from '@/lib/utils/cn';

function Box({ className }: { className?: string }) {
  return (
    <div
      aria-hidden
      className={cn('relative overflow-hidden rounded-xl bg-surface-muted', className)}
    >
      <div className="absolute inset-0 -translate-x-full animate-shimmer bg-gradient-to-r from-transparent via-black/5 to-transparent" />
    </div>
  );
}

function List({ rows = 3 }: { rows?: number }) {
  return (
    <div className="space-y-3" role="status" aria-label="Loading">
      {Array.from({ length: rows }).map((_, index) => (
        <div key={index} className="flex items-center gap-3">
          <Box className="h-14 w-14 rounded-full" />
          <div className="flex-1 space-y-2">
            <Box className="h-3.5 w-1/3" />
            <Box className="h-3 w-2/3" />
          </div>
        </div>
      ))}
    </div>
  );
}

function Cards({ count = 4 }: { count?: number }) {
  return (
    <div className="grid grid-cols-2 gap-3" role="status" aria-label="Loading">
      {Array.from({ length: count }).map((_, index) => (
        <Box key={index} className="aspect-[3/4] rounded-card" />
      ))}
    </div>
  );
}

export const Skeleton = Object.assign(Box, { List, Cards });
