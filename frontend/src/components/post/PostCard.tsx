import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Bookmark, Heart, MessageCircle, Send } from 'lucide-react'
import { copyToClipboard, formatRelative, plural, postUrl } from '@/lib/utils'
import { toast } from '@/stores/toast'
import { Avatar } from '@/components/ui/Avatar'
import { RichText, UserListDialog, UserName } from '@/components/social'
import { MediaCarousel } from './MediaCarousel'
import { PostMenu } from './PostMenu'
import { usePostActions } from './usePostActions'
import { cn } from '@/lib/utils'
import type { Post } from '@/lib/types'

/**
 * A post in the feed. No card, no border: posts are separated by space and
 * a hairline, the way a photo app should read, and the media runs edge to
 * edge on phones.
 */
export function PostCard({ post, eager }: { post: Post; eager?: boolean }) {
  const actions = usePostActions(post)
  const [likers, setLikers] = useState(false)

  return (
    <article className="border-b border-line pb-4 md:pb-5">
      <header className="flex items-center gap-3 px-3 py-2.5 md:px-0">
        <Link to={`/u/${post.author.username}`}>
          <Avatar user={post.author} size="sm" />
        </Link>
        <div className="min-w-0 flex-1 leading-tight">
          <div className="flex items-center gap-1 text-[14px]">
            <UserName user={post.author} />
            <span className="text-fg-subtle">·</span>
            <Link to={`/p/${post.id}`} className="text-fg-muted hover:text-fg" title={new Date(post.createdAt).toLocaleString()}>
              <time dateTime={post.createdAt}>{formatRelative(post.createdAt)}</time>
            </Link>
          </div>
          {post.location && <p className="truncate text-[12px] text-fg-muted">{post.location}</p>}
        </div>
        <PostMenu post={post} />
      </header>

      <MediaCarousel media={post.media} onDoubleTap={actions.likeOnce} className="md:rounded-[4px]" eager={eager} />

      <div className="px-3 md:px-0">
        <ActionBar post={post} actions={actions} />

        {post.likeCount > 0 && (
          <button onClick={() => setLikers(true)} className="text-[14px] font-semibold">
            {plural(post.likeCount, 'like')}
          </button>
        )}

        {post.caption && (
          <Caption username={post.author.username} verified={post.author.verified} text={post.caption} />
        )}

        {post.commentCount > 0 && (
          <Link to={`/p/${post.id}`} className="mt-1 block text-[14px] text-fg-muted hover:text-fg">
            {post.commentCount === 1 ? 'View 1 comment' : `View all ${post.commentCount.toLocaleString()} comments`}
          </Link>
        )}
        {post.commentCount === 0 && (
          <Link to={`/p/${post.id}`} className="mt-1 block text-[14px] text-fg-subtle hover:text-fg-muted">
            Add a comment…
          </Link>
        )}
      </div>

      <UserListDialog
        open={likers}
        onClose={() => setLikers(false)}
        title="Likes"
        path={`/api/posts/${post.id}/likes`}
        queryKey={['likers', post.id]}
      />
    </article>
  )
}

export function ActionBar({
  post,
  actions,
  onComment,
}: {
  post: Post
  actions: ReturnType<typeof usePostActions>
  onComment?: () => void
}) {
  return (
    <div className="-ml-2 flex items-center py-1.5">
      <IconAction label={post.likedByMe ? 'Unlike' : 'Like'} onClick={actions.toggleLike}>
        <Heart
          size={24}
          className={cn('transition-transform active:scale-90', post.likedByMe && 'text-like')}
          fill={post.likedByMe ? 'currentColor' : 'none'}
        />
      </IconAction>
      {onComment ? (
        <IconAction label="Comment" onClick={onComment}>
          <MessageCircle size={24} className="-scale-x-100" />
        </IconAction>
      ) : (
        <Link to={`/p/${post.id}`} aria-label="Comment" className="grid size-10 place-items-center hover:opacity-60">
          <MessageCircle size={24} className="-scale-x-100" />
        </Link>
      )}
      <IconAction
        label="Copy link"
        onClick={() => copyToClipboard(postUrl(post.id)).then((ok) => toast(ok ? 'Link copied' : 'Couldn’t copy the link'))}
      >
        <Send size={22} />
      </IconAction>
      <div className="ml-auto -mr-2">
        <IconAction label={post.savedByMe ? 'Remove from saved' : 'Save'} onClick={actions.toggleSave}>
          <Bookmark size={24} fill={post.savedByMe ? 'currentColor' : 'none'} />
        </IconAction>
      </div>
    </div>
  )
}

function IconAction({ label, onClick, children }: { label: string; onClick: () => void; children: React.ReactNode }) {
  return (
    <button onClick={onClick} aria-label={label} className="grid size-10 place-items-center hover:opacity-60">
      {children}
    </button>
  )
}

/** Caption collapsed to two lines with a "more" toggle once it runs long. */
function Caption({ username, verified, text }: { username: string; verified: boolean; text: string }) {
  const [expanded, setExpanded] = useState(false)
  const long = text.length > 140 || text.split('\n').length > 2
  return (
    <div className="mt-1 text-[14px] leading-snug">
      <p className={cn(!expanded && long && 'line-clamp-2')}>
        <UserName user={{ username, verified }} className="mr-1.5 align-baseline" />
        <RichText text={text} />
      </p>
      {long && !expanded && (
        <button onClick={() => setExpanded(true)} className="text-fg-muted hover:text-fg">
          more
        </button>
      )}
    </div>
  )
}

export function PostCardSkeleton() {
  return (
    <div className="border-b border-line pb-5">
      <div className="flex items-center gap-3 px-3 py-2.5 md:px-0">
        <div className="size-8 animate-shimmer rounded-full bg-surface-muted" />
        <div className="h-3 w-32 animate-shimmer rounded bg-surface-muted" />
      </div>
      <div className="aspect-square w-full animate-shimmer bg-surface-muted md:rounded-[4px]" />
      <div className="space-y-2 px-3 pt-3 md:px-0">
        <div className="h-3 w-20 animate-shimmer rounded bg-surface-muted" />
        <div className="h-3 w-3/4 animate-shimmer rounded bg-surface-muted" />
      </div>
    </div>
  )
}
