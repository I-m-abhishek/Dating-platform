'use client';

import { useQuery } from '@tanstack/react-query';
import { standoutApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';

/**
 * The Standouts shelf.
 *
 * <p>Served from a snapshot the backend rebuilds every few hours, so it is cheap to read
 * and stable between visits - a shelf that reshuffles on every render is not a shelf.
 */
export function useStandouts(limit = 12) {
  return useQuery({
    queryKey: queryKeys.standouts.list(limit),
    queryFn: () => standoutApi.list(limit),
    staleTime: 5 * 60_000,
  });
}
