import { Link } from 'react-router-dom'
import { Copy, Heart, MessageCircle, Play } from 'lucide-react'
import { BlurImage, Skeleton } from '@/components/ui/bits'
import { cn, formatCount } from '@/lib/utils'
import type { Post } from '@/lib/types'

/**
 * Three-column square grid for profiles, explore and hashtags. With
 * {@code featured}, every tenth tile spans 2×2 (alternating sides), which
 * breaks up the wall of squares on Explore.
 */
export function PostGrid({ posts, featured }: { posts: Post[]; featured?: boolean }) {
  return (
    <div className={cn('grid grid-cols-3 gap-0.5 md:gap-1', featured && 'grid-flow-dense')}>
      {posts.map((p, i) => {
        const big = featured && (i % 10 === 2 || i % 10 === 5)
        return <GridTile key={p.id} post={p} className={cn(big && 'col-span-2 row-span-2', big && i % 10 === 5 && 'col-start-1')} />
      })}
    </div>
  )
}

function GridTile({ post, className }: { post: Post; className?: string }) {
  const cover = post.media[0]
  const isVideo = cover?.mimeType.startsWith('video/')
  return (
    <Link to={`/p/${post.id}`} className={cn('group relative block aspect-square overflow-hidden bg-surface-muted', className)}>
      {isVideo ? (
        <video src={cover.url} muted playsInline preload="metadata" className="size-full object-cover" />
      ) : (
        <BlurImage src={cover?.url} blurhash={cover?.blurhash} className="size-full" />
      )}

      <div className="absolute right-2 top-2 text-white drop-shadow-[0_1px_2px_rgba(0,0,0,0.5)]">
        {post.kind === 'CAROUSEL' ? <Copy size={18} /> : isVideo ? <Play size={18} fill="white" /> : null}
      </div>

      <div className="absolute inset-0 hidden items-center justify-center gap-6 bg-black/35 text-[15px] font-bold text-white group-hover:flex">
        <span className="flex items-center gap-1.5">
          <Heart size={19} fill="white" /> {formatCount(post.likeCount)}
        </span>
        <span className="flex items-center gap-1.5">
          <MessageCircle size={19} fill="white" className="-scale-x-100" /> {formatCount(post.commentCount)}
        </span>
      </div>
    </Link>
  )
}

export function PostGridSkeleton({ count = 9 }: { count?: number }) {
  return (
    <div className="grid grid-cols-3 gap-0.5 md:gap-1">
      {Array.from({ length: count }).map((_, i) => (
        <Skeleton key={i} className="aspect-square rounded-none" />
      ))}
    </div>
  )
}
