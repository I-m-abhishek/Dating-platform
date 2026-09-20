'use client';

import Link from 'next/link';
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
    <div className="flex flex-col items-center gap-4 px-6 py-16 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-full bg-accent-soft text-2xl">
        ✦
      </div>
      <div className="space-y-1.5">
        <h2 className="text-lg font-semibold text-ink">{copy.title}</h2>
        <p className="mx-auto max-w-sm text-sm text-ink-muted">{copy.body}</p>
      </div>
      <Link
        href="/plans"
        className="mt-1 inline-flex h-11 items-center justify-center rounded-pill bg-accent px-6 text-[15px] font-medium text-accent-ink transition-[filter] hover:brightness-95"
      >
        See plans
      </Link>
    </div>
  );
}
