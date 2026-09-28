import { create } from 'zustand'
import type { UserSummary } from '@/lib/types'

interface TypingEntry {
  user: UserSummary
  until: number
}

interface ChatState {
  /** conversationId -> userId -> who is typing, and until when. */
  typing: Record<string, Record<string, TypingEntry>>
  /** userId -> live presence, fed by the socket. */
  presence: Record<string, { online: boolean; lastSeenAt?: string }>
  /** The thread on screen, so incoming messages there don't count as unread. */
  activeConversationId: string | null
  setTyping: (conversationId: string, user: UserSummary, typing: boolean) => void
  setPresence: (userId: string, online: boolean, lastSeenAt?: string) => void
  setActive: (conversationId: string | null) => void
}

/** A typing indicator lapses on its own if the "stopped" event is lost. */
const TYPING_TTL_MS = 6000

export const useChat = create<ChatState>((set, get) => ({
  typing: {},
  presence: {},
  activeConversationId: null,

  setTyping: (conversationId, user, typing) => {
    const current = { ...(get().typing[conversationId] ?? {}) }
    if (typing) current[user.id] = { user, until: Date.now() + TYPING_TTL_MS }
    else delete current[user.id]
    set({ typing: { ...get().typing, [conversationId]: current } })
    if (typing) {
      window.setTimeout(() => {
        const entry = get().typing[conversationId]?.[user.id]
        if (entry && entry.until <= Date.now()) get().setTyping(conversationId, user, false)
      }, TYPING_TTL_MS + 50)
    }
  },

  setPresence: (userId, online, lastSeenAt) =>
    set({ presence: { ...get().presence, [userId]: { online, lastSeenAt } } }),

  setActive: (conversationId) => set({ activeConversationId: conversationId }),
}))
