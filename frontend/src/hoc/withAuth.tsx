'use client';

import { useEffect, type ComponentType } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { useAuthStore } from '@/lib/stores/authStore';
import { FullPageLoader } from '@/components/ui/FullPageLoader';

export interface WithAuthOptions {
  /** Where to send anonymous visitors. */
  redirectTo?: string;
  /**
   * Also require a finished onboarding. Pages inside the main app shell set this;
   * the onboarding flow itself does not, or it would redirect to itself.
   */
  requireOnboarding?: boolean;
}

/**
 * Gate a page behind a signed-in session.
 *
 * <p>Why a HOC and not a hook: the guard has to decide whether the wrapped component
 * renders <em>at all</em>. A hook runs inside the component, so the protected UI would
 * mount and fire its queries before the redirect - a visible flash of private chrome and a
 * burst of 401s. Wrapping lets us return a loader instead of ever mounting the page.
 *
 * <p>The redirect carries the attempted path as {@code ?next=}, so signing in lands the
 * user where they were going.
 *
 * @example
 * export default withAuth(MatchesPage);
 * export default withAuth(OnboardingPage, { requireOnboarding: false });
 */
export function withAuth<P extends object>(
  Component: ComponentType<P>,
  options: WithAuthOptions = {},
): ComponentType<P> {
  const { redirectTo = '/login', requireOnboarding = true } = options;

  function AuthGuarded(props: P) {
    const router = useRouter();
    const pathname = usePathname();
    const status = useAuthStore((state) => state.status);
    const account = useAuthStore((state) => state.account);
    const bootstrap = useAuthStore((state) => state.bootstrap);

    useEffect(() => {
      if (status === 'idle') {
        void bootstrap();
      }
    }, [status, bootstrap]);

    useEffect(() => {
      if (status === 'anonymous') {
        const next = pathname && pathname !== '/' ? `?next=${encodeURIComponent(pathname)}` : '';
        router.replace(`${redirectTo}${next}`);
        return;
      }
      if (status === 'authenticated' && requireOnboarding && account && !account.onboardingCompleted) {
        router.replace('/onboarding');
      }
    }, [status, account, router, pathname]);

    if (status !== 'authenticated' || !account) {
      return <FullPageLoader label="Getting things ready" />;
    }
    if (requireOnboarding && !account.onboardingCompleted) {
      return <FullPageLoader label="Taking you to setup" />;
    }

    return <Component {...props} />;
  }

  AuthGuarded.displayName = `withAuth(${Component.displayName ?? Component.name ?? 'Component'})`;
  return AuthGuarded;
}
