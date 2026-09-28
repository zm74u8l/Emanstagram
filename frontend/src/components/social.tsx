import { Fragment, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { del, errorMessage, get, post, put } from '@/lib/api'
import { cursorPaging, flatten, withCursor } from '@/lib/cache'
import { cn } from '@/lib/utils'
import { toast, toastError } from '@/stores/toast'
import { useAuth } from '@/stores/auth'
import { Avatar } from '@/components/ui/Avatar'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { VerifiedBadge } from '@/components/ui/bits'
import { InfiniteSentinel } from '@/components/InfiniteSentinel'
import { Spinner } from '@/components/ui/Spinner'
import type { Page, ReportReason, UserSummary } from '@/lib/types'

// ---------------------------------------------------------------------
// Rich text
// ---------------------------------------------------------------------

const TOKEN = /(@[A-Za-z0-9_.]{3,30}|#[\p{L}\p{N}_]{1,60})/gu

/** Caption/comment text with @mentions and #hashtags linked. */
export function RichText({ text, className }: { text: string; className?: string }) {
  const parts = text.split(TOKEN)
  return (
    <span className={cn('whitespace-pre-wrap break-words', className)}>
      {parts.map((part, i) => {
        if (i % 2 === 1) {
          if (part.startsWith('@')) {
            const name = part.slice(1).replace(/\.+$/, '')
            const trailing = part.slice(1 + name.length)
            return (
              <Fragment key={i}>
                <Link to={`/u/${name}`} className="text-accent hover:underline">
                  @{name}
                </Link>
                {trailing}
              </Fragment>
            )
          }
          return (
            <Link key={i} to={`/t/${encodeURIComponent(part.slice(1).toLowerCase())}`} className="text-accent hover:underline">
              {part}
            </Link>
          )
        }
        return <Fragment key={i}>{part}</Fragment>
      })}
    </span>
  )
}

// ---------------------------------------------------------------------
// People
// ---------------------------------------------------------------------

export function UserName({ user, className }: { user: Pick<UserSummary, 'username' | 'verified'>; className?: string }) {
  return (
    <Link to={`/u/${user.username}`} className={cn('inline-flex items-center gap-1 font-semibold hover:opacity-70', className)}>
      <span className="truncate">{user.username}</span>
      {user.verified && <VerifiedBadge />}
    </Link>
  )
}

export function UserRow({
  user,
  subtitle,
  trailing,
  onNavigate,
  size = 'md',
}: {
  user: UserSummary
  subtitle?: ReactNode
  trailing?: ReactNode
  onNavigate?: () => void
  size?: 'sm' | 'md' | 'lg'
}) {
  return (
    <div className="flex items-center gap-3 py-2">
      <Link to={`/u/${user.username}`} onClick={onNavigate}>
        <Avatar user={user} size={size === 'lg' ? 'lg' : size === 'sm' ? 'sm' : 'md'} />
      </Link>
      <div className="min-w-0 flex-1 leading-tight">
        <Link
          to={`/u/${user.username}`}
          onClick={onNavigate}
          className="flex items-center gap-1 text-[14px] font-semibold hover:opacity-70"
        >
          <span className="truncate">{user.username}</span>
          {user.verified && <VerifiedBadge />}
        </Link>
        <p className="truncate text-[13px] text-fg-muted">
          {subtitle ?? (user.effectiveName !== user.username ? user.effectiveName : null)}
        </p>
      </div>
      {trailing}
    </div>
  )
}

/**
 * Follow / Following toggle. Optimistic: the label flips immediately and
 * rolls back if the request fails.
 */
export function FollowButton({
  user,
  following: initial,
  size = 'sm',
  className,
  onChange,
}: {
  user: Pick<UserSummary, 'id' | 'username'>
  following: boolean
  size?: 'sm' | 'md'
  className?: string
  onChange?: (following: boolean, followerCount?: number) => void
}) {
  const qc = useQueryClient()
  const me = useAuth((s) => s.user)
  const patchUser = useAuth((s) => s.patchUser)
  const [following, setFollowing] = useState(initial)

  const mutation = useMutation({
    mutationFn: (next: boolean) =>
      next
        ? put<{ following: boolean; followerCount: number }>(`/api/users/${user.id}/follow`)
        : del<{ following: boolean; followerCount: number }>(`/api/users/${user.id}/follow`),
    onMutate: (next) => {
      setFollowing(next)
      if (me) patchUser({ followingCount: Math.max(0, me.followingCount + (next ? 1 : -1)) })
    },
    onSuccess: (res) => {
      onChange?.(res.following, res.followerCount)
      qc.invalidateQueries({ queryKey: ['profile', user.username] })
      qc.invalidateQueries({ queryKey: ['feed'] })
      qc.invalidateQueries({ queryKey: ['stories'] })
    },
    onError: (err, next) => {
      setFollowing(!next)
      if (me) patchUser({ followingCount: me.followingCount })
      toastError(errorMessage(err))
    },
  })

  if (me?.id === user.id) return null

  return (
    <Button
      size={size}
      variant={following ? 'secondary' : 'primary'}
      className={cn('min-w-[92px]', className)}
      onClick={() => mutation.mutate(!following)}
      aria-pressed={following}
    >
      {following ? 'Following' : 'Follow'}
    </Button>
  )
}

/** Followers, following, or likers of a post, in an infinite list. */
export function UserListDialog({
  open,
  onClose,
  title,
  path,
  queryKey,
}: {
  open: boolean
  onClose: () => void
  title: string
  path: string
  queryKey: unknown[]
}) {
  const query = useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) => get<Page<UserSummary>>(withCursor(path, pageParam)),
    ...cursorPaging,
    enabled: open,
  })
  const users = flatten(query.data)

  return (
    <Dialog open={open} onClose={onClose} title={title}>
      <div className="max-h-[60dvh] min-h-48 overflow-y-auto px-4 py-2 scrollbar-thin">
        {query.isLoading && (
          <div className="grid h-40 place-items-center text-fg-subtle">
            <Spinner />
          </div>
        )}
        {!query.isLoading && users.length === 0 && (
          <p className="py-12 text-center text-[14px] text-fg-muted">Nobody here yet.</p>
        )}
        {users.map((u) => (
          <UserRow
            key={u.id}
            user={u}
            onNavigate={onClose}
            trailing={u.following !== undefined && <FollowButton user={u} following={u.following} />}
          />
        ))}
        <InfiniteSentinel
          hasMore={!!query.hasNextPage}
          loading={query.isFetchingNextPage}
          onLoadMore={() => query.fetchNextPage()}
          rootMargin="200px"
        />
      </div>
    </Dialog>
  )
}

