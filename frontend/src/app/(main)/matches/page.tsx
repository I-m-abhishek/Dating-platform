'use client';

import Link from 'next/link';
import type { CSSProperties } from 'react';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { TopBar } from '@/components/layout/TopBar';
import { Avatar } from '@/components/ui/Avatar';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { MatchIcon, SparkleIcon } from '@/components/ui/icons';
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
      <TopBar title="Matches" />

      <div className="space-y-7 p-4">
        {/*
          The auto-match panel is the one piece of the product that works while you are not
          looking, so it gets a surface of its own rather than a line of small print.
        */}
        <section className="hairline-gradient relative overflow-hidden rounded-card bg-accent-gradient-soft p-5">
          <div className="flex items-start gap-4">
            <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-accent-gradient text-white shadow-glow">
              <SparkleIcon size={22} />
            </span>
            <div className="min-w-0 flex-1">
              <p className="font-display text-[19px] font-semibold leading-tight text-ink">
                Your auto-match
              </p>
              <p className="mt-1 text-[13px] leading-relaxed text-ink-muted">
                We pick someone for you {cadence}, based on your interests, qualities and what you
                are both looking for.
              </p>
              <Button
                size="sm"
                className="mt-3.5"
                loading={isRunning}
                onClick={() => void run()}
                leftIcon={<SparkleIcon size={15} />}
              >
                Find me a match
              </Button>
            </div>
          </div>
        </section>

        {query.isPending ? (
          <Skeleton.List rows={5} />
        ) : query.isError ? (
          <ErrorState description={messageOf(query.error)} onRetry={() => void query.refetch()} />
        ) : newMatches.length === 0 && conversations.length === 0 ? (
          <EmptyState
            icon={<MatchIcon size={30} />}
            title="No matches yet"
            description="Like a few profiles, and your weekly auto-match will do the rest."
          />
        ) : (
          <>
            {newMatches.length > 0 ? (
              <section className="space-y-3.5">
                <h2 className="eyebrow px-1">New matches · {newMatches.length}</h2>

                <ul className="hide-scrollbar -mx-4 flex gap-4 overflow-x-auto px-4 pb-2">
                  {newMatches.map((match, index) => (
                    <li
                      key={match.id}
                      className="stagger w-[4.5rem] shrink-0"
                      style={{ '--i': Math.min(index, 8) } as CSSProperties}
                    >
                      {/*
                        A brand new match goes to the profile, not the empty chat. You have
                        not spoken yet, so the useful first action is reading about them -
                        and the profile carries a Message button for when you are ready.
                      */}
                      <Link
                        href={`/u/${match.user.userId}`}
                        className="group flex flex-col items-center gap-2 text-center"
                      >
                        <span className="relative">
                          <span
                            aria-hidden
                            className="absolute -inset-1 animate-pulse-ring rounded-full bg-accent opacity-25"
                          />
                          <Avatar
                            src={match.user.primaryPhotoUrl}
                            name={match.user.displayName}
                            size={62}
                            ring="gradient"
                            className="relative transition-transform duration-300 ease-snap group-hover:scale-105"
                          />
                        </span>
                        <span className="w-full truncate text-[11.5px] font-semibold text-ink-muted">
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
                <h2 className="eyebrow px-1">Conversations</h2>

                <ul className="-mx-2">
                  {conversations.map((match, index) => (
                    <li
                      key={match.id}
                      className="stagger flex items-center gap-3.5 rounded-2xl px-2 py-2.5 transition-colors hover:bg-surface-muted"
                      style={{ '--i': Math.min(index, 8) } as CSSProperties}
                    >
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
                          size={54}
                          online={match.user.recentlyActive}
                        />
                      </Link>

                      <Link
                        href={match.conversationId ? `/messages/${match.conversationId}` : '/matches'}
                        className="flex min-w-0 flex-1 items-center gap-3 py-1"
                      >
                        <div className="min-w-0 flex-1">
                          <div className="flex items-baseline justify-between gap-2">
                            <p className="truncate text-[15px] font-semibold text-ink">
                              {match.user.displayName}
                            </p>
                            <span className="shrink-0 text-[11px] font-medium text-ink-subtle">
                              {conversationTimestamp(match.lastMessageAt)}
                            </span>
                          </div>
                          <p
                            className={
                              match.unreadCount > 0
                                ? 'truncate text-sm font-medium text-ink'
                                : 'truncate text-sm text-ink-muted'
                            }
                          >
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
