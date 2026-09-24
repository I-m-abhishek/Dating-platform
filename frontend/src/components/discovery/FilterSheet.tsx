'use client';

import { useEffect, useState, type ReactNode } from 'react';
import { Sheet } from '@/components/ui/Sheet';
import { Button } from '@/components/ui/Button';
import { Chip } from '@/components/ui/Chip';
import { Switch } from '@/components/ui/Switch';
import { useEntitlements } from '@/lib/hooks/useEntitlements';
import { useReferenceData } from '@/lib/hooks/useProfile';
import { useUiStore } from '@/lib/stores/uiStore';
import { humanise } from '@/lib/utils/format';
import {
  DISTANCE_SLIDER_MAX,
  DISTANCE_SLIDER_MIN,
  distanceFilterLabel,
  distanceToSlider,
  isUnlimitedDistance,
  sliderToDistance,
} from '@/lib/utils/distance';
import type {
  ChildrenPreference,
  FeedFilter,
  FeedSort,
  Gender,
  LifestyleChoice,
  RelationshipIntent,
} from '@/lib/api/types';

const GENDERS: Gender[] = ['WOMAN', 'MAN', 'NON_BINARY', 'OTHER'];
const INTENTS: RelationshipIntent[] = [
  'LONG_TERM',
  'LONG_TERM_OPEN_TO_SHORT',
  'FIGURING_IT_OUT',
  'SHORT_TERM_OPEN_TO_LONG',
  'SHORT_TERM',
  'NEW_FRIENDS',
];
const CHILDREN: ChildrenPreference[] = [
  'WANT_SOMEDAY',
  'DONT_WANT',
  'HAVE_AND_WANT_MORE',
  'HAVE_AND_DONT_WANT_MORE',
  'OPEN_TO_CHILDREN',
  'NOT_SURE',
];
const HABITS: LifestyleChoice[] = ['NO', 'SOMETIMES', 'YES'];
const ACTIVITY: Array<{ value: number | undefined; label: string }> = [
  { value: undefined, label: 'Anytime' },
  { value: 24, label: 'Active today' },
  { value: 168, label: 'This week' },
];
const SORTS: Array<{ value: FeedSort; label: string }> = [
  { value: 'RECOMMENDED', label: 'Recommended' },
  { value: 'NEAREST', label: 'Nearest' },
  { value: 'RECENTLY_ACTIVE', label: 'Active now' },
  { value: 'NEWEST', label: 'New here' },
];

const HEIGHT_MIN = 140;
const HEIGHT_MAX = 220;
const MAX_INTERESTS = 6;

export interface FilterSheetProps {
  open: boolean;
  value: FeedFilter;
  saving?: boolean;
  onClose: () => void;
  /** Resolves true when the filters were saved; the sheet closes only then. */
  onApply: (filter: FeedFilter) => Promise<boolean>;
}

/**
 * The discovery filters.
 *
 * <p>Split the way the big apps split them: who, age, distance and interests are free, in
 * the recommended order; the other sort orders, looking-for, height, activity, family
 * plans, habits and verification are Plus. Paid options are shown locked rather than
 * hidden - tapping one explains the upgrade - so the value of paying is visible instead
 * of mysterious.
 *
 * <p>"Show me" is the same setting as "Interested in" on the profile and in Settings;
 * saving here changes it everywhere.
 */
