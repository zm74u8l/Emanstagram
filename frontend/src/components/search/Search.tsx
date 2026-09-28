import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Hash, Search as SearchIcon, X } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { get } from '@/lib/api'
import { useDebounced } from '@/hooks/useDebounced'
import { cn, plural, safeStorage } from '@/lib/utils'
import { UserRow } from '@/components/social'
import { Spinner } from '@/components/ui/Spinner'
import type { SearchResponse, UserSummary } from '@/lib/types'

const RECENT_KEY = 'emanstagram.recentSearches'

type Recent = { kind: 'user'; user: UserSummary } | { kind: 'tag'; tag: string }

/** Search input with results for people and hashtags, plus recent searches. */
export function SearchBox({ onNavigate, autoFocus, className }: { onNavigate?: () => void; autoFocus?: boolean; className?: string }) {
  const [q, setQ] = useState('')
  const [recent, setRecent] = useState<Recent[]>(() => safeStorage.get<Recent[]>(RECENT_KEY, []))
  const input = useRef<HTMLInputElement>(null)
  const term = useDebounced(q.trim(), 250)

  useEffect(() => {
    if (autoFocus) input.current?.focus()
  }, [autoFocus])

  const { data, isFetching } = useQuery({
    queryKey: ['search', term],
    queryFn: () => get<SearchResponse>(`/api/search?q=${encodeURIComponent(term)}`),
    enabled: term.length > 0,
    staleTime: 30_000,
  })

  function remember(item: Recent) {
    const key = (r: Recent) => (r.kind === 'user' ? `u:${r.user.id}` : `t:${r.tag}`)
    const next = [item, ...recent.filter((r) => key(r) !== key(item))].slice(0, 12)
    setRecent(next)
    safeStorage.set(RECENT_KEY, next)
    onNavigate?.()
  }

  function clearRecent() {
    setRecent([])
    safeStorage.set(RECENT_KEY, [])
  }

  const empty = term.length > 0 && data && data.users.length === 0 && data.tags.length === 0

  return (
    <div className={cn('flex min-h-0 flex-col', className)}>
      <div className="relative">
        <SearchIcon size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-fg-subtle" />
        <input
          ref={input}
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search people and #tags"
          aria-label="Search"
          className="h-10 w-full rounded-[var(--radius-control)] bg-surface-muted pl-10 pr-10 text-[15px] outline-none placeholder:text-fg-subtle focus:ring-3 focus:ring-accent/15"
        />
        <div className="absolute right-3 top-1/2 -translate-y-1/2 text-fg-subtle">
          {isFetching ? (
            <Spinner size={14} />
          ) : (
            q && (
              <button onClick={() => setQ('')} aria-label="Clear search" className="grid size-5 place-items-center rounded-full bg-fg-subtle text-surface">
                <X size={12} strokeWidth={3} />
              </button>
            )
          )}
        </div>
      </div>

      <div className="mt-3 min-h-0 flex-1 overflow-y-auto scrollbar-thin">
        {term.length === 0 && (
          <>
            <div className="flex items-center justify-between py-2">
              <h3 className="text-[15px] font-semibold">Recent</h3>
              {recent.length > 0 && (
                <button onClick={clearRecent} className="text-[13px] font-semibold text-accent">
                  Clear all
                </button>
              )}
            </div>
            {recent.length === 0 && <p className="py-10 text-center text-[14px] text-fg-muted">No recent searches.</p>}
            {recent.map((r) =>
              r.kind === 'user' ? (
                <UserRow key={`u${r.user.id}`} user={r.user} onNavigate={onNavigate} />
              ) : (
                <TagRow key={`t${r.tag}`} tag={r.tag} onClick={onNavigate} />
              ),
            )}
          </>
        )}

        {empty && <p className="py-10 text-center text-[14px] text-fg-muted">No results for “{term}”.</p>}

        {data?.users.map((u) => (
          <div key={u.id} onClickCapture={() => remember({ kind: 'user', user: u })}>
            <UserRow user={u} subtitle={u.following ? 'Following' : undefined} />
          </div>
        ))}
        {data?.tags.map((t) => (
          <TagRow key={t.tag} tag={t.tag} count={t.postCount} onClick={() => remember({ kind: 'tag', tag: t.tag })} />
        ))}
      </div>
    </div>
  )
}

function TagRow({ tag, count, onClick }: { tag: string; count?: number; onClick?: () => void }) {
  return (
    <Link to={`/t/${encodeURIComponent(tag)}`} onClick={onClick} className="flex items-center gap-3 py-2 hover:opacity-80">
      <span className="grid size-10 place-items-center rounded-full border border-line-strong">
        <Hash size={18} />
      </span>
      <span className="leading-tight">
        <span className="block text-[14px] font-semibold">#{tag}</span>
        {count !== undefined && <span className="text-[13px] text-fg-muted">{plural(count, 'recent post')}</span>}
      </span>
    </Link>
  )
}
