'use client';

import type { ComponentType } from 'react';
import { useEntitlements } from '@/lib/hooks/useEntitlements';
import type { Feature } from '@/lib/api/types';
import { UpgradePrompt } from '@/components/paywall/UpgradePrompt';
import { FullPageLoader } from '@/components/ui/FullPageLoader';

export interface WithEntitlementOptions {
  /** Rendered instead of the upgrade prompt when the feature is locked. */
  fallback?: ComponentType<{ feature: Feature }>;
  /** Render the component anyway and let it degrade, rather than blocking it. */
  softGate?: boolean;
}

/**
 * Gate a screen or a section behind a paid feature.
 *
 * <p>This is a <em>presentation</em> guard, never a security one. The server refuses the
 * request regardless - see {@code EntitlementService.require} - and, for the Likes tab,
 * strips the identifying fields out of the payload before it is serialised. This HOC only
 * decides what the user sees instead of an error.
 *
 * <p>With {@code softGate} the component still renders; use that when the feature degrades
 * gracefully (a blurred grid) rather than disappearing.
 *
 * @example
 * export default withAuth(withEntitlement(GlobalModeSettings, 'GLOBAL_MODE'));
 */
export function withEntitlement<P extends object>(
  Component: ComponentType<P>,
  feature: Feature,
  options: WithEntitlementOptions = {},
): ComponentType<P> {
  const { fallback: Fallback, softGate = false } = options;

  function EntitlementGated(props: P) {
    const { entitlements, isLoading, has } = useEntitlements();

    if (isLoading || !entitlements) {
      return <FullPageLoader />;
    }
    if (has(feature) || softGate) {
      return <Component {...props} />;
    }
    if (Fallback) {
      return <Fallback feature={feature} />;
    }
    return <UpgradePrompt feature={feature} />;
  }

  EntitlementGated.displayName =
    `withEntitlement(${Component.displayName ?? Component.name ?? 'Component'}, ${feature})`;
  return EntitlementGated;
}
