'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { TopBar } from '@/components/layout/TopBar';
import { Button } from '@/components/ui/Button';
import { LinkButton } from '@/components/ui/LinkButton';
import { FullPageLoader } from '@/components/ui/FullPageLoader';
import { Switch } from '@/components/ui/Switch';
import { ThemeToggle } from '@/components/ui/ThemeToggle';
import { Chip } from '@/components/ui/Chip';
import { humanise } from '@/lib/utils/format';
import {
  DISTANCE_SLIDER_MAX,
  DISTANCE_SLIDER_MIN,
  distanceFilterLabel,
  distanceToSlider,
  sliderToDistance,
} from '@/lib/utils/distance';
import { accountApi, subscriptionApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';
import { useAuthStore } from '@/lib/stores/authStore';
import { useEntitlements } from '@/lib/hooks/useEntitlements';
import { useUiStore } from '@/lib/stores/uiStore';
import { isApiError, messageOf } from '@/lib/api/errors';
import type { Account, Gender } from '@/lib/api/types';

const GENDERS: Gender[] = ['WOMAN', 'MAN', 'NON_BINARY', 'OTHER'];

interface DiscoveryDraft {
  distance: number;
  minAge: number;
  maxAge: number;
  interestedIn: Gender[];
}

function draftFrom(account: Account | null | undefined): DiscoveryDraft {
  return {
    distance: distanceToSlider(account?.preferredMaxDistanceKm),
    minAge: account?.preferredMinAge ?? 18,
    maxAge: account?.preferredMaxAge ?? 45,
    interestedIn: account?.interestedIn ?? [],
  };
}

/** Order-insensitive identity of a draft, for dirty checks and resyncs. */
function draftKey(draft: DiscoveryDraft): string {
  return [draft.distance, draft.minAge, draft.maxAge, [...draft.interestedIn].sort().join('|')].join(',');
}

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
   * Discovery preferences are edited as a draft and committed with an explicit Save.
   * Committing on every slider release raced, tripped the rate limiter, and left no way
   * to back out of a half-made change. The distance draft is the slider POSITION, not the
   * stored radius: the top stop maps to "no limit", a far larger number than it shows.
   */
  const [draft, setDraft] = useState<DiscoveryDraft>(() => draftFrom(account));

  const saved = draftFrom(account);
  const savedKey = draftKey(saved);
  const dirty = draftKey(draft) !== savedKey;

  // Resync when the saved values change underneath (e.g. from the home filter sheet).
  useEffect(() => {
    setDraft(draftFrom(useAuthStore.getState().account));
  }, [savedKey]);

  const toggleGender = (gender: Gender) =>
    setDraft((current) => ({
      ...current,
      interestedIn: current.interestedIn.includes(gender)
        ? current.interestedIn.filter((item) => item !== gender)
        : [...current.interestedIn, gender],
    }));

  const saveDiscovery = () => {
    if (!dirty || draft.interestedIn.length === 0) return;
    updatePreferences.mutate(
      {
        preferredMaxDistanceKm: sliderToDistance(draft.distance),
        preferredMinAge: draft.minAge,
        preferredMaxAge: draft.maxAge,
        interestedIn: draft.interestedIn,
      },
      {
        onSuccess: () => {
          toast({ title: 'Preferences saved', tone: 'success' });
          // The feed is keyed by filter, so cached pages from the old range are stale.
          void queryClient.invalidateQueries({ queryKey: queryKeys.discovery.all });
        },
      },
    );
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

      <div className="space-y-7 p-4">
        <section className="card divide-y divide-border">
          <Row label="Email" value={account.email} />
          <Row label="Plan" value={entitlements?.planName ?? 'Free'} />
          {account.city ? <Row label="Location" value={account.city} /> : null}
        </section>

        <section className="space-y-3">
          <h2 className="eyebrow px-1">Appearance</h2>
          <div className="card flex flex-wrap items-center justify-between gap-3 p-5">
            <div className="min-w-0">
              <p className="text-[14px] font-medium text-ink">Theme</p>
              <p className="mt-0.5 text-xs text-ink-subtle">
                Auto follows your device from sunset to sunrise.
              </p>
            </div>
            <ThemeToggle />
          </div>
        </section>

        <section className="space-y-3">
          <h2 className="eyebrow px-1">Discovery</h2>

          <div className="card space-y-5 p-5">
            <div className="space-y-3">
              <div className="flex items-baseline justify-between">
                <label htmlFor="distance" className="text-[14px] font-medium text-ink">
                  Maximum distance
                </label>
                <span className="font-display text-[17px] font-semibold tabular-nums text-gradient">
                  {distanceFilterLabel(draft.distance)}
                </span>
              </div>
              <input
                id="distance"
                type="range"
                min={DISTANCE_SLIDER_MIN}
                max={DISTANCE_SLIDER_MAX}
                value={draft.distance}
                aria-valuetext={distanceFilterLabel(draft.distance)}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, distance: Number(event.target.value) }))
                }
                className="w-full"
              />
            </div>

            <div className="space-y-3">
              <div className="flex items-baseline justify-between">
                <span className="text-[14px] font-medium text-ink">Age range</span>
                <span className="font-display text-[17px] font-semibold tabular-nums text-gradient">
                  {draft.minAge}&#8202;&ndash;&#8202;{draft.maxAge}
                </span>
              </div>
              <div className="flex items-center gap-3">
                <input
                  type="range"
                  min={18}
                  max={99}
                  value={draft.minAge}
                  onChange={(event) =>
                    setDraft((current) => ({
                      ...current,
                      minAge: Math.min(Number(event.target.value), current.maxAge),
                    }))
                  }
                  className="w-full"
                  aria-label="Minimum age"
                />
                <input
                  type="range"
                  min={18}
                  max={99}
                  value={draft.maxAge}
                  onChange={(event) =>
                    setDraft((current) => ({
                      ...current,
                      maxAge: Math.max(Number(event.target.value), current.minAge),
                    }))
                  }
                  className="w-full"
                  aria-label="Maximum age"
                />
              </div>
            </div>

            <div className="space-y-3">
              <span className="text-[14px] font-medium text-ink">Show me</span>
              <div className="flex flex-wrap gap-2">
                {GENDERS.map((gender) => (
                  <Chip
                    key={gender}
                    selected={draft.interestedIn.includes(gender)}
                    onClick={() => toggleGender(gender)}
                  >
                    {humanise(gender)}
                  </Chip>
                ))}
              </div>
              {draft.interestedIn.length === 0 ? (
                <p className="text-xs text-danger">Pick at least one.</p>
              ) : null}
            </div>

            <div className="flex gap-3">
              <Button
                variant="ghost"
                disabled={!dirty || updatePreferences.isPending}
                onClick={() => setDraft(saved)}
              >
                Reset
              </Button>
              <Button
                fullWidth
                disabled={!dirty || draft.interestedIn.length === 0}
                loading={updatePreferences.isPending}
                onClick={saveDiscovery}
              >
                Save preferences
              </Button>
            </div>

            <div className="space-y-5 border-t border-border pt-5">
              <Switch
                label="Global mode"
                description="Ignore distance entirely. Premium."
                checked={account.globalMode}
                onChange={(checked) => updatePreferences.mutate({ globalMode: checked })}
              />
              <Switch
                label="Incognito"
                description="Browse without appearing in discovery. Premium."
                checked={account.incognito}
                onChange={(checked) => updatePreferences.mutate({ incognito: checked })}
              />
            </div>
          </div>
        </section>

        <section className="space-y-3">
          <h2 className="eyebrow px-1">Subscription</h2>
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
          <h2 className="eyebrow px-1">Account</h2>
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
    <div className="flex items-center justify-between gap-3 px-5 py-3.5">
      <span className="text-sm text-ink-muted">{label}</span>
      <span className="truncate text-sm font-medium text-ink">{value}</span>
    </div>
  );
}

export default compose(withErrorBoundary, withAuth)(SettingsPage);
