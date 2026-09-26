import { cn } from '@/lib/utils'
import { useAuth } from '@/stores/auth'
import type { User } from '@/lib/types'

/**
 * Deterministic identicon for users without an avatar.
 *
 * <p>Derived from the user id so the same person always gets the same
 * colours, and it renders instantly with no network request.
 */
const PALETTE = [
  ['#6366f1', '#a855f7'],
  ['#ec4899', '#f97316'],
  ['#14b8a6', '#22c55e'],
  ['#f59e0b', '#ef4444'],
  ['#0ea5e9', '#8b5cf6'],
  ['#10b981', '#84cc16'],
]

function hash(input: string): number {
  let h = 0
  for (let i = 0; i < input.length; i++) {
    h = (h << 5) - h + input.charCodeAt(i)
    h |= 0
  }
  return Math.abs(h)
}

const SIZES = {
  xs: 'size-6 text-[10px]',
  sm: 'size-8 text-xs',
  md: 'size-10 text-sm',
  lg: 'size-14 text-lg',
  xl: 'size-24 text-2xl',
  full: 'size-full text-xl',
} as const

interface AvatarProps {
  user: Pick<User, 'id' | 'username' | 'avatarUrl'> | null
  size?: keyof typeof SIZES
  className?: string
  /** Adds the gradient story ring. */
  ring?: boolean
  online?: boolean
}

export function Avatar({ user, size = 'md', className, ring, online }: AvatarProps) {
  const current = useAuth((s) => s.user)

  if (!user) {
    return (
      <div
        aria-hidden
        className={cn(
          'shrink-0 rounded-full bg-surface-muted border border-line',
          SIZES[size],
          className,
        )}
      />
    )
  }

  const [from, to] = PALETTE[hash(user.id) % PALETTE.length]
  const initial = user.username.charAt(0).toUpperCase()

  return (
    <div className={cn('relative shrink-0', SIZES[size], className)}>
      <div
        className={cn(
          'size-full overflow-hidden rounded-full bg-surface-muted',
          ring && 'p-[2px]',
        )}
        style={
          ring
            ? { background: `linear-gradient(135deg, ${from}, ${to})` }
            : undefined
        }
      >
        {user.avatarUrl ? (
          <img
            src={user.avatarUrl}
            alt=""
            loading="lazy"
            decoding="async"
            className={cn(
              'size-full object-cover bg-surface-muted',
              ring && 'ring-2 ring-surface',
            )}
          />
        ) : (
          <div
            className="size-full grid place-items-center font-semibold text-white"
            style={{ background: `linear-gradient(135deg, ${from}, ${to})` }}
          >
            {/* aria-hidden: the username is always adjacent in the markup */}
            <span aria-hidden>{initial}</span>
            <span className="sr-only">{user.username}</span>
          </div>
        )}
      </div>

      {online && (
        <span
          aria-label="Online"
          className="absolute bottom-0 right-0 size-3 rounded-full bg-emerald-500 ring-2 ring-surface"
        />
      )}

      {current?.id === user.id && (
        <span className="sr-only">(you)</span>
      )}
    </div>
  )
}
