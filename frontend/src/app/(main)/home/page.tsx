'use client';

import { useEffect, useRef } from 'react';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { TopBar } from '@/components/layout/TopBar';
import { SwipeDeck } from '@/components/discovery/SwipeDeck';
import { FilterSheet } from '@/components/discovery/FilterSheet';
import { Button } from '@/components/ui/Button';
import { SlidersIcon } from '@/components/ui/icons';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { useFeed, useSaveFilters, useSavedFilters } from '@/lib/hooks/useFeed';
import { usePaywall } from '@/lib/hooks/usePaywall';
import { useAuthStore } from '@/lib/stores/authStore';
import { useUiStore } from '@/lib/stores/uiStore';
import { messageOf } from '@/lib/api/errors';
import type { FeedFilter, LikeIntent } from '@/lib/api/types';

/**
 * The home feed.
 *
 * <p>Filters live in a sheet rather than a permanent bar: the common case is browsing with
 * saved preferences, and a filter row on every screen costs more attention than it earns.
 */
function HomePage() {
  const account = useAuthStore((state) => state.account);
  const saved = useSavedFilters();

  // The feed starts from the SAVED filters, so it must wait for them - starting from
  // defaults and then swapping would flash the wrong people first.
  if (saved.isPending) {
    return (
      <>
        <TopBar title="Discover" subtitle="People we think you will get on with" />
        <div className="px-4 pb-4 pt-2">
          <Skeleton.Feed count={1} />
        </div>
      </>
    );
  }

  // If the saved filters cannot be read, browse on the account's preferences instead.
  const initialFilter: FeedFilter = saved.data ?? {
    sort: 'RECOMMENDED',
    minAge: account?.preferredMinAge,
    maxAge: account?.preferredMaxAge,
    maxDistanceKm: account?.preferredMaxDistanceKm,
    genders: account?.interestedIn,
  };

  return <Discover initialFilter={initialFilter} />;
}

function Discover({ initialFilter }: { initialFilter: FeedFilter }) {
  const { cards, query, filter, applyFilter, hasMore, like, pass } = useFeed(initialFilter);
  const saveFilters = useSaveFilters();
  const { handleError } = usePaywall();

  /*
   * Filters are saved, not just applied: age, distance and "show me" are the account's
   * preferences (they also drive auto-match, Settings and the profile), and the rest is
   * stored so the sheet looks the same on the next visit and on another device.
   */
  const onApplyFilter = async (next: FeedFilter): Promise<boolean> => {
    try {
      const stored = await saveFilters.mutateAsync(next);
      applyFilter({ ...stored, sort: next.sort });
      toast({ title: 'Filters saved', tone: 'success' });
      return true;
    } catch (error) {
      handleError(error);
      return false;
    }
  };
  const filtersOpen = useUiStore((state) => state.filtersOpen);
  const setFiltersOpen = useUiStore((state) => state.setFiltersOpen);
  const toast = useUiStore((state) => state.toast);


  /*
   * Swiped people are excluded server-side, so when the deck runs dry the same page simply
   * returns the next people. Refetch once per loaded batch - never in a loop.
   */
  const refilledFor = useRef<number | null>(null);
  useEffect(() => {
    if (cards.length > 0 || !hasMore || query.isFetching) return;
    if (refilledFor.current === query.dataUpdatedAt) return;
    refilledFor.current = query.dataUpdatedAt;
    void query.refetch();
  }, [cards.length, hasMore, query]);

  const onLike = async (userId: string, intent: LikeIntent) => {
    // Failures are already surfaced (paywall or toast) by the hook's onError.
    const result = await like({ targetUserId: userId, ...intent }).catch(() => null);
    if (!result) return;
    if (result.matched) {
      toast({
        title: 'It is a match',
        description: intent.note
          ? 'Your comment is waiting for them in the chat.'
          : 'Say hello before the moment passes.',
        tone: 'success',
      });
    } else if (intent.superLike) {
      toast({ title: 'Super like sent', description: 'You are at the top of their likes.', tone: 'success' });
    } else if (intent.note) {
      toast({ title: 'Like sent with your comment', tone: 'success' });
    }
  };

  return (
    <>
      <TopBar
        title="Discover"
        subtitle="People we think you will get on with"
        action={
          <Button
            variant="outline"
            size="sm"
            onClick={() => setFiltersOpen(true)}
            leftIcon={<SlidersIcon size={16} />}
          >
            Filters
          </Button>
        }
      />

      <div className="px-4 pb-4 pt-2">
        {query.isPending || (cards.length === 0 && hasMore) ? (
          <Skeleton.Feed count={1} />
        ) : query.isError ? (
          <ErrorState description={messageOf(query.error)} onRetry={() => void query.refetch()} />
        ) : cards.length === 0 ? (
          <EmptyState
            title="That is everyone for now"
            description="Widen your distance or age range, or check back later - new people join every day."
            action={
              <Button variant="outline" onClick={() => setFiltersOpen(true)}>
                Adjust filters
              </Button>
            }
          />
        ) : (
          <SwipeDeck
            cards={cards}
            onLike={(card, intent) => void onLike(card.userId, intent)}
            onPass={(card) => void pass(card.userId).catch(() => undefined)}
          />
        )}
      </div>

      <FilterSheet
        open={filtersOpen}
        value={filter}
        saving={saveFilters.isPending}
        onClose={() => setFiltersOpen(false)}
        onApply={onApplyFilter}
      />
    </>
  );
}

export default compose(withErrorBoundary, withAuth)(HomePage);
