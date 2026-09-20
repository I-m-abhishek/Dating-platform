import { http } from './client';
import type {
  Account,
  AppNotification,
  AuthResponse,
  AutoMatchResult,
  Call,
  Comment,
  CommentQuota,
  Conversation,
  CursorPageResponse,
  Entitlements,
  FeedCard,
  FeedFilter,
  Gender,
  LikesOverview,
  LikeResult,
  Match,
  MediaAsset,
  Message,
  MessageType,
  PageResponse,
  Photo,
  Plan,
  Profile,
  PromptOption,
  PublicProfile,
  Standout,
  Subscription,
  Tag,
} from './types';

/**
 * The API surface, one function per endpoint.
 *
 * Hooks call these; components call hooks. No component ever builds a URL, which is what
 * keeps a backend route change to a single-file edit.
 */

// ---- auth ------------------------------------------------------------

export interface RegisterPayload {
  email: string;
  password: string;
  displayName: string;
  dateOfBirth: string;
  gender: Gender;
  interestedIn: Gender[];
}

export const authApi = {
  register: (payload: RegisterPayload) =>
    http.post<AuthResponse>('/api/v1/auth/register', payload, { anonymous: true }),
  login: (email: string, password: string) =>
    http.post<AuthResponse>('/api/v1/auth/login', { email, password }, { anonymous: true }),
  logout: (refreshToken: string | null) =>
    http.post<void>('/api/v1/auth/logout', { refreshToken }),
  logoutEverywhere: () => http.post<void>('/api/v1/auth/logout-all'),
  changePassword: (currentPassword: string, newPassword: string) =>
    http.post<void>('/api/v1/auth/change-password', { currentPassword, newPassword }),
};

// ---- account ---------------------------------------------------------

export const accountApi = {
  me: () => http.get<Account>('/api/v1/users/me'),
  updatePreferences: (payload: Partial<Account>) =>
    http.patch<Account>('/api/v1/users/me/preferences', payload),
  updateLocation: (latitude: number, longitude: number, city?: string, country?: string) =>
    http.put<Account>('/api/v1/users/me/location', { latitude, longitude, city, country }),
  completeOnboarding: () => http.post<Account>('/api/v1/users/me/complete-onboarding'),
  pause: () => http.post<void>('/api/v1/users/me/pause'),
  deactivate: () => http.delete<void>('/api/v1/users/me'),
  heartbeat: () => http.post<void>('/api/v1/users/me/heartbeat'),
};

// ---- profile ---------------------------------------------------------

export const profileApi = {
  me: () => http.get<Profile>('/api/v1/profile'),
  update: (payload: Record<string, unknown>) => http.patch<Profile>('/api/v1/profile', payload),
  publicProfile: (userId: string) => http.get<PublicProfile>(`/api/v1/profiles/${userId}`),

  photos: () => http.get<Photo[]>('/api/v1/profile/photos'),
  uploadPhoto: (file: File, caption?: string) => {
    const form = new FormData();
    form.append('file', file);
    return http.upload<Photo>(
      `/api/v1/profile/photos${caption ? `?caption=${encodeURIComponent(caption)}` : ''}`,
      form,
    );
  },
  reorderPhotos: (photoIdsInOrder: string[]) =>
    http.put<Photo[]>('/api/v1/profile/photos/order', { photoIdsInOrder }),
  setPrimaryPhoto: (photoId: string) => http.put<Photo[]>(`/api/v1/profile/photos/${photoId}/primary`),
  deletePhoto: (photoId: string) => http.delete<void>(`/api/v1/profile/photos/${photoId}`),

  prompts: () => http.get<PromptOption[]>('/api/v1/reference/prompts'),
  myPrompts: () => http.get<Profile['prompts']>('/api/v1/profile/prompts'),
  upsertPrompt: (promptId: string, answer: string, displayOrder = 0) =>
    http.put<Profile['prompts']>('/api/v1/profile/prompts', { promptId, answer, displayOrder }),
  deletePrompt: (answerId: string) =>
    http.delete<Profile['prompts']>(`/api/v1/profile/prompts/${answerId}`),
};

export const referenceApi = {
  interests: () => http.get<Tag[]>('/api/v1/reference/interests'),
  qualities: () => http.get<Tag[]>('/api/v1/reference/qualities'),
  prompts: () => http.get<PromptOption[]>('/api/v1/reference/prompts'),
};

// ---- discovery, likes, matches ---------------------------------------

export const discoveryApi = {
  feed: (filter: FeedFilter, page = 0, size = 10) =>
    http.post<PageResponse<FeedCard>>('/api/v1/discovery/feed', filter, { query: { page, size } }),
};

export const likeApi = {
  like: (payload: {
    targetUserId: string;
    type?: 'STANDARD' | 'SUPER';
    targetPhotoId?: string;
    targetPromptAnswerId?: string;
    note?: string;
  }) => http.post<LikeResult>('/api/v1/likes', payload),
  pass: (targetUserId: string) => http.post<void>('/api/v1/likes/pass', { targetUserId }),
  rewind: () => http.post<{ restoredUserId: string }>('/api/v1/likes/rewind'),
  inbound: (page = 0, size = 20) =>
    http.get<LikesOverview>('/api/v1/likes/inbound', { query: { page, size } }),
  markSeen: () => http.post<void>('/api/v1/likes/inbound/seen'),
  unseenCount: () => http.get<{ count: number }>('/api/v1/likes/inbound/unseen-count'),
};

