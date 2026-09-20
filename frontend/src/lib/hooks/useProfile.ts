'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { profileApi, referenceApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';
import { usePaywall } from './usePaywall';
import { useUiStore } from '@/lib/stores/uiStore';

export function useMyProfile() {
  return useQuery({
    queryKey: queryKeys.profile.me(),
    queryFn: profileApi.me,
  });
}

export function usePublicProfile(userId: string | null) {
  return useQuery({
    queryKey: queryKeys.profile.public(userId ?? ''),
    queryFn: () => profileApi.publicProfile(userId as string),
    enabled: Boolean(userId),
  });
}

export function useUpdateProfile() {
  const queryClient = useQueryClient();
  const toast = useUiStore((state) => state.toast);
  const { handleError } = usePaywall();

  return useMutation({
    mutationFn: (payload: Record<string, unknown>) => profileApi.update(payload),
    onSuccess: (profile) => {
      queryClient.setQueryData(queryKeys.profile.me(), profile);
      toast({ title: 'Profile saved', tone: 'success' });
    },
    onError: handleError,
  });
}

export function usePhotos() {
  const queryClient = useQueryClient();
  const toast = useUiStore((state) => state.toast);
  const { handleError } = usePaywall();

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.profile.all });
  };

  const query = useQuery({
    queryKey: queryKeys.profile.photos(),
    queryFn: profileApi.photos,
  });

  const upload = useMutation({
    mutationFn: (input: { file: File; caption?: string }) =>
      profileApi.uploadPhoto(input.file, input.caption),
    onSuccess: () => {
      toast({ title: 'Photo added', tone: 'success' });
      invalidate();
    },
    onError: handleError,
  });

  const remove = useMutation({
    mutationFn: (photoId: string) => profileApi.deletePhoto(photoId),
    onSuccess: invalidate,
    onError: handleError,
  });

  const reorder = useMutation({
    mutationFn: (photoIdsInOrder: string[]) => profileApi.reorderPhotos(photoIdsInOrder),
    onSuccess: (photos) => {
      queryClient.setQueryData(queryKeys.profile.photos(), photos);
      invalidate();
    },
    onError: handleError,
  });

  return {
    query,
    photos: query.data ?? [],
    upload: upload.mutateAsync,
    isUploading: upload.isPending,
    remove: remove.mutate,
    reorder: reorder.mutate,
  };
}

export function useReferenceData() {
  const interests = useQuery({
    queryKey: queryKeys.reference.interests(),
    queryFn: referenceApi.interests,
    staleTime: Infinity,
  });
  const qualities = useQuery({
    queryKey: queryKeys.reference.qualities(),
    queryFn: referenceApi.qualities,
    staleTime: Infinity,
  });
  const prompts = useQuery({
    queryKey: queryKeys.reference.prompts(),
    queryFn: referenceApi.prompts,
    staleTime: Infinity,
  });

  return {
    interests: interests.data ?? [],
    qualities: qualities.data ?? [],
    prompts: prompts.data ?? [],
    isLoading: interests.isPending || qualities.isPending || prompts.isPending,
  };
}
