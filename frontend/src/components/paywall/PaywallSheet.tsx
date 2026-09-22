'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Sheet } from '@/components/ui/Sheet';
import { Button } from '@/components/ui/Button';
import { CrownIcon } from '@/components/ui/icons';
import { useUiStore } from '@/lib/stores/uiStore';
import { relativeTime } from '@/lib/utils/format';

/**
 * The global paywall.
 *
 * <p>Opened by {@code usePaywall} when the API answers QUOTA_EXCEEDED or PREMIUM_REQUIRED,
 * using the context the server sent - the real limit, the real reset time, the real tier.
 * Nothing here is guessed, which is why the copy can be specific instead of generic.
 */
export function PaywallSheet() {
  const paywall = useUiStore((state) => state.paywall);
  const close = useUiStore((state) => state.closePaywall);
  const router = useRouter();

  if (!paywall) return null;

  return (
    <Sheet open onClose={close} title={paywall.title} description={paywall.description}>
      <div className="space-y-4">
        {paywall.resetsAt ? (
          <p className="flex items-center gap-2.5 rounded-xl2 bg-surface-muted px-4 py-3 text-sm text-ink-muted">
            <ClockGlyph />
            Your allowance resets in {relativeTime(paywall.resetsAt).replace(' ago', '')}.
          </p>
        ) : null}

        <div className="flex flex-col gap-2">
          <Button
            fullWidth
            size="lg"
            onClick={() => {
              close();
              router.push('/plans');
            }}
            leftIcon={<CrownIcon size={17} />}
          >
            See {paywall.requiredTier === 'PREMIUM' ? 'Premium' : 'Plus'}
          </Button>
          <Button variant="ghost" fullWidth onClick={close}>
            Not now
          </Button>
        </div>

        <p className="text-center text-xs text-ink-subtle">
          <Link href="/plans" className="font-medium underline underline-offset-2">
            Compare every plan
          </Link>
        </p>
      </div>
    </Sheet>
  );
}

function ClockGlyph() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.7}
      strokeLinecap="round"
      className="h-4 w-4 shrink-0 text-accent"
      aria-hidden
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7.5V12l3 1.8" />
    </svg>
  );
}
