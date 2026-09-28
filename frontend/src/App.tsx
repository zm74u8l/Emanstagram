import { lazy, Suspense, useEffect } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

import AppLayout from '@/layouts/AppLayout'
import Login from '@/pages/Login'
import Register from '@/pages/Register'
import Feed from '@/pages/Feed'
import NotFound from '@/pages/NotFound'
import { Toaster } from '@/components/ui/bits'
import { PageSpinner } from '@/components/ui/Spinner'

import { get, tokenStore } from '@/lib/api'
import { useAuth } from '@/stores/auth'
import { applyAccent, applyTheme, useHydratePreferences } from '@/hooks/useTheme'
import type { User } from '@/lib/types'

// Everything past the first screen is split out, so signing in doesn't
// download the chat client or the story player.
const PostDetail = lazy(() => import('@/pages/PostDetail'))
const Explore = lazy(() => import('@/pages/Explore'))
const Tag = lazy(() => import('@/pages/Tag'))
const Profile = lazy(() => import('@/pages/Profile'))
const Saved = lazy(() => import('@/pages/Saved'))
const CreatePost = lazy(() => import('@/pages/CreatePost'))
const Notifications = lazy(() => import('@/pages/Notifications'))
const Messages = lazy(() => import('@/pages/Messages'))
const Settings = lazy(() => import('@/pages/Settings'))
const Admin = lazy(() => import('@/pages/Admin'))
const StoryViewer = lazy(() => import('@/pages/StoryViewer'))

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        // Never retry an auth, permission or not-found failure; it will never succeed.
        const status = (error as { status?: number })?.status
        if (status && status >= 400 && status < 500) return false
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
      <div className="grid min-h-dvh place-items-center bg-surface">
        <span className="font-display text-[40px] animate-shimmer">Emanstagram</span>
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

/** Validates the stored token once, on boot. */
function useSessionBootstrap() {
  useEffect(() => {
    let cancelled = false
    if (!tokenStore.refresh && !tokenStore.access) {
      useAuth.setState({ initialising: false })
      return
    }
    // The api() layer transparently refreshes an expired access token.
    get<User>('/api/auth/me')
      .then((me) => !cancelled && useAuth.getState().setUser(me))
      .catch(() => !cancelled && useAuth.setState({ user: null, initialising: false }))
    return () => {
      cancelled = true
    }
  }, [])
}

export default function App() {
  useHydratePreferences()
  useSessionBootstrap()

  // Reflect the signed-in user's saved theme and accent colour.
  const theme = useAuth((s) => s.user?.theme)
  const accent = useAuth((s) => s.user?.accentColor)
  useEffect(() => {
    if (theme) applyTheme(theme.toLowerCase() as 'light' | 'dark' | 'system')
  }, [theme])
  useEffect(() => {
    applyAccent(accent)
  }, [accent])

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Suspense fallback={<PageSpinner />}>
          <Routes>
            <Route path="/login" element={<RedirectIfAuthed><Login /></RedirectIfAuthed>} />
            <Route path="/register" element={<RedirectIfAuthed><Register /></RedirectIfAuthed>} />

            <Route path="/stories/:username" element={<RequireAuth><StoryViewer /></RequireAuth>} />

            <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
              <Route index element={<Feed />} />
              <Route path="explore" element={<Explore />} />
              <Route path="t/:tag" element={<Tag />} />
              <Route path="p/:postId" element={<PostDetail />} />
              <Route path="u/:username" element={<Profile />} />
              <Route path="saved" element={<Saved />} />
              <Route path="create" element={<CreatePost />} />
              <Route path="notifications" element={<Notifications />} />
              <Route path="messages" element={<Messages />} />
              <Route path="messages/:conversationId" element={<Messages />} />
              <Route path="settings" element={<Settings />} />
              <Route path="admin" element={<Admin />} />
            </Route>

            <Route path="*" element={<NotFound />} />
          </Routes>
        </Suspense>
        <Toaster />
      </BrowserRouter>
    </QueryClientProvider>
  )
}
