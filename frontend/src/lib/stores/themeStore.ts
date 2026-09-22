'use client';

import { create } from 'zustand';

export type Theme = 'light' | 'dark' | 'system';

const STORAGE_KEY = 'tt-theme';

interface ThemeState {
  theme: Theme;
  /** What is actually on screen once 'system' has been resolved. */
  resolved: 'light' | 'dark';
  setTheme: (theme: Theme) => void;
  /** Called by the provider when the OS preference changes under a 'system' choice. */
  syncResolved: (resolved: 'light' | 'dark') => void;
}

export function prefersDark(): boolean {
  if (typeof window === 'undefined') return false;
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

export function resolveTheme(theme: Theme): 'light' | 'dark' {
  return theme === 'system' ? (prefersDark() ? 'dark' : 'light') : theme;
}

/**
 * Reads the stored choice.
 *
 * <p>Wrapped because localStorage throws outright in a locked-down browser rather than
 * returning null, and a theme preference is not worth crashing the app over.
 */
export function readStoredTheme(): Theme {
  if (typeof window === 'undefined') return 'system';
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system';
  } catch {
    return 'system';
  }
}

/**
 * The colour scheme.
 *
 * <p>The store holds the preference; {@code ThemeProvider} is what touches the DOM. Both the
 * store's initial value and the class on <html> come from the same {@code readStoredTheme},
 * so the first paint and the first render agree and nothing flashes.
 */
export const useThemeStore = create<ThemeState>((set) => ({
  theme: 'system',
  resolved: 'light',

  setTheme(theme) {
    try {
      window.localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // A browser that refuses storage still gets the theme for this session.
    }
    set({ theme, resolved: resolveTheme(theme) });
  },

  syncResolved(resolved) {
    set({ resolved });
  },
}));
