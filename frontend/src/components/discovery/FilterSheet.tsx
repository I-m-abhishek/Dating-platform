'use client';

import { useEffect, useState } from 'react';
import { Sheet } from '@/components/ui/Sheet';
import { Button } from '@/components/ui/Button';
import { Chip } from '@/components/ui/Chip';
import { Switch } from '@/components/ui/Switch';
import { useEntitlements } from '@/lib/hooks/useEntitlements';
import { humanise } from '@/lib/utils/format';
import {
  DISTANCE_SLIDER_MAX,
  DISTANCE_SLIDER_MIN,
  distanceFilterLabel,
  distanceToSlider,
  isUnlimitedDistance,
  sliderToDistance,
} from '@/lib/utils/distance';
import type { FeedFilter, FeedSort, Gender, RelationshipIntent } from '@/lib/api/types';

const GENDERS: Gender[] = ['WOMAN', 'MAN', 'NON_BINARY', 'OTHER'];
const INTENTS: RelationshipIntent[] = [
  'LONG_TERM',
  'LONG_TERM_OPEN_TO_SHORT',
  'FIGURING_IT_OUT',
  'SHORT_TERM_OPEN_TO_LONG',
  'SHORT_TERM',
  'NEW_FRIENDS',
];
const SORTS: Array<{ value: FeedSort; label: string }> = [
  { value: 'RECOMMENDED', label: 'Recommended' },
  { value: 'NEAREST', label: 'Nearest' },
  { value: 'RECENTLY_ACTIVE', label: 'Active now' },
  { value: 'NEWEST', label: 'New here' },
];

export interface FilterSheetProps {
  open: boolean;
  value: FeedFilter;
  onClose: () => void;
  onApply: (filter: FeedFilter) => void;
}

/**
 * Distance and age are free. Intent, height, verification and interests are the paid
 * filters - the sheet marks them rather than hiding them, so the value of upgrading is
 * visible instead of mysterious.
 */
