'use client';

import { useCallback, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { discoveryApi, likeApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';
import { usePaywall } from './usePaywall';
import { useUiStore } from '@/lib/stores/uiStore';
import type { FeedCard, FeedFilter, LikeResult } from '@/lib/api/types';

const DEFAULT_FILTER: FeedFilter = { sort: 'RECOMMENDED' };

/**
 * The home feed plus the two actions that consume it.
 *
 * <p>Liking and passing update the cached page immediately - the card leaves the stack
 * before the request finishes, because waiting a round trip to remove a card someone just
 * swiped feels broken. On failure the page is refetched, which puts the card back.
 */
export function useFeed(initialFilter: FeedFilter = DEFAULT_FILTER) {
  const queryClient = useQueryClient();
  const { handleError } = usePaywall();
  const toast = useUiStore((state) => state.toast);

  const [filter, setFilter] = useState<FeedFilter>(initialFilter);
  const [page, setPage] = useState(0);

  const queryKey = useMemo(() => queryKeys.discovery.feed({ filter, page }), [filter, page]);

  const feedQuery = useQuery({
    queryKey,
    queryFn: () => discoveryApi.feed(filter, page, 10),
    staleTime: 30_000,
  });

  const removeCard = useCallback(
    (userId: string) => {
      queryClient.setQueryData<{ items: FeedCard[] } | undefined>(queryKey, (current) => {
        if (!current) return current;
        return { ...current, items: current.items.filter((card) => card.userId !== userId) };
      });
    },
    [queryClient, queryKey],
  );

  const likeMutation = useMutation({
    mutationFn: (input: { targetUserId: string; note?: string; targetPhotoId?: string; superLike?: boolean }) =>
      likeApi.like({
        targetUserId: input.targetUserId,
        note: input.note,
        targetPhotoId: input.targetPhotoId,
        type: input.superLike ? 'SUPER' : 'STANDARD',
      }),
    onMutate: (input) => {
      removeCard(input.targetUserId);
    },
    onSuccess: (result: LikeResult) => {
      if (result.matched) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.matches.all });
        void queryClient.invalidateQueries({ queryKey: queryKeys.chat.conversations() });
      }
    },
    onError: (error) => {
      handleError(error);
      void feedQuery.refetch();
    },
  });

  const passMutation = useMutation({
    mutationFn: (targetUserId: string) => likeApi.pass(targetUserId),
    onMutate: (targetUserId) => {
      removeCard(targetUserId);
    },
    onError: (error) => {
      handleError(error);
      void feedQuery.refetch();
    },
  });

  const rewindMutation = useMutation({
    mutationFn: likeApi.rewind,
    onSuccess: () => {
      toast({ title: 'Brought them back', tone: 'success' });
      void feedQuery.refetch();
    },
    onError: handleError,
  });

  const applyFilter = useCallback((next: FeedFilter) => {
    setFilter(next);
    setPage(0);
  }, []);

  return {
    cards: feedQuery.data?.items ?? [],
    query: feedQuery,
    filter,
    applyFilter,
    page,
    setPage,
    hasMore: feedQuery.data ? !feedQuery.data.last : false,
    like: likeMutation.mutateAsync,
    pass: passMutation.mutateAsync,
    rewind: rewindMutation.mutate,
    isActing: likeMutation.isPending || passMutation.isPending,
  };
}
