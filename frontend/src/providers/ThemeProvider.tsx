'use client';

import { useEffect, type ReactNode } from 'react';
import { readStoredTheme, resolveTheme, useThemeStore } from '@/lib/stores/themeStore';

/**
 * Keeps the {@code .dark} class on <html> in step with the stored preference.
 *
 * <p>The class is also set by a blocking script in the document head - see
 * {@code themeScript} - so the correct palette is painted on the very first frame. This
 * provider takes over afterwards: it seeds the store from storage and, while the choice is
 * 'system', follows the OS if it changes mid-session.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const theme = useThemeStore((state) => state.theme);
  const setTheme = useThemeStore((state) => state.setTheme);
  const syncResolved = useThemeStore((state) => state.syncResolved);

  // Runs once, after hydration. Reading storage during render would desync server and client.
  useEffect(() => {
    setTheme(readStoredTheme());
  }, [setTheme]);

  useEffect(() => {
    const resolved = resolveTheme(theme);
    document.documentElement.classList.toggle('dark', resolved === 'dark');
    syncResolved(resolved);

    if (theme !== 'system') return;

    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = (event: MediaQueryListEvent) => {
      document.documentElement.classList.toggle('dark', event.matches);
      syncResolved(event.matches ? 'dark' : 'light');
    };
    media.addEventListener('change', onChange);
    return () => media.removeEventListener('change', onChange);
  }, [theme, syncResolved]);

  return <>{children}</>;
}

/**
 * Applies the theme before the first paint.
 *
 * <p>Injected as a synchronous inline script. Doing this in React instead would render one
 * frame of the light palette before the effect runs, which on a dark-mode device is a white
 * flash straight in the face.
 */
export const themeScript = `(function(){try{var t=localStorage.getItem('tt-theme');var d=t==='dark'||((!t||t==='system')&&window.matchMedia('(prefers-color-scheme: dark)').matches);document.documentElement.classList.toggle('dark',d);}catch(e){}})();`;
