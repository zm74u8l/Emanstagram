// =====================================================================
// Shared API types. These mirror the Java DTOs in
// com.emanstagram.auth.dto.AuthDtos and the other response records.
// =====================================================================

export interface User {
  id: string
  username: string
  displayName: string | null
  effectiveName: string
  bio: string | null
  pronouns: string | null
  website: string | null
  location: string | null
  avatarUrl: string | null
  bannerUrl: string | null
  theme: 'SYSTEM' | 'LIGHT' | 'DARK'
  accentColor: string
  privateAccount: boolean
  verified: boolean
  role: 'USER' | 'MODERATOR' | 'ADMIN'
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
