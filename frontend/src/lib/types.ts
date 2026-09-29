// =====================================================================
// Shared API types, mirroring the Java DTOs.
//
// The backend serialises with `non_null`, so a null field is *absent* from
// the JSON rather than null. Optional (`?`) marks those fields.
// =====================================================================

export type Role = 'USER' | 'MODERATOR' | 'ADMIN'
export type ThemePreference = 'SYSTEM' | 'LIGHT' | 'DARK'

/** The signed-in account (AuthDtos.UserResponse). */
export interface User {
  id: string
  username: string
  displayName?: string | null
  effectiveName: string
  bio?: string | null
  pronouns?: string | null
  website?: string | null
  location?: string | null
  avatarUrl?: string | null
  bannerUrl?: string | null
  theme: ThemePreference
  accentColor: string
  privateAccount: boolean
  verified: boolean
  role: Role
  followerCount: number
  followingCount: number
  postCount: number
  createdAt: string
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: User
}

/** The compact shape embedded in posts, comments and lists. */
export interface UserSummary {
  id: string
  username: string
  displayName?: string | null
  effectiveName: string
  avatarUrl?: string | null
  verified: boolean
  /** Only present where the UI shows a follow button. */
  following?: boolean
}

export interface Profile {
  id: string
  username: string
  displayName?: string
  effectiveName: string
  bio?: string
  pronouns?: string
  website?: string
  location?: string
  avatarUrl?: string
  bannerUrl?: string
  verified: boolean
  followerCount: number
  followingCount: number
  postCount: number
  createdAt: string
  me: boolean
  following: boolean
  followsYou: boolean
  blockedByMe: boolean
  hasActiveStory: boolean
}

export interface Page<T> {
  items: T[]
  nextCursor?: string | null
}

// ---- posts -----------------------------------------------------------

export type PostVisibility = 'PUBLIC' | 'FOLLOWERS' | 'PRIVATE'
export type PostKind = 'IMAGE' | 'VIDEO' | 'CAROUSEL'

export interface MediaItem {
  id: string
  url?: string
  mimeType: string
  width?: number
  height?: number
  durationMs?: number
  blurhash?: string
}

export interface Post {
  id: string
  author: UserSummary
  caption?: string
  kind: PostKind
  visibility: PostVisibility
  location?: string
  media: MediaItem[]
  likeCount: number
  commentCount: number
  likedByMe: boolean
  savedByMe: boolean
  createdAt: string
  editedAt?: string
}

export interface Comment {
  id: string
  postId: string
  parentId?: string
  author: UserSummary
  body: string
  likeCount: number
  likedByMe: boolean
  replyCount: number
  createdAt: string
  editedAt?: string
}

export interface MosaicTile {
  url: string
  blurhash?: string
  width?: number
  height?: number
}

// ---- notifications ---------------------------------------------------

export type NotificationKind = 'LIKE' | 'COMMENT' | 'FOLLOW' | 'MENTION' | 'MESSAGE' | 'SYSTEM'

export interface AppNotification {
  id: string
  kind: NotificationKind
  actor?: UserSummary
  postId?: string
  postThumbUrl?: string
  commentId?: string
  message?: string
  read: boolean
  createdAt: string
}

// ---- chat ------------------------------------------------------------

export type ConversationKind = 'DIRECT' | 'GROUP'
export type MessageKind = 'TEXT' | 'IMAGE' | 'VIDEO' | 'FILE' | 'SYSTEM'

export interface Member {
  user: UserSummary
  role: 'OWNER' | 'ADMIN' | 'MEMBER'
  lastReadAt?: string
  online: boolean
}

export interface ReplyPreview {
  id: string
  senderUsername?: string
  kind: MessageKind
  body?: string
  deleted: boolean
}

export interface Message {
  id: string
  conversationId: string
  sender?: UserSummary
  kind: MessageKind
  body?: string
  attachmentUrl?: string
  replyTo?: ReplyPreview
  editedAt?: string
  deletedAt?: string
  createdAt: string
  clientId?: string
  /** Client-only: an optimistic bubble not yet confirmed by the server. */
  pending?: boolean
  failed?: boolean
}

export interface Conversation {
  id: string
  kind: ConversationKind
  title?: string
  members: Member[]
  lastMessage?: Message
  unreadCount: number
  lastMessageAt?: string
  createdAt: string
}

export interface Presence {
  userId: string
  online: boolean
  lastSeenAt?: string
}

// ---- stories -----------------------------------------------------------

export interface StoryItem {
  id: string
  url?: string
  mimeType: string
  caption?: string
  backgroundHex?: string
  createdAt: string
  expiresAt: string
  viewed: boolean
  viewCount?: number
}

export interface StoryGroup {
  user: UserSummary
  stories: StoryItem[]
  hasUnseen: boolean
  latestAt: string
}

export interface StoryViewer {
  user: UserSummary
  viewedAt: string
}

// ---- search & moderation ----------------------------------------------

export interface SearchResponse {
  users: UserSummary[]
  tags: { tag: string; postCount: number }[]
}

export type ReportReason = 'SPAM' | 'HARASSMENT' | 'NUDITY' | 'VIOLENCE' | 'OTHER'

export interface Report {
  id: string
  reason: ReportReason
  details?: string
  resolved: boolean
  createdAt: string
  reporter?: UserSummary
  targetType: 'USER' | 'POST' | 'COMMENT'
  targetUser?: UserSummary
  post?: { id: string; caption?: string; thumbUrl?: string; author: UserSummary; visibility: PostVisibility }
  comment?: { id: string; postId: string; body: string; author: UserSummary }
}

// ---- config & errors ---------------------------------------------------

/** Mirrors EmanstagramProperties.Media, served from /api/config/public. */
export interface MediaLimits {
  maxImageBytes: number
  maxVideoBytes: number
  maxAvatarBytes: number
  maxStoryVideoBytes: number
  maxCarouselItems: number
  maxCaptionLength: number
  maxCommentLength: number
  allowedImageTypes: string[]
  allowedVideoTypes: string[]
  storageEnabled: boolean
  /** Present when the server requires a Cloudflare Turnstile check on sign-up. */
  turnstileSiteKey?: string
}

/** GET /api/me/storage */
export interface StorageUsage {
  usedBytes: number
  quotaBytes: number
  uploadedTodayBytes: number
  dailyLimitBytes: number
  uploadsToday: number
  dailyLimitFiles: number
  newAccount: boolean
}

/** A row in the moderators' accounts list. */
export interface AccountView {
  user: UserSummary
  email: string
  role: Role
  createdAt: string
  postCount: number
  followerCount: number
  storageBytes: number
  suspendedAt?: string
  suspendedReason?: string
}

/** The single error shape produced by GlobalExceptionHandler. */
export interface ApiErrorBody {
  error: {
    code: string
    message: string
    fieldErrors?: Record<string, string> | null
    path?: string
    traceId?: string
    timestamp?: string
  }
}