export const matchApi = {
  list: (page = 0, size = 20) =>
    http.get<PageResponse<Match>>('/api/v1/matches', { query: { page, size } }),
  get: (matchId: string) => http.get<Match>(`/api/v1/matches/${matchId}`),
  count: () => http.get<{ count: number }>('/api/v1/matches/count'),
  unmatch: (matchId: string, reason?: string, alsoBlock = false) =>
    http.delete<void>(`/api/v1/matches/${matchId}`, { reason, alsoBlock }),
};

export const autoMatchApi = {
  run: () => http.post<AutoMatchResult>('/api/v1/auto-match/run'),
  history: () => http.get<unknown[]>('/api/v1/auto-match/history'),
};

export const standoutApi = {
  list: (limit = 12) => http.get<Standout[]>('/api/v1/standouts', { query: { limit } }),
};

// ---- chat ------------------------------------------------------------

export interface SendMessagePayload {
  body?: string;
  type?: MessageType;
  attachmentIds?: string[];
  replyToId?: string;
  clientMessageId?: string;
}

export const chatApi = {
  conversations: (page = 0, size = 20) =>
    http.get<PageResponse<Conversation>>('/api/v1/conversations', { query: { page, size } }),
  conversation: (conversationId: string) =>
    http.get<Conversation>(`/api/v1/conversations/${conversationId}`),
  messages: (conversationId: string, before?: string, limit = 30) =>
    http.get<CursorPageResponse<Message>>(`/api/v1/conversations/${conversationId}/messages`, {
      query: { before, limit },
    }),
  send: (conversationId: string, payload: SendMessagePayload) =>
    http.post<Message>(`/api/v1/conversations/${conversationId}/messages`, payload),
  uploadAttachment: (file: File, durationSeconds?: number, waveform?: string) => {
    const form = new FormData();
    form.append('file', file);
    const params = new URLSearchParams();
    if (durationSeconds !== undefined) params.set('durationSeconds', String(durationSeconds));
    if (waveform) params.set('waveform', waveform);
    const suffix = params.toString() ? `?${params.toString()}` : '';
    return http.upload<MediaAsset>(`/api/v1/conversations/attachments${suffix}`, form);
  },
  markRead: (conversationId: string) =>
    http.post<void>(`/api/v1/conversations/${conversationId}/read`),
  deleteMessage: (conversationId: string, messageId: string) =>
    http.delete<void>(`/api/v1/conversations/${conversationId}/messages/${messageId}`),
  unreadCount: () => http.get<{ count: number }>('/api/v1/conversations/unread-count'),
};

// ---- calls -----------------------------------------------------------

export const callApi = {
  start: (conversationId: string, type: 'VOICE' | 'VIDEO' = 'VOICE') =>
    http.post<Call>(`/api/v1/calls/conversations/${conversationId}`, { type }),
  accept: (callId: string) => http.post<Call>(`/api/v1/calls/${callId}/accept`),
  decline: (callId: string) => http.post<Call>(`/api/v1/calls/${callId}/decline`),
  hangUp: (callId: string) => http.post<Call>(`/api/v1/calls/${callId}/hangup`),
  history: (conversationId: string, limit = 20) =>
    http.get<Call[]>(`/api/v1/calls/conversations/${conversationId}`, { query: { limit } }),
};

// ---- photo comments --------------------------------------------------

export const commentApi = {
  list: (photoId: string, page = 0, size = 20) =>
    http.get<PageResponse<Comment>>(`/api/v1/photos/${photoId}/comments`, { query: { page, size } }),
  create: (photoId: string, body: string, parentCommentId?: string) =>
    http.post<Comment>(`/api/v1/photos/${photoId}/comments`, { body, parentCommentId }),
  delete: (commentId: string) => http.delete<void>(`/api/v1/comments/${commentId}`),
  quota: () => http.get<CommentQuota>('/api/v1/comments/quota'),
  onMyPhotos: (page = 0, size = 20) =>
    http.get<PageResponse<Comment>>('/api/v1/profile/photo-comments', { query: { page, size } }),
};

// ---- plans and subscriptions -----------------------------------------

export const subscriptionApi = {
  plans: () => http.get<Plan[]>('/api/v1/plans'),
  entitlements: () => http.get<Entitlements>('/api/v1/subscriptions/me/entitlements'),
  current: () => http.get<Subscription | null>('/api/v1/subscriptions/me'),
  subscribe: (planCode: string, paymentToken?: string) =>
    http.post<Subscription>('/api/v1/subscriptions', { planCode, paymentToken }),
  cancel: () => http.delete<Subscription>('/api/v1/subscriptions/me'),
};

// ---- notifications and safety ----------------------------------------

export const notificationApi = {
  list: (page = 0, size = 20) =>
    http.get<PageResponse<AppNotification>>('/api/v1/notifications', { query: { page, size } }),
  unreadCount: () => http.get<{ count: number }>('/api/v1/notifications/unread-count'),
  markRead: (notificationId: string) =>
    http.post<void>(`/api/v1/notifications/${notificationId}/read`),
  markAllRead: () => http.post<void>('/api/v1/notifications/read-all'),
};

export const safetyApi = {
  block: (userId: string, reason?: string) =>
    http.post<void>('/api/v1/safety/blocks', { userId, reason }),
  unblock: (userId: string) => http.delete<void>(`/api/v1/safety/blocks/${userId}`),
  blocked: () => http.get<string[]>('/api/v1/safety/blocks'),
  report: (payload: {
    reportedUserId: string;
    reason: string;
    details?: string;
    contextType?: string;
    contextId?: string;
    alsoBlock?: boolean;
  }) => http.post<{ reportId?: string }>('/api/v1/safety/reports', payload),
};
