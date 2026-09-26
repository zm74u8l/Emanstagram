import { useEffect } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

import AppLayout from '@/layouts/AppLayout'
import Login from '@/pages/Login'
import Register from '@/pages/Register'
import Feed from '@/pages/Feed'
import Explore from '@/pages/Explore'
import Messages from '@/pages/Messages'
import Notifications from '@/pages/Notifications'
import CreatePost from '@/pages/CreatePost'
import Settings from '@/pages/Settings'
import Profile from '@/pages/Profile'
import NotFound from '@/pages/NotFound'

import { get, tokenStore } from '@/lib/api'
import { useAuth } from '@/stores/auth'
import { applyAccent, applyTheme, useHydratePreferences } from '@/hooks/useTheme'
import type { User } from '@/lib/types'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        // Never retry an auth or validation failure; it will never succeed.
        const status = (error as { status?: number })?.status
        if (status === 401 || status === 403 || status === 404) return false
        return failureCount < 2
      },
    },
  },
})

/** Blocks a route until the initial session check has finished. */
function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user, initialising } = useAuth()
  const location = useLocation()

  if (initialising) {
    return (
      <div className="min-h-dvh grid place-items-center bg-surface">
        <span
          className="size-8 animate-spin rounded-full border-2 border-accent border-r-transparent"
          role="status"
          aria-label="Loading"
        />
      </div>
    )
  }

  if (!user) {
    // Remember where they were headed so login can return them there.
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return <>{children}</>
}

function RedirectIfAuthed({ children }: { children: React.ReactNode }) {
  const { user, initialising } = useAuth()
  if (!initialising && user) return <Navigate to="/" replace />
  return <>{children}</>
}

function Placeholder({ title }: { title: string }) {
  return (
    <div className="grid min-h-[60dvh] place-items-center px-4">
      <div className="text-center">
        <h1 className="text-lg font-semibold">{title}</h1>
        <p className="mt-1 text-sm text-fg-muted">
          Coming in the next build.
        </p>
      </div>
    </div>
  )
}

/** Validates the stored token once, on boot. */
function useSessionBootstrap() {
  const setUser = useAuth((s) => s.setUser)
  const user = useAuth((s) => s.user)
  const initialising = useAuth((s) => s.initialising)

  useEffect(() => {
    let cancelled = false

    async function bootstrap() {
      if (!tokenStore.refresh && !tokenStore.access) {
        useAuth.setState({ initialising: false })
        return
      }
      try {
        // The api() layer transparently refreshes an expired access token.
        const me = await get<User>('/api/auth/me')
        if (!cancelled) setUser(me)
      } catch {
        if (!cancelled) useAuth.setState({ user: null, initialising: false })
      }
    }

    if (!user && initialising) void bootstrap()
    return () => {
      cancelled = true
    }
  }, [user, initialising, setUser])
}

export default function App() {
  useHydratePreferences()
  useSessionBootstrap()

  // Reflect the signed-in user's saved theme and accent colour.
  const user = useAuth((s) => s.user)
  useEffect(() => {
    if (!user) return
    const theme = user.theme.toLowerCase()
    if (theme === 'light' || theme === 'dark' || theme === 'system') {
      applyTheme(theme)
    }
    applyAccent(user.accentColor)
  }, [user])

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route
            path="/login"
            element={
              <RedirectIfAuthed>
                <Login />
              </RedirectIfAuthed>
            }
          />
          <Route
            path="/register"
            element={
              <RedirectIfAuthed>
                <Register />
              </RedirectIfAuthed>
            }
          />

          <Route
            element={
              <RequireAuth>
                <AppLayout />
              </RequireAuth>
            }
          >
            <Route index element={<Feed />} />
            <Route path="explore" element={<Explore />} />
            <Route path="messages" element={<Messages />} />
            <Route path="messages/:conversationId" element={<Messages />} />
            <Route path="notifications" element={<Notifications />} />
            <Route path="create" element={<CreatePost />} />
            <Route path="settings" element={<Settings />} />
            <Route path="u/:username" element={<Profile />} />
            <Route path="p/:postId" element={<Placeholder title="Post" />} />
          </Route>

          <Route path="*" element={<NotFound />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
