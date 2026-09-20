'use client';

import { Suspense, useEffect, type ComponentType } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuthStore } from '@/lib/stores/authStore';
import { FullPageLoader } from '@/components/ui/FullPageLoader';

/**
 * The mirror of {@link withAuth}: for sign-in and sign-up pages, which a signed-in user
 * should never see. Honours the {@code ?next=} parameter that {@code withAuth} set.
 *
 * <p><b>Why the Suspense boundary.</b> Reading {@code useSearchParams()} opts a route out of
 * static prerendering, and Next refuses to build unless the read sits inside a boundary. It
 * belongs here rather than in each page: this HOC is the thing that reads the parameter, so
 * it is the thing that should declare the boundary. Every page it wraps gets it for free.
 *
 * @example
 * export default withGuest(LoginPage);
 */
export function withGuest<P extends object>(Component: ComponentType<P>): ComponentType<P> {
  function GuestOnlyInner(props: P) {
    const router = useRouter();
    const searchParams = useSearchParams();
    const status = useAuthStore((state) => state.status);
    const account = useAuthStore((state) => state.account);
    const bootstrap = useAuthStore((state) => state.bootstrap);

    useEffect(() => {
      if (status === 'idle') {
        void bootstrap();
      }
    }, [status, bootstrap]);

    useEffect(() => {
      if (status !== 'authenticated') return;
      if (account && !account.onboardingCompleted) {
        router.replace('/onboarding');
        return;
      }
      router.replace(searchParams.get('next') ?? '/home');
    }, [status, account, router, searchParams]);

    if (status === 'idle' || status === 'loading') {
      return <FullPageLoader />;
    }
    if (status === 'authenticated') {
      return <FullPageLoader label="Signing you in" />;
    }
    return <Component {...props} />;
  }

  function GuestOnly(props: P) {
    return (
      <Suspense fallback={<FullPageLoader />}>
        <GuestOnlyInner {...props} />
      </Suspense>
    );
  }

  GuestOnly.displayName = `withGuest(${Component.displayName ?? Component.name ?? 'Component'})`;
  return GuestOnly;
}
