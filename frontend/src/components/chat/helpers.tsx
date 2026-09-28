import { Avatar } from '@/components/ui/Avatar'
import { cn } from '@/lib/utils'
import type { Conversation, Member, Message } from '@/lib/types'

/** Everyone in the conversation except me. */
export function others(c: Conversation, myId: string): Member[] {
  return c.members.filter((m) => m.user.id !== myId)
}

export function conversationTitle(c: Conversation, myId: string): string {
  if (c.kind === 'GROUP') return c.title || others(c, myId).map((m) => m.user.username).join(', ') || 'Group'
  return others(c, myId)[0]?.user.username ?? 'Conversation'
}

/** One avatar for a DM; two overlapping for a group. */
export function ConversationAvatar({
  c,
  myId,
  size = 'md',
  online,
}: {
  c: Conversation
  myId: string
  size?: 'sm' | 'md' | 'lg'
  online?: boolean
}) {
  const rest = others(c, myId)
  if (c.kind === 'DIRECT' || rest.length < 2) {
    return <Avatar user={rest[0]?.user} size={size === 'lg' ? 'lg' : size} online={online} />
  }
  const box = size === 'lg' ? 'size-14' : size === 'sm' ? 'size-8' : 'size-12'
  const inner = size === 'lg' ? 'size-10' : size === 'sm' ? 'size-6' : 'size-9'
  return (
    <div className={cn('relative shrink-0', box)}>
      <Avatar user={rest[0].user} className={cn('absolute left-0 top-0', inner)} />
      <Avatar user={rest[1].user} className={cn('absolute bottom-0 right-0 rounded-full ring-2 ring-surface', inner)} />
    </div>
  )
}

/** The one-line inbox preview: "You: see you there", "Sent a photo", etc. */
export function previewText(m: Message | undefined, myId: string, group: boolean): string {
  if (!m) return 'Say hi'
  if (m.kind === 'SYSTEM') return m.body ?? ''
  if (m.deletedAt) return 'Message deleted'
  const who = m.sender?.id === myId ? 'You: ' : group && m.sender ? `${m.sender.username}: ` : ''
  if (m.kind === 'IMAGE') return `${who}${m.body || 'Sent a photo'}`
  if (m.kind === 'VIDEO') return `${who}${m.body || 'Sent a video'}`
  return `${who}${m.body ?? ''}`
}
