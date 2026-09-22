'use client';

import { LinkButton } from '@/components/ui/LinkButton';
import { SparkleIcon } from '@/components/ui/icons';
import { humanise } from '@/lib/utils/format';
import type { Feature } from '@/lib/api/types';

const COPY: Partial<Record<Feature, { title: string; body: string }>> = {
  SEE_WHO_LIKES_YOU: {
    title: 'See who likes you',
    body: 'Skip the guessing. Open every like and match instantly.',
  },
  DAILY_AUTO_MATCH: {
    title: 'A new match every day',
    body: 'Free accounts get one hand-picked match a week. Premium gets one every day.',
  },
  EXTRA_PHOTO_COMMENTS: {
    title: 'More comments',
    body: 'Five a day is the free allowance. Upgrade for more conversations.',
  },
  ADVANCED_FILTERS: {
    title: 'Filter by what matters',
    body: 'Height, intent, verification and shared interests.',
  },
  GLOBAL_MODE: { title: 'Match anywhere', body: 'Drop the distance filter and see the world.' },
  INCOGNITO: { title: 'Browse privately', body: 'Look around without appearing in discovery.' },
  REWIND: { title: 'Undo a pass', body: 'Changed your mind? Bring the last profile back.' },
  UNLIMITED_LIKES: { title: 'Unlimited likes', body: 'Never run out before you find them.' },
  READ_RECEIPTS: { title: 'Read receipts', body: 'Know when your message has landed.' },
};

/** Full-panel upsell. Used by {@code withEntitlement} when a whole screen is gated. */
export function UpgradePrompt({ feature }: { feature: Feature }) {
  const copy = COPY[feature] ?? { title: humanise(feature), body: 'Available on a paid plan.' };

  return (
    <div className="flex animate-slide-up flex-col items-center gap-5 px-6 py-16 text-center">
      <div className="relative flex h-[72px] w-[72px] items-center justify-center">
        <span
          aria-hidden
          className="absolute inset-0 animate-pulse-ring rounded-full bg-accent opacity-25"
        />
        <span className="relative flex h-full w-full items-center justify-center rounded-full bg-accent-gradient text-white shadow-glow-lg">
          <SparkleIcon size={30} />
        </span>
      </div>

      <div className="space-y-2">
        <h2 className="font-display text-[23px] font-semibold tracking-[-0.02em] text-ink">
          {copy.title}
        </h2>
        <p className="mx-auto max-w-[20rem] text-sm leading-relaxed text-ink-muted">{copy.body}</p>
      </div>

      <LinkButton href="/plans" size="lg">
        See plans
      </LinkButton>
    </div>
  );
}