export function FilterSheet({ open, value, saving, onClose, onApply }: FilterSheetProps) {
  const { has } = useEntitlements();
  const advancedUnlocked = has('ADVANCED_FILTERS');
  const { interests } = useReferenceData();
  const openPaywall = useUiStore((state) => state.openPaywall);
  const toast = useUiStore((state) => state.toast);
  const [draft, setDraft] = useState<FeedFilter>(value);
  const [showAllInterests, setShowAllInterests] = useState(false);

  // Resync whenever the sheet opens, so it never disagrees with what was saved elsewhere.
  useEffect(() => {
    if (open) setDraft(value);
  }, [open, value]);

  const update = (patch: Partial<FeedFilter>) => setDraft((current) => ({ ...current, ...patch }));

  /** Runs a paid-filter change, or explains why it cannot. */
  const paid = (change: () => void) => {
    if (advancedUnlocked) {
      change();
      return;
    }
    openPaywall({
      title: 'Advanced filters',
      description:
        'Sort by nearest, active now or newest, and filter by what people are looking for, height, activity, family plans and habits on Plus.',
      requiredTier: 'PLUS',
      feature: 'ADVANCED_FILTERS',
    });
  };

  const genders = draft.genders ?? [];
  const interestIds = draft.interestIds ?? [];
  const distanceSlider = distanceToSlider(draft.maxDistanceKm);
  const minAge = draft.minAge ?? 18;
  const maxAge = draft.maxAge ?? 45;
  const minHeight = draft.minHeightCm ?? HEIGHT_MIN;
  const maxHeight = draft.maxHeightCm ?? HEIGHT_MAX;
  const heightFiltered = draft.minHeightCm != null || draft.maxHeightCm != null;

  // Selected interests first, so a long list never hides what is already chosen.
  const orderedInterests = [
    ...interests.filter((tag) => interestIds.includes(tag.id)),
    ...interests.filter((tag) => !interestIds.includes(tag.id)),
  ];
  const visibleInterests = showAllInterests ? orderedInterests : orderedInterests.slice(0, 14);

  const toggleInterest = (id: string) => {
    if (interestIds.includes(id)) {
      update({ interestIds: interestIds.filter((item) => item !== id) });
    } else if (interestIds.length >= MAX_INTERESTS) {
      toast({ title: `Pick up to ${MAX_INTERESTS} interests`, tone: 'error' });
    } else {
      update({ interestIds: [...interestIds, id] });
    }
  };

  const reset = () =>
    setDraft({
      sort: 'RECOMMENDED',
      genders: draft.genders,
      minAge: 18,
      maxAge: 45,
      maxDistanceKm: 80,
    });

  const apply = async () => {
    if (genders.length === 0) {
      toast({ title: 'Choose who you want to see', tone: 'error' });
      return;
    }
    // Only send paid filters when they can be used; a lapsed plan should still browse.
    const next: FeedFilter = advancedUnlocked
      ? draft
      : {
          sort: 'RECOMMENDED',
          genders: draft.genders,
          minAge: draft.minAge,
          maxAge: draft.maxAge,
          maxDistanceKm: draft.maxDistanceKm,
          interestIds: draft.interestIds,
        };
    if (await onApply(next)) onClose();
  };

  return (
    <Sheet open={open} onClose={onClose} title="Filters" size="tall">
      <div className="space-y-6">
        {/* Recommended is free; the other orders are Plus, like the paid filters they mirror. */}
        <Section title="Sort by" badge={!advancedUnlocked ? <PlusTag /> : undefined}>
          <div className="flex flex-wrap gap-2">
            {SORTS.map((sort) => {
              const locked = !advancedUnlocked && sort.value !== 'RECOMMENDED';
              return (
                <Chip
                  key={sort.value}
                  selected={(draft.sort ?? 'RECOMMENDED') === sort.value}
                  className={locked ? 'opacity-60' : undefined}
                  onClick={() =>
                    sort.value === 'RECOMMENDED'
                      ? update({ sort: sort.value })
                      : paid(() => update({ sort: sort.value }))
                  }
                >
                  {sort.label}
                </Chip>
              );
            })}
          </div>
        </Section>

        <Section title="Show me" hint="Also your “Interested in” on your profile.">
          <div className="flex flex-wrap gap-2">
            {GENDERS.map((gender) => (
              <Chip
                key={gender}
                selected={genders.includes(gender)}
                onClick={() => update({ genders: toggled(genders, gender) })}
              >
                {humanise(gender)}
              </Chip>
            ))}
          </div>
          {genders.length === 0 ? <p className="text-xs text-danger">Pick at least one.</p> : null}
        </Section>

        <Section title="Age range" value={`${minAge} – ${maxAge}`}>
          <div className="flex items-center gap-3">
            <input
              type="range"
              min={18}
              max={99}
              value={minAge}
              onChange={(event) => update({ minAge: Math.min(Number(event.target.value), maxAge) })}
              className="w-full"
              aria-label="Minimum age"
            />
            <input
              type="range"
              min={18}
              max={99}
              value={maxAge}
              onChange={(event) => update({ maxAge: Math.max(Number(event.target.value), minAge) })}
              className="w-full"
              aria-label="Maximum age"
            />
          </div>
        </Section>

        <Section title="Maximum distance" value={distanceFilterLabel(distanceSlider)}>
          <input
            type="range"
            min={DISTANCE_SLIDER_MIN}
            max={DISTANCE_SLIDER_MAX}
            step={1}
            value={distanceSlider}
            onChange={(event) => update({ maxDistanceKm: sliderToDistance(Number(event.target.value)) })}
            className="w-full"
            aria-label="Maximum distance in kilometres"
            aria-valuetext={distanceFilterLabel(distanceSlider)}
          />
          {isUnlimitedDistance(draft.maxDistanceKm) ? (
            <p className="text-xs text-ink-subtle">Distance is not limited.</p>
          ) : null}
        </Section>

        <Section
          title="Interests"
          value={interestIds.length ? `${interestIds.length}/${MAX_INTERESTS}` : undefined}
          hint="Show people who share at least one."
        >
          <div className="flex flex-wrap gap-2">
            {visibleInterests.map((tag) => (
              <Chip
                key={tag.id}
                size="sm"
                selected={interestIds.includes(tag.id)}
                onClick={() => toggleInterest(tag.id)}
              >
                {tag.emoji ? <span aria-hidden>{tag.emoji}</span> : null}
                {tag.label}
              </Chip>
            ))}
          </div>
          {orderedInterests.length > 14 ? (
            <button
              type="button"
              className="text-xs font-semibold text-accent"
              onClick={() => setShowAllInterests((current) => !current)}
            >
              {showAllInterests ? 'Show fewer' : `Show all ${orderedInterests.length}`}
            </button>
          ) : null}
        </Section>

        <div className="space-y-6 border-t border-border pt-5">
          <div className="flex items-center gap-2">
            <h3 className="font-display text-[16px] font-semibold text-ink">Advanced filters</h3>
            {!advancedUnlocked ? <PlusTag /> : null}
          </div>

          <Section title="Looking for" locked={!advancedUnlocked}>
            <div className="flex flex-wrap gap-2">
              {INTENTS.map((intent) => (
                <Chip
                  key={intent}
                  size="sm"
                  selected={(draft.intents ?? []).includes(intent)}
                  onClick={() => paid(() => update({ intents: toggled(draft.intents ?? [], intent) }))}
                >
                  {humanise(intent)}
                </Chip>
              ))}
            </div>
          </Section>

          <Section
            title="Height"
            locked={!advancedUnlocked}
            value={heightFiltered ? `${minHeight} – ${maxHeight} cm` : 'Any'}
          >
            <div className="flex items-center gap-3">
              <input
                type="range"
                min={HEIGHT_MIN}
                max={HEIGHT_MAX}
                value={minHeight}
                disabled={!advancedUnlocked}
                onChange={(event) =>
                  update({ minHeightCm: Math.min(Number(event.target.value), maxHeight), maxHeightCm: maxHeight })
                }
                className="w-full"
                aria-label="Minimum height in centimetres"
              />
              <input
                type="range"
                min={HEIGHT_MIN}
                max={HEIGHT_MAX}
                value={maxHeight}
                disabled={!advancedUnlocked}
                onChange={(event) =>
                  update({ maxHeightCm: Math.max(Number(event.target.value), minHeight), minHeightCm: minHeight })
                }
                className="w-full"
                aria-label="Maximum height in centimetres"
              />
            </div>
            {!advancedUnlocked ? (
              <button type="button" className="text-xs font-semibold text-accent" onClick={() => paid(() => {})}>
                Unlock height filter
              </button>
            ) : heightFiltered ? (
              <button
                type="button"
                className="text-xs font-semibold text-accent"
                onClick={() => update({ minHeightCm: undefined, maxHeightCm: undefined })}
              >
                Any height
              </button>
            ) : null}
          </Section>

          <Section title="Activity" locked={!advancedUnlocked}>
            <div className="flex flex-wrap gap-2">
              {ACTIVITY.map((option) => (
                <Chip
                  key={option.label}
                  size="sm"
                  selected={draft.activeWithinHours === option.value}
                  onClick={() => paid(() => update({ activeWithinHours: option.value }))}
                >
                  {option.label}
                </Chip>
              ))}
            </div>
          </Section>

          <Section title="Family plans" locked={!advancedUnlocked}>
            <div className="flex flex-wrap gap-2">
              {CHILDREN.map((value) => (
                <Chip
                  key={value}
                  size="sm"
                  selected={(draft.children ?? []).includes(value)}
                  onClick={() => paid(() => update({ children: toggled(draft.children ?? [], value) }))}
                >
                  {humanise(value)}
                </Chip>
              ))}
            </div>
          </Section>

          <HabitSection
            title="Drinking"
            locked={!advancedUnlocked}
            selected={draft.drinking ?? []}
            onToggle={(choice) => paid(() => update({ drinking: toggled(draft.drinking ?? [], choice) }))}
          />
          <HabitSection
            title="Smoking"
            locked={!advancedUnlocked}
            selected={draft.smoking ?? []}
            onToggle={(choice) => paid(() => update({ smoking: toggled(draft.smoking ?? [], choice) }))}
          />

          <Switch
            label="Verified profiles only"
            description={advancedUnlocked ? undefined : 'Available on Plus.'}
            checked={Boolean(draft.onlyVerified)}
            disabled={!advancedUnlocked}
            onChange={(checked) => update({ onlyVerified: checked })}
          />
        </div>

        {/*
          Sticky rather than inline: this sheet scrolls, and a confirm button that scrolls
          out of reach turns every filter change into a hunt for the bottom of the list.
        */}
        <div className="sticky bottom-0 -mx-6 -mb-8 flex gap-3 border-t border-border bg-surface px-6 pb-8 pt-4 sm:-mb-6 sm:pb-6">
          <Button variant="ghost" size="lg" onClick={reset} disabled={saving}>
            Reset
          </Button>
          <Button fullWidth size="lg" loading={saving} onClick={() => void apply()}>
            Apply filters
          </Button>
        </div>
      </div>
    </Sheet>
  );
}

