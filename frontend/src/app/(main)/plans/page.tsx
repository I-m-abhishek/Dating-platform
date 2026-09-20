'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { TopBar } from '@/components/layout/TopBar';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { FullPageLoader } from '@/components/ui/FullPageLoader';
import { subscriptionApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';
import { useEntitlements } from '@/lib/hooks/useEntitlements';
import { useUiStore } from '@/lib/stores/uiStore';
import { priceLabel } from '@/lib/utils/format';
import { messageOf } from '@/lib/api/errors';

/**
 * Plans.
 *
 * <p>Payments are stubbed in this build: subscribing grants the plan immediately and records
 * the token verbatim. Wiring a real processor means adding a webhook that flips the
 * subscription status - nothing on this screen changes.
 */
function PlansPage() {
  const queryClient = useQueryClient();
  const toast = useUiStore((state) => state.toast);
  const { entitlements } = useEntitlements();

  const plansQuery = useQuery({
    queryKey: queryKeys.subscription.plans(),
    queryFn: subscriptionApi.plans,
    staleTime: Infinity,
  });

  const subscribe = useMutation({
    mutationFn: (planCode: string) => subscriptionApi.subscribe(planCode, 'demo-token'),
    onSuccess: (subscription) => {
      toast({ title: `${subscription.planName} is active`, tone: 'success' });
      void queryClient.invalidateQueries({ queryKey: queryKeys.subscription.all });
      void queryClient.invalidateQueries({ queryKey: queryKeys.account.all });
    },
    onError: (error) => toast({ title: messageOf(error), tone: 'error' }),
  });

  if (plansQuery.isPending) return <FullPageLoader />;

  const currentTier = entitlements?.tier ?? 'FREE';

  return (
    <>
      <TopBar showBack title="Plans" subtitle={`You are on ${entitlements?.planName ?? 'Free'}`} />

      <div className="space-y-4 p-4">
        {(plansQuery.data ?? []).map((plan) => {
          const isCurrent = currentTier === plan.tier;

          return (
            <section key={plan.id} className="card space-y-4 p-5">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2">
                    <h2 className="text-lg font-semibold text-ink">{plan.name}</h2>
                    {isCurrent ? <Badge tone="success">Current</Badge> : null}
                  </div>
                  {plan.description ? (
                    <p className="mt-1 text-sm text-ink-muted">{plan.description}</p>
                  ) : null}
                </div>
                <div className="shrink-0 text-right">
                  <p className="text-lg font-semibold text-ink">
                    {priceLabel(plan.price, plan.currency)}
                  </p>
                  <p className="text-xs text-ink-subtle">
                    {plan.billingPeriodMonths === 1
                      ? 'per month'
                      : `every ${plan.billingPeriodMonths} months`}
                  </p>
                </div>
              </div>

              <ul className="space-y-1.5">
                {plan.highlights.map((highlight) => (
                  <li key={highlight} className="flex items-start gap-2 text-sm text-ink-muted">
                    <span aria-hidden className="mt-0.5 text-accent">
                      ✓
                    </span>
                    {highlight}
                  </li>
                ))}
              </ul>

              <Button
                fullWidth
                variant={isCurrent ? 'outline' : 'primary'}
                disabled={isCurrent}
                loading={subscribe.isPending && subscribe.variables === plan.code}
                onClick={() => subscribe.mutate(plan.code)}
              >
                {isCurrent ? 'Your current plan' : `Get ${plan.name}`}
              </Button>
            </section>
          );
        })}

        <p className="px-2 pb-4 text-center text-xs text-ink-subtle">
          Plans renew automatically. Cancel any time from Settings - you keep access until the end
          of the period you have paid for.
        </p>
      </div>
    </>
  );
}

export default compose(withErrorBoundary, withAuth)(PlansPage);
