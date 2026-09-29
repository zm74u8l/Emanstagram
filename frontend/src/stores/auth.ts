import { create } from 'zustand'
import { onSessionChangedElsewhere, post, storedSessionUserId, tokenStore } from '@/lib/api'
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

// Every tab shares one stored session. If another tab signs in as someone
// else, this tab would keep showing the old account while its requests went
// out as the new one: following "someone else" became following yourself,
// and messages were sent from the wrong account. Reload so the tab shows who
// it really is. A token refresh keeps the same user, so it doesn't reload.
// While this tab is still checking its session, `user` is null and any
// stored session reloads it, so the check can't finish on the old account.
onSessionChangedElsewhere(() => {
  const shown = useAuth.getState().user?.id ?? null
  if (shown !== storedSessionUserId()) window.location.reload()
})