export function FilterSheet({ open, value, onClose, onApply }: FilterSheetProps) {
  const { has } = useEntitlements();
  const advancedUnlocked = has('ADVANCED_FILTERS');
  const [draft, setDraft] = useState<FeedFilter>(value);

  // Resync whenever the sheet is opened. Without this the draft keeps whatever was last
  // typed here and silently disagrees with preferences changed in Settings since.
  useEffect(() => {
    if (open) {
      setDraft(value);
    }
  }, [open, value]);

  const update = (patch: Partial<FeedFilter>) => setDraft((current) => ({ ...current, ...patch }));

  const distanceSlider = distanceToSlider(draft.maxDistanceKm);

  const toggleGender = (gender: Gender) => {
    const current = draft.genders ?? [];
    update({
      genders: current.includes(gender)
        ? current.filter((item) => item !== gender)
        : [...current, gender],
    });
  };

  const toggleIntent = (intent: RelationshipIntent) => {
    const current = draft.intents ?? [];
    update({
      intents: current.includes(intent)
        ? current.filter((item) => item !== intent)
        : [...current, intent],
    });
  };

  return (
    <Sheet open={open} onClose={onClose} title="Filters" size="tall">
      <div className="space-y-6">
        <section className="space-y-2.5">
          <h3 className="eyebrow">Sort by</h3>
          <div className="flex flex-wrap gap-2">
            {SORTS.map((sort) => (
              <Chip
                key={sort.value}
                selected={(draft.sort ?? 'RECOMMENDED') === sort.value}
                onClick={() => update({ sort: sort.value })}
              >
                {sort.label}
              </Chip>
            ))}
          </div>
        </section>

        <section className="space-y-2">
          <div className="flex items-baseline justify-between">
            <h3 className="eyebrow">Maximum distance</h3>
            <span className="font-display text-[17px] font-semibold tabular-nums text-gradient">
              {distanceFilterLabel(distanceSlider)}
            </span>
          </div>
          <input
            type="range"
            min={DISTANCE_SLIDER_MIN}
            max={DISTANCE_SLIDER_MAX}
            step={1}
            value={distanceSlider}
            onChange={(event) =>
              update({ maxDistanceKm: sliderToDistance(Number(event.target.value)) })
            }
            className="w-full"
            aria-label="Maximum distance in kilometres"
            aria-valuetext={distanceFilterLabel(distanceSlider)}
          />
          {/* The top stop is not 500 km, it is "no limit" - say so rather than let the
              number imply a cap that is not there. */}
          {isUnlimitedDistance(draft.maxDistanceKm) ? (
            <p className="text-xs text-ink-subtle">Distance is not limited.</p>
          ) : null}
        </section>

        <section className="space-y-2">
          <div className="flex items-baseline justify-between">
            <h3 className="eyebrow">Age range</h3>
            <span className="font-display text-[17px] font-semibold tabular-nums text-gradient">
              {draft.minAge ?? 18}&#8202;&ndash;&#8202;{draft.maxAge ?? 45}
            </span>
          </div>
          <div className="flex items-center gap-3">
            <input
              type="range"
              min={18}
              max={99}
              value={draft.minAge ?? 18}
              onChange={(event) =>
                update({ minAge: Math.min(Number(event.target.value), draft.maxAge ?? 45) })
              }
              className="w-full"
              aria-label="Minimum age"
            />
            <input
              type="range"
              min={18}
              max={99}
              value={draft.maxAge ?? 45}
              onChange={(event) =>
                update({ maxAge: Math.max(Number(event.target.value), draft.minAge ?? 18) })
              }
              className="w-full"
              aria-label="Maximum age"
            />
          </div>
        </section>

        <section className="space-y-2.5">
          <h3 className="eyebrow">Show me</h3>
          <div className="flex flex-wrap gap-2">
            {GENDERS.map((gender) => (
              <Chip
                key={gender}
                selected={(draft.genders ?? []).includes(gender)}
                onClick={() => toggleGender(gender)}
              >
                {humanise(gender)}
              </Chip>
            ))}
          </div>
        </section>

        <section className="space-y-2.5">
          <div className="flex items-center gap-2">
            <h3 className="eyebrow">Looking for</h3>
            {!advancedUnlocked ? <PlusTag /> : null}
          </div>
          <div className="flex flex-wrap gap-2">
            {INTENTS.map((intent) => (
              <Chip
                key={intent}
                selected={(draft.intents ?? []).includes(intent)}
                onClick={advancedUnlocked ? () => toggleIntent(intent) : undefined}
                className={advancedUnlocked ? undefined : 'opacity-50'}
              >
                {humanise(intent)}
              </Chip>
            ))}
          </div>
        </section>

        <section className="border-t border-border pt-5">
          <Switch
            label="Verified profiles only"
            description={advancedUnlocked ? undefined : 'Available on Plus.'}
            checked={Boolean(draft.onlyVerified)}
            disabled={!advancedUnlocked}
            onChange={(checked) => update({ onlyVerified: checked })}
          />
        </section>

        {/*
          Sticky rather than inline: this sheet scrolls, and a confirm button that scrolls
          out of reach turns every filter change into a hunt for the bottom of the list.
        */}
        <div className="sticky bottom-0 -mx-6 -mb-8 flex gap-3 border-t border-border bg-surface px-6 pb-8 pt-4 sm:-mb-6 sm:pb-6">
          <Button variant="ghost" size="lg" onClick={() => setDraft({ sort: 'RECOMMENDED' })}>
            Reset
          </Button>
          <Button
            fullWidth
            size="lg"
            onClick={() => {
              onApply(draft);
              onClose();
            }}
          >
            Show profiles
          </Button>
        </div>
      </div>
    </Sheet>
  );
}

/** Marks a control that exists but is not yours yet. */
function PlusTag() {
  return (
    <span className="rounded-pill bg-accent-gradient px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-white">
      Plus
    </span>
  );
}
