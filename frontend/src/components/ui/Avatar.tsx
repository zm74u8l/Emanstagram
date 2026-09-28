import { cn } from '@/lib/utils'
import type { UserSummary } from '@/lib/types'

/**
 * A profile picture, with a deterministic monogram when there isn't one.
 *
 * The fallback is derived from the user id, so the same person always gets
 * the same tone, and it renders instantly with no network request. The tones
 * are muted greys and earths so fallback avatars sit quietly in the
 * monochrome UI rather than looking like placeholder confetti.
 */
const TONES = [
  ['#e7e5e4', '#44403c'],
  ['#e5e7eb', '#374151'],
  ['#dbe4dd', '#2f4a3a'],
  ['#e8dfd6', '#5b4636'],
  ['#dfe3ea', '#34405a'],
  ['#ebe3e3', '#5a3a3a'],
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
  sm: 'size-8 text-[12px]',
  md: 'size-10 text-sm',
  lg: 'size-14 text-lg',
  xl: 'size-20 text-2xl',
  '2xl': 'size-[88px] md:size-[150px] text-3xl md:text-5xl',
} as const

interface AvatarProps {
  user: Pick<UserSummary, 'id' | 'username' | 'avatarUrl'> | null | undefined
  size?: keyof typeof SIZES
  className?: string
  /**
   * Story ring: 'unseen' draws it in the accent colour, 'seen' in a
   * neutral hairline.
   */
  ring?: 'unseen' | 'seen'
  online?: boolean
}

export function Avatar({ user, size = 'md', className, ring, online }: AvatarProps) {
  if (!user) {
    return <div aria-hidden className={cn('shrink-0 rounded-full bg-surface-muted', SIZES[size], className)} />
  }

  const [bg, fg] = TONES[hash(user.id) % TONES.length]
  const initial = user.username.charAt(0).toUpperCase()

  const face = user.avatarUrl ? (
    <img
      src={user.avatarUrl}
      alt=""
      loading="lazy"
      decoding="async"
      className="size-full rounded-full bg-surface-muted object-cover"
    />
  ) : (
    <div
      className="grid size-full place-items-center rounded-full font-display leading-none"
      style={{ backgroundColor: bg, color: fg }}
      aria-hidden
    >
      {initial}
    </div>
  )

  return (
    <div className={cn('relative shrink-0', SIZES[size], className)}>
      {ring ? (
        <div
          className={cn(
            'size-full rounded-full p-[2px]',
            ring === 'unseen' ? 'bg-accent' : 'bg-line-strong',
          )}
        >
          <div className="size-full rounded-full bg-surface p-[2px]">{face}</div>
        </div>
      ) : (
        <div className="size-full rounded-full ring-1 ring-inset ring-black/5 dark:ring-white/5">{face}</div>
      )}

      {online && (
        <span
          aria-label="Online"
          className="absolute bottom-[4%] right-[4%] size-[26%] min-h-2.5 min-w-2.5 rounded-full border-2 border-surface bg-emerald-500"
        />
      )}
    </div>
  )
}
