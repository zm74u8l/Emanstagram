import { useState } from 'react'
import { useInfiniteQuery } from '@tanstack/react-query'
import { Search } from 'lucide-react'
import { get } from '@/lib/api'
import { cursorPaging, flatten, withCursor } from '@/lib/cache'
import { PostGrid, PostGridSkeleton } from '@/components/post/PostGrid'
import { InfiniteSentinel } from '@/components/InfiniteSentinel'
import { SearchBox } from '@/components/search/Search'
import { EmptyState } from '@/components/ui/bits'
import type { Page, Post } from '@/lib/types'

/**
 * Explore: public posts from across the network. On phones this is also
 * where search lives; tapping the search bar swaps the grid for results.
 */
export default function Explore() {
  const [searching, setSearching] = useState(false)
  const query = useInfiniteQuery({
    queryKey: ['explore'],
    queryFn: ({ pageParam }) => get<Page<Post>>(withCursor('/api/explore', pageParam, 'limit=24')),
    ...cursorPaging,
  })
  const posts = flatten(query.data)

  return (
    <div className="mx-auto w-full max-w-[935px] md:px-6 md:py-8">
      <div className="px-3 py-2.5 md:hidden">
        {searching ? (
          <div className="flex h-[calc(100dvh-52px-56px-20px)] flex-col">
            <div className="flex items-start gap-3">
              <SearchBox autoFocus className="min-h-0 flex-1" />
              <button onClick={() => setSearching(false)} className="h-10 text-[14px] font-semibold">
                Cancel
              </button>
            </div>
          </div>
        ) : (
          <button
            onClick={() => setSearching(true)}
            className="flex h-10 w-full items-center gap-2.5 rounded-[var(--radius-control)] bg-surface-muted px-3.5 text-[15px] text-fg-subtle"
          >
            <Search size={16} />
            Search
          </button>
        )}
      </div>

      {!searching && (
        <>
          {query.isLoading && <PostGridSkeleton count={12} />}
          {query.isSuccess && posts.length === 0 && (
            <EmptyState title="Nothing to explore yet" body="When people share public posts, they’ll appear here." />
          )}
          <PostGrid posts={posts} featured />
          <InfiniteSentinel hasMore={!!query.hasNextPage} loading={query.isFetchingNextPage} onLoadMore={() => query.fetchNextPage()} />
        </>
      )}
    </div>
  )
}
