'use client';

import { useEffect, useRef, useState } from 'react';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { TopBar } from '@/components/layout/TopBar';
import { SwipeDeck } from '@/components/discovery/SwipeDeck';
import { FilterSheet } from '@/components/discovery/FilterSheet';
import { PhotoCommentSheet } from '@/components/profile/PhotoCommentSheet';
import { Button } from '@/components/ui/Button';
import { SlidersIcon } from '@/components/ui/icons';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useFeed } from '@/lib/hooks/useFeed';
import { accountApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';
import { useAuthStore } from '@/lib/stores/authStore';
import { useUiStore } from '@/lib/stores/uiStore';
import { messageOf } from '@/lib/api/errors';
import type { FeedFilter, Photo } from '@/lib/api/types';

/**
 * The home feed.
 *
 * <p>Filters live in a sheet rather than a permanent bar: the common case is browsing with
 * saved preferences, and a filter row on every screen costs more attention than it earns.
 */
function HomePage() {
  const account = useAuthStore((state) => state.account);
  const setAccount = useAuthStore((state) => state.setAccount);
  const queryClient = useQueryClient();

  // Seed the feed from the saved preferences so the sheet opens showing the real numbers
  // rather than hardcoded defaults. withAuth guarantees the account is loaded by now.
  const { cards, query, filter, applyFilter, hasMore, like, pass } = useFeed({
    sort: 'RECOMMENDED',
    minAge: account?.preferredMinAge,
    maxAge: account?.preferredMaxAge,
    maxDistanceKm: account?.preferredMaxDistanceKm,
  });

  const savePreferences = useMutation({
    mutationFn: accountApi.updatePreferences,
    onSuccess: (updated) => {
      setAccount(updated);
      void queryClient.invalidateQueries({ queryKey: queryKeys.account.all });
    },
  });

  /*
   * Distance and age are real preferences, not a per-request whim: they also drive the
   * auto-match engine and the Settings screen. Persisting them here keeps all three in
   * agreement instead of letting the feed disagree with what the user saved.
   */
  const onApplyFilter = (next: FeedFilter) => {
    applyFilter(next);

    const changed =
      next.minAge !== account?.preferredMinAge ||
      next.maxAge !== account?.preferredMaxAge ||
      next.maxDistanceKm !== account?.preferredMaxDistanceKm;

    if (changed) {
      savePreferences.mutate({
        preferredMinAge: next.minAge,
        preferredMaxAge: next.maxAge,
        preferredMaxDistanceKm: next.maxDistanceKm,
      });
    }
  };
  const filtersOpen = useUiStore((state) => state.filtersOpen);
  const setFiltersOpen = useUiStore((state) => state.setFiltersOpen);
  const toast = useUiStore((state) => state.toast);

  const [commentPhoto, setCommentPhoto] = useState<Photo | null>(null);

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

  const onLike = async (userId: string) => {
    // Failures are already surfaced (paywall or toast) by the hook's onError.
    const result = await like({ targetUserId: userId }).catch(() => null);
    if (!result) return;
    if (result.matched) {
      toast({
        title: 'It is a match',
        description: 'Say hello before the moment passes.',
        tone: 'success',
      });
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
            onLike={(card) => void onLike(card.userId)}
            onPass={(card) => void pass(card.userId).catch(() => undefined)}
            onComment={setCommentPhoto}
          />
        )}
      </div>

      <FilterSheet
        open={filtersOpen}
        value={filter}
        onClose={() => setFiltersOpen(false)}
        onApply={onApplyFilter}
      />
      <PhotoCommentSheet photo={commentPhoto} onClose={() => setCommentPhoto(null)} />
    </>
  );
}

export default compose(withErrorBoundary, withAuth)(HomePage);