// ---------------------------------------------------------------------
// Reporting
// ---------------------------------------------------------------------

const REASONS: { value: ReportReason; label: string }[] = [
  { value: 'SPAM', label: 'It’s spam' },
  { value: 'HARASSMENT', label: 'Bullying or harassment' },
  { value: 'NUDITY', label: 'Nudity or sexual content' },
  { value: 'VIOLENCE', label: 'Violence or dangerous content' },
  { value: 'OTHER', label: 'Something else' },
]

export function ReportDialog({
  open,
  onClose,
  target,
}: {
  open: boolean
  onClose: () => void
  target: { userId?: string; postId?: string; commentId?: string }
}) {
  const [reason, setReason] = useState<ReportReason | null>(null)
  const [details, setDetails] = useState('')
  const mutation = useMutation({
    mutationFn: () => post('/api/reports', { ...target, reason, details: details.trim() || undefined }),
    onSuccess: () => {
      toast('Thanks. A moderator will take a look.')
      close()
    },
    onError: (err) => toastError(errorMessage(err)),
  })

  function close() {
    setReason(null)
    setDetails('')
    onClose()
  }

  const what = target.commentId ? 'comment' : target.postId ? 'post' : 'account'

  return (
    <Dialog open={open} onClose={close} title="Report">
      <div className="px-5 pb-5 pt-4">
        <p className="mb-3 text-[15px] font-semibold">Why are you reporting this {what}?</p>
        <div className="divide-y divide-line rounded-[var(--radius-card)] border border-line">
          {REASONS.map((r) => (
            <label key={r.value} className="flex cursor-pointer items-center justify-between px-4 py-3 text-[14px] hover:bg-surface-muted">
              {r.label}
              <input
                type="radio"
                name="reason"
                checked={reason === r.value}
                onChange={() => setReason(r.value)}
                className="size-4 accent-[var(--fg)]"
              />
            </label>
          ))}
        </div>
        <textarea
          value={details}
          onChange={(e) => setDetails(e.target.value)}
          maxLength={1000}
          placeholder="Anything else we should know? (optional)"
          className="mt-3 h-20 w-full resize-none rounded-[var(--radius-control)] border border-line-strong bg-surface-elevated px-3 py-2 text-[14px] outline-none focus:border-fg-muted"
        />
        <Button className="mt-3 w-full" disabled={!reason} loading={mutation.isPending} onClick={() => mutation.mutate()}>
          Submit report
        </Button>
      </div>
    </Dialog>
  )
}
