/**
 * Every React Query key in one place.
 *
 * Hierarchical on purpose: invalidating {@code queryKeys.chat.all} clears every
 * conversation and thread in one call, which is what a "you were unmatched" event needs.
 */
export const queryKeys = {
  account: {
    all: ['account'] as const,
    me: () => [...queryKeys.account.all, 'me'] as const,
  },
  profile: {
    all: ['profile'] as const,
    me: () => [...queryKeys.profile.all, 'me'] as const,
    photos: () => [...queryKeys.profile.all, 'photos'] as const,
    prompts: () => [...queryKeys.profile.all, 'prompts'] as const,
    public: (userId: string) => [...queryKeys.profile.all, 'public', userId] as const,
  },
  reference: {
    all: ['reference'] as const,
    interests: () => [...queryKeys.reference.all, 'interests'] as const,
    qualities: () => [...queryKeys.reference.all, 'qualities'] as const,
    prompts: () => [...queryKeys.reference.all, 'prompts'] as const,
  },
  discovery: {
    all: ['discovery'] as const,
    feed: (filter: unknown) => [...queryKeys.discovery.all, 'feed', filter] as const,
  },
  likes: {
    all: ['likes'] as const,
    inbound: () => [...queryKeys.likes.all, 'inbound'] as const,
    unseenCount: () => [...queryKeys.likes.all, 'unseen-count'] as const,
  },
  matches: {
    all: ['matches'] as const,
    list: () => [...queryKeys.matches.all, 'list'] as const,
    detail: (matchId: string) => [...queryKeys.matches.all, 'detail', matchId] as const,
    count: () => [...queryKeys.matches.all, 'count'] as const,
  },
  standouts: {
    all: ['standouts'] as const,
    list: (limit: number) => [...queryKeys.standouts.all, limit] as const,
  },
  chat: {
    all: ['chat'] as const,
    conversations: () => [...queryKeys.chat.all, 'conversations'] as const,
    conversation: (id: string) => [...queryKeys.chat.all, 'conversation', id] as const,
    messages: (id: string) => [...queryKeys.chat.all, 'messages', id] as const,
    unreadCount: () => [...queryKeys.chat.all, 'unread-count'] as const,
  },
  comments: {
    all: ['comments'] as const,
    forPhoto: (photoId: string) => [...queryKeys.comments.all, 'photo', photoId] as const,
    quota: () => [...queryKeys.comments.all, 'quota'] as const,
    inbox: () => [...queryKeys.comments.all, 'inbox'] as const,
  },
  subscription: {
    all: ['subscription'] as const,
    plans: () => [...queryKeys.subscription.all, 'plans'] as const,
    entitlements: () => [...queryKeys.subscription.all, 'entitlements'] as const,
    current: () => [...queryKeys.subscription.all, 'current'] as const,
  },
  notifications: {
    all: ['notifications'] as const,
    list: () => [...queryKeys.notifications.all, 'list'] as const,
    unreadCount: () => [...queryKeys.notifications.all, 'unread-count'] as const,
  },
} as const;
