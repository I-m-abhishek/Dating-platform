'use client';

import type { ReactNode } from 'react';
import { SideNav } from '@/components/layout/SideNav';
import { BottomNav } from '@/components/layout/BottomNav';

/**
 * The app shell.
 *
 * <p>No auth guard here on purpose. Guarding a layout means every page inside it shares one
 * redirect decision, and pages like onboarding need a different one. The guard is a HOC on
 * each page instead - see {@code withAuth} - which keeps the rule next to the thing it
 * protects.
 */
export default function MainLayout({ children }: { children: ReactNode }) {
  return (
    <div className="relative flex min-h-screen bg-bg">
      {/* Ambient wash. Fixed and inert, so it never intercepts a tap or scrolls with content. */}
      <div aria-hidden className="aurora" />

      <SideNav />

      <main className="relative z-10 mx-auto w-full max-w-2xl flex-1 pb-28 md:pb-10">
        {children}
      </main>

      <BottomNav />
    </div>
  );
}
