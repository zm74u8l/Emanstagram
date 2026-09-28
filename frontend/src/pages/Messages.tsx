import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Check, PenSquare, Send, X } from 'lucide-react'
import { errorMessage, get, post } from '@/lib/api'
import { cn, formatRelative } from '@/lib/utils'
import { toastError } from '@/stores/toast'
import { useAuth } from '@/stores/auth'
import { useChat } from '@/stores/chat'
import { useDebounced } from '@/hooks/useDebounced'
import { Avatar } from '@/components/ui/Avatar'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { Skeleton } from '@/components/ui/bits'
import { Spinner } from '@/components/ui/Spinner'
import { Thread } from '@/components/chat/Thread'
import { ConversationAvatar, conversationTitle, previewText } from '@/components/chat/helpers'
import type { Conversation, SearchResponse, UserSummary } from '@/lib/types'

/**
 * Direct messages. Two panes on desktop, list-then-thread on phones (where
 * the thread takes the full screen and the app chrome steps aside).
 */
export default function Messages() {
  const { conversationId } = useParams()
  const [composing, setComposing] = useState(false)

  return (
    <div className="flex h-[calc(100dvh-52px-56px)] md:h-dvh">
      <aside
        className={cn(
          'flex w-full flex-col border-line md:w-[360px] md:shrink-0 md:border-r',
          conversationId && 'max-md:hidden',
        )}
      >
        <header className="flex items-center justify-between px-5 pb-3 pt-5 md:pt-9">
          <h1 className="font-display text-[34px] leading-none">Messages</h1>
          <button onClick={() => setComposing(true)} aria-label="New message" className="grid size-10 place-items-center rounded-full hover:bg-surface-muted">
            <PenSquare size={22} />
          </button>
        </header>
        <Inbox activeId={conversationId} />
      </aside>

      <section className={cn('min-w-0 flex-1', !conversationId && 'max-md:hidden')}>
        {conversationId ? (
          <Thread key={conversationId} conversationId={conversationId} />
        ) : (
          <div className="grid h-full place-items-center px-6 text-center">
            <div>
              <div className="mx-auto grid size-24 place-items-center rounded-full border-2 border-fg">
                <Send size={40} strokeWidth={1.25} />
              </div>
              <h2 className="mt-5 font-display text-[32px] leading-none">Your messages</h2>
              <p className="mt-2 text-[14px] text-fg-muted">Send a photo or a message to a friend or a group.</p>
              <Button className="mt-5" onClick={() => setComposing(true)}>
                Send message
              </Button>
            </div>
          </div>
        )}
      </section>

      <NewMessageDialog open={composing} onClose={() => setComposing(false)} />
    </div>
  )
}

function Inbox({ activeId }: { activeId?: string }) {
  const me = useAuth((s) => s.user)!
  const presence = useChat((s) => s.presence)
  const typing = useChat((s) => s.typing)
  const { data, isLoading } = useQuery({
    queryKey: ['conversations'],
    queryFn: () => get<Conversation[]>('/api/conversations'),
  })

  if (isLoading) {
    return (
      <div className="space-y-1 px-5">
        {Array.from({ length: 7 }).map((_, i) => (
          <div key={i} className="flex items-center gap-3 py-2">
            <Skeleton className="size-14 rounded-full" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-3.5 w-32" />
              <Skeleton className="h-3 w-48" />
            </div>
          </div>
        ))}
      </div>
    )
  }

  if (!data?.length) {
    return <p className="px-5 py-10 text-center text-[14px] text-fg-muted">No conversations yet.</p>
  }

  return (
    <ul className="min-h-0 flex-1 overflow-y-auto pb-4 scrollbar-thin">
      {data.map((c) => {
        const partner = c.kind === 'DIRECT' ? c.members.find((m) => m.user.id !== me.id) : undefined
        const online = partner ? presence[partner.user.id]?.online ?? partner.online : false
        const unread = c.unreadCount > 0
        const typers = Object.values(typing[c.id] ?? {})
        return (
          <li key={c.id}>
            <Link
              to={`/messages/${c.id}`}
              className={cn('flex items-center gap-3 px-5 py-2.5 transition-colors hover:bg-surface-muted', activeId === c.id && 'bg-surface-muted')}
            >
              <ConversationAvatar c={c} myId={me.id} size="lg" online={online} />
              <div className="min-w-0 flex-1 leading-tight">
                <p className={cn('truncate text-[14px]', unread && 'font-semibold')}>{conversationTitle(c, me.id)}</p>
                <p className={cn('mt-1 flex text-[13px]', unread ? 'font-semibold text-fg' : 'text-fg-muted')}>
                  {typers.length > 0 ? (
                    <span className="text-accent">typing…</span>
                  ) : (
                    <>
                      <span className="truncate">{previewText(c.lastMessage, me.id, c.kind === 'GROUP')}</span>
                      {c.lastMessageAt && <span className="shrink-0 font-normal text-fg-muted">&nbsp;· {formatRelative(c.lastMessageAt)}</span>}
                    </>
                  )}
                </p>
              </div>
              {unread && <span className="size-2 shrink-0 rounded-full bg-accent" aria-label={`${c.unreadCount} unread`} />}
            </Link>
          </li>
        )
      })}
    </ul>
  )
}

