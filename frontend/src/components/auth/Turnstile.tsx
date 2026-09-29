import { useEffect, useRef } from 'react'

/**
 * Cloudflare Turnstile, the bot check on sign-up.
 *
 * Rendered with appearance "interaction-only": most people never see it,
 * because Cloudflare decides silently. It only shows a box when it wants a
 * click. Each token is single-use, so the parent remounts this (new `key`)
 * after a failed submit to get a fresh one.
 */

interface TurnstileApi {
  render: (el: HTMLElement, options: Record<string, unknown>) => string
  remove: (widgetId: string) => void
}

declare global {
  interface Window {
    turnstile?: TurnstileApi
  }
}

const SCRIPT_URL = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
let scriptPromise: Promise<void> | null = null

function loadScript(): Promise<void> {
  if (window.turnstile) return Promise.resolve()
  if (!scriptPromise) {
    scriptPromise = new Promise((resolve, reject) => {
      const s = document.createElement('script')
      s.src = SCRIPT_URL
      s.async = true
      s.onload = () => resolve()
      s.onerror = () => {
        scriptPromise = null
        reject(new Error('The bot check could not load. Check your connection or ad blocker.'))
      }
      document.head.appendChild(s)
    })
  }
  return scriptPromise
}

export function Turnstile({
  siteKey,
  onToken,
  onError,
}: {
  siteKey: string
  onToken: (token: string | null) => void
  onError?: (message: string) => void
}) {
  const box = useRef<HTMLDivElement>(null)
  // Keep the latest callbacks without re-rendering the widget.
  const handlers = useRef({ onToken, onError })
  handlers.current = { onToken, onError }

  useEffect(() => {
    let widgetId: string | null = null
    let cancelled = false
    loadScript()
      .then(() => {
        if (cancelled || !box.current || !window.turnstile) return
        widgetId = window.turnstile.render(box.current, {
          sitekey: siteKey,
          appearance: 'interaction-only',
          theme: document.documentElement.classList.contains('dark') ? 'dark' : 'light',
          callback: (token: string) => handlers.current.onToken(token),
          'expired-callback': () => handlers.current.onToken(null),
          'error-callback': () => {
            handlers.current.onToken(null)
            handlers.current.onError?.('The bot check failed to run. Please reload the page.')
          },
        })
      })
      .catch((err: Error) => handlers.current.onError?.(err.message))
    return () => {
      cancelled = true
      if (widgetId && window.turnstile) window.turnstile.remove(widgetId)
    }
  }, [siteKey])

  return <div ref={box} className="min-h-0 [&:empty]:hidden" />
}
