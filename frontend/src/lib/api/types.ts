/**
 * Transport contracts, mirroring the backend DTOs one for one.
 *
 * These are hand-written rather than generated so the shapes stay readable, but they are
 * the only place API shapes are described - no component invents its own.
 */

// ---- envelope --------------------------------------------------------

export interface FieldViolation {
  field: string;
  message: string;
  rejectedValue?: unknown;
}

export interface ApiErrorBody {
  code: ErrorCode;
  message: string;
  details?: FieldViolation[];
  context?: unknown;
}

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  error?: ApiErrorBody;
  timestamp: string;
  traceId?: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface CursorPageResponse<T> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
}

/** Mirrors the backend ErrorCode enum. Switch on these, never on messages. */
export type ErrorCode =
  | 'VALIDATION_FAILED'
  | 'BAD_REQUEST'
  | 'UNAUTHORIZED'
  | 'INVALID_CREDENTIALS'
  | 'TOKEN_EXPIRED'
  | 'TOKEN_INVALID'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'CONFLICT'
  | 'RATE_LIMITED'
  | 'INTERNAL_ERROR'
  | 'EMAIL_ALREADY_REGISTERED'
  | 'ACCOUNT_DISABLED'
  | 'ACCOUNT_BANNED'
  | 'UNDERAGE'
  | 'PROFILE_INCOMPLETE'
  | 'UNSUPPORTED_MEDIA'
  | 'FILE_TOO_LARGE'
  | 'PHOTO_LIMIT_REACHED'
  | 'SELF_INTERACTION'
  | 'ALREADY_LIKED'
  | 'USER_BLOCKED'
  | 'NOT_MATCHED'
  | 'OPENER_LIMIT_REACHED'
  | 'MESSAGE_TOO_LONG'
  | 'CONVERSATION_CLOSED'
  | 'QUOTA_EXCEEDED'
  | 'PREMIUM_REQUIRED'
  | 'PLAN_NOT_FOUND'
  | 'SUBSCRIPTION_ACTIVE'
  | 'CALL_NOT_ALLOWED'
  | 'CALL_ALREADY_ACTIVE'
  | 'NETWORK_ERROR';

// ---- enums -----------------------------------------------------------

export type Gender = 'WOMAN' | 'MAN' | 'NON_BINARY' | 'OTHER';
export type UserStatus = 'PENDING_ONBOARDING' | 'ACTIVE' | 'PAUSED' | 'DEACTIVATED' | 'BANNED';
export type PlanTier = 'FREE' | 'PLUS' | 'PREMIUM';
export type LifestyleChoice = 'YES' | 'SOMETIMES' | 'NO' | 'PREFER_NOT_TO_SAY';
export type RelationshipIntent =
  | 'LONG_TERM'
  | 'LONG_TERM_OPEN_TO_SHORT'
  | 'SHORT_TERM_OPEN_TO_LONG'
  | 'SHORT_TERM'
  | 'FIGURING_IT_OUT'
  | 'NEW_FRIENDS';
export type ChildrenPreference =
  | 'WANT_SOMEDAY'
  | 'DONT_WANT'
  | 'HAVE_AND_WANT_MORE'
  | 'HAVE_AND_DONT_WANT_MORE'
  | 'OPEN_TO_CHILDREN'
  | 'NOT_SURE'
  | 'PREFER_NOT_TO_SAY';

export type Feature =
  | 'SEE_WHO_LIKES_YOU'
  | 'EXTRA_PHOTO_COMMENTS'
  | 'DAILY_AUTO_MATCH'
  | 'REWIND'
  | 'GLOBAL_MODE'
  | 'INCOGNITO'
  | 'READ_RECEIPTS'
  | 'ADVANCED_FILTERS'
  | 'UNLIMITED_LIKES';

export type MessageType =
  | 'TEXT'
  | 'IMAGE'
  | 'VOICE_NOTE'
  | 'VIDEO'
  | 'FILE'
  | 'SYSTEM'
  | 'CALL_SUMMARY';

