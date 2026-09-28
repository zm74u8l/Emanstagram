import { Link } from 'react-router-dom'
import { Plus } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { get } from '@/lib/api'
import { useAuth } from '@/stores/auth'
import { Avatar } from '@/components/ui/Avatar'
import { Skeleton } from '@/components/ui/bits'
import type { StoryGroup } from '@/lib/types'

export const storiesQuery = {
  queryKey: ['stories'],
  queryFn: () => get<StoryGroup[]>('/api/stories/feed'),
  staleTime: 60_000,
}

/** The row of story rings at the top of the home feed. */
export function StoriesRail() {
  const me = useAuth((s) => s.user)
  const { data, isLoading } = useQuery(storiesQuery)

  const mine = data?.find((g) => g.user.id === me?.id)
  const others = data?.filter((g) => g.user.id !== me?.id) ?? []

  return (
    <div className="flex gap-4 overflow-x-auto px-3 py-4 scrollbar-none md:px-0">
      {me && (
        <div className="flex w-[66px] shrink-0 flex-col items-center gap-1.5">
          {mine ? (
            <Link to={`/stories/${me.username}`} state={{ fromTray: true }} aria-label="Your story">
              <Avatar user={me} size="lg" ring={mine.hasUnseen ? 'unseen' : 'seen'} className="size-[62px]" />
            </Link>
          ) : (
            <Link to="/create?mode=story" className="relative" aria-label="Add to your story">
              <Avatar user={me} size="lg" className="size-[62px]" />
              <span className="absolute bottom-0 right-0 grid size-5 place-items-center rounded-full border-2 border-surface bg-surface-inverse text-fg-inverse">
                <Plus size={12} strokeWidth={3} />
              </span>
            </Link>
          )}
          <span className="w-full truncate text-center text-[12px] text-fg-muted">Your story</span>
        </div>
      )}

      {isLoading &&
        Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="flex w-[66px] shrink-0 flex-col items-center gap-1.5">
            <Skeleton className="size-[62px] rounded-full" />
            <Skeleton className="h-2.5 w-12" />
          </div>
        ))}

      {others.map((g) => (
        <Link
          key={g.user.id}
          to={`/stories/${g.user.username}`}
          state={{ fromTray: true }}
          className="flex w-[66px] shrink-0 flex-col items-center gap-1.5"
        >
          <Avatar user={g.user} size="lg" ring={g.hasUnseen ? 'unseen' : 'seen'} className="size-[62px]" />
          <span className="w-full truncate text-center text-[12px]">{g.user.username}</span>
        </Link>
      ))}
    </div>
  )
}
