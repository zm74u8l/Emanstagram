import { useParams } from 'react-router-dom'
import { useInfiniteQuery } from '@tanstack/react-query'
import { get } from '@/lib/api'
import { cursorPaging, flatten, withCursor } from '@/lib/cache'
import { PostGrid, PostGridSkeleton } from '@/components/post/PostGrid'
import { InfiniteSentinel } from '@/components/InfiniteSentinel'
import { EmptyState } from '@/components/ui/bits'
import type { Page, Post } from '@/lib/types'

export default function Tag() {
  const { tag = '' } = useParams()
  const query = useInfiniteQuery({
    queryKey: ['tag', tag.toLowerCase()],
    queryFn: ({ pageParam }) => get<Page<Post>>(withCursor(`/api/tags/${encodeURIComponent(tag)}/posts`, pageParam)),
    ...cursorPaging,
  })
  const posts = flatten(query.data)
  const cover = posts[0]?.media[0]

  return (
    <div className="mx-auto w-full max-w-[935px] md:px-6 md:py-8">
      <header className="flex items-center gap-6 px-4 py-6 md:gap-10 md:px-0 md:pb-10">
        <div className="size-20 shrink-0 overflow-hidden rounded-full bg-surface-muted md:size-[150px]">
          {cover?.url && !cover.mimeType.startsWith('video/') && <img src={cover.url} alt="" className="size-full object-cover" />}
        </div>
        <div>
          <h1 className="font-display text-[40px] leading-none md:text-[56px]">#{tag}</h1>
          {query.isSuccess && (
            <p className="mt-2 text-[14px] text-fg-muted">
              {posts.length === 0 ? 'No posts yet' : `${posts.length}${query.hasNextPage ? '+' : ''} ${posts.length === 1 ? 'post' : 'posts'}`}
            </p>
          )}
        </div>
      </header>

      {query.isLoading && <PostGridSkeleton />}
      {query.isSuccess && posts.length === 0 && (
        <EmptyState title="Nothing tagged yet" body={`Be the first to use #${tag} in a caption.`} />
      )}
      <PostGrid posts={posts} />
      <InfiniteSentinel hasMore={!!query.hasNextPage} loading={query.isFetchingNextPage} onLoadMore={() => query.fetchNextPage()} />
    </div>
  )
}
