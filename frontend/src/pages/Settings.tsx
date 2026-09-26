import { useState } from 'react'
import { Monitor, Sun, Moon, Check } from 'lucide-react'
import { useAuth } from '@/stores/auth'
import { applyAccent, applyTheme, type Theme } from '@/hooks/useTheme'
import { cn } from '@/lib/utils'
import { Avatar } from '@/components/ui/Avatar'
import { Button } from '@/components/ui/Button'

const THEMES: { value: Theme; label: string; icon: typeof Sun }[] = [
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'system', label: 'System', icon: Monitor },
]

const ACCENTS = [
  '#6366f1', '#8b5cf6', '#ec4899', '#ef4444',
  '#f59e0b', '#22c55e', '#14b8a6', '#0ea5e9',
]

/**
 * Account and appearance settings.
 *
 * <p>Theme and accent apply instantly and are cached locally, so the UI
 * responds before the profile endpoint exists. Persisting them to the server
 * is wired up with the profile PATCH endpoint in Phase 2.
 */
export default function Settings() {
  const user = useAuth((s) => s.user)
  const logout = useAuth((s) => s.logout)

  const [theme, setTheme] = useState<Theme>(
    () => (localStorage.getItem('emanstagram.theme') as Theme | null) ?? 'system',
  )
  const [accent, setAccent] = useState<string>(
    () => localStorage.getItem('emanstagram.accent') ?? user?.accentColor ?? '#6366f1',
  )

  function chooseTheme(next: Theme) {
    setTheme(next)
    applyTheme(next)
  }

  function chooseAccent(next: string) {
    setAccent(next)
    applyAccent(next)
  }

  return (
    <div className="mx-auto w-full max-w-2xl px-4 py-6 space-y-6">
      <h1 className="text-lg font-semibold">Settings</h1>

      <section className="rounded-2xl border border-line bg-surface-elevated p-5">
        <h2 className="mb-4 text-sm font-semibold text-fg-muted">Profile</h2>
        <div className="flex items-center gap-4">
          <Avatar user={user} size="lg" />
          <div className="min-w-0">
            <p className="truncate font-semibold">{user?.effectiveName}</p>
            <p className="truncate text-sm text-fg-muted">@{user?.username}</p>
          </div>
          <Button variant="secondary" size="sm" className="ml-auto" disabled>
            Change photo
          </Button>
        </div>
      </section>

      <section className="rounded-2xl border border-line bg-surface-elevated p-5">
        <h2 className="mb-4 text-sm font-semibold text-fg-muted">Appearance</h2>

        <p className="mb-2 text-sm">Theme</p>
        <div className="grid grid-cols-3 gap-2">
          {THEMES.map(({ value, label, icon: Icon }) => (
            <button
              key={value}
              onClick={() => chooseTheme(value)}
              aria-pressed={theme === value}
              className={cn(
                'flex flex-col items-center gap-2 rounded-xl border p-4 text-sm transition-all',
                theme === value
                  ? 'border-accent bg-accent/10 text-accent'
                  : 'border-line hover:bg-surface-muted',
              )}
            >
              <Icon size={20} />
              {label}
            </button>
          ))}
        </div>

        <p className="mb-2 mt-6 text-sm">Accent colour</p>
        <div className="flex flex-wrap gap-2.5">
          {ACCENTS.map((colour) => (
            <button
              key={colour}
              onClick={() => chooseAccent(colour)}
              aria-label={`Use ${colour}`}
              aria-pressed={accent.toLowerCase() === colour}
              className="grid size-9 place-items-center rounded-full transition-transform hover:scale-110 active:scale-95"
              style={{ backgroundColor: colour }}
            >
              {accent.toLowerCase() === colour && (
                <Check size={16} className="text-white drop-shadow" />
              )}
            </button>
          ))}
        </div>
      </section>

      <section className="rounded-2xl border border-line bg-surface-elevated p-5">
        <h2 className="mb-2 text-sm font-semibold text-fg-muted">Session</h2>
        <p className="mb-4 text-sm text-fg-muted">
          Sign out of this device, or of every device at once.
        </p>
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => void logout()}>
            Log out
          </Button>
          <Button variant="danger" disabled>
            Log out everywhere
          </Button>
        </div>
      </section>
    </div>
  )
}
