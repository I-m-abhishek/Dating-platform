'use client';

import { useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';
import { destinations, socket } from '@/lib/ws/socket';
import { useUiStore } from '@/lib/stores/uiStore';
import {
  notificationPermission,
  requestNotificationPermission,
  showSystemNotification,
} from '@/lib/utils/systemNotifications';
import type { AppNotification } from '@/lib/api/types';

/** Where tapping a notification should land. */
function linkFor(notification: AppNotification): string {
  switch (notification.type) {
    case 'NEW_MESSAGE':
      return notification.targetId ? `/messages/${notification.targetId}` : '/messages';
    case 'NEW_MATCH':
    case 'AUTO_MATCH_READY':
      return '/matches';
    case 'NEW_LIKE':
      return '/likes';
    case 'MISSED_CALL':
      return '/messages';
    default:
      return '/home';
  }
}

/**
 * The live half of notifications: toast when the app is in view, a system notification
 * when it is not, and cache invalidation so badges and lists catch up.
 *
 * <p>Mounted ONCE, app-wide, by {@code LiveNotifications}. It used to sit inside
 * {@link useNotifications}, which no screen rendered - so nothing was listening at all.
 */
export function useLiveNotifications() {
  const queryClient = useQueryClient();
  const toast = useUiStore((state) => state.toast);

  useEffect(() => {
    return socket.subscribe(destinations.notifications, (payload) => {
      const notification = payload as AppNotification;
      if (!notification?.title) return;

      // A message for the thread that is already open on screen needs no interruption.
      const onThatThread =
        notification.type === 'NEW_MESSAGE' &&
        notification.targetId &&
        window.location.pathname === `/messages/${notification.targetId}` &&
        document.visibilityState === 'visible';

      if (!onThatThread) {
        toast({
          title: notification.title,
          description: notification.body,
          tone: notification.type === 'NEW_MATCH' || notification.type === 'AUTO_MATCH_READY'
            ? 'success'
            : 'default',
        });
        void showSystemNotification(notification.title, {
          body: notification.body,
          tag: notification.targetId ?? notification.id,
          url: linkFor(notification),
        });
      }

      void queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all });

      // Nudge the counters the tab bar shows.
      // Only the list and the badge - chat.all would also refetch the open thread's
      // history, which resets it to page one and drops older messages already loaded.
      if (notification.type === 'NEW_MESSAGE' || notification.type === 'MISSED_CALL') {
        void queryClient.invalidateQueries({ queryKey: queryKeys.chat.conversations() });
        void queryClient.invalidateQueries({ queryKey: queryKeys.chat.unreadCount() });
      }
      if (notification.type === 'NEW_LIKE') {
        void queryClient.invalidateQueries({ queryKey: queryKeys.likes.all });
      }
      if (notification.type === 'NEW_MATCH' || notification.type === 'AUTO_MATCH_READY') {
        void queryClient.invalidateQueries({ queryKey: queryKeys.matches.all });
        void queryClient.invalidateQueries({ queryKey: queryKeys.chat.conversations() });
      }
    });
  }, [queryClient, toast]);

  // Browsers only allow the permission prompt from a user gesture, so ask on the first tap.
  useEffect(() => {
    if (notificationPermission() !== 'default') return;
    const ask = () => void requestNotificationPermission();
    window.addEventListener('pointerdown', ask, { once: true });
    return () => window.removeEventListener('pointerdown', ask);
  }, []);
}

/**
 * The persisted notification list - the record of what was missed. Live delivery is
 * {@link useLiveNotifications}, which invalidates this list on every event.
 */
export function useNotifications(page = 0, size = 20) {
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: [...queryKeys.notifications.list(), page, size],
    queryFn: () => notificationApi.list(page, size),
  });

  const markAllRead = useMutation({
    mutationFn: notificationApi.markAllRead,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all });
    },
  });

  return {
    query,
    notifications: query.data?.items ?? [],
    markAllRead: markAllRead.mutate,
  };
}

export function useUnreadNotificationCount() {
  return useQuery({
    queryKey: queryKeys.notifications.unreadCount(),
    queryFn: notificationApi.unreadCount,
    select: (data) => data.count,
    refetchInterval: 180_000,
  });
}
