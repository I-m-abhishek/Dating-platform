'use client';

import { useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { likeApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';
import { usePaywall } from './usePaywall';

/**
 * The Likes You tab.
 *
 * <p>The {@code revealed} flag comes from the server, and so does the redaction: when it is
 * false the rows genuinely contain no identifying data. The UI blurs a placeholder for
 * effect, but there is nothing to un-blur.
 */
export function useInboundLikes(page = 0, size = 20) {
  const queryClient = useQueryClient();
  const { handleError } = usePaywall();

  const query = useQuery({
    queryKey: [...queryKeys.likes.inbound(), page, size],
    queryFn: () => likeApi.inbound(page, size),
  });

  const markSeen = useMutation({
    mutationFn: likeApi.markSeen,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.likes.unseenCount() });
    },
  });

  // Opening the tab clears the badge; the rows stay, only the "new" marker goes.
  useEffect(() => {
    if (query.data && query.data.newLikes > 0) {
      markSeen.mutate();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query.data?.newLikes]);

  const likeBack = useMutation({
    mutationFn: (targetUserId: string) => likeApi.like({ targetUserId }),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.likes.all });
      if (result.matched) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.matches.all });
        // A match opens a conversation; the Messages tab should show it without a refresh.
        void queryClient.invalidateQueries({ queryKey: queryKeys.chat.conversations() });
      }
    },
    onError: handleError,
  });

  const passOn = useMutation({
    mutationFn: (targetUserId: string) => likeApi.pass(targetUserId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.likes.all });
    },
    onError: handleError,
  });

  return {
    query,
    overview: query.data,
    likeBack: likeBack.mutate,
    passOn: passOn.mutate,
    isActing: likeBack.isPending || passOn.isPending,
  };
}

export function useUnseenLikeCount() {
  return useQuery({
    queryKey: queryKeys.likes.unseenCount(),
    queryFn: likeApi.unseenCount,
    refetchInterval: 60_000,
    select: (data) => data.count,
  });
}
