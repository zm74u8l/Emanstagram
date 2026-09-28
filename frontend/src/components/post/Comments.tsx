import { forwardRef, useEffect, useImperativeHandle, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Heart, X } from 'lucide-react'
import { useInfiniteQuery, useMutation, useQueryClient, type InfiniteData } from '@tanstack/react-query'
import { del, errorMessage, get, post as postJson, put } from '@/lib/api'
import { cursorPaging, flatten, patchPost, withCursor } from '@/lib/cache'
import { cn, formatRelative, plural } from '@/lib/utils'
import { toastError } from '@/stores/toast'
import { useAuth } from '@/stores/auth'
import { Avatar } from '@/components/ui/Avatar'
import { ActionSheet } from '@/components/ui/Dialog'
import { Spinner } from '@/components/ui/Spinner'
import { ReportDialog, RichText, UserName } from '@/components/social'
import type { Comment, Page, Post } from '@/lib/types'

type CommentPages = InfiniteData<Page<Comment>>

interface ReplyTarget {
  rootId: string
  username: string
}

export interface CommentComposerHandle {
  focus: () => void
}

/** The comment list for a post. Replies load on demand under each thread. */
export function CommentList({ post, onReply }: { post: Post; onReply: (target: ReplyTarget) => void }) {
  const query = useInfiniteQuery({
    queryKey: ['comments', post.id],
    queryFn: ({ pageParam }) => get<Page<Comment>>(withCursor(`/api/posts/${post.id}/comments`, pageParam)),
    ...cursorPaging,
  })
  const comments = flatten(query.data)

  if (query.isLoading) {
    return (
      <div className="grid h-32 place-items-center text-fg-subtle">
        <Spinner />
      </div>
    )
  }

  return (
    <div className="space-y-1">
      {comments.length === 0 && (
        <div className="py-10 text-center">
          <p className="font-display text-[26px]">No comments yet</p>
          <p className="mt-1 text-[14px] text-fg-muted">Start the conversation.</p>
        </div>
      )}
      {comments.map((c) => (
        <CommentThread key={c.id} comment={c} post={post} onReply={onReply} />
      ))}
      {query.hasNextPage && (
        <button
          onClick={() => query.fetchNextPage()}
          disabled={query.isFetchingNextPage}
          className="mx-auto grid size-8 place-items-center rounded-full border border-line-strong text-fg-muted hover:text-fg"
          aria-label="Load more comments"
        >
          {query.isFetchingNextPage ? <Spinner size={14} /> : '+'}
        </button>
      )}
    </div>
  )
}

function CommentThread({ comment, post, onReply }: { comment: Comment; post: Post; onReply: (t: ReplyTarget) => void }) {
  const [open, setOpen] = useState(false)
  const replies = useInfiniteQuery({
    queryKey: ['replies', comment.id],
    queryFn: ({ pageParam }) => get<Page<Comment>>(withCursor(`/api/comments/${comment.id}/replies`, pageParam)),
    ...cursorPaging,
    enabled: open,
  })
  const items = flatten(replies.data)

  return (
    <div>
      <CommentItem
        comment={comment}
        post={post}
        onReply={() => {
          setOpen(true)
          onReply({ rootId: comment.id, username: comment.author.username })
        }}
      />
      {comment.replyCount > 0 && (
        <div className="ml-12">
          <button
            onClick={() => setOpen((o) => !o)}
            className="flex items-center gap-3 py-1 text-[12px] font-semibold text-fg-muted hover:text-fg"
          >
            <span className="h-px w-6 bg-fg-subtle" />
            {open ? 'Hide replies' : `View replies (${comment.replyCount})`}
            {replies.isFetching && <Spinner size={12} />}
          </button>
          {open &&
            items.map((r) => (
              <CommentItem
                key={r.id}
                comment={r}
                post={post}
                small
                onReply={() => onReply({ rootId: comment.id, username: r.author.username })}
              />
            ))}
          {open && replies.hasNextPage && (
            <button onClick={() => replies.fetchNextPage()} className="py-1 text-[12px] font-semibold text-fg-muted">
              More replies
            </button>
          )}
        </div>
      )}
    </div>
  )
}

