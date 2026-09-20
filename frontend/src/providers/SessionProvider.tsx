'use client';

import { useEffect, type ReactNode } from 'react';
import { useAuthStore } from '@/lib/stores/authStore';
import { tokenStore } from '@/lib/api/tokenStore';
import { socket } from '@/lib/ws/socket';
import { accountApi } from '@/lib/api/endpoints';

const HEARTBEAT_INTERVAL_MS = 5 * 60_000;

/**
 * Ties the session to everything that depends on it.
 *
 * <p>Three jobs: restore the session on boot, open and close the socket as the session
 * comes and goes, and send a periodic heartbeat so the "active recently" badge means
 * something. All three are side effects of being signed in, so they live in one place
 * rather than being re-implemented per screen.
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const status = useAuthStore((state) => state.status);
  const bootstrap = useAuthStore((state) => state.bootstrap);

  useEffect(() => {
    void bootstrap();
  }, [bootstrap]);

  // Socket follows the token: connect on sign-in, drop on sign-out.
  useEffect(() => {
    const unsubscribe = tokenStore.subscribe((token) => {
      if (token) {
        socket.connect();
      } else {
        socket.disconnect();
      }
    });

    if (tokenStore.getAccessToken()) {
      socket.connect();
    }
    return () => {
      unsubscribe();
      socket.disconnect();
    };
  }, []);

  useEffect(() => {
    if (status !== 'authenticated') return;

    const beat = () => {
      void accountApi.heartbeat().catch(() => {
        // A missed heartbeat is cosmetic; never surface it.
      });
    };
    beat();
    const interval = setInterval(beat, HEARTBEAT_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [status]);

  return <>{children}</>;
}
