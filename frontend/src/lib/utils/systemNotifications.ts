'use client';

/**
 * OS-level notifications for when the app is open but not in view.
 *
 * <p>In-app toasts cover the foreground. When the tab is hidden (another tab, the phone
 * locked with Safari in the background) a toast is never seen, so the same event is raised
 * as a system notification instead. It goes through the service worker where one is
 * registered, because Android Chrome refuses {@code new Notification()} outright.
 *
 * <p>Browsers without the API (iOS Safari outside a home-screen app) are a silent no-op.
 */

const SW_PATH = '/sw.js';

function supported(): boolean {
  return typeof window !== 'undefined' && 'Notification' in window;
}

export function notificationPermission(): NotificationPermission | 'unsupported' {
  return supported() ? Notification.permission : 'unsupported';
}

let registration: Promise<ServiceWorkerRegistration | null> | null = null;

function serviceWorker(): Promise<ServiceWorkerRegistration | null> {
  if (!registration) {
    registration =
      typeof navigator !== 'undefined' && 'serviceWorker' in navigator && window.isSecureContext
        ? navigator.serviceWorker.register(SW_PATH).catch(() => null)
        : Promise.resolve(null);
  }
  return registration;
}

/** Must run inside a user gesture - Safari and Firefox ignore the request otherwise. */
export async function requestNotificationPermission(): Promise<void> {
  if (!supported() || Notification.permission !== 'default') return;
  try {
    await Notification.requestPermission();
    void serviceWorker();
  } catch {
    // Older Safari only has the callback form; not worth a polyfill for an optional feature.
  }
}

export interface SystemNotificationOptions {
  body?: string;
  /** Same tag replaces the previous notification instead of stacking another one. */
  tag?: string;
  /** Where a tap should take the user. */
  url?: string;
  requireInteraction?: boolean;
}

/** Shows a system notification, but only when the page is not what the user is looking at. */
export async function showSystemNotification(
  title: string,
  options: SystemNotificationOptions = {},
): Promise<void> {
  if (!supported() || Notification.permission !== 'granted') return;
  if (document.visibilityState === 'visible' && document.hasFocus()) return;

  const { url, ...rest } = options;
  const init: NotificationOptions = { ...rest, icon: '/icon.svg', data: { url } };

  const sw = await serviceWorker();
  if (sw) {
    try {
      await sw.showNotification(title, init);
      return;
    } catch {
      // Fall through to the page-level constructor.
    }
  }

  try {
    const notification = new Notification(title, init);
    notification.onclick = () => {
      window.focus();
      if (url) window.location.assign(url);
      notification.close();
    };
  } catch {
    // Constructor is illegal on some mobile browsers and there was no worker to fall back to.
  }
}
