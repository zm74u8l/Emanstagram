import { useEffect } from 'react'

export type Theme = 'light' | 'dark' | 'system'

const THEME_KEY = 'emanstagram.theme'
const ACCENT_KEY = 'emanstagram.accent'

/**
 * Applies the theme and accent to <html>.
 *
 * <p>Both are CSS-variable driven, so switching themes never triggers a
 * React re-render of the tree and costs nothing at runtime.
 */
export function applyTheme(theme: Theme) {
  const root = document.documentElement
  if (theme === 'system') {
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    root.classList.toggle('dark', prefersDark)
    localStorage.setItem(THEME_KEY, 'system')
  } else {
    root.classList.toggle('dark', theme === 'dark')
    localStorage.setItem(THEME_KEY, theme)
  }
}

/** Validates a hex colour before it reaches the DOM as a custom property. */
export function applyAccent(hex: string | null | undefined) {
  if (!hex) return
  if (!/^#[0-9a-fA-F]{6}$/.test(hex)) return
  document.documentElement.style.setProperty('--accent', hex)
  // Pick a readable foreground for the accent fill.
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
  document.documentElement.style.setProperty(
    '--accent-fg',
    luminance > 0.6 ? '#0b0d10' : '#ffffff',
  )
  localStorage.setItem(ACCENT_KEY, hex)
}

/** Restores the locally cached preferences. Runs once on mount. */
export function useHydratePreferences() {
  useEffect(() => {
    const storedTheme = (localStorage.getItem(THEME_KEY) as Theme | null) ?? 'system'

    if (storedTheme === 'system') {
      applyTheme('system')
      // Keep the UI in step with the OS while the app is open.
      const media = window.matchMedia('(prefers-color-scheme: dark)')
      const onChange = () => applyTheme('system')
      media.addEventListener('change', onChange)
      return () => media.removeEventListener('change', onChange)
    }

    applyTheme(storedTheme)
    return undefined
  }, [])

  useEffect(() => {
    applyAccent(localStorage.getItem(ACCENT_KEY))
  }, [])
}
