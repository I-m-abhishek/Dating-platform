'use client';

import { useState } from 'react';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { TopBar } from '@/components/layout/TopBar';
import { FeedCardView } from '@/components/discovery/FeedCardView';
import { FilterSheet } from '@/components/discovery/FilterSheet';
import { PhotoCommentSheet } from '@/components/profile/PhotoCommentSheet';
import { Button } from '@/components/ui/Button';
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
  const { cards, query, filter, applyFilter, page, setPage, hasMore, like, pass, isActing } = useFeed({
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

  const onLike = async (userId: string, photo?: Photo) => {
    const result = await like({ targetUserId: userId, targetPhotoId: photo?.id });
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
        action={
          <Button variant="ghost" size="sm" onClick={() => setFiltersOpen(true)}>
            Filters
          </Button>
        }
      />

      <div className="space-y-4 p-4">
        {query.isPending ? (
          <Skeleton.Cards count={2} />
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
          <>
            {cards.map((card) => (
              <FeedCardView
                key={card.userId}
                card={card}
                busy={isActing}
                onLike={(photo) => void onLike(card.userId, photo)}
                onPass={() => void pass(card.userId)}
                onCommentPhoto={setCommentPhoto}
              />
            ))}

            {hasMore ? (
              <Button variant="outline" fullWidth onClick={() => setPage(page + 1)}>
                Show more
              </Button>
            ) : null}
          </>
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
