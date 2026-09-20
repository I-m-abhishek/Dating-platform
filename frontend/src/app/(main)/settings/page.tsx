'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { TopBar } from '@/components/layout/TopBar';
import { Button } from '@/components/ui/Button';
import { LinkButton } from '@/components/ui/LinkButton';
import { FullPageLoader } from '@/components/ui/FullPageLoader';
import { accountApi, subscriptionApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';
import { useAuthStore } from '@/lib/stores/authStore';
import { useEntitlements } from '@/lib/hooks/useEntitlements';
import { useUiStore } from '@/lib/stores/uiStore';
import { isApiError, messageOf } from '@/lib/api/errors';

/**
 * Settings.
 *
 * <p>Global mode and incognito are paid perks, so toggling them can come back as
 * PREMIUM_REQUIRED. That is surfaced as the upgrade sheet rather than an error - the
 * request was reasonable, it just needs a plan.
 */
function SettingsPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const account = useAuthStore((state) => state.account);
  const setAccount = useAuthStore((state) => state.setAccount);
  const logout = useAuthStore((state) => state.logout);
  const { entitlements } = useEntitlements();
  const toast = useUiStore((state) => state.toast);
  const openPaywall = useUiStore((state) => state.openPaywall);

  const updatePreferences = useMutation({
    mutationFn: accountApi.updatePreferences,
    onSuccess: (updated) => {
      setAccount(updated);
      void queryClient.invalidateQueries({ queryKey: queryKeys.account.all });
    },
    onError: (error) => {
      if (isApiError(error) && error.code === 'PREMIUM_REQUIRED') {
        openPaywall({
          title: 'Upgrade to use this',
          description: error.message,
          requiredTier: 'PREMIUM',
        });
        return;
      }
      toast({ title: messageOf(error), tone: 'error' });
    },
  });

  /*
   * A range input emits a change event per pixel of drag. Committing each one raced (the
   * last response won, not the last value), fought the thumb - which was pinned to the
   * server's value until a reply arrived - and tripped the rate limiter. The thumb now
   * follows local state and the server is told once, when the drag ends.
   */
  const [distanceKm, setDistanceKm] = useState(account?.preferredMaxDistanceKm ?? 80);

  useEffect(() => {
    if (account) {
      setDistanceKm(account.preferredMaxDistanceKm);
    }
  }, [account?.preferredMaxDistanceKm]);

  const commitDistance = () => {
    if (account && distanceKm !== account.preferredMaxDistanceKm) {
      updatePreferences.mutate({ preferredMaxDistanceKm: distanceKm });
    }
  };

  const cancel = useMutation({
    mutationFn: subscriptionApi.cancel,
    onSuccess: () => {
      toast({ title: 'Auto-renewal is off', tone: 'default' });
      void queryClient.invalidateQueries({ queryKey: queryKeys.subscription.all });
    },
    onError: (error) => toast({ title: messageOf(error), tone: 'error' }),
  });

  if (!account) return <FullPageLoader />;

  return (
    <>
      <TopBar showBack title="Settings" />

      <div className="space-y-6 p-4 pb-24">
        <section className="card divide-y divide-border">
          <Row label="Email" value={account.email} />
          <Row label="Plan" value={entitlements?.planName ?? 'Free'} />
          {account.city ? <Row label="Location" value={account.city} /> : null}
        </section>

        <section className="space-y-3">
          <h2 className="text-sm font-medium text-ink-muted">Discovery</h2>

          <div className="card space-y-4 p-4">
            <div className="space-y-2">
              <div className="flex items-baseline justify-between">
                <label htmlFor="distance" className="text-sm text-ink">
                  Maximum distance
                </label>
                <span className="text-sm tabular-nums text-ink-muted">{distanceKm} km</span>
              </div>
              <input
                id="distance"
                type="range"
                min={1}
                max={500}
                value={distanceKm}
                onChange={(event) => setDistanceKm(Number(event.target.value))}
                onPointerUp={commitDistance}
                onKeyUp={commitDistance}
                onBlur={commitDistance}
                className="w-full accent-accent"
              />
            </div>

            <ToggleRow
              label="Global mode"
              description="Ignore distance entirely. Premium."
              checked={account.globalMode}
              onChange={(checked) => updatePreferences.mutate({ globalMode: checked })}
            />
            <ToggleRow
              label="Incognito"
              description="Browse without appearing in discovery. Premium."
              checked={account.incognito}
              onChange={(checked) => updatePreferences.mutate({ incognito: checked })}
            />
          </div>
        </section>

        <section className="space-y-3">
          <h2 className="text-sm font-medium text-ink-muted">Subscription</h2>
          <div className="flex gap-3">
            <LinkButton href="/plans" variant="outline" fullWidth>
              See plans
            </LinkButton>
            {entitlements && entitlements.tier !== 'FREE' ? (
              <Button
                variant="ghost"
                fullWidth
                loading={cancel.isPending}
                onClick={() => cancel.mutate()}
              >
                Cancel renewal
              </Button>
            ) : null}
          </div>
        </section>

        <section className="space-y-3">
          <h2 className="text-sm font-medium text-ink-muted">Account</h2>
          <div className="space-y-2">
            <Button
              variant="outline"
              fullWidth
              onClick={() => {
                void accountApi.pause().then(() => {
                  toast({ title: 'Your profile is hidden', tone: 'default' });
                });
              }}
            >
              Pause my profile
            </Button>
            <Button
              variant="ghost"
              fullWidth
              onClick={() => {
                void logout().then(() => router.replace('/login'));
              }}
            >
              Sign out
            </Button>
          </div>
        </section>
      </div>
    </>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3 px-4 py-3">
      <span className="text-sm text-ink-muted">{label}</span>
      <span className="truncate text-sm text-ink">{value}</span>
    </div>
  );
}

function ToggleRow({
  label,
  description,
  checked,
  onChange,
}: {
  label: string;
  description: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <label className="flex cursor-pointer items-center justify-between gap-4">
      <span className="min-w-0">
        <span className="block text-sm text-ink">{label}</span>
        <span className="block text-xs text-ink-subtle">{description}</span>
      </span>
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
        className="h-5 w-5 shrink-0 accent-accent"
      />
    </label>
  );
}

export default compose(withErrorBoundary, withAuth)(SettingsPage);
