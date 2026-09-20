'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { autoMatchApi, matchApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';
import { usePaywall } from './usePaywall';
import { useUiStore } from '@/lib/stores/uiStore';
import type { AutoMatchResult } from '@/lib/api/types';

export function useMatches(page = 0, size = 20) {
  const queryClient = useQueryClient();
  const { handleError } = usePaywall();
  const toast = useUiStore((state) => state.toast);

  const query = useQuery({
    queryKey: [...queryKeys.matches.list(), page, size],
    queryFn: () => matchApi.list(page, size),
  });

  const unmatch = useMutation({
    mutationFn: (input: { matchId: string; reason?: string; alsoBlock?: boolean }) =>
      matchApi.unmatch(input.matchId, input.reason, input.alsoBlock ?? false),
    onSuccess: () => {
      toast({ title: 'Unmatched', tone: 'default' });
      // A match disappearing also closes its conversation, so both trees are invalidated.
      void queryClient.invalidateQueries({ queryKey: queryKeys.matches.all });
      void queryClient.invalidateQueries({ queryKey: queryKeys.chat.all });
    },
    onError: handleError,
  });

  const matches = query.data?.items ?? [];

  return {
    query,
    matches,
    /** The "new matches" rail: matched, but nobody has spoken yet. */
    newMatches: matches.filter((match) => match.isNew),
    conversations: matches.filter((match) => !match.isNew),
    unmatch: unmatch.mutate,
  };
}

export function useMatchCount() {
  return useQuery({
    queryKey: queryKeys.matches.count(),
    queryFn: matchApi.count,
    select: (data) => data.count,
    refetchInterval: 120_000,
  });
}

/**
 * Auto-match on demand.
 *
 * <p>An outcome of NO_CANDIDATE or BELOW_THRESHOLD is a success, not an error: the engine
 * would rather return nothing than a bad match, and the UI says so plainly. Only a spent
 * allowance produces a paywall.
 */
export function useAutoMatch() {
  const queryClient = useQueryClient();
  const { handleError } = usePaywall();
  const toast = useUiStore((state) => state.toast);

  const run = useMutation({
    mutationFn: autoMatchApi.run,
    onSuccess: (result: AutoMatchResult) => {
      if (result.outcome === 'MATCHED') {
        void queryClient.invalidateQueries({ queryKey: queryKeys.matches.all });
        void queryClient.invalidateQueries({ queryKey: queryKeys.chat.conversations() });
        toast({ title: 'Your match is ready', tone: 'success' });
      } else {
        toast({ title: result.message, tone: 'default' });
      }
    },
    onError: handleError,
  });

  return {
    run: run.mutateAsync,
    isRunning: run.isPending,
    result: run.data,
  };
}
