'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Sheet } from '@/components/ui/Sheet';
import { Button } from '@/components/ui/Button';
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
          <p className="rounded-2xl bg-surface-muted px-4 py-3 text-sm text-ink-muted">
            Your allowance resets in {relativeTime(paywall.resetsAt).replace(' ago', '')}.
          </p>
        ) : null}

        <div className="flex flex-col gap-2">
          <Button
            fullWidth
            onClick={() => {
              close();
              router.push('/plans');
            }}
          >
            See {paywall.requiredTier === 'PREMIUM' ? 'Premium' : 'Plus'}
          </Button>
          <Button variant="ghost" fullWidth onClick={close}>
            Not now
          </Button>
        </div>

        <p className="text-center text-xs text-ink-subtle">
          <Link href="/plans" className="underline">
            Compare every plan
          </Link>
        </p>
      </div>
    </Sheet>
  );
}
