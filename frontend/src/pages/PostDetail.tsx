import { useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ApiError, get } from '@/lib/api'
import { formatRelative, plural } from '@/lib/utils'
import { Avatar } from '@/components/ui/Avatar'
import { EmptyState } from '@/components/ui/bits'
import { PageSpinner } from '@/components/ui/Spinner'
import { MediaCarousel } from '@/components/post/MediaCarousel'
import { PostMenu } from '@/components/post/PostMenu'
import { ActionBar } from '@/components/post/PostCard'
import { CommentComposer, CommentList, type CommentComposerHandle } from '@/components/post/Comments'
import { usePostActions } from '@/components/post/usePostActions'
import { RichText, UserListDialog, UserName } from '@/components/social'
import type { Post } from '@/lib/types'

/**
 * A single post. Desktop shows media and conversation side by side, the
 * way a photo deserves; phones stack them with the comment box pinned.
 */
export default function PostDetail() {
  const { postId = '' } = useParams()
  const navigate = useNavigate()
  const { data: post, error, isLoading } = useQuery({
    queryKey: ['post', postId],
    queryFn: () => get<Post>(`/api/posts/${postId}`),
  })

  if (isLoading) return <PageSpinner />
  if (error || !post) {
    return (
      <EmptyState
        title="This post isn’t available"
        body={
          error instanceof ApiError && error.status === 404
            ? 'It may have been deleted, or it’s only visible to certain people.'
            : 'Something went wrong loading it.'
        }
        action={<Link to="/" className="text-[14px] font-semibold text-accent">Back to your feed</Link>}
        className="min-h-[60dvh] justify-center"
      />
    )
  }

  return <PostView post={post} onDeleted={() => navigate(`/u/${post.author.username}`, { replace: true })} />
}

function PostView({ post, onDeleted }: { post: Post; onDeleted: () => void }) {
  const actions = usePostActions(post)
  const composer = useRef<CommentComposerHandle>(null)
  const [replyTo, setReplyTo] = useState<{ rootId: string; username: string } | null>(null)
  const [likers, setLikers] = useState(false)

  const header = (
    <header className="flex items-center gap-3 border-b border-line px-4 py-3">
      <Link to={`/u/${post.author.username}`}>
        <Avatar user={post.author} size="sm" />
      </Link>
      <div className="min-w-0 flex-1 leading-tight">
        <UserName user={post.author} className="text-[14px]" />
        {post.location && <p className="truncate text-[12px] text-fg-muted">{post.location}</p>}
      </div>
      <PostMenu post={post} onDeleted={onDeleted} />
    </header>
  )

  return (
    <div className="mx-auto w-full max-w-[1100px] md:px-6 md:py-8">
      <article className="flex flex-col overflow-hidden border-line bg-surface-elevated md:h-[min(86dvh,820px)] md:flex-row md:rounded-[4px] md:border">
        <div className="md:hidden">{header}</div>

        <div className="bg-black md:min-w-0 md:flex-[1.35]">
          <MediaCarousel media={post.media} onDoubleTap={actions.likeOnce} eager fill />
        </div>

        <div className="flex min-h-0 flex-col md:w-[400px] md:shrink-0 md:border-l md:border-line">
          <div className="hidden md:block">{header}</div>

          <div className="min-h-0 flex-1 overflow-y-auto px-4 pt-3 scrollbar-thin">
            {post.caption && (
              <div className="flex gap-3 pb-3">
                <Avatar user={post.author} size="sm" />
                <div className="min-w-0 text-[14px] leading-snug">
                  <UserName user={post.author} className="mr-1.5 align-baseline" />
                  <RichText text={post.caption} />
                  <p className="mt-1 text-[12px] text-fg-muted">
                    {formatRelative(post.createdAt)}
                    {post.editedAt && ' · Edited'}
                  </p>
                </div>
              </div>
            )}
            <CommentList post={post} onReply={setReplyTo} />
          </div>

          <div className="border-t border-line px-4">
            <ActionBar post={post} actions={actions} onComment={() => composer.current?.focus()} />
            {post.likeCount > 0 ? (
              <button onClick={() => setLikers(true)} className="text-[14px] font-semibold">
                {plural(post.likeCount, 'like')}
              </button>
            ) : (
              <p className="text-[14px]">
                Be the first to <button onClick={actions.toggleLike} className="font-semibold">like this</button>
              </p>
            )}
            <time dateTime={post.createdAt} className="mb-2 block text-[11px] uppercase tracking-wide text-fg-subtle">
              {new Date(post.createdAt).toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' })}
            </time>
          </div>

          <CommentComposer
            ref={composer}
            post={post}
            replyTo={replyTo}
            onClearReply={() => setReplyTo(null)}
            className="sticky bottom-[52px] border-t border-line bg-surface-elevated md:static"
          />
        </div>
      </article>

      <UserListDialog
        open={likers}
        onClose={() => setLikers(false)}
        title="Likes"
        path={`/api/posts/${post.id}/likes`}
        queryKey={['likers', post.id]}
      />
    </div>
  )
}
