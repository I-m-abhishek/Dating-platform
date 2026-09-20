'use client';

import { useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';
import { destinations, socket } from '@/lib/ws/socket';
import { useUiStore } from '@/lib/stores/uiStore';
import type { AppNotification } from '@/lib/api/types';

/**
 * Notifications, live and persisted.
 *
 * <p>The socket delivers them instantly when the app is open; the list endpoint is the
 * record of what was missed. Both are kept in sync by invalidating on every live event.
 */
export function useNotifications(page = 0, size = 20) {
  const queryClient = useQueryClient();
  const toast = useUiStore((state) => state.toast);

  const query = useQuery({
    queryKey: [...queryKeys.notifications.list(), page, size],
    queryFn: () => notificationApi.list(page, size),
  });

  useEffect(() => {
    return socket.subscribe(destinations.notifications, (payload) => {
      const notification = payload as AppNotification;
      if (!notification?.title) return;

      toast({ title: notification.title, description: notification.body, tone: 'default' });
      void queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all });

      // Nudge the counters the tab bar shows.
      if (notification.type === 'NEW_MESSAGE') {
        void queryClient.invalidateQueries({ queryKey: queryKeys.chat.all });
      }
      if (notification.type === 'NEW_LIKE') {
        void queryClient.invalidateQueries({ queryKey: queryKeys.likes.all });
      }
      if (notification.type === 'NEW_MATCH' || notification.type === 'AUTO_MATCH_READY') {
        void queryClient.invalidateQueries({ queryKey: queryKeys.matches.all });
      }
    });
  }, [queryClient, toast]);

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
    refetchInterval: 90_000,
  });
}
