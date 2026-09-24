'use client';

import { useCallback, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { accountApi, discoveryApi, likeApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';
import { usePaywall } from './usePaywall';
import { useUiStore } from '@/lib/stores/uiStore';
import { useAuthStore } from '@/lib/stores/authStore';
import type { FeedCard, FeedFilter, LikeIntent, LikeResult } from '@/lib/api/types';

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
    mutationFn: (input: { targetUserId: string } & LikeIntent) =>
      likeApi.like({
        targetUserId: input.targetUserId,
        note: input.note,
        targetPhotoId: input.targetPhotoId,
        targetPromptAnswerId: input.targetPromptAnswerId,
        type: input.superLike ? 'SUPER' : 'STANDARD',
      }),
    onMutate: (input) => {
      removeCard(input.targetUserId);
    },
    onSuccess: (result: LikeResult) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.likes.quota() });
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

/**
 * The saved filter sheet. Age, distance and "show me" come back from the account's
 * preferences; paid filters are omitted by the server for accounts without them.
 */
export function useSavedFilters() {
  return useQuery({
    queryKey: queryKeys.discovery.filters(),
    queryFn: discoveryApi.filters,
    staleTime: 5 * 60_000,
  });
}

/**
 * Saves the filter sheet. The server also writes age, distance and "show me" through to
 * the account, so the cached account is refreshed to keep Settings and the profile in step.
 */
export function useSaveFilters() {
  const queryClient = useQueryClient();
  const setAccount = useAuthStore((state) => state.setAccount);

  return useMutation({
    mutationFn: discoveryApi.saveFilters,
    onSuccess: (saved) => {
      queryClient.setQueryData(queryKeys.discovery.filters(), saved);
      void accountApi.me().then(setAccount);
      void queryClient.invalidateQueries({ queryKey: queryKeys.account.all });
    },
  });
}
