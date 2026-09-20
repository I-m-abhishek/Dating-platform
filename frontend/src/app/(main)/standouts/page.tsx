'use client';

import Link from 'next/link';
import Image from 'next/image';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { TopBar } from '@/components/layout/TopBar';
import { Badge } from '@/components/ui/Badge';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { useStandouts } from '@/lib/hooks/useStandouts';
import { compatibilityLabel, distanceLabel } from '@/lib/utils/format';
import { messageOf } from '@/lib/api/errors';

/**
 * Standouts.
 *
 * <p>Each tile says <em>why</em> it is here - popular this week, new, active now. A ranked
 * shelf with no explanation reads as arbitrary; one that explains itself reads as curation.
 */
function StandoutsPage() {
  const query = useStandouts(12);

  return (
    <>
      <TopBar title="Standouts" subtitle="Refreshed through the day" />

      <div className="p-4">
        {query.isPending ? (
          <Skeleton.Cards count={6} />
        ) : query.isError ? (
          <ErrorState description={messageOf(query.error)} onRetry={() => void query.refetch()} />
        ) : (query.data ?? []).length === 0 ? (
          <EmptyState
            title="Nothing to show yet"
            description="Standouts appear once there are enough people nearby."
          />
        ) : (
          <ul className="grid grid-cols-2 gap-3">
            {(query.data ?? []).map((standout) => {
              const photo = standout.photos[0];
              return (
                <li key={standout.userId}>
                  <Link
                    href={`/u/${standout.userId}`}
                    className="group block overflow-hidden rounded-card border border-border bg-surface"
                  >
                    <div className="relative aspect-[3/4] bg-surface-muted">
                      {photo ? (
                        <Image
                          src={photo.url}
                          alt={standout.displayName}
                          fill
                          sizes="(max-width: 768px) 50vw, 300px"
                          className="object-cover transition-transform duration-300 group-hover:scale-[1.02]"
                          unoptimized
                        />
                      ) : null}
                      <span className="absolute left-2 top-2">
                        <Badge tone="accent">{standout.reasonLabel}</Badge>
                      </span>
                    </div>
                    <div className="space-y-0.5 p-3">
                      <p className="truncate text-sm font-medium text-ink">
                        {standout.displayName}, {standout.age}
                      </p>
                      <p className="truncate text-xs text-ink-subtle">
                        {[compatibilityLabel(standout.compatibilityScore), distanceLabel(standout.distanceKm)]
                          .filter(Boolean)
                          .join(' · ')}
                      </p>
                    </div>
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </>
  );
}

export default compose(withErrorBoundary, withAuth)(StandoutsPage);
