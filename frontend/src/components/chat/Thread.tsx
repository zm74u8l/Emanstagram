import { Fragment, useEffect, useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient, type InfiniteData } from '@tanstack/react-query'
import { ArrowLeft, Image as ImageIcon, Info, Pencil, Reply, Trash2, X } from 'lucide-react'
import { ApiError, del, errorMessage, get, patch, post, upload } from '@/lib/api'
import { cursorPaging, flatten, withCursor } from '@/lib/cache'
import { publish } from '@/lib/realtime'
import { prepareImage, prepareVideo } from '@/lib/media'
import { cn, formatLastSeen, formatStamp } from '@/lib/utils'
import { toastError } from '@/stores/toast'
import { useAuth } from '@/stores/auth'
import { useChat } from '@/stores/chat'
import { patchMessages } from '@/hooks/useRealtimeBridge'
import { useDebounced } from '@/hooks/useDebounced'
import { Avatar } from '@/components/ui/Avatar'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog, Dialog } from '@/components/ui/Dialog'
import { EmptyState } from '@/components/ui/bits'
import { Spinner } from '@/components/ui/Spinner'
import { InfiniteSentinel } from '@/components/InfiniteSentinel'
import { UserRow } from '@/components/social'
import { ConversationAvatar, conversationTitle, others } from './helpers'
import type { Conversation, Message, Page, Presence, SearchResponse, UserSummary } from '@/lib/types'

type MessagePages = InfiniteData<Page<Message>>

const GROUP_GAP_MS = 5 * 60_000
const SEPARATOR_GAP_MS = 60 * 60_000

