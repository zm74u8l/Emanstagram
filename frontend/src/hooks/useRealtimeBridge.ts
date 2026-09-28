import { useEffect } from 'react'
import { useQuery, useQueryClient, type InfiniteData, type QueryClient } from '@tanstack/react-query'
import { get } from '@/lib/api'
import { connectRealtime, onRealtime } from '@/lib/realtime'
import { useAuth } from '@/stores/auth'
import { useChat } from '@/stores/chat'
import type { AppNotification, Conversation, Message, Page, UserSummary } from '@/lib/types'

type MessagePages = InfiniteData<Page<Message>>

export const unreadKeys = {
  notifications: ['unread', 'notifications'] as const,
  messages: ['unread', 'messages'] as const,
}

/** Badge counts for the nav. The socket keeps them current; polling is only a slow backstop. */
export function useUnreadCounts() {
  const notifications = useQuery({
    queryKey: unreadKeys.notifications,
    queryFn: () => get<{ count: number }>('/api/notifications/unread-count'),
    refetchInterval: 120_000,
  })
  const messages = useQuery({
    queryKey: unreadKeys.messages,
    queryFn: () => get<{ count: number }>('/api/conversations/unread-count'),
    refetchInterval: 120_000,
  })
  return { notifications: notifications.data?.count ?? 0, messages: messages.data?.count ?? 0 }
}

/**
 * Opens the socket for the signed-in session and turns each server event
 * into a cache update, so any screen showing the affected data re-renders
 * without its own socket code.
 */
export function useRealtimeBridge() {
  const qc = useQueryClient()
  const me = useAuth((s) => s.user)

  useEffect(() => {
    if (!me) return
    connectRealtime()

    return onRealtime((event) => {
      const chat = useChat.getState()
      switch (event.type) {
        case 'notification': {
          const n = event.data as AppNotification
          qc.setQueryData<{ count: number }>(unreadKeys.notifications, (c) => ({ count: (c?.count ?? 0) + 1 }))
          qc.invalidateQueries({ queryKey: ['notifications'] })
          if (n.kind === 'FOLLOW') qc.invalidateQueries({ queryKey: ['profile', me.username] })
          break
        }
        case 'notifications.read':
          qc.setQueryData(unreadKeys.notifications, { count: 0 })
          break

        case 'message':
          onMessage(qc, event.data as Message, me.id, chat.activeConversationId)
          break
        case 'message.updated':
          replaceMessage(qc, event.data as Message)
          break
        case 'message.deleted': {
          const { conversationId, messageId } = event.data as { conversationId: string; messageId: string }
          patchMessages(qc, conversationId, (m) =>
            m.id === messageId ? { ...m, deletedAt: new Date().toISOString(), body: undefined, attachmentUrl: undefined } : m,
          )
          qc.invalidateQueries({ queryKey: ['conversations'] })
          break
        }
        case 'read': {
          const { conversationId, userId, lastReadAt } = event.data as {
            conversationId: string
            userId: string
            lastReadAt: string
          }
          patchConversation(qc, conversationId, (c) => ({
            ...c,
            unreadCount: userId === me.id ? 0 : c.unreadCount,
            members: c.members.map((m) => (m.user.id === userId ? { ...m, lastReadAt } : m)),
          }))
          if (userId === me.id) qc.invalidateQueries({ queryKey: unreadKeys.messages })
          break
        }
        case 'typing': {
          const { conversationId, user, typing } = event.data as {
            conversationId: string
            user: UserSummary
            typing: boolean
          }
          chat.setTyping(conversationId, user, typing)
          break
        }
        case 'presence': {
          const { userId, online, lastSeenAt } = event.data as { userId: string; online: boolean; lastSeenAt?: string }
          chat.setPresence(userId, online, lastSeenAt)
          break
        }
        case 'conversation.updated': {
          const c = event.data as Conversation
          qc.setQueryData(['conversation', c.id], (old: Conversation | undefined) => (old ? { ...old, ...c } : c))
          qc.invalidateQueries({ queryKey: ['conversations'] })
          break
        }
        case 'conversation.removed':
          qc.invalidateQueries({ queryKey: ['conversations'] })
          qc.invalidateQueries({ queryKey: ['conversation'] })
          break
        case 'reconnected':
          // Anything that happened while the socket was down.
          qc.invalidateQueries({ queryKey: ['conversations'] })
          qc.invalidateQueries({ queryKey: ['messages'] })
          qc.invalidateQueries({ queryKey: ['unread'] })
          break
      }
    })
  }, [me, qc])
}

function onMessage(qc: QueryClient, message: Message, myId: string, activeId: string | null) {
  // Append to the open thread, replacing the optimistic bubble if it's ours.
  qc.setQueryData<MessagePages>(['messages', message.conversationId], (data) => {
    if (!data) return data
    const exists = data.pages.some((p) =>
      p.items.some((m) => m.id === message.id || (message.clientId && m.clientId === message.clientId)),
    )
    if (exists) {
      return {
        ...data,
        pages: data.pages.map((p) => ({
          ...p,
          items: p.items.map((m) =>
            m.id === message.id || (message.clientId && m.clientId === message.clientId) ? message : m,
          ),
        })),
      }
    }
    const [first, ...rest] = data.pages
    return { ...data, pages: [{ ...first, items: [message, ...first.items] }, ...rest] }
  })

  const mine = message.sender?.id === myId
  const counts = !mine && message.kind !== 'SYSTEM' && activeId !== message.conversationId

  // Move the conversation to the top of the inbox with the new preview.
  const known = qc.getQueryData<Conversation[]>(['conversations'])
  if (known?.some((c) => c.id === message.conversationId)) {
    qc.setQueryData<Conversation[]>(['conversations'], (list) => {
      if (!list) return list
      const target = list.find((c) => c.id === message.conversationId)!
      const updated: Conversation = {
        ...target,
        lastMessage: message,
        lastMessageAt: message.createdAt,
        unreadCount: counts ? target.unreadCount + 1 : target.unreadCount,
      }
      return [updated, ...list.filter((c) => c.id !== message.conversationId)]
    })
  } else {
    qc.invalidateQueries({ queryKey: ['conversations'] })
  }
  if (counts) qc.invalidateQueries({ queryKey: unreadKeys.messages })
}

function replaceMessage(qc: QueryClient, message: Message) {
  patchMessages(qc, message.conversationId, (m) => (m.id === message.id ? message : m))
}

export function patchMessages(qc: QueryClient, conversationId: string, fn: (m: Message) => Message) {
  qc.setQueryData<MessagePages>(['messages', conversationId], (data) =>
    data ? { ...data, pages: data.pages.map((p) => ({ ...p, items: p.items.map(fn) })) } : data,
  )
}

function patchConversation(qc: QueryClient, id: string, fn: (c: Conversation) => Conversation) {
  qc.setQueryData<Conversation[]>(['conversations'], (list) => list?.map((c) => (c.id === id ? fn(c) : c)))
  qc.setQueryData<Conversation>(['conversation', id], (c) => (c ? fn(c) : c))
}
