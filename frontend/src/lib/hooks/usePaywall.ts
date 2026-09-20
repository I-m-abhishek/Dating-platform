'use client';

import { useCallback } from 'react';
import { useUiStore } from '@/lib/stores/uiStore';
import { premiumContextOf, quotaContextOf, isApiError } from '@/lib/api/errors';

/**
 * Turns a paywall error from the API into the sheet the user should see.
 *
 * <p>Mutations pass their error here instead of showing a toast. QUOTA_EXCEEDED and
 * PREMIUM_REQUIRED are product states, not failures, and deserve a real explanation with
 * the reset time and the tier that unlocks it - all of which the server already sent in the
 * error context.
 *
 * @returns true when the error was a paywall and has been handled
 */
export function usePaywall() {
  const openPaywall = useUiStore((state) => state.openPaywall);
  const toast = useUiStore((state) => state.toast);

  const handleError = useCallback(
    (error: unknown): boolean => {
      const quota = quotaContextOf(error);
      if (quota) {
        openPaywall({
          title: `You have used your ${quota.feature}`,
          description:
            quota.upgradeHint ?? 'Upgrade for a bigger daily allowance, or wait for the reset.',
          requiredTier: 'PLUS',
          resetsAt: quota.resetsAt,
        });
        return true;
      }

      const premium = premiumContextOf(error);
      if (premium) {
        openPaywall({
          title: premium.feature,
          description: `Available on the ${premium.requiredTier === 'PREMIUM' ? 'Premium' : 'Plus'} plan.`,
          requiredTier: premium.requiredTier,
        });
        return true;
      }

      if (isApiError(error)) {
        toast({ title: error.message, tone: 'error' });
        return false;
      }
      toast({ title: 'Something went wrong', tone: 'error' });
      return false;
    },
    [openPaywall, toast],
  );

  return { handleError };
}
