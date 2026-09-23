/*
 * Minimal service worker: lets the page raise system notifications on browsers that only
 * allow them through a worker (Android Chrome), and routes a tap back into the app.
 */
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = (event.notification.data && event.notification.data.url) || '/';

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
      for (const client of windows) {
        if ('focus' in client) {
          // navigate() rejects for a window this worker does not control; focusing is enough then.
          return client.focus().then((focused) => focused.navigate(url)).catch(() => undefined);
        }
      }
      return self.clients.openWindow(url);
    }),
  );
});
