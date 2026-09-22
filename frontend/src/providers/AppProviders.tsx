'use client';

import type { ReactNode } from 'react';
import { QueryProvider } from './QueryProvider';
import { SessionProvider } from './SessionProvider';
import { ThemeProvider } from './ThemeProvider';
import { Toaster } from '@/components/ui/Toaster';
import { PaywallSheet } from '@/components/paywall/PaywallSheet';

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
          {children}
          <Toaster />
          <PaywallSheet />
        </SessionProvider>
      </QueryProvider>
    </ThemeProvider>
  );
}
