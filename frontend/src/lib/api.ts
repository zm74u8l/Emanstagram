import type { ApiErrorBody, MediaLimits, TokenResponse } from './types'

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

// ---- token storage -------------------------------------------------

export const tokenStore = {
  get access(): string | null {
    return localStorage.getItem(ACCESS_KEY)
  },
  get refresh(): string | null {
    return localStorage.getItem(REFRESH_KEY)
  },
  set(access: string, refresh: string) {
    localStorage.setItem(ACCESS_KEY, access)
    localStorage.setItem(REFRESH_KEY, refresh)
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
  },
}

// ---- refresh coordination -----------------------------------------

let refreshInFlight: Promise<string | null> | null = null

/**
 * Refreshes the access token, collapsing concurrent 401s into a single
 * request. Without this, a page firing five parallel queries would trigger
 * five refreshes, and rotation would invalidate four of them and log the
 * user out.
 */
async function refreshAccessToken(): Promise<string | null> {
  if (refreshInFlight) return refreshInFlight

  const refreshToken = tokenStore.refresh
  if (!refreshToken) return null

  refreshInFlight = (async () => {
    try {
      const res = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      })
      if (!res.ok) {
        tokenStore.clear()
        return null
      }
      const data: TokenResponse = await res.json()
      tokenStore.set(data.accessToken, data.refreshToken)
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

// ---- request helpers ----------------------------------------------

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  /** Internal: prevents an infinite refresh loop. */
  _isRetry?: boolean
  /** Set for form-data uploads; JSON serialisation is skipped. */
  raw?: boolean
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, raw, _isRetry, headers, ...rest } = options

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

  const response = await fetch(path, {
    ...rest,
    headers: finalHeaders,
    body: payload,
  })

  if (response.status === 401 && !_isRetry && tokenStore.refresh) {
    const fresh = await refreshAccessToken()
    if (fresh) {
      return api<T>(path, { ...options, _isRetry: true })
    }
  }

  if (response.status === 204) return undefined as T

  const text = await response.text()
  const parsed = text ? (JSON.parse(text) as unknown) : null

  if (!response.ok) {
    const errorBody = (parsed as ApiErrorBody)?.error
    throw new ApiError(
      response.status,
      errorBody ?? { code: 'UNKNOWN', message: response.statusText || 'Request failed' },
    )
  }

  return parsed as T
}

export const get = <T>(path: string) => api<T>(path, { method: 'GET' })
export const post = <T>(path: string, body?: unknown) => api<T>(path, { method: 'POST', body })
export const patch = <T>(path: string, body?: unknown) => api<T>(path, { method: 'PATCH', body })
export const del = <T>(path: string) => api<T>(path, { method: 'DELETE' })

/** Media limits, fetched once and cached by TanStack Query. */
export const fetchMediaLimits = () => get<MediaLimits>('/api/config/public')
