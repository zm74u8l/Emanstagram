import { create } from 'zustand'
import { post, tokenStore } from '@/lib/api'
import { disconnectRealtime } from '@/lib/realtime'
import type { User } from '@/lib/types'

interface AuthState {
  user: User | null
  /** True until the stored token has been validated against the API. */
  initialising: boolean
  setUser: (user: User | null) => void
  /** Merges fields into the signed-in user, e.g. after editing the profile. */
  patchUser: (patch: Partial<User>) => void
  applyTokens: (access: string, refresh: string, user: User) => void
  logout: () => Promise<void>
}

export const useAuth = create<AuthState>((set, get) => ({
  user: null,
  initialising: true,

  setUser: (user) => set({ user, initialising: false }),

  patchUser: (patch) => {
    const current = get().user
    if (current) set({ user: { ...current, ...patch } })
  },

  applyTokens: (access, refresh, user) => {
    tokenStore.set(access, refresh)
    set({ user, initialising: false })
  },

  logout: async () => {
    const refresh = tokenStore.refresh
    tokenStore.clear()
    disconnectRealtime()
    set({ user: null, initialising: false })
    // Fire-and-forget: the local session is already gone, so a failure here
    // must not trap the user on a spinner.
    if (refresh) {
      void post('/api/auth/logout', { refreshToken: refresh }).catch(() => {})
    }
  },
}))