function toggled<T>(list: T[], item: T): T[] {
  return list.includes(item) ? list.filter((value) => value !== item) : [...list, item];
}

function Section({
  title,
  value,
  hint,
  badge,
  locked,
  children,
}: {
  title: string;
  value?: string;
  hint?: string;
  badge?: ReactNode;
  locked?: boolean;
  children: ReactNode;
}) {
  return (
    <section className="space-y-2.5">
      <div className="flex items-baseline justify-between gap-3">
        <h3 className="flex items-center gap-2">
          <span className="eyebrow">{title}</span>
          {badge}
        </h3>
        {value ? (
          <span className="font-display text-[17px] font-semibold tabular-nums text-gradient">{value}</span>
        ) : null}
      </div>
      {hint ? <p className="-mt-1 text-xs text-ink-subtle">{hint}</p> : null}
      <div className={locked ? 'space-y-2 opacity-60' : 'space-y-2'}>{children}</div>
    </section>
  );
}

function HabitSection({
  title,
  locked,
  selected,
  onToggle,
}: {
  title: string;
  locked: boolean;
  selected: LifestyleChoice[];
  onToggle: (choice: LifestyleChoice) => void;
}) {
  return (
    <Section title={title} locked={locked}>
      <div className="flex flex-wrap gap-2">
        {HABITS.map((choice) => (
          <Chip key={choice} size="sm" selected={selected.includes(choice)} onClick={() => onToggle(choice)}>
            {humanise(choice)}
          </Chip>
        ))}
      </div>
    </Section>
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
