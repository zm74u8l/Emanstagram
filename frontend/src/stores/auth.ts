import { create } from 'zustand'
import { post, tokenStore } from '@/lib/api'
import type { User } from '@/lib/types'

interface AuthState {
  user: User | null
  /** True until the stored token has been validated against the API. */
  initialising: boolean
  setUser: (user: User | null) => void
  applyTokens: (access: string, refresh: string, user: User) => void
  logout: () => Promise<void>
}

export const useAuth = create<AuthState>((set) => ({
  user: null,
  initialising: true,

  setUser: (user) => set({ user }),

  applyTokens: (access, refresh, user) => {
    tokenStore.set(access, refresh)
    set({ user, initialising: false })
  },

  logout: async () => {
    const refresh = tokenStore.refresh
    tokenStore.clear()
    set({ user: null, initialising: false })
    // Fire-and-forget: the local session is already gone, so a failure here
    // must not trap the user on a spinner.
    if (refresh) {
      void post('/api/auth/logout', { refreshToken: refresh }).catch(() => {})
    }
  },
}))

/** Non-reactive token read for the API layer. */
export const isAuthenticated = () => tokenStore.access !== null
