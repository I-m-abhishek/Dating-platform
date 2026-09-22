'use client';

import type { CSSProperties } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { TopBar } from '@/components/layout/TopBar';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { FullPageLoader } from '@/components/ui/FullPageLoader';
import { CheckIcon, CrownIcon } from '@/components/ui/icons';
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

  if (plansQuery.isPending) return <FullPageLoader label="Loading plans" />;

  const currentTier = entitlements?.tier ?? 'FREE';
  const plans = plansQuery.data ?? [];

  return (
    <>
      <TopBar showBack title="Plans" subtitle={`You are on ${entitlements?.planName ?? 'Free'}`} />

      <div className="space-y-5 p-4">
        <section className="px-1 pt-2 text-center">
          <h2 className="font-display text-[28px] font-semibold leading-tight tracking-[-0.02em] text-ink">
            Stop guessing who is <span className="text-gradient">interested</span>.
          </h2>
          <p className="mx-auto mt-2 max-w-sm text-sm leading-relaxed text-ink-muted">
            Every plan unlocks the same app - just more of the parts that save you time.
          </p>
        </section>

        {plans.map((plan, index) => {
          const isCurrent = currentTier === plan.tier;
          // The top tier carries the lit rim. One highlighted option, never two.
          const featured = plan.tier === 'PREMIUM' && !isCurrent;

          return (
            <section
              key={plan.id}
              style={{ '--i': index } as CSSProperties}
              className={
                featured
                  ? 'stagger hairline-gradient relative space-y-5 overflow-hidden rounded-card bg-accent-gradient-soft p-6 shadow-lift'
                  : 'stagger card space-y-5 p-6'
              }
            >
              {featured ? (
                <span className="absolute right-5 top-5">
                  <Badge tone="gold">
                    <CrownIcon size={12} />
                    Most chosen
                  </Badge>
                </span>
              ) : null}

              <div>
                <div className="flex items-center gap-2">
                  <h3 className="font-display text-[22px] font-semibold text-ink">{plan.name}</h3>
                  {isCurrent ? <Badge tone="success">Current</Badge> : null}
                </div>

                <div className="mt-3 flex items-baseline gap-1.5">
                  <span className="font-display text-[38px] font-semibold leading-none tracking-[-0.03em] text-ink">
                    {priceLabel(plan.price, plan.currency)}
                  </span>
                  <span className="text-[13px] font-medium text-ink-subtle">
                    {plan.billingPeriodMonths === 1
                      ? '/ month'
                      : `/ ${plan.billingPeriodMonths} months`}
                  </span>
                </div>

                {plan.description ? (
                  <p className="mt-2.5 text-sm leading-relaxed text-ink-muted">
                    {plan.description}
                  </p>
                ) : null}
              </div>

              <ul className="space-y-2.5">
                {plan.highlights.map((highlight) => (
                  <li key={highlight} className="flex items-start gap-2.5 text-sm text-ink-muted">
                    <span className="mt-[1px] flex h-[18px] w-[18px] shrink-0 items-center justify-center rounded-full bg-accent-gradient text-white">
                      <CheckIcon size={11} strokeWidth={3} />
                    </span>
                    {highlight}
                  </li>
                ))}
              </ul>

              <Button
                fullWidth
                size="lg"
                variant={isCurrent ? 'outline' : featured ? 'primary' : 'secondary'}
                disabled={isCurrent}
                loading={subscribe.isPending && subscribe.variables === plan.code}
                onClick={() => subscribe.mutate(plan.code)}
              >
                {isCurrent ? 'Your current plan' : `Get ${plan.name}`}
              </Button>
            </section>
          );
        })}

        <p className="px-4 pb-4 text-center text-xs leading-relaxed text-ink-subtle">
          Plans renew automatically. Cancel any time from Settings - you keep access until the end
          of the period you have paid for.
        </p>
      </div>
    </>
  );
}

export default compose(withErrorBoundary, withAuth)(PlansPage);