function CommentItem({
  comment,
  post,
  onReply,
  small,
}: {
  comment: Comment
  post: Post
  onReply: () => void
  small?: boolean
}) {
  const qc = useQueryClient()
  const me = useAuth((s) => s.user)
  const [liked, setLiked] = useState(comment.likedByMe)
  const [likes, setLikes] = useState(comment.likeCount)
  const [sheet, setSheet] = useState(false)
  const [report, setReport] = useState(false)

  const canDelete = me && (me.id === comment.author.id || me.id === post.author.id || me.role !== 'USER')

  const like = useMutation({
    mutationFn: (next: boolean) =>
      next
        ? put<{ liked: boolean; likeCount: number }>(`/api/comments/${comment.id}/like`)
        : del<{ liked: boolean; likeCount: number }>(`/api/comments/${comment.id}/like`),
    onMutate: (next) => {
      setLiked(next)
      setLikes((n) => Math.max(0, n + (next ? 1 : -1)))
    },
    onSuccess: (res) => {
      setLiked(res.liked)
      setLikes(res.likeCount)
    },
    onError: (err, next) => {
      setLiked(!next)
      setLikes((n) => Math.max(0, n + (next ? -1 : 1)))
      toastError(errorMessage(err))
    },
  })

  const remove = useMutation({
    mutationFn: () => del(`/api/comments/${comment.id}`),
    onSuccess: () => {
      const removed = 1 + (comment.parentId ? 0 : comment.replyCount)
      patchPost(qc, post.id, (p) => ({ commentCount: Math.max(0, p.commentCount - removed) }))
      qc.invalidateQueries({ queryKey: ['comments', post.id] })
      if (comment.parentId) qc.invalidateQueries({ queryKey: ['replies', comment.parentId] })
    },
    onError: (err) => toastError(errorMessage(err)),
  })

  return (
    <div className={cn('group flex gap-3 py-2', remove.isPending && 'opacity-40')}>
      <Link to={`/u/${comment.author.username}`} className="shrink-0">
        <Avatar user={comment.author} size={small ? 'xs' : 'sm'} />
      </Link>
      <div className="min-w-0 flex-1">
        <p className="text-[14px] leading-snug">
          <UserName user={comment.author} className="mr-1.5 align-baseline" />
          <RichText text={comment.body} />
        </p>
        <div className="mt-1 flex items-center gap-3 text-[12px] font-semibold text-fg-muted">
          <time dateTime={comment.createdAt} className="font-normal">
            {formatRelative(comment.createdAt)}
          </time>
          {likes > 0 && <span>{plural(likes, 'like')}</span>}
          <button onClick={onReply} className="hover:text-fg">
            Reply
          </button>
          <button
            onClick={() => setSheet(true)}
            className="opacity-0 transition-opacity hover:text-fg group-hover:opacity-100 focus-visible:opacity-100 max-md:opacity-100"
            aria-label="Comment options"
          >
            •••
          </button>
        </div>
      </div>
      <button
        onClick={() => like.mutate(!liked)}
        aria-label={liked ? 'Unlike comment' : 'Like comment'}
        className="mt-1 grid size-6 shrink-0 place-items-center hover:opacity-60"
      >
        <Heart size={13} className={cn(liked && 'text-like')} fill={liked ? 'currentColor' : 'none'} />
      </button>

      <ActionSheet
        open={sheet}
        onClose={() => setSheet(false)}
        actions={[
          ...(canDelete ? [{ label: 'Delete', tone: 'danger' as const, onClick: () => remove.mutate() }] : []),
          ...(me?.id !== comment.author.id
            ? [{ label: 'Report', tone: 'danger' as const, onClick: () => setReport(true) }]
            : []),
        ]}
      />
      <ReportDialog open={report} onClose={() => setReport(false)} target={{ commentId: comment.id }} />
    </div>
  )
}

/** "Add a comment…" with reply-to support. Enter posts. */
export const CommentComposer = forwardRef<
  CommentComposerHandle,
  { post: Post; replyTo: ReplyTarget | null; onClearReply: () => void; className?: string }
>(({ post, replyTo, onClearReply, className }, ref) => {
  const qc = useQueryClient()
  const me = useAuth((s) => s.user)
  const [body, setBody] = useState('')
  const input = useRef<HTMLInputElement>(null)
  useImperativeHandle(ref, () => ({ focus: () => input.current?.focus() }))

  const mutation = useMutation({
    mutationFn: (text: string) =>
      postJson<Comment>(`/api/posts/${post.id}/comments`, { body: text, parentId: replyTo?.rootId }),
    onSuccess: (created) => {
      setBody('')
      onClearReply()
      patchPost(qc, post.id, (p) => ({ commentCount: p.commentCount + 1 }))
      if (created.parentId) {
        qc.invalidateQueries({ queryKey: ['replies', created.parentId] })
        qc.setQueryData<CommentPages>(['comments', post.id], (data) =>
          data && {
            ...data,
            pages: data.pages.map((pg) => ({
              ...pg,
              items: pg.items.map((c) => (c.id === created.parentId ? { ...c, replyCount: c.replyCount + 1 } : c)),
            })),
          },
        )
      } else {
        // Top-level comments read oldest-first; append to the last loaded page.
        qc.setQueryData<CommentPages>(['comments', post.id], (data) => {
          if (!data) return data
          const pages = [...data.pages]
          const last = pages[pages.length - 1]
          pages[pages.length - 1] = { ...last, items: [...last.items, created] }
          return { ...data, pages }
        })
      }
    },
    onError: (err) => toastError(errorMessage(err)),
  })

  // Replying pre-fills the @mention and focuses the field, as people expect.
  useEffect(() => {
    if (replyTo) {
      setBody(`@${replyTo.username} `)
      input.current?.focus()
    }
  }, [replyTo])
  const value = body

  function submit(e: FormEvent) {
    e.preventDefault()
    const text = value.trim()
    if (text && !mutation.isPending) mutation.mutate(text)
  }

  return (
    <div className={className}>
      {replyTo && (
        <div className="flex items-center justify-between bg-surface-muted px-4 py-2 text-[13px] text-fg-muted">
          Replying to @{replyTo.username}
          <button onClick={onClearReply} aria-label="Cancel reply" className="hover:text-fg">
            <X size={16} />
          </button>
        </div>
      )}
      <form onSubmit={submit} className="flex items-center gap-3 px-4 py-2.5">
        <Avatar user={me} size="sm" />
        <input
          ref={input}
          value={value}
          onChange={(e) => setBody(e.target.value)}
          placeholder="Add a comment…"
          maxLength={1000}
          aria-label="Add a comment"
          className="h-9 min-w-0 flex-1 bg-transparent text-[14px] outline-none placeholder:text-fg-subtle"
        />
        <button
          type="submit"
          disabled={!value.trim() || mutation.isPending}
          className="text-[14px] font-semibold text-accent disabled:opacity-40"
        >
          {mutation.isPending ? <Spinner size={14} /> : 'Post'}
        </button>
      </form>
    </div>
  )
})
CommentComposer.displayName = 'CommentComposer'
