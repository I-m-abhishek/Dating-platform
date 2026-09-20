'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/lib/stores/authStore';
import { FullPageLoader } from '@/components/ui/FullPageLoader';

/**
 * The root only routes. It sends signed-in users to the feed, half-onboarded users to
 * onboarding, and everyone else to sign-in - which keeps that decision in one place
 * instead of spread across three layouts.
 */
export default function RootPage() {
  const router = useRouter();
  const status = useAuthStore((state) => state.status);
  const account = useAuthStore((state) => state.account);

  useEffect(() => {
    if (status === 'anonymous') {
      router.replace('/login');
      return;
    }
    if (status === 'authenticated' && account) {
      router.replace(account.onboardingCompleted ? '/home' : '/onboarding');
    }
  }, [status, account, router]);

  return <FullPageLoader />;
}