export type MatchSource = 'MUTUAL_LIKE' | 'AUTO_MATCH_WEEKLY' | 'AUTO_MATCH_DAILY' | 'MANUAL';
export type CallStatus = 'RINGING' | 'ACTIVE' | 'ENDED' | 'DECLINED' | 'MISSED' | 'FAILED';

// ---- auth and account ------------------------------------------------

export interface AuthResponse {
  userId: string;
  email: string;
  displayName: string;
  status: UserStatus;
  onboardingRequired: boolean;
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export interface Account {
  id: string;
  email: string;
  displayName: string;
  dateOfBirth: string;
  age: number;
  gender: Gender;
  interestedIn: Gender[];
  status: UserStatus;
  city?: string;
  country?: string;
  latitude?: number;
  longitude?: number;
  preferredMinAge: number;
  preferredMaxAge: number;
  preferredMaxDistanceKm: number;
  globalMode: boolean;
  incognito: boolean;
  photoVerified: boolean;
  emailVerified: boolean;
  onboardingCompleted: boolean;
  tier: PlanTier;
  lastActiveAt?: string;
  createdAt: string;
}

// ---- profile ---------------------------------------------------------

export interface Photo {
  id: string;
  url: string;
  blurhash?: string;
  width?: number;
  height?: number;
  caption?: string;
  displayOrder: number;
  primaryPhoto: boolean;
  commentCount: number;
  likeCount: number;
}

export interface PromptAnswer {
  id: string;
  promptId: string;
  prompt: string;
  answer: string;
  displayOrder: number;
}

export interface Tag {
  id: string;
  slug: string;
  label: string;
  group: string;
  emoji?: string;
}

export interface PromptOption {
  id: string;
  slug: string;
  text: string;
  category: string;
}

export interface Profile {
  userId: string;
  displayName: string;
  age: number;
  gender: Gender;
  interestedIn: Gender[];
  city?: string;
  country?: string;
  bio?: string;
  jobTitle?: string;
  company?: string;
  school?: string;
  educationLevel?: string;
  hometown?: string;
  heightCm?: number;
  religion?: string;
  politics?: string;
  zodiacSign?: string;
  relationshipIntent?: RelationshipIntent;
  drinking?: LifestyleChoice;
  smoking?: LifestyleChoice;
  cannabis?: LifestyleChoice;
  exercise?: LifestyleChoice;
  children?: ChildrenPreference;
  languages: string[];
  interests: Tag[];
  qualities: Tag[];
  photos: Photo[];
  prompts: PromptAnswer[];
  completeness: number;
  photoVerified: boolean;
  incognito: boolean;
}

export interface PublicProfile {
  userId: string;
  displayName: string;
  age: number;
  gender: Gender;
  city?: string;
  distanceKm?: number;
  bio?: string;
  jobTitle?: string;
  company?: string;
  school?: string;
  hometown?: string;
  heightCm?: number;
  religion?: string;
  zodiacSign?: string;
  relationshipIntent?: RelationshipIntent;
  drinking?: LifestyleChoice;
  smoking?: LifestyleChoice;
  children?: ChildrenPreference;
  languages: string[];
  interests: Tag[];
  qualities: Tag[];
  photos: Photo[];
  prompts: PromptAnswer[];
  photoVerified: boolean;
  recentlyActive: boolean;
  lastActiveAt?: string;
  compatibilityScore?: number;
  sharedInterests: string[];
  /**
   * Optional on purpose. A current server always sends it, but the frontend hot-reloads
   * while the backend does not, so during development the client routinely outruns the
   * API by a few minutes. Marking it optional makes TypeScript force a fallback instead
   * of letting the page white-screen on the skew.
   */
  relationship?: Relationship;
}

/**
 * Where the viewer stands with this person, decided by the server.
 *
 * The profile screen uses it to choose between "Like" and "Message". Deriving it on the
 * client from a cached matches list goes wrong the moment that list is stale.
 */
export interface Relationship {
  matched: boolean;
  matchId?: string;
  conversationId?: string;
  matchedAt?: string;
  /** A like was sent and has not been answered yet. */
  likeSent: boolean;
}

export interface UserSummary {
  userId: string | null;
  displayName: string | null;
  age: number;
  city?: string | null;
  distanceKm?: number | null;
  primaryPhotoUrl?: string | null;
  blurhash?: string | null;
  photoVerified: boolean;
  recentlyActive: boolean;
  compatibilityScore?: number | null;
}

// ---- discovery -------------------------------------------------------

export type FeedSort = 'RECOMMENDED' | 'NEWEST' | 'NEAREST' | 'RECENTLY_ACTIVE';

/**
 * The filter sheet. Age, distance and genders ("show me") are the account's preferences;
 * interests are free; the rest are paid (ADVANCED_FILTERS) and the server rejects them
 * for free accounts.
 */
export interface FeedFilter {
  minAge?: number;
  maxAge?: number;
  maxDistanceKm?: number;
  genders?: Gender[];
  interestIds?: string[];
  intents?: RelationshipIntent[];
  minHeightCm?: number;
  maxHeightCm?: number;
  onlyVerified?: boolean;
  /** 24 = active today, 168 = this week. */
  activeWithinHours?: number;
  children?: ChildrenPreference[];
  drinking?: LifestyleChoice[];
  smoking?: LifestyleChoice[];
  sort?: FeedSort;
}

export interface FeedCard {
  userId: string;
  displayName: string;
  age: number;
  city?: string;
  distanceKm?: number;
  bio?: string;
  jobTitle?: string;
  school?: string;
  heightCm?: number;
  photos: Photo[];
  prompts: PromptAnswer[];
  interests: Tag[];
  qualities: Tag[];
  sharedInterests: string[];
  compatibilityScore: number;
  highlights: string[];
  photoVerified: boolean;
  recentlyActive: boolean;
  /** They super liked you - badged and moved to the front of the deck. */
  superLikedYou?: boolean;
}

// ---- likes and matches -----------------------------------------------

export interface LikeResult {
  likeId: string;
  matched: boolean;
  match?: Match;
  likesRemainingToday: number;
  superLikesRemainingToday: number;
}

/** Today's allowance; -1 means unlimited. */
export interface LikeQuota {
  likesLimit: number;
  likesRemaining: number;
  superLikesLimit: number;
  superLikesRemaining: number;
  resetsAt: string;
}

/** What a like is on, and the comment written under it. */
export interface LikeIntent {
  superLike?: boolean;
  note?: string;
  targetPhotoId?: string;
  targetPromptAnswerId?: string;
}

export interface InboundLike {
  likeId: string;
  blurred: boolean;
  user: UserSummary;
  type: 'STANDARD' | 'SUPER';
  note?: string;
  targetPhotoId?: string;
  targetPhotoUrl?: string;
  /** Set when they liked one of your prompts. */
  targetPromptAnswerId?: string;
  targetPrompt?: string;
  targetPromptAnswer?: string;
  seen: boolean;
  likedAt: string;
}

export interface LikesOverview {
  totalLikes: number;
  newLikes: number;
  revealed: boolean;
  upgradeHint?: string;
  likes: PageResponse<InboundLike>;
}

export interface Match {
  id: string;
  user: UserSummary;
  source: MatchSource;
  status: 'ACTIVE' | 'UNMATCHED' | 'EXPIRED';
  matchedAt: string;
  compatibilityScore?: number;
  highlights: string[];
  conversationId?: string;
  conversationStarted: boolean;
  lastMessagePreview?: string;
  lastMessageAt?: string;
  unreadCount: number;
  isNew: boolean;
}

export type AutoMatchOutcome =
  | 'MATCHED'
  | 'NO_CANDIDATE'
  | 'BELOW_THRESHOLD'
  | 'SKIPPED_INELIGIBLE'
  | 'FAILED';

export interface AutoMatchResult {
  outcome: AutoMatchOutcome;
  cadence: 'WEEKLY' | 'DAILY';
  periodKey: string;
  match?: Match;
  score?: number;
  candidatesConsidered: number;
  nextRunAt: string;
  message: string;
}

export interface Standout {
  userId: string;
  displayName: string;
  age: number;
  city?: string;
  distanceKm?: number;
  reason: 'POPULAR' | 'NEW' | 'ACTIVE' | 'RISING';
  reasonLabel: string;
  rank: number;
  compatibilityScore: number;
  sharedInterests: string[];
  photos: Photo[];
  prompts: PromptAnswer[];
  photoVerified: boolean;
}

// ---- chat ------------------------------------------------------------

export interface Attachment {
  id: string;
  url: string;
  contentType: string;
  fileName?: string;
  sizeBytes: number;
  width?: number;
  height?: number;
  durationSeconds?: number;
  waveform?: string;
  thumbnailUrl?: string;
}

export interface Message {
  id: string;
  conversationId: string;
  senderId: string;
  mine: boolean;
  type: MessageType;
  body?: string;
  attachments: Attachment[];
  replyToId?: string;
  deleted: boolean;
  deliveredAt?: string;
  readAt?: string;
  clientMessageId?: string;
  createdAt: string;
}

export interface SendingState {
  canSend: boolean;
  /** -1 means unrestricted. */
  remainingOpeners: number;
  openerLimit: number;
  reason?: string;
  message?: string;
}

export interface Conversation {
  id: string;
  matchId: string;
  participant: UserSummary;
  status: 'ACTIVE' | 'CLOSED';
  lastMessagePreview?: string;
  lastMessageAt?: string;
  lastMessageSenderId?: string;
  unreadCount: number;
  bothSpoke: boolean;
  sendingState: SendingState;
  createdAt: string;
}

export interface MediaAsset {
  id: string;
  url: string;
  contentType: string;
  fileName?: string;
  sizeBytes: number;
  width?: number;
  height?: number;
  durationSeconds?: number;
}

// ---- calls -----------------------------------------------------------

export interface IceServer {
  urls: string[];
  username?: string;
  credential?: string;
}

export interface Call {
  id: string;
  conversationId: string;
  callerId: string;
  calleeId: string;
  type: 'VOICE' | 'VIDEO';
  status: CallStatus;
  startedAt: string;
  answeredAt?: string;
  endedAt?: string;
  durationSeconds?: number;
  iceServers: IceServer[];
}

// ---- comments, plans, notifications ----------------------------------

export interface Comment {
  id: string;
  photoId: string;
  author: UserSummary;
  body: string;
  parentCommentId?: string;
  replyCount: number;
  replies: Comment[];
  canDelete: boolean;
  createdAt: string;
}

export interface CommentQuota {
  limit: number;
  used: number;
  remaining: number;
  unlimited: boolean;
  resetsAt: string;
  upgradeHint?: string;
}

export interface Entitlements {
  tier: PlanTier;
  planName: string;
  renewsAt?: string;
  photoCommentsPerDay: number;
  likesPerDay: number;
  superLikesPerDay: number;
  autoMatchPerWeek: number;
  autoMatchPerDay: number;
  rewindsPerDay: number;
  unlockedFeatures: Feature[];
  lockedFeatures: Feature[];
}

export interface Plan {
  id: string;
  code: string;
  name: string;
  description?: string;
  tier: PlanTier;
  price: number;
  currency: string;
  billingPeriodMonths: number;
  highlights: string[];
  features: Feature[];
}

export interface Subscription {
  id: string;
  planCode: string;
  planName: string;
  tier: PlanTier;
  status: 'ACTIVE' | 'PAST_DUE' | 'CANCELLED' | 'EXPIRED';
  startedAt: string;
  currentPeriodEnd: string;
  autoRenew: boolean;
}

export type NotificationType =
  | 'NEW_LIKE'
  | 'NEW_MATCH'
  | 'AUTO_MATCH_READY'
  | 'NEW_MESSAGE'
  | 'NEW_PHOTO_COMMENT'
  | 'MISSED_CALL'
  | 'PROFILE_REMINDER'
  | 'SUBSCRIPTION_UPDATE'
  | 'SAFETY_NOTICE';

export interface AppNotification {
  id: string;
  type: NotificationType;
  title: string;
  body?: string;
  actorId?: string;
  targetType?: string;
  targetId?: string;
  imageUrl?: string;
  read: boolean;
  createdAt: string;
}
