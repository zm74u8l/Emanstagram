import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ShieldCheck } from 'lucide-react'
import { errorMessage, get, post } from '@/lib/api'
import { cursorPaging, flatten, withCursor } from '@/lib/cache'
import { cn, formatRelative } from '@/lib/utils'
import { toast, toastError } from '@/stores/toast'
import { useAuth } from '@/stores/auth'
import { Avatar } from '@/components/ui/Avatar'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/Dialog'
import { EmptyState, Tabs } from '@/components/ui/bits'
import { PageSpinner } from '@/components/ui/Spinner'
import { InfiniteSentinel } from '@/components/InfiniteSentinel'
import type { Page, Report, ReportReason } from '@/lib/types'

type Status = 'open' | 'resolved'

const REASON_LABEL: Record<ReportReason, string> = {
  SPAM: 'Spam',
  HARASSMENT: 'Harassment',
  NUDITY: 'Nudity',
  VIOLENCE: 'Violence',
  OTHER: 'Other',
}

/** The moderator queue: review reports, dismiss them or remove the content. */
export default function Admin() {
  const me = useAuth((s) => s.user)
  const [status, setStatus] = useState<Status>('open')
  const allowed = me?.role === 'MODERATOR' || me?.role === 'ADMIN'

  const stats = useQuery({
    queryKey: ['admin-stats'],
    queryFn: () => get<{ open: number }>('/api/admin/stats'),
    enabled: allowed,
  })
  const query = useInfiniteQuery({
    queryKey: ['admin-reports', status],
    queryFn: ({ pageParam }) => get<Page<Report>>(withCursor('/api/admin/reports', pageParam, `status=${status}`)),
    ...cursorPaging,
    enabled: allowed,
  })
  const reports = flatten(query.data)

  if (!allowed) {
    return (
      <EmptyState
        icon={<ShieldCheck size={28} strokeWidth={1.5} />}
        title="Moderators only"
        body="This page is for the people who keep Emanstagram safe."
        className="min-h-[60dvh] justify-center"
      />
    )
  }

  return (
    <div className="mx-auto w-full max-w-[760px] px-4 py-6 md:py-10">
      <header className="mb-6">
        <h1 className="font-display text-[40px] leading-none">Moderation</h1>
        <p className="mt-2 text-[14px] text-fg-muted">
          {stats.data ? `${stats.data.open} open ${stats.data.open === 1 ? 'report' : 'reports'}` : ' '}
        </p>
      </header>

      <Tabs<Status>
        tabs={[
          { value: 'open', label: 'Open' },
          { value: 'resolved', label: 'Dismissed' },
        ]}
        value={status}
        onChange={setStatus}
        className="mb-4 justify-start"
      />

      {query.isLoading && <PageSpinner />}
      {query.isSuccess && reports.length === 0 && (
        <EmptyState title={status === 'open' ? 'All clear' : 'Nothing here'} body={status === 'open' ? 'No reports are waiting for review.' : undefined} />
      )}

      <div className="space-y-3">
        {reports.map((r) => (
          <ReportCard key={r.id} report={r} />
        ))}
      </div>
      <InfiniteSentinel hasMore={!!query.hasNextPage} loading={query.isFetchingNextPage} onLoadMore={() => query.fetchNextPage()} />
    </div>
  )
}

function ReportCard({ report: r }: { report: Report }) {
  const qc = useQueryClient()
  const [confirm, setConfirm] = useState(false)

  const resolve = useMutation({
    mutationFn: (action: 'DISMISS' | 'REMOVE_CONTENT') => post(`/api/admin/reports/${r.id}/resolve`, { action }),
    onSuccess: (_, action) => {
      toast(action === 'DISMISS' ? 'Report dismissed' : 'Content removed')
      setConfirm(false)
      qc.invalidateQueries({ queryKey: ['admin-reports'] })
      qc.invalidateQueries({ queryKey: ['admin-stats'] })
    },
    onError: (err) => toastError(errorMessage(err)),
  })

  const removable = r.targetType !== 'USER'

  return (
    <article className="rounded-[var(--radius-card)] border border-line bg-surface-elevated p-4">
      <div className="flex flex-wrap items-center gap-2 text-[13px]">
        <span
          className={cn(
            'rounded-full px-2.5 py-0.5 text-[12px] font-semibold',
            r.reason === 'OTHER' || r.reason === 'SPAM' ? 'bg-surface-muted' : 'bg-danger/10 text-danger',
          )}
        >
          {REASON_LABEL[r.reason]}
        </span>
        <span className="text-fg-muted">
          {r.targetType.toLowerCase()} reported by{' '}
          {r.reporter ? (
            <Link to={`/u/${r.reporter.username}`} className="font-semibold text-fg">
              {r.reporter.username}
            </Link>
          ) : (
            'a deleted account'
          )}{' '}
          · {formatRelative(r.createdAt)}
        </span>
      </div>

      {r.details && <p className="mt-3 border-l-2 border-line-strong pl-3 text-[14px] italic text-fg-muted">“{r.details}”</p>}

      <div className="mt-4 rounded-[10px] bg-surface-muted p-3">
        {r.post && (
          <Link to={`/p/${r.post.id}`} className="flex gap-3">
            {r.post.thumbUrl && <img src={r.post.thumbUrl} alt="" className="size-16 shrink-0 rounded-[6px] object-cover" />}
            <div className="min-w-0 text-[14px]">
              <p className="font-semibold">{r.post.author.username}</p>
              <p className="line-clamp-2 text-fg-muted">{r.post.caption || 'No caption'}</p>
            </div>
          </Link>
        )}
        {r.comment && (
          <Link to={`/p/${r.comment.postId}`} className="flex gap-3">
            <Avatar user={r.comment.author} size="sm" />
            <p className="min-w-0 text-[14px]">
              <span className="font-semibold">{r.comment.author.username}</span> {r.comment.body}
            </p>
          </Link>
        )}
        {!r.post && !r.comment && r.targetUser && (
          <Link to={`/u/${r.targetUser.username}`} className="flex items-center gap-3">
            <Avatar user={r.targetUser} size="md" />
            <span className="text-[14px] font-semibold">{r.targetUser.username}</span>
          </Link>
        )}
        {!r.post && !r.comment && !r.targetUser && <p className="text-[14px] text-fg-muted">The reported content no longer exists.</p>}
      </div>

      {!r.resolved && (
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="secondary" size="sm" loading={resolve.isPending && resolve.variables === 'DISMISS'} onClick={() => resolve.mutate('DISMISS')}>
            Dismiss
          </Button>
          {removable && (
            <Button variant="danger" size="sm" onClick={() => setConfirm(true)}>
              Remove {r.targetType.toLowerCase()}
            </Button>
          )}
        </div>
      )}

      <ConfirmDialog
        open={confirm}
        onClose={() => setConfirm(false)}
        onConfirm={() => resolve.mutate('REMOVE_CONTENT')}
        busy={resolve.isPending}
        title={`Remove this ${r.targetType.toLowerCase()}?`}
        body="It’s deleted for everyone and every report about it is closed."
        confirmLabel="Remove"
      />
    </article>
  )
}
