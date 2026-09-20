'use client';

import type { AuthResponse } from './types';

/**
 * Where the session lives.
 *
 * <p>Tokens are kept in memory and mirrored to {@code localStorage} so a refresh does not
 * sign the user out. That is a deliberate trade-off: an httpOnly cookie would be stronger
 * against XSS, but this client talks to a stateless API across an origin boundary, and the
 * memory-first pattern keeps the token out of every request the browser makes on its own.
 * Access tokens live 15 minutes, which bounds the damage.
 */
const ACCESS_KEY = 'dp.accessToken';
const REFRESH_KEY = 'dp.refreshToken';
const USER_KEY = 'dp.userId';

type Listener = (token: string | null) => void;

let accessToken: string | null = null;
let refreshToken: string | null = null;
let userId: string | null = null;
const listeners = new Set<Listener>();

function isBrowser(): boolean {
  return typeof window !== 'undefined';
}

export const tokenStore = {
  /** Call once on boot to rehydrate from storage. */
  hydrate(): void {
    if (!isBrowser()) return;
    accessToken = window.localStorage.getItem(ACCESS_KEY);
    refreshToken = window.localStorage.getItem(REFRESH_KEY);
    userId = window.localStorage.getItem(USER_KEY);
  },

  getAccessToken(): string | null {
    return accessToken;
  },

  getRefreshToken(): string | null {
    return refreshToken;
  },

  getUserId(): string | null {
    return userId;
  },

  setSession(auth: Pick<AuthResponse, 'accessToken' | 'refreshToken' | 'userId'>): void {
    accessToken = auth.accessToken;
    refreshToken = auth.refreshToken;
    userId = auth.userId;
    if (isBrowser()) {
      window.localStorage.setItem(ACCESS_KEY, auth.accessToken);
      window.localStorage.setItem(REFRESH_KEY, auth.refreshToken);
      window.localStorage.setItem(USER_KEY, auth.userId);
    }
    listeners.forEach((listener) => listener(accessToken));
  },

  clear(): void {
    accessToken = null;
    refreshToken = null;
    userId = null;
    if (isBrowser()) {
      window.localStorage.removeItem(ACCESS_KEY);
      window.localStorage.removeItem(REFRESH_KEY);
      window.localStorage.removeItem(USER_KEY);
    }
    listeners.forEach((listener) => listener(null));
  },

  /** Notifies on sign-in and sign-out; the socket provider uses this to reconnect. */
  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};
