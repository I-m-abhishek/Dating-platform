'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { commentApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';
import { usePaywall } from './usePaywall';

/**
 * Photo comments and the daily allowance that governs them.
 *
 * <p>The limit is never assumed client side. {@link useCommentQuota} reads it from the
 * server, and a spent allowance comes back as a QUOTA_EXCEEDED error that
 * {@code usePaywall} turns into the upgrade sheet, with the real reset time.
 */
export function usePhotoComments(photoId: string | null, page = 0, size = 20) {
  const queryClient = useQueryClient();
  const { handleError } = usePaywall();

  const query = useQuery({
    queryKey: [...queryKeys.comments.forPhoto(photoId ?? ''), page, size],
    queryFn: () => commentApi.list(photoId as string, page, size),
    enabled: Boolean(photoId),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.comments.all });
    void queryClient.invalidateQueries({ queryKey: queryKeys.profile.all });
  };

  const create = useMutation({
    mutationFn: (input: { body: string; parentCommentId?: string }) =>
      commentApi.create(photoId as string, input.body, input.parentCommentId),
    onSuccess: invalidate,
    onError: handleError,
  });

  const remove = useMutation({
    mutationFn: (commentId: string) => commentApi.delete(commentId),
    onSuccess: invalidate,
    onError: handleError,
  });

  return {
    query,
    comments: query.data?.items ?? [],
    total: query.data?.totalElements ?? 0,
    create: create.mutateAsync,
    isPosting: create.isPending,
    remove: remove.mutate,
  };
}

export function useCommentQuota() {
  return useQuery({
    queryKey: queryKeys.comments.quota(),
    queryFn: commentApi.quota,
    staleTime: 30_000,
  });
}
