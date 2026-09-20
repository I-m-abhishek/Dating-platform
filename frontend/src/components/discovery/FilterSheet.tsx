'use client';

import { useEffect, useState } from 'react';
import { Sheet } from '@/components/ui/Sheet';
import { Button } from '@/components/ui/Button';
import { Chip } from '@/components/ui/Chip';
import { useEntitlements } from '@/lib/hooks/useEntitlements';
import { humanise } from '@/lib/utils/format';
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
        <section className="space-y-2">
          <h3 className="text-sm font-medium text-ink-muted">Sort by</h3>
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
            <h3 className="text-sm font-medium text-ink-muted">Maximum distance</h3>
            <span className="text-sm tabular-nums text-ink">{draft.maxDistanceKm ?? 80} km</span>
          </div>
          <input
            type="range"
            min={1}
            max={500}
            step={1}
            value={draft.maxDistanceKm ?? 80}
            onChange={(event) => update({ maxDistanceKm: Number(event.target.value) })}
            className="w-full accent-accent"
            aria-label="Maximum distance in kilometres"
          />
        </section>

        <section className="space-y-2">
          <div className="flex items-baseline justify-between">
            <h3 className="text-sm font-medium text-ink-muted">Age range</h3>
            <span className="text-sm tabular-nums text-ink">
              {draft.minAge ?? 18} to {draft.maxAge ?? 45}
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
              className="w-full accent-accent"
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
              className="w-full accent-accent"
              aria-label="Maximum age"
            />
          </div>
        </section>

        <section className="space-y-2">
          <h3 className="text-sm font-medium text-ink-muted">Show me</h3>
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

        <section className="space-y-2">
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-medium text-ink-muted">Looking for</h3>
            {!advancedUnlocked ? (
              <span className="rounded-pill bg-accent-soft px-2 py-0.5 text-[10px] font-medium text-accent">
                Plus
              </span>
            ) : null}
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

        <section className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-medium text-ink-muted">Verified profiles only</h3>
            {!advancedUnlocked ? (
              <span className="rounded-pill bg-accent-soft px-2 py-0.5 text-[10px] font-medium text-accent">
                Plus
              </span>
            ) : null}
          </div>
          <input
            type="checkbox"
            checked={Boolean(draft.onlyVerified)}
            disabled={!advancedUnlocked}
            onChange={(event) => update({ onlyVerified: event.target.checked })}
            className="h-5 w-5 accent-accent"
            aria-label="Verified profiles only"
          />
        </section>

        <div className="flex gap-3 pt-2">
          <Button variant="ghost" fullWidth onClick={() => setDraft({ sort: 'RECOMMENDED' })}>
            Reset
          </Button>
          <Button
            fullWidth
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
