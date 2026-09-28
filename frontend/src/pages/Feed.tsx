import { Link } from 'react-router-dom'
import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { get } from '@/lib/api'
import { cursorPaging, flatten, withCursor } from '@/lib/cache'
import { useAuth } from '@/stores/auth'
import { PostCard, PostCardSkeleton } from '@/components/post/PostCard'
import { StoriesRail } from '@/components/stories/StoriesRail'
import { InfiniteSentinel } from '@/components/InfiniteSentinel'
import { FollowButton, UserRow } from '@/components/social'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/bits'
import type { Page, Post, UserSummary } from '@/lib/types'

/** Home: stories, then posts from people you follow, with suggestions alongside. */
export default function Feed() {
  const feed = useInfiniteQuery({
    queryKey: ['feed'],
    queryFn: ({ pageParam }) => get<Page<Post>>(withCursor('/api/feed', pageParam)),
    ...cursorPaging,
  })
  const posts = flatten(feed.data)

  return (
    <div className="mx-auto flex w-full max-w-[935px] justify-center gap-16 md:px-6 md:pt-4">
      <div className="w-full max-w-[470px]">
        <StoriesRail />

        {feed.isLoading && (
          <div className="space-y-4">
            <PostCardSkeleton />
            <PostCardSkeleton />
          </div>
        )}

        {feed.isSuccess && posts.length === 0 && <QuietFeed />}

        <div className="space-y-4 md:space-y-5">
          {posts.map((p, i) => (
            <PostCard key={p.id} post={p} eager={i < 2} />
          ))}
        </div>

        <InfiniteSentinel hasMore={!!feed.hasNextPage} loading={feed.isFetchingNextPage} onLoadMore={() => feed.fetchNextPage()} />

        {feed.isSuccess && posts.length > 0 && !feed.hasNextPage && (
          <p className="py-12 text-center text-[13px] text-fg-subtle">You’re all caught up.</p>
        )}
      </div>

      <Sidebar />
    </div>
  )
}

function useSuggestions() {
  return useQuery({
    queryKey: ['suggested'],
    queryFn: () => get<UserSummary[]>('/api/users/suggested?limit=6'),
    staleTime: 5 * 60_000,
  })
}

/** An empty home feed is a prompt to follow people, not a dead end. */
function QuietFeed() {
  const { data } = useSuggestions()
  return (
    <div className="px-4 md:px-0">
      <EmptyState
        title="Your feed is quiet"
        body="Follow a few people and their photos will show up here."
        action={
          <Link to="/explore">
            <Button>Explore posts</Button>
          </Link>
        }
        className="pb-8"
      />
      {data && data.length > 0 && (
        <section className="rounded-[var(--radius-card)] border border-line px-4 py-2">
          <h3 className="py-2 text-[13px] font-semibold text-fg-muted">Suggested for you</h3>
          {data.map((u) => (
            <UserRow key={u.id} user={u} trailing={<FollowButton user={u} following={!!u.following} />} />
          ))}
        </section>
      )}
    </div>
  )
}

function Sidebar() {
  const me = useAuth((s) => s.user)
  const { data } = useSuggestions()
  if (!me) return null
  return (
    <aside className="hidden w-[320px] shrink-0 pt-8 xl:block">
      <UserRow user={me} size="lg" subtitle={me.effectiveName} />
      {data && data.length > 0 && (
        <>
          <h3 className="mb-1 mt-6 text-[14px] font-semibold text-fg-muted">Suggested for you</h3>
          {data.map((u) => (
            <UserRow
              key={u.id}
              user={u}
              size="sm"
              trailing={<FollowButton user={u} following={!!u.following} className="h-auto min-w-0 bg-transparent px-0 text-[12px] text-accent hover:bg-transparent hover:opacity-70" />}
            />
          ))}
        </>
      )}
      <p className="mt-8 text-[12px] text-fg-subtle">© {new Date().getFullYear()} Emanstagram</p>
    </aside>
  )
}
