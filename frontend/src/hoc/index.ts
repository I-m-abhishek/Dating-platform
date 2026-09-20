/**
 * Higher-order components.
 *
 * Each one owns a single cross-cutting concern that would otherwise be copy-pasted into
 * every page: who may see this, what is shown while it loads, what happens when it breaks.
 *
 * Conventional order, outermost first:
 *   withErrorBoundary -> withAuth -> withEntitlement -> Page
 *
 * That order matters. The boundary must wrap the guard so a crash during the auth check is
 * still caught, and the entitlement check must sit inside the auth check because it needs a
 * signed-in user to have an entitlement at all.
 */
export { withAuth, type WithAuthOptions } from './withAuth';
export { withGuest } from './withGuest';
export { withEntitlement, type WithEntitlementOptions } from './withEntitlement';
export { withErrorBoundary, ErrorBoundary } from './withErrorBoundary';
export { withQueryState, type WithQueryStateOptions } from './withQueryState';
export { compose } from './compose';
