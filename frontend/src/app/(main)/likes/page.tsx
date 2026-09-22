'use client';

import Image from 'next/image';
import Link from 'next/link';
import type { CSSProperties } from 'react';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { TopBar } from '@/components/layout/TopBar';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { LinkButton } from '@/components/ui/LinkButton';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { CloseIcon, HeartIcon, LockIcon, StarIcon } from '@/components/ui/icons';
import { useInboundLikes } from '@/lib/hooks/useLikes';
import { relativeTime, distanceLabel } from '@/lib/utils/format';
import { messageOf } from '@/lib/api/errors';

/**
 * The Likes You tab.
 *
 * <p>Free accounts see blurred tiles. Worth being precise about what that means: the server
 * sends no name, no photo URL and no note for a locked row, so the blur is a visual
 * treatment over an empty placeholder rather than a cover over real data. There is nothing
 * to recover from the network tab.
 */
function LikesPage() {
  const { overview, query, likeBack, passOn, isActing } = useInboundLikes();
  const rows = overview?.likes.items ?? [];

  return (
    <>
      <TopBar
        title="Likes"
        subtitle={
          overview
            ? `${overview.totalLikes} ${overview.totalLikes === 1 ? 'person likes' : 'people like'} you`
            : undefined
        }
      />

      <div className="space-y-5 p-4">
        {overview && !overview.revealed && overview.totalLikes > 0 ? (
          <section className="hairline-gradient relative overflow-hidden rounded-card bg-accent-gradient-soft p-5">
            <div className="flex items-center gap-4">
              <span className="relative flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-accent-gradient text-white shadow-glow">
                <span
                  aria-hidden
                  className="absolute inset-0 animate-pulse-ring rounded-full bg-accent opacity-40"
                />
                <LockIcon size={20} className="relative" />
              </span>

              <div className="min-w-0 flex-1">
                <p className="font-display text-[19px] font-semibold leading-tight text-ink">
                  {overview.totalLikes}{' '}
                  {overview.totalLikes === 1 ? 'person is waiting' : 'people are waiting'}
                </p>
                <p className="mt-0.5 text-[13px] leading-snug text-ink-muted">
                  {overview.upgradeHint ?? 'Upgrade to see who they are.'}
                </p>
              </div>

              <LinkButton href="/plans" size="sm" className="shrink-0">
                Reveal
              </LinkButton>
            </div>
          </section>
        ) : null}

        {query.isPending ? (
          <Skeleton.Cards count={4} />
        ) : query.isError ? (
          <ErrorState description={messageOf(query.error)} onRetry={() => void query.refetch()} />
        ) : rows.length === 0 ? (
          <EmptyState
            title="No likes yet"
            description="Keep your profile fresh - a new photo or prompt makes a real difference."
            action={
              <LinkButton href="/profile/edit" variant="outline">
                Polish my profile
              </LinkButton>
            }
          />
        ) : (
          <ul className="grid grid-cols-2 gap-3.5">
            {rows.map((like, index) => (
              <li
                key={like.likeId}
                className="stagger card-interactive overflow-hidden"
                style={{ '--i': Math.min(index, 8) } as CSSProperties}
              >
                <div className="relative aspect-[3/4] overflow-hidden bg-surface-muted">
                  {like.blurred ? (
                    <>
                      <div className="locked-preview absolute inset-0 bg-gradient-to-br from-accent/35 via-accent-2/25 to-surface-muted" />
                      <div className="absolute inset-0 flex flex-col items-center justify-center gap-2">
                        <span className="glass-dark flex h-11 w-11 items-center justify-center rounded-full text-white ring-1 ring-inset ring-white/25">
                          <LockIcon size={19} />
                        </span>
                        <span className="text-[11px] font-semibold uppercase tracking-[0.14em] text-white/85">
                          Hidden
                        </span>
                      </div>
                    </>
                  ) : like.user.primaryPhotoUrl ? (
                    <>
                      <Image
                        src={like.user.primaryPhotoUrl}
                        alt={like.user.displayName ?? ''}
                        fill
                        sizes="(max-width: 768px) 50vw, 300px"
                        className="object-cover transition-transform duration-500 ease-snap hover:scale-105"
                        unoptimized
                      />
                      <div aria-hidden className="absolute inset-0 bg-photo-scrim opacity-80" />
                    </>
                  ) : null}

                  {like.type === 'SUPER' ? (
                    <span className="absolute left-2.5 top-2.5">
                      <Badge tone="gold">
                        <StarIcon size={12} filled />
                        Super
                      </Badge>
                    </span>
                  ) : null}

                  {!like.blurred && like.user.displayName ? (
                    <div className="pointer-events-none absolute inset-x-0 bottom-0 p-3">
                      <p className="truncate font-display text-[17px] font-semibold leading-tight text-white">
                        {like.user.displayName}
                        <span className="font-sans text-sm font-medium text-white/75">
                          {' '}
                          {like.user.age}
                        </span>
                      </p>
                      <p className="truncate text-[11px] font-medium text-white/70">
                        {distanceLabel(like.user.distanceKm) ?? relativeTime(like.likedAt)}
                      </p>
                    </div>
                  ) : null}
                </div>

                <div className="space-y-2.5 p-3">
                  {like.blurred ? (
                    <>
                      <p className="text-[13px] font-semibold text-ink">Someone new</p>
                      <p className="text-[11px] font-medium text-ink-subtle">
                        {relativeTime(like.likedAt)}
                      </p>
                      <LinkButton href="/plans" size="sm" variant="outline" fullWidth>
                        Reveal
                      </LinkButton>
                    </>
                  ) : (
                    <>
                      <Link
                        href={`/u/${like.user.userId}`}
                        className="block text-[13px] font-semibold text-accent underline-offset-4 hover:underline"
                      >
                        View profile
                      </Link>

                      {like.note ? (
                        <p className="line-clamp-2 rounded-xl bg-surface-muted px-2.5 py-2 text-[12px] leading-snug text-ink-muted">
                          {like.note}
                        </p>
                      ) : null}

                      {/*
                        Stacked below 360px. Side by side, the square Pass button plus a
                        "Match" label needs more room than half a 320px screen leaves once
                        the grid gap and card padding are taken out.
                      */}
                      <div className="flex flex-col gap-2 min-[360px]:flex-row">
                        <Button
                          size="icon-sm"
                          variant="outline"
                          aria-label="Pass"
                          disabled={isActing}
                          onClick={() => like.user.userId && passOn(like.user.userId)}
                        >
                          <CloseIcon size={16} />
                        </Button>
                        <Button
                          size="sm"
                          fullWidth
                          disabled={isActing}
                          leftIcon={<HeartIcon size={15} />}
                          onClick={() => like.user.userId && likeBack(like.user.userId)}
                        >
                          Match
                        </Button>
                      </div>
                    </>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

export default compose(withErrorBoundary, withAuth)(LikesPage);
