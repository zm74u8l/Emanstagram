import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query'
import { Heart } from 'lucide-react'
import { get, post } from '@/lib/api'
import { cursorPaging, flatten, withCursor } from '@/lib/cache'
import { cn, formatRelative } from '@/lib/utils'
import { unreadKeys } from '@/hooks/useRealtimeBridge'
import { Avatar } from '@/components/ui/Avatar'
import { EmptyState, Skeleton } from '@/components/ui/bits'
import { FollowButton } from '@/components/social'
import { InfiniteSentinel } from '@/components/InfiniteSentinel'
import type { AppNotification, Page } from '@/lib/types'

const DAY = 86_400_000

function bucket(iso: string): 'Today' | 'This week' | 'This month' | 'Earlier' {
  const age = Date.now() - new Date(iso).getTime()
  if (age < DAY) return 'Today'
  if (age < 7 * DAY) return 'This week'
  if (age < 30 * DAY) return 'This month'
  return 'Earlier'
}

export default function Notifications() {
  const qc = useQueryClient()
  const query = useInfiniteQuery({
    queryKey: ['notifications'],
    queryFn: ({ pageParam }) => get<Page<AppNotification>>(withCursor('/api/notifications', pageParam, 'limit=30')),
    ...cursorPaging,
  })
  const items = flatten(query.data)

  // Opening the page counts as reading everything on it. The unread styling
  // stays for this visit, so you can still see what was new.
  const hasUnread = items.some((n) => !n.read)
  useEffect(() => {
    if (!hasUnread) return
    const t = window.setTimeout(() => {
      void post('/api/notifications/read-all').then(() => qc.setQueryData(unreadKeys.notifications, { count: 0 }))
    }, 800)
    return () => window.clearTimeout(t)
  }, [hasUnread, qc])

  const groups: [string, AppNotification[]][] = []
  for (const n of items) {
    const b = bucket(n.createdAt)
    const last = groups[groups.length - 1]
    if (last && last[0] === b) last[1].push(n)
    else groups.push([b, [n]])
  }

  return (
    <div className="mx-auto w-full max-w-[600px] px-4 py-6 md:py-10">
      <h1 className="mb-4 font-display text-[40px] leading-none">Activity</h1>

      {query.isLoading &&
        Array.from({ length: 6 }).map((_, i) => (
          <div key={i} className="flex items-center gap-3 py-3">
            <Skeleton className="size-11 rounded-full" />
            <Skeleton className="h-3.5 flex-1" />
            <Skeleton className="size-11 rounded-[4px]" />
          </div>
        ))}

      {query.isSuccess && items.length === 0 && (
        <EmptyState
          icon={<Heart size={28} strokeWidth={1.5} />}
          title="Nothing yet"
          body="When people like or comment on your posts, follow you or mention you, you’ll see it here."
        />
      )}

      {groups.map(([label, list]) => (
        <section key={label} className="border-b border-line pb-3 pt-4 last:border-0">
          <h2 className="mb-1 text-[15px] font-semibold">{label}</h2>
          {list.map((n) => (
            <NotificationRow key={n.id} n={n} />
          ))}
        </section>
      ))}

      <InfiniteSentinel hasMore={!!query.hasNextPage} loading={query.isFetchingNextPage} onLoadMore={() => query.fetchNextPage()} />
    </div>
  )
}

function describe(n: AppNotification): string {
  switch (n.kind) {
    case 'LIKE':
      return n.commentId ? `liked your comment: ${n.message ?? ''}` : 'liked your post.'
    case 'COMMENT':
      return n.message?.startsWith('replied:') ? `${n.message}` : `commented: ${n.message ?? ''}`
    case 'FOLLOW':
      return 'started following you.'
    case 'MENTION':
      return n.commentId ? `mentioned you in a comment: ${n.message ?? ''}` : 'mentioned you in a post.'
    default:
      return n.message ?? ''
  }
}

function NotificationRow({ n }: { n: AppNotification }) {
  const target = n.postId ? `/p/${n.postId}` : n.actor ? `/u/${n.actor.username}` : '#'
  return (
    <div className={cn('-mx-3 flex items-center gap-3 rounded-[10px] px-3 py-2.5', !n.read && 'bg-accent/[0.06]')}>
      {n.actor && (
        <Link to={`/u/${n.actor.username}`} className="shrink-0">
          <Avatar user={n.actor} size="md" className="size-11" />
        </Link>
      )}
      <Link to={target} className="min-w-0 flex-1 text-[14px] leading-snug">
        {n.actor && <span className="font-semibold">{n.actor.username} </span>}
        <span className="line-clamp-2 inline">{describe(n)}</span>{' '}
        <span className="whitespace-nowrap text-fg-muted">{formatRelative(n.createdAt)}</span>
      </Link>
      {n.kind === 'FOLLOW' && n.actor ? (
        <FollowButton user={n.actor} following={!!n.actor.following} />
      ) : (
        n.postThumbUrl && (
          <Link to={target} className="shrink-0">
            <img src={n.postThumbUrl} alt="" className="size-11 rounded-[4px] object-cover" />
          </Link>
        )
      )}
      {!n.read && <span className="size-2 shrink-0 rounded-full bg-accent" aria-label="New" />}
    </div>
  )
}
