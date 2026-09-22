'use client';

import Link from 'next/link';
import Image from 'next/image';
import type { CSSProperties } from 'react';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { TopBar } from '@/components/layout/TopBar';
import { Badge } from '@/components/ui/Badge';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { ChevronRightIcon, SparkleIcon } from '@/components/ui/icons';
import { useStandouts } from '@/lib/hooks/useStandouts';
import { compatibilityLabel, distanceLabel } from '@/lib/utils/format';
import { messageOf } from '@/lib/api/errors';
import type { Standout } from '@/lib/api/types';

/**
 * Standouts.
 *
 * <p>Each tile says <em>why</em> it is here - popular this week, new, active now. A ranked
 * shelf with no explanation reads as arbitrary; one that explains itself reads as curation.
 *
 * <p>The top-ranked person gets a full-width tile. A flat grid says "here are twelve people";
 * a grid with a lead says "here is the one, and then eleven more", which is what a ranked
 * shelf actually means.
 */
function StandoutsPage() {
  const query = useStandouts(12);
  const standouts = query.data ?? [];
  const [lead, ...rest] = standouts;

  return (
    <>
      <TopBar title="Standouts" subtitle="Refreshed through the day" />

      <div className="space-y-4 p-4">
        {query.isPending ? (
          <Skeleton.Cards count={6} />
        ) : query.isError ? (
          <ErrorState description={messageOf(query.error)} onRetry={() => void query.refetch()} />
        ) : standouts.length === 0 ? (
          <EmptyState
            icon={<SparkleIcon size={30} />}
            title="Nothing to show yet"
            description="Standouts appear once there are enough people nearby."
          />
        ) : (
          <>
            {lead ? <Tile standout={lead} featured index={0} /> : null}

            {rest.length > 0 ? (
              <ul className="grid grid-cols-2 gap-3.5">
                {rest.map((standout, index) => (
                  <li key={standout.userId}>
                    <Tile standout={standout} index={index + 1} />
                  </li>
                ))}
              </ul>
            ) : null}
          </>
        )}
      </div>
    </>
  );
}

function Tile({
  standout,
  featured,
  index,
}: {
  standout: Standout;
  featured?: boolean;
  index: number;
}) {
  const photo = standout.photos[0];
  const meta = [compatibilityLabel(standout.compatibilityScore), distanceLabel(standout.distanceKm)]
    .filter(Boolean)
    .join(' · ');

  return (
    <Link
      href={`/u/${standout.userId}`}
      style={{ '--i': Math.min(index, 8) } as CSSProperties}
      className="stagger group relative block overflow-hidden rounded-card border border-border bg-surface shadow-card transition-[transform,box-shadow] duration-300 ease-snap hover:-translate-y-1 hover:shadow-lift"
    >
      <div
        className={`relative overflow-hidden bg-surface-muted ${featured ? 'aspect-[16/11]' : 'aspect-[3/4]'}`}
      >
        {photo ? (
          <Image
            src={photo.url}
            alt={standout.displayName}
            fill
            sizes={featured ? '(max-width: 768px) 100vw, 640px' : '(max-width: 768px) 50vw, 300px'}
            className="object-cover transition-transform duration-700 ease-snap group-hover:scale-[1.06]"
            unoptimized
          />
        ) : null}

        <div aria-hidden className="absolute inset-0 bg-photo-scrim" />

        <span className="absolute left-3 top-3">
          <Badge tone={featured ? 'gold' : 'glass'}>
            <SparkleIcon size={12} />
            {standout.reasonLabel}
          </Badge>
        </span>

        <div className="absolute inset-x-0 bottom-0 flex items-end justify-between gap-3 p-4">
          <div className="min-w-0">
            <p
              className={`truncate font-display font-semibold leading-tight text-white ${featured ? 'text-[26px]' : 'text-[17px]'}`}
            >
              {standout.displayName}
              <span className="font-sans font-medium text-white/75"> {standout.age}</span>
            </p>
            {meta ? (
              <p
                className={`truncate font-medium text-white/70 ${featured ? 'mt-1 text-[13px]' : 'text-[11px]'}`}
              >
                {meta}
              </p>
            ) : null}
          </div>

          {featured ? (
            <span className="glass-dark flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-white ring-1 ring-inset ring-white/20 transition-transform duration-300 ease-snap group-hover:translate-x-0.5">
              <ChevronRightIcon size={18} />
            </span>
          ) : null}
        </div>
      </div>
    </Link>
  );
}

export default compose(withErrorBoundary, withAuth)(StandoutsPage);
