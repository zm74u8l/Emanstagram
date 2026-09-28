import { useEffect } from 'react'

export type Theme = 'light' | 'dark' | 'system'

const THEME_KEY = 'emanstagram.theme'
const ACCENT_KEY = 'emanstagram.accent'

function store(key: string, value: string) {
  try {
    localStorage.setItem(key, value)
  } catch {
    /* private mode */
  }
}

function read(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

let systemListener: ((e: MediaQueryListEvent) => void) | null = null

/**
 * Applies the theme to <html>. In "system" mode it also follows the OS live,
 * so switching the OS to dark at sunset flips the open app too.
 */
export function applyTheme(theme: Theme) {
  const root = document.documentElement
  const media = window.matchMedia('(prefers-color-scheme: dark)')
  if (systemListener) {
    media.removeEventListener('change', systemListener)
    systemListener = null
  }
  if (theme === 'system') {
    root.classList.toggle('dark', media.matches)
    systemListener = (e) => root.classList.toggle('dark', e.matches)
    media.addEventListener('change', systemListener)
  } else {
    root.classList.toggle('dark', theme === 'dark')
  }
  store(THEME_KEY, theme)
}

export function currentTheme(): Theme {
  const stored = read(THEME_KEY)
  return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system'
}

/** Validates a hex colour before it reaches the DOM as a custom property. */
export function applyAccent(hex: string | null | undefined) {
  if (!hex || !/^#[0-9a-fA-F]{6}$/.test(hex)) return
  document.documentElement.style.setProperty('--accent', hex)
  // Pick a readable foreground for text on an accent fill.
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
  document.documentElement.style.setProperty('--accent-fg', luminance > 0.62 ? '#0a0a0a' : '#ffffff')
  store(ACCENT_KEY, hex)
}

/** Restores the locally cached preferences once, before the session loads. */
export function useHydratePreferences() {
  useEffect(() => {
    applyTheme(currentTheme())
    applyAccent(read(ACCENT_KEY))
  }, [])
}
