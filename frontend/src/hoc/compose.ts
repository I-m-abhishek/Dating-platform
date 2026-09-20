import type { ComponentType } from 'react';

type Enhancer = <P extends object>(component: ComponentType<P>) => ComponentType<P>;

/**
 * Applies HOCs right to left, so the call reads in the order the wrappers apply.
 *
 * <p>{@code compose(withErrorBoundary, withAuth)(Page)} produces
 * {@code withErrorBoundary(withAuth(Page))}: the boundary is outermost and therefore still
 * catches errors thrown while the auth guard is resolving.
 *
 * @example
 * export default compose(withErrorBoundary, withAuth)(MatchesPage);
 */
export function compose(...enhancers: Enhancer[]): Enhancer {
  return (<P extends object>(component: ComponentType<P>) =>
    enhancers.reduceRight<ComponentType<P>>((acc, enhancer) => enhancer(acc), component)) as Enhancer;
}