export function Thread({ conversationId }: { conversationId: string }) {
  const me = useAuth((s) => s.user)!
  const qc = useQueryClient()
  const navigate = useNavigate()
  const setActive = useChat((s) => s.setActive)
  const setPresence = useChat((s) => s.setPresence)
  const presence = useChat((s) => s.presence)
  const typing = useChat((s) => s.typing[conversationId])
  const [info, setInfo] = useState(false)
  const [replyTo, setReplyTo] = useState<Message | null>(null)
  const [editing, setEditing] = useState<Message | null>(null)
  const [scroller, setScroller] = useState<HTMLDivElement | null>(null)

  const conversation = useQuery({
    queryKey: ['conversation', conversationId],
    queryFn: () => get<Conversation>(`/api/conversations/${conversationId}`),
  })
  const history = useInfiniteQuery({
    queryKey: ['messages', conversationId],
    queryFn: ({ pageParam }) => get<Page<Message>>(withCursor(`/api/conversations/${conversationId}/messages`, pageParam)),
    ...cursorPaging,
  })
  // Newest first, as the server pages them; the list renders bottom-up.
  const messages = flatten(history.data)

  useEffect(() => {
    setActive(conversationId)
    return () => setActive(null)
  }, [conversationId, setActive])

  const c = conversation.data
  const partner = c?.kind === 'DIRECT' ? others(c, me.id)[0] : undefined

  // Presence for the person on the other end of a DM.
  useEffect(() => {
    if (!partner) return
    get<Presence[]>(`/api/presence?ids=${partner.user.id}`)
      .then((list) => list.forEach((p) => setPresence(p.userId, p.online, p.lastSeenAt)))
      .catch(() => {})
  }, [partner?.user.id, setPresence]) // eslint-disable-line react-hooks/exhaustive-deps

  // Mark read on open, and whenever someone else's message lands while the tab is visible.
  const newest = messages[0]
  const newestFromOthers = newest && newest.sender?.id !== me.id ? newest.id : null
  useEffect(() => {
    if (!c || document.visibilityState !== 'visible') return
    void post(`/api/conversations/${conversationId}/read`).catch(() => {})
  }, [c?.id, newestFromOthers, conversationId]) // eslint-disable-line react-hooks/exhaustive-deps

  if (conversation.error) {
    return (
      <EmptyState
        title="This chat isn’t available"
        body={conversation.error instanceof ApiError && conversation.error.status === 404 ? 'You may have left it, or it was deleted.' : errorMessage(conversation.error)}
        action={<Link to="/messages" className="text-[14px] font-semibold text-accent">Back to messages</Link>}
        className="h-full justify-center"
      />
    )
  }
  if (!c) {
    return (
      <div className="grid h-full place-items-center text-fg-subtle">
        <Spinner />
      </div>
    )
  }

  const partnerPresence = partner ? presence[partner.user.id] : undefined
  const online = partner ? partnerPresence?.online ?? partner.online : false
  const subtitle =
    c.kind === 'GROUP'
      ? `${c.members.length} members`
      : online
        ? 'Active now'
        : formatLastSeen(partnerPresence?.lastSeenAt)
  const typers = Object.values(typing ?? {}).map((t) => t.user)

  return (
    <div className="flex h-dvh flex-col md:h-full">
      <header className="flex h-[60px] shrink-0 items-center gap-3 border-b border-line px-3 md:h-[75px] md:px-5">
        <button onClick={() => navigate('/messages')} aria-label="Back" className="grid size-9 place-items-center md:hidden">
          <ArrowLeft size={24} />
        </button>
        {partner ? (
          <Link to={`/u/${partner.user.username}`} className="flex min-w-0 items-center gap-3">
            <ConversationAvatar c={c} myId={me.id} online={online} />
            <HeaderText title={conversationTitle(c, me.id)} subtitle={subtitle} />
          </Link>
        ) : (
          <button onClick={() => setInfo(true)} className="flex min-w-0 items-center gap-3 text-left">
            <ConversationAvatar c={c} myId={me.id} />
            <HeaderText title={conversationTitle(c, me.id)} subtitle={subtitle} />
          </button>
        )}
        <button onClick={() => setInfo(true)} aria-label="Conversation details" className="ml-auto grid size-10 place-items-center rounded-full hover:bg-surface-muted">
          <Info size={24} />
        </button>
      </header>

      <div ref={setScroller} className="flex min-h-0 flex-1 flex-col-reverse overflow-y-auto px-3 py-3 scrollbar-thin md:px-5">
        {typers.length > 0 && <TypingBubble users={typers} group={c.kind === 'GROUP'} />}

        {messages.map((m, i) => {
          const older = messages[i + 1]
          const newer = messages[i - 1]
          return (
            <Fragment key={m.clientId ?? m.id}>
              <MessageRow
                message={m}
                conversation={c}
                meId={me.id}
                joinsOlder={continues(older, m)}
                joinsNewer={continues(m, newer)}
                isNewest={i === 0}
                onReply={() => {
                  setEditing(null)
                  setReplyTo(m)
                }}
                onEdit={() => {
                  setReplyTo(null)
                  setEditing(m)
                }}
              />
              {(!older || new Date(m.createdAt).getTime() - new Date(older.createdAt).getTime() > SEPARATOR_GAP_MS) &&
                m.kind !== 'SYSTEM' && (
                  <p className="py-4 text-center text-[12px] font-medium text-fg-subtle">{formatStamp(m.createdAt)}</p>
                )}
            </Fragment>
          )
        })}

        <InfiniteSentinel
          hasMore={!!history.hasNextPage}
          loading={history.isFetchingNextPage}
          onLoadMore={() => history.fetchNextPage()}
          root={scroller}
          rootMargin="300px"
        />

        {history.isSuccess && !history.hasNextPage && (
          <div className="flex flex-col items-center pb-6 pt-10 text-center">
            <ConversationAvatar c={c} myId={me.id} size="lg" />
            <p className="mt-3 text-[18px] font-semibold">{conversationTitle(c, me.id)}</p>
            {partner && (
              <Link to={`/u/${partner.user.username}`} className="mt-3">
                <Button variant="secondary" size="sm">View profile</Button>
              </Link>
            )}
          </div>
        )}
        {history.isLoading && (
          <div className="grid flex-1 place-items-center text-fg-subtle">
            <Spinner />
          </div>
        )}
      </div>

      <Composer
        conversationId={conversationId}
        me={me}
        replyTo={replyTo}
        editing={editing}
        onDone={() => {
          setReplyTo(null)
          setEditing(null)
        }}
        qcKey={['messages', conversationId]}
        onSent={() => qc.invalidateQueries({ queryKey: ['conversations'] })}
      />

      <InfoDialog open={info} onClose={() => setInfo(false)} conversation={c} />
    </div>
  )
}

