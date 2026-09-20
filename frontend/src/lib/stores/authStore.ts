'use client';

import { create } from 'zustand';
import { tokenStore } from '@/lib/api/tokenStore';
import { accountApi, authApi, type RegisterPayload } from '@/lib/api/endpoints';
import type { Account } from '@/lib/api/types';

/**
 * Session state.
 *
 * The tokens themselves live in {@link tokenStore} (which the HTTP client reads directly);
 * this store holds the derived state React renders from. Keeping them separate means the
 * client never has to import a React store, and the store never has to know about headers.
 */
interface AuthState {
  status: 'idle' | 'loading' | 'authenticated' | 'anonymous';
  account: Account | null;

  bootstrap: () => Promise<void>;
  login: (email: string, password: string) => Promise<Account>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => Promise<void>;
  setAccount: (account: Account) => void;
  refreshAccount: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  status: 'idle',
  account: null,

  /** Runs once on mount: rehydrate the token, then confirm it with the server. */
  async bootstrap() {
    if (get().status === 'loading') return;
    set({ status: 'loading' });

    tokenStore.hydrate();
    if (!tokenStore.getAccessToken()) {
      set({ status: 'anonymous', account: null });
      return;
    }
    try {
      const account = await accountApi.me();
      set({ status: 'authenticated', account });
    } catch {
      tokenStore.clear();
      set({ status: 'anonymous', account: null });
    }
  },

  async login(email, password) {
    const auth = await authApi.login(email, password);
    tokenStore.setSession(auth);
    const account = await accountApi.me();
    set({ status: 'authenticated', account });
    return account;
  },

  async register(payload) {
    const auth = await authApi.register(payload);
    tokenStore.setSession(auth);
    const account = await accountApi.me();
    set({ status: 'authenticated', account });
  },

  async logout() {
    try {
      await authApi.logout(tokenStore.getRefreshToken());
    } catch {
      // Signing out locally must succeed even when the server call does not.
    }
    tokenStore.clear();
    set({ status: 'anonymous', account: null });
  },

  setAccount(account) {
    set({ account, status: 'authenticated' });
  },

  async refreshAccount() {
    if (!tokenStore.getAccessToken()) return;
    const account = await accountApi.me();
    set({ account });
  },
}));
