'use client';

import type { ReactNode } from 'react';
import { QueryProvider } from './QueryProvider';
import { SessionProvider } from './SessionProvider';
import { CallProvider } from './CallProvider';
import { ThemeProvider } from './ThemeProvider';
import { Toaster } from '@/components/ui/Toaster';
import { PaywallSheet } from '@/components/paywall/PaywallSheet';
import { useLiveNotifications } from '@/lib/hooks/useNotifications';

/** Listens for notifications on every screen, not just one that happens to ask. */
function LiveNotifications() {
  useLiveNotifications();
  return null;
}

/**
 * Single mount point for everything global.
 *
 * <p>Order matters: queries first (the session bootstrap uses them), then the session, then
 * the overlays that read from both. The theme sits outermost because it touches only the
 * document element and must not wait on a network call to settle.
 */
export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider>
      <QueryProvider>
        <SessionProvider>
          <CallProvider>
            {children}
            <LiveNotifications />
            <Toaster />
            <PaywallSheet />
          </CallProvider>
        </SessionProvider>
      </QueryProvider>
    </ThemeProvider>
  );
}