function HeaderText({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <div className="min-w-0 leading-tight">
      <p className="truncate text-[16px] font-semibold">{title}</p>
      {subtitle && <p className="truncate text-[12px] text-fg-muted">{subtitle}</p>}
    </div>
  )
}

/** Whether {@code b} directly continues {@code a}: same sender, close in time. */
function continues(a?: Message, b?: Message): boolean {
  if (!a || !b || a.kind === 'SYSTEM' || b.kind === 'SYSTEM') return false
  if (a.sender?.id !== b.sender?.id) return false
  return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime() < GROUP_GAP_MS
}

// ---------------------------------------------------------------------

function MessageRow({
  message: m,
  conversation,
  meId,
  joinsOlder,
  joinsNewer,
  isNewest,
  onReply,
  onEdit,
}: {
  message: Message
  conversation: Conversation
  meId: string
  joinsOlder: boolean
  joinsNewer: boolean
  isNewest: boolean
  onReply: () => void
  onEdit: () => void
}) {
  const qc = useQueryClient()
  const [confirm, setConfirm] = useState(false)
  const [showActions, setShowActions] = useState(false)
  const mine = m.sender?.id === meId

  const remove = useMutation({
    mutationFn: () => del(`/api/messages/${m.id}`),
    onSuccess: () =>
      patchMessages(qc, m.conversationId, (x) =>
        x.id === m.id ? { ...x, deletedAt: new Date().toISOString(), body: undefined, attachmentUrl: undefined } : x,
      ),
    onError: (err) => toastError(errorMessage(err)),
  })

  if (m.kind === 'SYSTEM') {
    return <p className="py-2 text-center text-[12px] text-fg-muted">{m.body}</p>
  }

  // "Seen" under my latest message, when it's the newest in the thread.
  let receipt: string | null = null
  if (mine && isNewest && !m.pending && !m.failed) {
    const readers = others(conversation, meId).filter((o) => o.lastReadAt && o.lastReadAt >= m.createdAt)
    if (conversation.kind === 'DIRECT') receipt = readers.length ? 'Seen' : 'Sent'
    else receipt = readers.length ? `Seen by ${readers.length}` : 'Sent'
  }

  const deleted = !!m.deletedAt
  const round = mine
    ? cn('rounded-[20px]', joinsOlder && 'rounded-tr-[6px]', joinsNewer && 'rounded-br-[6px]')
    : cn('rounded-[20px]', joinsOlder && 'rounded-tl-[6px]', joinsNewer && 'rounded-bl-[6px]')

  return (
    <div className={cn('group flex flex-col', mine ? 'items-end' : 'items-start', joinsNewer ? 'mt-0.5' : 'mt-2.5')}>
      {!mine && conversation.kind === 'GROUP' && !joinsOlder && m.sender && (
        <span className="mb-0.5 ml-11 text-[12px] text-fg-muted">{m.sender.username}</span>
      )}

      {m.replyTo && (
        <div className={cn('mb-0.5 max-w-[70%] text-[12px] text-fg-muted', mine ? 'mr-1 text-right' : 'ml-11')}>
          <p className="mb-0.5">
            <Reply size={11} className="mr-1 inline" />
            {mine ? 'You replied' : 'Replied'} to {m.replyTo.senderUsername ?? 'a message'}
          </p>
          <p className="line-clamp-2 rounded-[14px] bg-surface-muted/70 px-3 py-1.5 text-fg-muted">
            {m.replyTo.deleted ? 'Message deleted' : m.replyTo.body ?? (m.replyTo.kind === 'IMAGE' ? 'Photo' : 'Video')}
          </p>
        </div>
      )}

      <div className={cn('flex max-w-[85%] items-end gap-2 md:max-w-[70%]', mine && 'flex-row-reverse')}>
        {!mine && (
          <div className="w-7 shrink-0">{!joinsNewer && <Avatar user={m.sender} size="xs" className="size-7" />}</div>
        )}

        <div onClick={() => setShowActions((s) => !s)} className={cn('min-w-0', m.pending && 'opacity-60')}>
          {deleted ? (
            <p className={cn(round, 'border border-line-strong px-3.5 py-2 text-[14px] italic text-fg-muted')}>Message deleted</p>
          ) : (
            <>
              {m.attachmentUrl && (
                <a href={m.pending ? undefined : m.attachmentUrl} target="_blank" rel="noreferrer" className="block">
                  {m.kind === 'VIDEO' ? (
                    <video src={m.attachmentUrl} controls playsInline className="max-h-80 max-w-[260px] rounded-[16px] bg-black" />
                  ) : (
                    <img src={m.attachmentUrl} alt="" className="max-h-80 max-w-[260px] rounded-[16px] object-cover" />
                  )}
                </a>
              )}
              {m.body && (
                <p
                  className={cn(
                    round,
                    'whitespace-pre-wrap break-words px-3.5 py-2 text-[15px] leading-snug',
                    m.attachmentUrl && 'mt-0.5',
                    mine ? 'bg-surface-inverse text-fg-inverse' : 'bg-surface-muted',
                  )}
                >
                  {m.body}
                </p>
              )}
            </>
          )}
        </div>

        {!deleted && !m.pending && (
          <div
            className={cn(
              'flex shrink-0 items-center text-fg-muted transition-opacity md:opacity-0 md:group-hover:opacity-100',
              showActions ? 'max-md:flex' : 'max-md:hidden',
            )}
          >
            <button onClick={onReply} aria-label="Reply" className="grid size-7 place-items-center rounded-full hover:bg-surface-muted hover:text-fg">
              <Reply size={15} />
            </button>
            {mine && m.kind === 'TEXT' && (
              <button onClick={onEdit} aria-label="Edit" className="grid size-7 place-items-center rounded-full hover:bg-surface-muted hover:text-fg">
                <Pencil size={14} />
              </button>
            )}
            {mine && (
              <button onClick={() => setConfirm(true)} aria-label="Delete" className="grid size-7 place-items-center rounded-full hover:bg-surface-muted hover:text-danger">
                <Trash2 size={14} />
              </button>
            )}
          </div>
        )}
      </div>

      {(m.editedAt || m.failed || receipt) && (
        <p className={cn('mt-0.5 text-[11px]', mine ? 'mr-1' : 'ml-11', m.failed ? 'text-danger' : 'text-fg-subtle')}>
          {m.failed ? 'Not delivered' : [m.editedAt && !deleted ? 'Edited' : null, receipt].filter(Boolean).join(' · ')}
        </p>
      )}

      <ConfirmDialog
        open={confirm}
        onClose={() => setConfirm(false)}
        onConfirm={() => {
          setConfirm(false)
          remove.mutate()
        }}
        title="Delete message?"
        body="It will be removed for everyone in this chat."
        confirmLabel="Delete"
      />
    </div>
  )
}

