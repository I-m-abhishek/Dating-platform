'use client';

import { create } from 'zustand';
import type { Feature, PlanTier } from '@/lib/api/types';

export interface Toast {
  id: string;
  title: string;
  description?: string;
  tone: 'default' | 'success' | 'error';
}

/** What the paywall sheet needs to explain itself. */
export interface PaywallContext {
  title: string;
  description: string;
  requiredTier: PlanTier;
  feature?: Feature;
  resetsAt?: string;
}

interface UiState {
  toasts: Toast[];
  paywall: PaywallContext | null;
  filtersOpen: boolean;

  toast: (toast: Omit<Toast, 'id'>) => void;
  dismissToast: (id: string) => void;
  openPaywall: (context: PaywallContext) => void;
  closePaywall: () => void;
  setFiltersOpen: (open: boolean) => void;
}

export const useUiStore = create<UiState>((set) => ({
  toasts: [],
  paywall: null,
  filtersOpen: false,

  toast(toast) {
    const id = crypto.randomUUID();
    set((state) => ({ toasts: [...state.toasts, { ...toast, id }] }));
    // Toasts are transient by design; anything that must persist belongs in a notification.
    setTimeout(() => {
      set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) }));
    }, 4500);
  },

  dismissToast(id) {
    set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) }));
  },

  openPaywall(context) {
    set({ paywall: context });
  },

  closePaywall() {
    set({ paywall: null });
  },

  setFiltersOpen(open) {
    set({ filtersOpen: open });
  },
}));
