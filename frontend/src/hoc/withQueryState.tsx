'use client';

import type { ComponentType, ReactNode } from 'react';
import type { UseQueryResult } from '@tanstack/react-query';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorState } from '@/components/ui/ErrorState';
import { EmptyState } from '@/components/ui/EmptyState';
import { messageOf } from '@/lib/api/errors';

export interface WithQueryStateOptions<TData> {
  /** Shown while the first fetch is in flight. Defaults to a skeleton. */
  loading?: ReactNode;
  /** Decides whether the loaded data counts as "nothing to show". */
  isEmpty?: (data: TData) => boolean;
  empty?: ReactNode;
}

/**
 * Removes the loading/error/empty triangle from every list component.
 *
 * <p>Without this, each screen re-implements the same three branches and they drift:
 * one shows a spinner, another a skeleton, a third nothing at all. The HOC makes the
 * wrapped component a pure function of loaded data - it can assume {@code data} exists.
 *
 * @example
 * const MatchList = withQueryState(
 *   ({ data }: { data: Match[] }) => <ul>{data.map(...)}</ul>,
 *   { isEmpty: (matches) => matches.length === 0, empty: <EmptyState title="No matches yet" /> },
 * );
 *
 * <MatchList query={matchesQuery} />
 */
export function withQueryState<TData, P extends { data: TData }>(
  Component: ComponentType<P>,
  options: WithQueryStateOptions<TData> = {},
): ComponentType<Omit<P, 'data'> & { query: UseQueryResult<TData> }> {
  const { loading, isEmpty, empty } = options;

  function QueryStated({ query, ...rest }: Omit<P, 'data'> & { query: UseQueryResult<TData> }) {
    if (query.isPending) {
      return <>{loading ?? <Skeleton.List rows={4} />}</>;
    }
    if (query.isError) {
      return (
        <ErrorState
          title="We could not load this"
          description={messageOf(query.error)}
          onRetry={() => void query.refetch()}
        />
      );
    }
    const data = query.data as TData;
    if (isEmpty?.(data)) {
      return <>{empty ?? <EmptyState title="Nothing here yet" />}</>;
    }
    // The spread is structurally correct but TypeScript cannot prove it for an arbitrary
    // subtype of P, so the cast goes via unknown. This is the one unchecked spot, and it is
    // contained: the wrapper only ever adds `data` back to the props it was given.
    return <Component {...({ ...rest, data } as unknown as P)} />;
  }

  QueryStated.displayName = `withQueryState(${Component.displayName ?? Component.name ?? 'Component'})`;
  return QueryStated;
}
