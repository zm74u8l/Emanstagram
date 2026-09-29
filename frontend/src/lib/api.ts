import type { ApiErrorBody, MediaLimits, TokenResponse } from './types'

/**
 * Where the backend lives. Empty in development, where Vite proxies /api
 * and /ws to localhost:8080. In production the frontend (Vercel) and the
 * backend (Railway etc.) are on different hosts, so this is set at build
 * time, e.g. VITE_API_URL=https://emanstagram-api.up.railway.app
 */
export const API_BASE = (import.meta.env.VITE_API_URL ?? '').replace(/\/+$/, '')

const ACCESS_KEY = 'emanstagram.accessToken'
const REFRESH_KEY = 'emanstagram.refreshToken'

/**
 * Error carrying the backend's stable `code` so the UI can react to specific
 * cases (e.g. USERNAME_TAKEN) instead of pattern-matching on message text.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>
  readonly traceId?: string

  constructor(status: number, body: ApiErrorBody['error']) {
    super(body.message)
    this.name = 'ApiError'
    this.status = status
    this.code = body.code
    this.fieldErrors = body.fieldErrors ?? {}
    this.traceId = body.traceId
  }
}

/** A readable message for any thrown value. */
export function errorMessage(err: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (err instanceof ApiError) return err.message
  if (err instanceof Error && err.message) return err.message
  return fallback
}

// ---- token storage -------------------------------------------------

