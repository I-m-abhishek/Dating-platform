'use client';

import type { ReactNode } from 'react';
import { QueryProvider } from './QueryProvider';
import { SessionProvider } from './SessionProvider';
import { Toaster } from '@/components/ui/Toaster';
import { PaywallSheet } from '@/components/paywall/PaywallSheet';

/**
 * Single mount point for everything global.
 *
 * <p>Order matters: queries first (the session bootstrap uses them), then the session, then
 * the overlays that read from both.
 */
export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <QueryProvider>
      <SessionProvider>
        {children}
        <Toaster />
        <PaywallSheet />
      </SessionProvider>
    </QueryProvider>
  );
}
