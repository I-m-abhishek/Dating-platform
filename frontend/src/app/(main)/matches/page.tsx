'use client';

import Link from 'next/link';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { TopBar } from '@/components/layout/TopBar';
import { Avatar } from '@/components/ui/Avatar';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { useMatches, useAutoMatch } from '@/lib/hooks/useMatches';
import { useEntitlements } from '@/lib/hooks/useEntitlements';
import { conversationTimestamp, compatibilityLabel } from '@/lib/utils/format';
import { messageOf } from '@/lib/api/errors';

/**
 * Matches.
 *
 * <p>Split into two: a rail of matches nobody has spoken in yet, and the list of live
 * conversations below. A single list buries the new ones, which are exactly the ones that
 * go stale if they are not acted on quickly.
 */
function MatchesPage() {
  const { query, newMatches, conversations } = useMatches();
  const { run, isRunning } = useAutoMatch();
  const { entitlements } = useEntitlements();

  const cadence = entitlements?.unlockedFeatures.includes('DAILY_AUTO_MATCH') ? 'today' : 'this week';

  return (
    <>
      <TopBar
        title="Matches"
        action={
          <Button size="sm" variant="ghost" loading={isRunning} onClick={() => void run()}>
            Find me a match
          </Button>
        }
      />

      <div className="space-y-6 p-4">
        <section className="card flex items-center gap-4 p-4">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-accent-soft text-lg">
            ✦
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-ink">Your auto-match</p>
            <p className="text-xs text-ink-muted">
              We pick someone for you {cadence}, based on your interests, qualities and what you are
              both looking for.
            </p>
          </div>
        </section>

        {query.isPending ? (
          <Skeleton.List rows={5} />
        ) : query.isError ? (
          <ErrorState description={messageOf(query.error)} onRetry={() => void query.refetch()} />
        ) : newMatches.length === 0 && conversations.length === 0 ? (
          <EmptyState
            title="No matches yet"
            description="Like a few profiles, and your weekly auto-match will do the rest."
          />
        ) : (
          <>
            {newMatches.length > 0 ? (
              <section className="space-y-3">
                <h2 className="text-sm font-medium text-ink-muted">
                  New matches · {newMatches.length}
                </h2>
                <ul className="hide-scrollbar flex gap-4 overflow-x-auto pb-1">
                  {newMatches.map((match) => (
                    <li key={match.id} className="w-20 shrink-0">
                      {/*
                        A brand new match goes to the profile, not the empty chat. You have
                        not spoken yet, so the useful first action is reading about them -
                        and the profile carries a Message button for when you are ready.
                      */}
                      <Link
                        href={`/u/${match.user.userId}`}
                        className="flex flex-col items-center gap-1.5 text-center"
                      >
                        <span className="rounded-full ring-2 ring-accent ring-offset-2 ring-offset-bg">
                          <Avatar
                            src={match.user.primaryPhotoUrl}
                            name={match.user.displayName}
                            size={64}
                          />
                        </span>
                        <span className="w-full truncate text-xs text-ink-muted">
                          {match.user.displayName}
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              </section>
            ) : null}

            {conversations.length > 0 ? (
              <section className="space-y-2">
                <h2 className="text-sm font-medium text-ink-muted">Conversations</h2>
                <ul className="divide-y divide-border">
                  {conversations.map((match) => (
                    <li key={match.id} className="flex items-center gap-3 py-3">
                      {/*
                        Two sibling links, not one wrapping the row: the avatar opens the
                        profile, the rest opens the chat. Nesting an anchor inside an anchor
                        is invalid HTML and breaks keyboard activation.
                      */}
                      <Link
                        href={`/u/${match.user.userId}`}
                        aria-label={`View ${match.user.displayName} profile`}
                        className="shrink-0 rounded-full"
                      >
                        <Avatar
                          src={match.user.primaryPhotoUrl}
                          name={match.user.displayName}
                          size={52}
                          online={match.user.recentlyActive}
                        />
                      </Link>
                      <Link
                        href={match.conversationId ? `/messages/${match.conversationId}` : '/matches'}
                        className="flex min-w-0 flex-1 items-center gap-3"
                      >
                        <div className="min-w-0 flex-1">
                          <div className="flex items-baseline justify-between gap-2">
                            <p className="truncate text-[15px] font-medium text-ink">
                              {match.user.displayName}
                            </p>
                            <span className="shrink-0 text-xs text-ink-subtle">
                              {conversationTimestamp(match.lastMessageAt)}
                            </span>
                          </div>
                          <p className="truncate text-sm text-ink-muted">
                            {match.lastMessagePreview ?? 'Say hello'}
                          </p>
                        </div>
                        {match.unreadCount > 0 ? (
                          <Badge tone="accent">{match.unreadCount}</Badge>
                        ) : match.source !== 'MUTUAL_LIKE' ? (
                          <Badge tone="muted">{compatibilityLabel(match.compatibilityScore)}</Badge>
                        ) : null}
                      </Link>
                    </li>
                  ))}
                </ul>
              </section>
            ) : null}
          </>
        )}
      </div>
    </>
  );
}

export default compose(withErrorBoundary, withAuth)(MatchesPage);