/** Pick one person for a DM, or several (plus a name) for a group. */
export function NewMessageDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [picked, setPicked] = useState<UserSummary[]>([])
  const [title, setTitle] = useState('')
  const term = useDebounced(q.trim(), 250)

  const search = useQuery({
    queryKey: ['search', term],
    queryFn: () => get<SearchResponse>(`/api/search?q=${encodeURIComponent(term)}`),
    enabled: open && term.length > 0,
  })
  const start = useMutation({
    mutationFn: () =>
      picked.length === 1
        ? post<Conversation>('/api/conversations/direct', { userId: picked[0].id })
        : post<Conversation>('/api/conversations/group', { title: title.trim(), memberIds: picked.map((p) => p.id) }),
    onSuccess: (c) => {
      close()
      navigate(`/messages/${c.id}`)
    },
    onError: (err) => toastError(errorMessage(err)),
  })

  function close() {
    setQ('')
    setPicked([])
    setTitle('')
    onClose()
  }

  function toggle(u: UserSummary) {
    setPicked((p) => (p.some((x) => x.id === u.id) ? p.filter((x) => x.id !== u.id) : [...p, u]))
  }

  const group = picked.length > 1
  const canStart = picked.length === 1 || (group && title.trim().length > 0)

  return (
    <Dialog open={open} onClose={close} title="New message" className="max-w-[548px]">
      <div className="flex flex-wrap items-center gap-2 border-b border-line px-4 py-2.5">
        <span className="text-[15px] font-semibold">To:</span>
        {picked.map((u) => (
          <button key={u.id} onClick={() => toggle(u)} className="flex items-center gap-1 rounded-full bg-accent/10 px-3 py-1 text-[13px] font-semibold text-accent">
            {u.username} <X size={13} />
          </button>
        ))}
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search…"
          autoFocus
          aria-label="Search people"
          className="h-8 min-w-32 flex-1 bg-transparent text-[14px] outline-none placeholder:text-fg-subtle"
        />
      </div>

      {group && (
        <div className="border-b border-line px-4 py-2.5">
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={120}
            placeholder="Name this group"
            aria-label="Group name"
            className="h-8 w-full bg-transparent text-[14px] outline-none placeholder:text-fg-subtle"
          />
        </div>
      )}

      <div className="h-[340px] overflow-y-auto px-4 py-2 scrollbar-thin">
        {search.isFetching && (
          <div className="grid h-20 place-items-center text-fg-subtle">
            <Spinner />
          </div>
        )}
        {!term && <p className="py-10 text-center text-[14px] text-fg-muted">Search for people to message.</p>}
        {term && search.data?.users.length === 0 && <p className="py-10 text-center text-[14px] text-fg-muted">No account found.</p>}
        {search.data?.users.map((u) => {
          const selected = picked.some((p) => p.id === u.id)
          return (
            <button key={u.id} onClick={() => toggle(u)} className="flex w-full items-center gap-3 py-2 text-left">
              <Avatar user={u} size="md" />
              <span className="min-w-0 flex-1 leading-tight">
                <span className="block truncate text-[14px] font-semibold">{u.username}</span>
                <span className="block truncate text-[13px] text-fg-muted">{u.effectiveName}</span>
              </span>
              <span
                className={cn(
                  'grid size-6 place-items-center rounded-full border-2',
                  selected ? 'border-fg bg-fg text-surface' : 'border-line-strong',
                )}
              >
                {selected && <Check size={14} strokeWidth={3} />}
              </span>
            </button>
          )
        })}
      </div>

      <div className="border-t border-line p-4">
        <Button className="w-full" disabled={!canStart} loading={start.isPending} onClick={() => start.mutate()}>
          {group ? 'Create group' : 'Chat'}
        </Button>
      </div>
    </Dialog>
  )
}
