'use client';

import Image from 'next/image';
import Link from 'next/link';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { TopBar } from '@/components/layout/TopBar';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { LinkButton } from '@/components/ui/LinkButton';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
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
        subtitle={overview ? `${overview.totalLikes} people like you` : undefined}
      />

      <div className="space-y-4 p-4">
        {overview && !overview.revealed && overview.totalLikes > 0 ? (
          <div className="card flex items-center gap-4 p-4">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-accent-soft text-lg">
              ♡
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-medium text-ink">
                {overview.totalLikes} {overview.totalLikes === 1 ? 'person likes' : 'people like'} you
              </p>
              <p className="text-xs text-ink-muted">
                {overview.upgradeHint ?? 'Upgrade to see who they are.'}
              </p>
            </div>
            <LinkButton href="/plans" size="sm">
              Upgrade
            </LinkButton>
          </div>
        ) : null}

        {query.isPending ? (
          <Skeleton.Cards count={4} />
        ) : query.isError ? (
          <ErrorState description={messageOf(query.error)} onRetry={() => void query.refetch()} />
        ) : rows.length === 0 ? (
          <EmptyState
            title="No likes yet"
            description="Keep your profile fresh - a new photo or prompt makes a real difference."
          />
        ) : (
          <ul className="grid grid-cols-2 gap-3">
            {rows.map((like) => (
              <li key={like.likeId} className="overflow-hidden rounded-card border border-border bg-surface">
                <div className="relative aspect-[3/4] bg-surface-muted">
                  {like.blurred ? (
                    <div className="locked-preview absolute inset-0 bg-gradient-to-br from-accent-soft via-surface-muted to-border" />
                  ) : like.user.primaryPhotoUrl ? (
                    <Image
                      src={like.user.primaryPhotoUrl}
                      alt={like.user.displayName ?? ''}
                      fill
                      sizes="(max-width: 768px) 50vw, 300px"
                      className="object-cover"
                      unoptimized
                    />
                  ) : null}

                  {like.type === 'SUPER' ? (
                    <span className="absolute left-2 top-2">
                      <Badge tone="accent">Super like</Badge>
                    </span>
                  ) : null}

                  {like.blurred ? (
                    <div className="absolute inset-0 flex items-center justify-center">
                      <span className="rounded-pill bg-black/45 px-3 py-1.5 text-xs text-white backdrop-blur-sm">
                        Hidden
                      </span>
                    </div>
                  ) : null}
                </div>

                <div className="space-y-2 p-3">
                  {like.blurred ? (
                    <>
                      <p className="text-sm font-medium text-ink">Someone</p>
                      <p className="text-xs text-ink-subtle">{relativeTime(like.likedAt)}</p>
                      <LinkButton href="/plans" size="sm" variant="outline" fullWidth>
                        Reveal
                      </LinkButton>
                    </>
                  ) : (
                    <>
                      <Link href={`/u/${like.user.userId}`} className="block">
                        <p className="truncate text-sm font-medium text-ink">
                          {like.user.displayName}, {like.user.age}
                        </p>
                        <p className="truncate text-xs text-ink-subtle">
                          {distanceLabel(like.user.distanceKm) ?? relativeTime(like.likedAt)}
                        </p>
                      </Link>
                      {like.note ? (
                        <p className="line-clamp-2 rounded-xl bg-surface-muted px-2.5 py-2 text-xs text-ink-muted">
                          {like.note}
                        </p>
                      ) : null}
                      <div className="flex gap-2">
                        <Button
                          size="sm"
                          variant="outline"
                          fullWidth
                          disabled={isActing}
                          onClick={() => like.user.userId && passOn(like.user.userId)}
                        >
                          Pass
                        </Button>
                        <Button
                          size="sm"
                          fullWidth
                          disabled={isActing}
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
