import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Search } from 'lucide-react'
import { del, errorMessage, get, post } from '@/lib/api'
import { cn, formatBytes, formatRelative } from '@/lib/utils'
import { toast, toastError } from '@/stores/toast'
import { useDebounced } from '@/hooks/useDebounced'
import { Avatar } from '@/components/ui/Avatar'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { EmptyState } from '@/components/ui/bits'
import { PageSpinner } from '@/components/ui/Spinner'
import type { AccountView, UserSummary } from '@/lib/types'

type Sort = 'STORAGE' | 'RECENT' | 'SUSPENDED'

const SORTS: { value: Sort; label: string }[] = [
  { value: 'STORAGE', label: 'Most storage' },
  { value: 'RECENT', label: 'Newest' },
  { value: 'SUSPENDED', label: 'Suspended' },
]

/**
 * Moderator view of accounts. "Most storage" is where a storage abuser shows
 * up first; "Newest" is where a wave of throwaway sign-ups shows up.
 */
export function Accounts() {
  const qc = useQueryClient()
  const [sort, setSort] = useState<Sort>('STORAGE')
  const [q, setQ] = useState('')
  const term = useDebounced(q.trim(), 300)
  const [target, setTarget] = useState<UserSummary | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['admin-accounts', sort, term],
    queryFn: () =>
      get<AccountView[]>(`/api/admin/accounts?sort=${sort}&limit=50${term ? `&q=${encodeURIComponent(term)}` : ''}`),
  })

  const unsuspend = useMutation({
    mutationFn: (id: string) => del(`/api/admin/accounts/${id}/suspend`),
    onSuccess: () => {
      toast('Suspension lifted')
      qc.invalidateQueries({ queryKey: ['admin-accounts'] })
    },
    onError: (err) => toastError(errorMessage(err)),
  })

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <div className="flex rounded-[var(--radius-control)] bg-surface-muted p-1 text-[13px] font-semibold">
          {SORTS.map((s) => (
            <button
              key={s.value}
              onClick={() => setSort(s.value)}
              aria-pressed={sort === s.value}
              className={cn(
                'h-8 rounded-[8px] px-3 transition-colors',
                sort === s.value ? 'bg-surface-elevated shadow-sm' : 'text-fg-muted hover:text-fg',
              )}
            >
              {s.label}
            </button>
          ))}
        </div>
        <label className="relative ml-auto min-w-48 flex-1 sm:max-w-64">
          <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-fg-subtle" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Username or email"
            aria-label="Filter accounts"
            className="h-10 w-full rounded-[var(--radius-control)] bg-surface-muted pl-9 pr-3 text-[14px] outline-none"
          />
        </label>
      </div>

      {isLoading && <PageSpinner />}
      {data?.length === 0 && (
        <EmptyState title="No accounts" body={sort === 'SUSPENDED' ? 'Nobody is suspended.' : 'Nothing matches that filter.'} />
      )}

      <ul className="divide-y divide-line rounded-[var(--radius-card)] border border-line bg-surface-elevated">
        {data?.map((a) => (
          <li key={a.user.id} className="flex items-center gap-3 px-4 py-3">
            <Link to={`/u/${a.user.username}`} className="shrink-0">
              <Avatar user={a.user} size="md" />
            </Link>
            <div className="min-w-0 flex-1 leading-tight">
              <p className="flex items-center gap-2 text-[14px]">
                <Link to={`/u/${a.user.username}`} className="truncate font-semibold hover:underline">
                  {a.user.username}
                </Link>
                {a.role !== 'USER' && (
                  <span className="rounded-full bg-surface-muted px-2 py-0.5 text-[11px] font-semibold uppercase">{a.role}</span>
                )}
                {a.suspendedAt && (
                  <span className="rounded-full bg-danger/10 px-2 py-0.5 text-[11px] font-semibold text-danger">Suspended</span>
                )}
              </p>
              <p className="mt-0.5 truncate text-[12.5px] text-fg-muted">
                {a.email} · joined {formatRelative(a.createdAt)} · {a.postCount} posts
              </p>
              {a.suspendedReason && <p className="mt-0.5 truncate text-[12.5px] text-fg-muted">Reason: {a.suspendedReason}</p>}
            </div>
            <span className="w-20 shrink-0 text-right text-[13px] font-semibold tabular-nums">{formatBytes(a.storageBytes)}</span>
            {a.role === 'ADMIN' ? (
              <span className="w-[92px]" />
            ) : a.suspendedAt ? (
              <Button
                size="sm"
                variant="secondary"
                className="w-[92px]"
                loading={unsuspend.isPending && unsuspend.variables === a.user.id}
                onClick={() => unsuspend.mutate(a.user.id)}
              >
                Unsuspend
              </Button>
            ) : (
              <Button size="sm" variant="ghost" className="w-[92px] text-danger" onClick={() => setTarget(a.user)}>
                Suspend
              </Button>
            )}
          </li>
        ))}
      </ul>

      <SuspendDialog user={target} onClose={() => setTarget(null)} />
    </div>
  )
}

/** Suspend an account, optionally wiping its posts and stories to reclaim storage. */
export function SuspendDialog({ user, onClose }: { user: UserSummary | null; onClose: () => void }) {
  const qc = useQueryClient()
  const [reason, setReason] = useState('')
  const [wipe, setWipe] = useState(false)

  const suspend = useMutation({
    mutationFn: () =>
      post<{ postsDeleted: number; storiesDeleted: number }>(`/api/admin/accounts/${user?.id}/suspend`, {
        reason: reason.trim() || undefined,
        deleteContent: wipe,
      }),
    onSuccess: (res) => {
      toast(
        wipe
          ? `Suspended @${user?.username}. Removed ${res.postsDeleted} posts and ${res.storiesDeleted} stories.`
          : `Suspended @${user?.username}`,
      )
      qc.invalidateQueries({ queryKey: ['admin-accounts'] })
      qc.invalidateQueries({ queryKey: ['admin-reports'] })
      close()
    },
    onError: (err) => toastError(errorMessage(err)),
  })

  function close() {
    setReason('')
    setWipe(false)
    onClose()
  }

  return (
    <Dialog open={user !== null} onClose={close} title={`Suspend @${user?.username ?? ''}`}>
      <div className="space-y-4 p-5">
        <p className="text-[14px] text-fg-muted">
          They’re signed out everywhere straight away and can’t sign back in, post or message until you lift it.
        </p>
        <label className="block">
          <span className="mb-1.5 block text-[13px] font-medium">Reason (shown to them when they try to sign in)</span>
          <input
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            maxLength={300}
            placeholder="e.g. Spam uploads"
            className="h-10 w-full rounded-[var(--radius-control)] border border-line-strong bg-surface-elevated px-3 text-[14px] outline-none focus:border-fg-muted"
          />
        </label>
        <label className="flex cursor-pointer items-start gap-3 rounded-[var(--radius-control)] border border-line p-3">
          <input type="checkbox" checked={wipe} onChange={(e) => setWipe(e.target.checked)} className="mt-0.5 size-4 accent-[var(--danger)]" />
          <span className="text-[14px] leading-snug">
            <span className="font-semibold">Also delete all their posts and stories</span>
            <span className="block text-[13px] text-fg-muted">Frees the storage they used. This can’t be undone.</span>
          </span>
        </label>
        <Button variant="danger" className="w-full" loading={suspend.isPending} onClick={() => suspend.mutate()}>
          Suspend account
        </Button>
      </div>
    </Dialog>
  )
}
