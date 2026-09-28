import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * Class joiner that also resolves Tailwind conflicts, so a caller's
 * `className="h-12"` reliably overrides a component's default `h-10`.
 */
export function cn(...classes: ClassValue[]): string {
  return twMerge(clsx(classes))
}

/** Compact counts for the UI: 1200 -> "1.2K", 3_400_000 -> "3.4M". */
export function formatCount(n: number): string {
  if (n < 1000) return String(n)
  if (n < 1_000_000) {
    const k = n / 1000
    return `${k < 10 ? k.toFixed(1).replace(/\.0$/, '') : Math.round(k)}K`
  }
  const m = n / 1_000_000
  return `${m < 10 ? m.toFixed(1).replace(/\.0$/, '') : Math.round(m)}M`
}

/** "1 like", "2 likes". Uses locale grouping for large numbers. */
export function plural(n: number, one: string, many = `${one}s`): string {
  return `${n.toLocaleString()} ${n === 1 ? one : many}`
}

/**
 * Relative timestamps: "now", "5m", "3h", "2d", "4w", then an absolute date.
 * Deliberately coarse, matching how social apps age content.
 */
export function formatRelative(iso: string): string {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return ''

  const seconds = Math.floor((Date.now() - then) / 1000)
  if (seconds < 45) return 'now'
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h`
  if (seconds < 604800) return `${Math.floor(seconds / 86400)}d`
  if (seconds < 2419200) return `${Math.floor(seconds / 604800)}w`

  const date = new Date(then)
  const sameYear = date.getFullYear() === new Date().getFullYear()
  return date.toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    ...(sameYear ? {} : { year: 'numeric' }),
  })
}

/** "Active 5m ago" style, for presence. */
export function formatLastSeen(iso?: string): string {
  if (!iso) return ''
  const rel = formatRelative(iso)
  return rel === 'now' ? 'Active just now' : `Active ${rel} ago`
}

/** Long form for tooltips and message separators: "Today 14:02", "Mon 09:15", "3 Mar 2025". */
export function formatStamp(iso: string): string {
  const d = new Date(iso)
  const now = new Date()
  const time = d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  if (d.getTime() >= startOfToday) return `Today ${time}`
  if (d.getTime() >= startOfToday - 86400_000) return `Yesterday ${time}`
  if (d.getTime() >= startOfToday - 6 * 86400_000) {
    return `${d.toLocaleDateString(undefined, { weekday: 'short' })} ${time}`
  }
  return d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function formatDuration(ms: number): string {
  const total = Math.round(ms / 1000)
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`
}

/** Validates a file before upload, returning an error message or null. */
export function validateFile(file: File, maxBytes: number, allowedTypes: string[]): string | null {
  if (allowedTypes.length > 0 && !allowedTypes.includes(file.type)) {
    return `${file.name} isn't a supported format. Use ${allowedTypes
      .map((t) => t.split('/')[1]?.replace('quicktime', 'mov').toUpperCase())
      .join(', ')}.`
  }
  if (file.size > maxBytes) {
    return `${file.name} is ${formatBytes(file.size)}. The limit is ${formatBytes(maxBytes)}.`
  }
  return null
}

/** localStorage that never throws (private mode, blocked storage). */
export const safeStorage = {
  get<T>(key: string, fallback: T): T {
    try {
      const raw = localStorage.getItem(key)
      return raw === null ? fallback : (JSON.parse(raw) as T)
    } catch {
      return fallback
    }
  },
  set(key: string, value: unknown) {
    try {
      localStorage.setItem(key, JSON.stringify(value))
    } catch {
      /* ignore */
    }
  },
}

export function copyToClipboard(text: string): Promise<boolean> {
  return navigator.clipboard
    .writeText(text)
    .then(() => true)
    .catch(() => false)
}

export function postUrl(postId: string): string {
  return `${window.location.origin}/p/${postId}`
}
