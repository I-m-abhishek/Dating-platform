'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { chatApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';
import { destinations, socket } from '@/lib/ws/socket';
import { useAuthStore } from '@/lib/stores/authStore';
import { usePaywall } from './usePaywall';
import type { Conversation, CursorPageResponse, Message } from '@/lib/api/types';
import { randomId } from '@/lib/utils/id';

export function useConversations(page = 0, size = 20) {
  return useQuery({
    queryKey: [...queryKeys.chat.conversations(), page, size],
    queryFn: () => chatApi.conversations(page, size),
  });
}

export function useUnreadCount() {
  return useQuery({
    queryKey: queryKeys.chat.unreadCount(),
    queryFn: chatApi.unreadCount,
    select: (data) => data.count,
    refetchInterval: 60_000,
  });
}

/**
 * One chat thread: history, live updates, sending, typing and read receipts.
 *
 * <p>History is loaded over REST and kept newest-first; the socket only appends. That split
 * matters - if the socket drops, the thread still works on a refresh, because the socket is
 * an accelerator and never the source of truth.
 *
 * <p>Sends are optimistic and carry a client-generated id. The server echoes that id back
 * and treats a repeat as a duplicate, so a retry over a flaky connection cannot post twice.
 */
export function useConversation(conversationId: string) {
  const queryClient = useQueryClient();
  const { handleError } = usePaywall();
  const myId = useAuthStore((state) => state.account?.id);
  const myIdRef = useRef(myId);
  myIdRef.current = myId;

  const [messages, setMessages] = useState<Message[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [typing, setTyping] = useState(false);
  const typingTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);

  const conversationQuery = useQuery({
    queryKey: queryKeys.chat.conversation(conversationId),
    queryFn: () => chatApi.conversation(conversationId),
    enabled: Boolean(conversationId),
  });

  const historyQuery = useQuery({
    queryKey: queryKeys.chat.messages(conversationId),
    queryFn: () => chatApi.messages(conversationId),
    enabled: Boolean(conversationId),
  });

  // Seed local state from the first history page.
  useEffect(() => {
    if (!historyQuery.data) return;
    setMessages(historyQuery.data.items);
    setCursor(historyQuery.data.nextCursor);
    setHasMore(historyQuery.data.hasMore);
  }, [historyQuery.data]);

  const appendIncoming = useCallback((raw: Message) => {
    /*
     * The server broadcasts ONE payload to everyone on the topic, with `mine` computed for
     * the sender. Re-derive it for whoever is looking, or the receiver renders the other
     * person's messages as their own and the whole thread collapses onto one side.
     */
    const incoming: Message = myIdRef.current
      ? { ...raw, mine: raw.senderId === myIdRef.current }
      : raw;
    setMessages((current) => {
      // The sender already has this message optimistically; match on the client id.
      const withoutOptimistic = current.filter(
        (message) =>
          message.id !== incoming.id &&
          !(incoming.clientMessageId && message.clientMessageId === incoming.clientMessageId),
      );
      return [incoming, ...withoutOptimistic];
    });
  }, []);

  // Live events for this thread.
  useEffect(() => {
    if (!conversationId) return;

    const unsubscribe = socket.subscribe(destinations.conversation(conversationId), (payload) => {
      const event = payload as Record<string, unknown>;

      if (event.event === 'TYPING') {
        // The topic echoes our own typing events back to us.
        if (event.userId === myIdRef.current) return;
        setTyping(Boolean(event.typing));
        if (typingTimeout.current) clearTimeout(typingTimeout.current);
        typingTimeout.current = setTimeout(() => setTyping(false), 4000);
        return;
      }
      if (event.event === 'READ') {
        // Only the OTHER person reading marks my messages as read.
        if (event.readerId === myIdRef.current) return;
        setMessages((current) =>
          current.map((message) =>
            message.mine && !message.readAt ? { ...message, readAt: new Date().toISOString() } : message,
          ),
        );
        return;
      }
      if (event.event === 'DELETED') {
        setMessages((current) =>
          current.map((message) =>
            message.id === event.messageId ? { ...message, deleted: true, body: undefined } : message,
          ),
        );
        return;
      }
      // Anything else on this topic is a message.
      appendIncoming(payload as Message);
      void queryClient.invalidateQueries({ queryKey: queryKeys.chat.conversations() });
    });

    return () => {
      unsubscribe();
      if (typingTimeout.current) clearTimeout(typingTimeout.current);
    };
  }, [conversationId, appendIncoming, queryClient]);

  const loadOlder = useCallback(async () => {
    if (!cursor || !hasMore) return;
    const older: CursorPageResponse<Message> = await chatApi.messages(conversationId, cursor);
    setMessages((current) => [...current, ...older.items]);
    setCursor(older.nextCursor);
    setHasMore(older.hasMore);
  }, [conversationId, cursor, hasMore]);

  const sendMutation = useMutation({
    mutationFn: (input: { body?: string; attachmentIds?: string[]; clientMessageId: string }) =>
      chatApi.send(conversationId, {
        body: input.body,
        attachmentIds: input.attachmentIds,
        clientMessageId: input.clientMessageId,
      }),
    onSuccess: (message) => {
      appendIncoming(message);
      void queryClient.invalidateQueries({ queryKey: queryKeys.chat.conversation(conversationId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.chat.conversations() });
    },
    onError: (error, variables) => {
      // Roll the optimistic bubble back out, then explain why.
      setMessages((current) =>
        current.filter((message) => message.clientMessageId !== variables.clientMessageId),
      );
      handleError(error);
    },
  });

  const send = useCallback(
    async (body: string, attachmentIds: string[] = []) => {
      const clientMessageId = randomId();
      const optimistic: Message = {
        id: clientMessageId,
        conversationId,
        senderId: 'me',
        mine: true,
        type: attachmentIds.length > 0 ? 'IMAGE' : 'TEXT',
        body,
        attachments: [],
        deleted: false,
        clientMessageId,
        createdAt: new Date().toISOString(),
      };
      setMessages((current) => [optimistic, ...current]);
      await sendMutation.mutateAsync({ body, attachmentIds, clientMessageId });
    },
    [conversationId, sendMutation],
  );

  const markRead = useCallback(() => {
    void chatApi.markRead(conversationId).then(() => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.chat.unreadCount() });
    });
  }, [conversationId, queryClient]);

  const notifyTyping = useCallback(
    (isTyping: boolean) => {
      socket.publish(destinations.typing(conversationId), { typing: isTyping });
    },
    [conversationId],
  );

  return {
    conversation: conversationQuery.data as Conversation | undefined,
    conversationQuery,
    historyQuery,
    messages,
    hasMore,
    loadOlder,
    send,
    isSending: sendMutation.isPending,
    markRead,
    notifyTyping,
    otherIsTyping: typing,
  };
}
