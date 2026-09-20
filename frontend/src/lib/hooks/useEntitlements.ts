'use client';

import { useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import { subscriptionApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Feature } from '@/lib/api/types';

/**
 * What this account can do, straight from the server.
 *
 * <p>Every limit the UI shows - "5 comments a day", "one match a week" - comes from here.
 * Nothing in the client hardcodes those numbers, so changing the free allowance is a
 * backend configuration change and the UI follows automatically.
 */
export function useEntitlements() {
  const query = useQuery({
    queryKey: queryKeys.subscription.entitlements(),
    queryFn: subscriptionApi.entitlements,
    staleTime: 60_000,
  });

  const has = useCallback(
    (feature: Feature) => query.data?.unlockedFeatures.includes(feature) ?? false,
    [query.data],
  );

  return {
    entitlements: query.data,
    isLoading: query.isPending,
    has,
    tier: query.data?.tier ?? 'FREE',
    refetch: query.refetch,
  };
}
