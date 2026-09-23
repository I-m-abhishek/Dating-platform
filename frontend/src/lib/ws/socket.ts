'use client';

import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { tokenStore } from '@/lib/api/tokenStore';
import { parseApiJson } from '@/lib/api/client';

/**
 * An empty NEXT_PUBLIC_WS_URL means "same origin": the socket goes through the Next dev
 * server's /ws rewrite. That is how a phone on HTTPS reaches the backend without mixed
 * content errors.
 */
function wsUrl(): string {
  const configured = process.env.NEXT_PUBLIC_WS_URL;
  if (configured) return configured;
  if (configured === '' && typeof window !== 'undefined') return `${window.location.origin}/ws`;
  return 'http://localhost:8080/ws';
}

type Handler = (payload: unknown) => void;

/**
 * One STOMP connection for the whole app.
 *
 * <p>A socket per screen would mean a reconnect storm every time the user changes tabs, so
 * this is a module-level singleton that components subscribe to and unsubscribe from. The
 * connection is opened when a session exists and closed when it ends.
 *
 * <p>Subscriptions made before the connection is live are queued and replayed on connect,
 * which removes the race between a component mounting and the socket finishing its
 * handshake.
 */
class SocketManager {
  private client: Client | null = null;
  private readonly subscriptions = new Map<string, StompSubscription>();
  private readonly handlers = new Map<string, Set<Handler>>();
  private connected = false;

  connect(): void {
    const token = tokenStore.getAccessToken();
    if (!token || this.client?.active) return;

    this.client = new Client({
      // SockJS rather than a raw WebSocket: it falls back cleanly behind proxies that
      // mangle upgrade requests, which is common on corporate networks.
      webSocketFactory: () => new SockJS(wsUrl()) as unknown as WebSocket,
      connectHeaders: { Authorization: `Bearer ${token}` },
      // Access tokens are short-lived. Read the current one on every (re)connect, or the
      // first reconnect after it rotates is rejected and the socket stays dead for good.
      beforeConnect: () => {
        const current = tokenStore.getAccessToken();
        if (current && this.client) {
          this.client.connectHeaders = { Authorization: `Bearer ${current}` };
        }
      },
      reconnectDelay: 4000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      onConnect: () => {
        this.connected = true;
        // Replay everything a component asked for while we were still connecting.
        this.handlers.forEach((_, destination) => this.attach(destination));
      },
      onStompError: (frame) => {
        console.error('STOMP error', frame.headers.message, frame.body);
      },
      onWebSocketClose: () => {
        this.connected = false;
        this.subscriptions.clear();
      },
    });

    this.client.activate();
  }

  disconnect(): void {
    this.subscriptions.forEach((subscription) => subscription.unsubscribe());
    this.subscriptions.clear();
    // Handlers are kept: they belong to mounted components, which remove them in their own
    // cleanup. Clearing them here silently orphaned every listener across a sign-out/in.
    this.connected = false;
    void this.client?.deactivate();
    this.client = null;
  }

  /** @returns an unsubscribe function; call it from the effect cleanup. */
  subscribe(destination: string, handler: Handler): () => void {
    const existing = this.handlers.get(destination) ?? new Set<Handler>();
    existing.add(handler);
    this.handlers.set(destination, existing);

    if (this.connected) {
      this.attach(destination);
    }

    return () => {
      const handlers = this.handlers.get(destination);
      handlers?.delete(handler);
      if (handlers && handlers.size === 0) {
        this.handlers.delete(destination);
        this.subscriptions.get(destination)?.unsubscribe();
        this.subscriptions.delete(destination);
      }
    };
  }

  publish(destination: string, body: unknown): void {
    if (!this.client?.connected) return;
    this.client.publish({
      destination,
      body: JSON.stringify(body ?? {}),
      headers: { 'content-type': 'application/json' },
    });
  }

  get isConnected(): boolean {
    return this.connected;
  }

  private attach(destination: string): void {
    if (!this.client?.connected || this.subscriptions.has(destination)) return;

    const subscription = this.client.subscribe(destination, (message: IMessage) => {
      let payload: unknown = message.body;
      try {
        payload = parseApiJson(message.body);
      } catch {
        // Not every frame is JSON; pass the raw body through rather than dropping it.
      }
      this.handlers.get(destination)?.forEach((handler) => handler(payload));
    });

    this.subscriptions.set(destination, subscription);
  }
}

export const socket = new SocketManager();

export const destinations = {
  conversation: (conversationId: string) => `/topic/conversations/${conversationId}`,
  notifications: '/user/queue/notifications',
  calls: '/user/queue/calls',
  sendMessage: (conversationId: string) => `/app/conversations/${conversationId}/send`,
  typing: (conversationId: string) => `/app/conversations/${conversationId}/typing`,
  read: (conversationId: string) => `/app/conversations/${conversationId}/read`,
  signal: '/app/calls/signal',
} as const;