function TypingBubble({ users, group }: { users: UserSummary[]; group: boolean }) {
  return (
    <div className="mt-2.5 flex items-end gap-2">
      <Avatar user={users[0]} size="xs" className="size-7" />
      <div>
        {group && <p className="mb-0.5 text-[12px] text-fg-muted">{users.map((u) => u.username).join(', ')}</p>}
        <div className="flex gap-1 rounded-[20px] bg-surface-muted px-4 py-3" aria-label="typing">
          {[0, 1, 2].map((i) => (
            <span key={i} className="size-1.5 animate-typing rounded-full bg-fg-muted" style={{ animationDelay: `${i * 0.15}s` }} />
          ))}
        </div>
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------

function Composer({
  conversationId,
  me,
  replyTo,
  editing,
  onDone,
  qcKey,
  onSent,
}: {
  conversationId: string
  me: { id: string; username: string; effectiveName: string; avatarUrl?: string | null; verified: boolean; displayName?: string | null }
  replyTo: Message | null
  editing: Message | null
  onDone: () => void
  qcKey: unknown[]
  onSent: () => void
}) {
  const qc = useQueryClient()
  const [text, setText] = useState('')
  const textarea = useRef<HTMLTextAreaElement>(null)
  const file = useRef<HTMLInputElement>(null)
  const typingSent = useRef(0)
  const stopTimer = useRef<number>(0)

  const meSummary: UserSummary = useMemo(
    () => ({ id: me.id, username: me.username, effectiveName: me.effectiveName, avatarUrl: me.avatarUrl, verified: me.verified, displayName: me.displayName }),
    [me],
  )

  useEffect(() => {
    if (editing) setText(editing.body ?? '')
    if (editing || replyTo) textarea.current?.focus()
  }, [editing, replyTo])

  // Grow with the text, up to about five lines.
  useEffect(() => {
    const el = textarea.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 120)}px`
  }, [text])

  // Typing indicators: at most one "typing" every 2.5s, and "stopped" after 3s idle.
  function signalTyping() {
    const now = Date.now()
    if (now - typingSent.current > 2500) {
      publish(`/app/conversations/${conversationId}/typing`, { typing: true })
      typingSent.current = now
    }
    window.clearTimeout(stopTimer.current)
    stopTimer.current = window.setTimeout(stopTyping, 3000)
  }
  function stopTyping() {
    window.clearTimeout(stopTimer.current)
    if (typingSent.current) {
      publish(`/app/conversations/${conversationId}/typing`, { typing: false })
      typingSent.current = 0
    }
  }
  useEffect(() => stopTyping, []) // eslint-disable-line react-hooks/exhaustive-deps

  function insertOptimistic(m: Message) {
    qc.setQueryData<MessagePages>(qcKey, (data) => {
      if (!data) return { pages: [{ items: [m] }], pageParams: [undefined] }
      const [first, ...rest] = data.pages
      return { ...data, pages: [{ ...first, items: [m, ...first.items] }, ...rest] }
    })
  }

  function settle(clientId: string, result: Message | null) {
    qc.setQueryData<MessagePages>(qcKey, (data) =>
      data && {
        ...data,
        pages: data.pages.map((p) => ({
          ...p,
          items: p.items.map((x) => (x.clientId === clientId ? (result ? { ...result, clientId } : { ...x, pending: false, failed: true }) : x)),
        })),
      },
    )
  }

  const edit = useMutation({
    mutationFn: ({ id, body }: { id: string; body: string }) => patch<Message>(`/api/messages/${id}`, { body }),
    onSuccess: (updated) => patchMessages(qc, conversationId, (x) => (x.id === updated.id ? updated : x)),
    onError: (err) => toastError(errorMessage(err)),
  })

  function replyPreview(m: Message | null) {
    return m ? { id: m.id, senderUsername: m.sender?.username, kind: m.kind, body: m.body, deleted: false } : undefined
  }

  async function send(e?: FormEvent) {
    e?.preventDefault()
    const body = text.trim()
    if (!body) return
    stopTyping()

    if (editing) {
      edit.mutate({ id: editing.id, body })
      setText('')
      onDone()
      return
    }

    const clientId = crypto.randomUUID()
    insertOptimistic({
      id: `tmp-${clientId}`,
      clientId,
      conversationId,
      sender: meSummary,
      kind: 'TEXT',
      body,
      replyTo: replyPreview(replyTo),
      createdAt: new Date().toISOString(),
      pending: true,
    })
    const replyToId = replyTo?.id
    setText('')
    onDone()
    try {
      const saved = await post<Message>(`/api/conversations/${conversationId}/messages`, { body, replyToId, clientId })
      settle(clientId, saved)
      onSent()
    } catch (err) {
      settle(clientId, null)
      toastError(errorMessage(err))
    }
  }

  async function sendFile(picked?: File) {
    if (!picked) return
    const clientId = crypto.randomUUID()
    try {
      const video = picked.type.startsWith('video/')
      const prepared = video ? await prepareVideo(picked) : await prepareImage(picked)
      const caption = text.trim()
      insertOptimistic({
        id: `tmp-${clientId}`,
        clientId,
        conversationId,
        sender: meSummary,
        kind: video ? 'VIDEO' : 'IMAGE',
        body: caption || undefined,
        attachmentUrl: prepared.previewUrl,
        replyTo: replyPreview(replyTo),
        createdAt: new Date().toISOString(),
        pending: true,
      })
      const form = new FormData()
      form.append('file', prepared.file)
      form.append('clientId', clientId)
      if (caption) form.append('body', caption)
      if (replyTo) form.append('replyToId', replyTo.id)
      setText('')
      onDone()
      const saved = await upload<Message>(`/api/conversations/${conversationId}/attachments`, form)
      settle(clientId, saved)
      onSent()
    } catch (err) {
      settle(clientId, null)
      toastError(errorMessage(err))
    }
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    // Enter sends; Shift+Enter makes a new line. Not while an IME is composing.
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      void send()
    }
    if (e.key === 'Escape' && (editing || replyTo)) {
      setText('')
      onDone()
    }
  }

  return (
    <div className="shrink-0 px-3 pb-safe [--pb-base:12px] md:px-5 md:[--pb-base:20px]">
      {(replyTo || editing) && (
        <div className="flex items-center justify-between rounded-t-[16px] border border-b-0 border-line px-4 py-2 text-[13px]">
          <span className="min-w-0 truncate text-fg-muted">
            {editing ? (
              'Editing message'
            ) : (
              <>
                Replying to <span className="font-semibold text-fg">{replyTo!.sender?.id === me.id ? 'yourself' : replyTo!.sender?.username}</span>
                {replyTo!.body && <span>: {replyTo!.body}</span>}
              </>
            )}
          </span>
          <button
            onClick={() => {
              if (editing) setText('')
              onDone()
            }}
            aria-label="Cancel"
            className="ml-2 text-fg-muted hover:text-fg"
          >
            <X size={16} />
          </button>
        </div>
      )}
      <form
        onSubmit={send}
        className={cn(
          'flex items-end gap-2 rounded-[24px] border border-line-strong px-2 py-1.5',
          (replyTo || editing) && 'rounded-t-none',
        )}
      >
        <button type="button" onClick={() => file.current?.click()} aria-label="Send a photo or video" className="grid size-9 shrink-0 place-items-center rounded-full hover:bg-surface-muted">
          <ImageIcon size={22} />
        </button>
        <input ref={file} type="file" accept="image/*,video/mp4,video/webm,video/quicktime" className="hidden" onChange={(e) => {
          void sendFile(e.target.files?.[0])
          e.target.value = ''
        }} />
        <textarea
          ref={textarea}
          value={text}
          onChange={(e) => {
            setText(e.target.value)
            if (e.target.value) signalTyping()
          }}
          onKeyDown={onKeyDown}
          rows={1}
          maxLength={4000}
          placeholder="Message…"
          aria-label="Message"
          className="max-h-[120px] min-h-9 flex-1 resize-none bg-transparent py-1.5 text-[15px] leading-snug outline-none placeholder:text-fg-subtle"
        />
        {text.trim() && (
          <button type="submit" className="h-9 shrink-0 px-3 text-[14px] font-semibold text-accent hover:opacity-70">
            {editing ? 'Save' : 'Send'}
          </button>
        )}
      </form>
    </div>
  )
}

// ---------------------------------------------------------------------

function InfoDialog({ open, onClose, conversation: c }: { open: boolean; onClose: () => void; conversation: Conversation }) {
  const me = useAuth((s) => s.user)!
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [title, setTitle] = useState(c.title ?? '')
  const [adding, setAdding] = useState(false)
  const [q, setQ] = useState('')
  const [confirmLeave, setConfirmLeave] = useState(false)
  const term = useDebounced(q.trim(), 250)

  const myRole = c.members.find((m) => m.user.id === me.id)?.role
  const canManage = myRole === 'OWNER' || myRole === 'ADMIN'
  const group = c.kind === 'GROUP'

  const refresh = (updated?: Conversation) => {
    if (updated) qc.setQueryData(['conversation', c.id], updated)
    qc.invalidateQueries({ queryKey: ['conversations'] })
  }

  const rename = useMutation({
    mutationFn: () => patch<Conversation>(`/api/conversations/${c.id}`, { title: title.trim() }),
    onSuccess: refresh,
    onError: (err) => toastError(errorMessage(err)),
  })
  const add = useMutation({
    mutationFn: (userId: string) => post<Conversation>(`/api/conversations/${c.id}/members`, { userIds: [userId] }),
    onSuccess: (updated) => {
      refresh(updated)
      setQ('')
      setAdding(false)
    },
    onError: (err) => toastError(errorMessage(err)),
  })
  const removeMember = useMutation({
    mutationFn: (userId: string) => del(`/api/conversations/${c.id}/members/${userId}`),
    onSuccess: (_, userId) => {
      if (userId === me.id) {
        onClose()
        qc.invalidateQueries({ queryKey: ['conversations'] })
        navigate('/messages')
      } else {
        qc.invalidateQueries({ queryKey: ['conversation', c.id] })
      }
    },
    onError: (err) => toastError(errorMessage(err)),
  })

  const search = useQuery({
    queryKey: ['search', term],
    queryFn: () => get<SearchResponse>(`/api/search?q=${encodeURIComponent(term)}`),
    enabled: adding && term.length > 0,
  })
  const memberIds = new Set(c.members.map((m) => m.user.id))

  return (
    <Dialog open={open} onClose={onClose} title="Details">
      <div className="max-h-[70dvh] overflow-y-auto px-5 py-4 scrollbar-thin">
        {group && (
          <form
            onSubmit={(e) => {
              e.preventDefault()
              if (title.trim() && title.trim() !== c.title) rename.mutate()
            }}
            className="mb-5 flex items-end gap-2"
          >
            <label className="min-w-0 flex-1">
              <span className="mb-1 block text-[13px] font-medium">Group name</span>
              <input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                maxLength={120}
                className="h-10 w-full rounded-[var(--radius-control)] border border-line-strong bg-surface-elevated px-3 text-[14px] outline-none focus:border-fg-muted"
              />
            </label>
            <Button type="submit" variant="secondary" disabled={!title.trim() || title.trim() === c.title} loading={rename.isPending}>
              Save
            </Button>
          </form>
        )}

        <div className="flex items-center justify-between">
          <h3 className="text-[14px] font-semibold">Members</h3>
          {group && (
            <button onClick={() => setAdding((a) => !a)} className="text-[13px] font-semibold text-accent">
              {adding ? 'Done' : 'Add people'}
            </button>
          )}
        </div>

        {adding && (
          <div className="mt-2">
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search people"
              autoFocus
              className="h-10 w-full rounded-[var(--radius-control)] bg-surface-muted px-3 text-[14px] outline-none"
            />
            {search.data?.users
              .filter((u) => !memberIds.has(u.id))
              .map((u) => (
                <UserRow
                  key={u.id}
                  user={u}
                  trailing={
                    <Button size="sm" variant="secondary" loading={add.isPending && add.variables === u.id} onClick={() => add.mutate(u.id)}>
                      Add
                    </Button>
                  }
                />
              ))}
          </div>
        )}

        <div className="mt-1">
          {c.members.map((m) => (
            <UserRow
              key={m.user.id}
              user={m.user}
              onNavigate={onClose}
              subtitle={m.role === 'OWNER' ? 'Owner' : m.role === 'ADMIN' ? 'Admin' : undefined}
              trailing={
                group && canManage && m.user.id !== me.id ? (
                  <Button size="sm" variant="ghost" onClick={() => removeMember.mutate(m.user.id)}>
                    Remove
                  </Button>
                ) : undefined
              }
            />
          ))}
        </div>

        {group && (
          <button onClick={() => setConfirmLeave(true)} className="mt-5 w-full border-t border-line pt-4 text-left text-[14px] font-semibold text-danger">
            Leave group
          </button>
        )}
      </div>

      <ConfirmDialog
        open={confirmLeave}
        onClose={() => setConfirmLeave(false)}
        onConfirm={() => removeMember.mutate(me.id)}
        busy={removeMember.isPending}
        title="Leave this group?"
        body="You won’t get new messages unless someone adds you back."
        confirmLabel="Leave"
      />
    </Dialog>
  )
}