function safeGet(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

export const tokenStore = {
  get access(): string | null {
    return safeGet(ACCESS_KEY)
  },
  get refresh(): string | null {
    return safeGet(REFRESH_KEY)
  },
  set(access: string, refresh: string) {
    try {
      localStorage.setItem(ACCESS_KEY, access)
      localStorage.setItem(REFRESH_KEY, refresh)
    } catch {
      /* private mode: the session lasts for this tab only */
    }
  },
  clear() {
    try {
      localStorage.removeItem(ACCESS_KEY)
      localStorage.removeItem(REFRESH_KEY)
    } catch {
      /* nothing to clear */
    }
  },
}

/**
 * Called when the server says this account is suspended. The session is
 * useless from then on, so drop it and show the sign-in page with a notice.
 */
function onSuspended() {
  tokenStore.clear()
  if (!window.location.pathname.startsWith('/login')) {
    window.location.assign('/login?suspended=1')
  }
}

// ---- refresh coordination -----------------------------------------

let refreshInFlight: Promise<string | null> | null = null
const refreshListeners = new Set<(token: string | null) => void>()

/** Lets the realtime client reconnect with the new token after a refresh. */
export function onTokenRefresh(listener: (token: string | null) => void): () => void {
  refreshListeners.add(listener)
  return () => refreshListeners.delete(listener)
}

/**
 * Refreshes the access token, collapsing concurrent 401s into a single
 * request. Without this, a page firing five parallel queries would trigger
 * five refreshes, and rotation would invalidate four of them and log the
 * user out.
 */
export async function refreshAccessToken(): Promise<string | null> {
  if (refreshInFlight) return refreshInFlight

  const refreshToken = tokenStore.refresh
  if (!refreshToken) return null

  refreshInFlight = (async () => {
    try {
      const res = await fetch(`${API_BASE}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      })
      if (!res.ok) {
        tokenStore.clear()
        refreshListeners.forEach((l) => l(null))
        if (res.status === 403) onSuspended()
        return null
      }
      const data: TokenResponse = await res.json()
      tokenStore.set(data.accessToken, data.refreshToken)
      refreshListeners.forEach((l) => l(data.accessToken))
      return data.accessToken
    } catch {
      return null
    } finally {
      // Cleared in finally so a transient network error does not wedge
      // every later request behind a dead promise.
      refreshInFlight = null
    }
  })()

  return refreshInFlight
}

/** Seconds until the stored access token expires (negative once expired). */
function secondsLeft(token: string): number {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return payload.exp - Date.now() / 1000
  } catch {
    return -1
  }
}

/**
 * An access token good for at least another minute, refreshing if needed.
 * The WebSocket authenticates once at CONNECT, so it must never present a
 * token that is about to expire.
 */
export async function freshAccessToken(): Promise<string | null> {
  const current = tokenStore.access
  if (current && secondsLeft(current) > 60) return current
  return refreshAccessToken()
}

// ---- request helpers ----------------------------------------------

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  /** Internal: prevents an infinite refresh loop. */
  _isRetry?: boolean
}

function parseBody(text: string): unknown {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    // A proxy error page, for instance. Treated as a body-less response.
    return null
  }
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, _isRetry, headers, ...rest } = options

  const finalHeaders = new Headers(headers)
  const token = tokenStore.access
  if (token) finalHeaders.set('Authorization', `Bearer ${token}`)

  let payload: BodyInit | undefined
  if (body instanceof FormData) {
    // Do NOT set Content-Type: the browser must add the multipart boundary.
    payload = body
  } else if (body !== undefined) {
    finalHeaders.set('Content-Type', 'application/json')
    payload = JSON.stringify(body)
  }

  let response: Response
  try {
    response = await fetch(API_BASE + path, { ...rest, headers: finalHeaders, body: payload })
  } catch {
    throw new ApiError(0, { code: 'NETWORK', message: "You're offline, or the server can't be reached." })
  }

  // A suspension reported on an authenticated call ends the session. (The
  // login endpoint also says ACCOUNT_SUSPENDED, but the form shows that one.)
  if (response.status === 403 && token && !path.startsWith('/api/auth/login')) {
    const peek = response.clone()
    const body = (await peek.json().catch(() => null)) as ApiErrorBody | null
    if (body?.error?.code === 'ACCOUNT_SUSPENDED') onSuspended()
  }

  if (response.status === 401 && !_isRetry && tokenStore.refresh) {
    const fresh = await refreshAccessToken()
    if (fresh) {
      return api<T>(path, { ...options, _isRetry: true })
    }
  }

  if (response.status === 204) return undefined as T

  const parsed = parseBody(await response.text())

  if (!response.ok) {
    const errorBody = (parsed as ApiErrorBody | null)?.error
    throw new ApiError(
      response.status,
      errorBody ?? { code: 'UNKNOWN', message: response.statusText || 'Request failed' },
    )
  }

  return parsed as T
}

export const get = <T>(path: string) => api<T>(path, { method: 'GET' })
export const post = <T>(path: string, body?: unknown) => api<T>(path, { method: 'POST', body })
export const put = <T>(path: string, body?: unknown) => api<T>(path, { method: 'PUT', body })
export const patch = <T>(path: string, body?: unknown) => api<T>(path, { method: 'PATCH', body })
export const del = <T>(path: string) => api<T>(path, { method: 'DELETE' })

/**
 * Multipart upload with progress. fetch() cannot report upload progress, so
 * this uses XMLHttpRequest, with the same 401 -> refresh -> retry behaviour
 * as {@link api}.
 */
export function upload<T>(
  path: string,
  form: FormData,
  options: { method?: 'POST' | 'PUT'; onProgress?: (fraction: number) => void; signal?: AbortSignal } = {},
  isRetry = false,
): Promise<T> {
  const { method = 'POST', onProgress, signal } = options
  return new Promise<T>((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open(method, API_BASE + path)
    const token = tokenStore.access
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress?.(e.loaded / e.total)
    }
    xhr.onerror = () =>
      reject(new ApiError(0, { code: 'NETWORK', message: 'The upload was interrupted. Check your connection.' }))
    xhr.onabort = () => reject(new ApiError(0, { code: 'ABORTED', message: 'Upload cancelled.' }))
    xhr.onload = async () => {
      if (xhr.status === 401 && !isRetry && tokenStore.refresh) {
        const fresh = await refreshAccessToken()
        if (fresh) {
          upload<T>(path, form, options, true).then(resolve, reject)
          return
        }
      }
      const parsed = parseBody(xhr.responseText)
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(parsed as T)
      } else {
        const errorBody = (parsed as ApiErrorBody | null)?.error
        reject(new ApiError(xhr.status, errorBody ?? { code: 'UNKNOWN', message: 'The upload failed.' }))
      }
    }
    signal?.addEventListener('abort', () => xhr.abort())
    xhr.send(form)
  })
}

/** Media limits, fetched once and cached by TanStack Query. */
export const fetchMediaLimits = () => get<MediaLimits>('/api/config/public')
