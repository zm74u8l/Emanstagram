import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ChevronLeft, ChevronRight, Eye, MoreHorizontal, Pause, Play, X } from 'lucide-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { del, errorMessage, get, post } from '@/lib/api'
import { cn, formatRelative } from '@/lib/utils'
import { toast, toastError } from '@/stores/toast'
import { useAuth } from '@/stores/auth'
import { Avatar } from '@/components/ui/Avatar'
import { ActionSheet, Dialog } from '@/components/ui/Dialog'
import { Spinner } from '@/components/ui/Spinner'
import { UserRow } from '@/components/social'
import { storiesQuery } from '@/components/stories/StoriesRail'
import type { StoryGroup, StoryViewer as Viewer } from '@/lib/types'

const IMAGE_MS = 5000

/**
 * Full-screen story player.
 *
 * Opened from the tray it plays through everyone in tray order; opened from
 * a profile it plays just that person. Tap the left third to go back, the
 * rest to go forward, press and hold to pause, arrow keys and Escape work
 * on desktop.
 */
export default function StoryViewer() {
  const { username = '' } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const me = useAuth((s) => s.user)
  const fromTray = (location.state as { fromTray?: boolean } | null)?.fromTray ?? false

  const tray = useQuery({ ...storiesQuery, enabled: fromTray })
  const single = useQuery({
    queryKey: ['stories', username],
    queryFn: () => get<StoryGroup>(`/api/stories/user/${username}`),
    enabled: !fromTray || (tray.isSuccess && !tray.data.some((g) => g.user.username === username)),
    retry: false,
  })

  const groups: StoryGroup[] = useMemo(() => {
    if (fromTray && tray.data?.some((g) => g.user.username === username)) return tray.data
    return single.data ? [single.data] : []
  }, [fromTray, tray.data, single.data, username])

  const groupIndex = Math.max(0, groups.findIndex((g) => g.user.username === username))
  const group = groups[groupIndex]

  // Start at the first unseen story, like everyone expects.
  const [storyIndex, setStoryIndex] = useState(0)
  const startedFor = useRef<string | null>(null)
  useEffect(() => {
    if (group && startedFor.current !== group.user.id) {
      startedFor.current = group.user.id
      const firstUnseen = group.stories.findIndex((s) => !s.viewed)
      setStoryIndex(firstUnseen >= 0 && group.user.id !== me?.id ? firstUnseen : 0)
    }
  }, [group, me?.id])

  const story = group?.stories[storyIndex]
  const mine = group?.user.id === me?.id

  const [progress, setProgress] = useState(0)
  const [held, setHeld] = useState(false)
  const [menu, setMenu] = useState(false)
  const [viewers, setViewers] = useState(false)
  const paused = held || menu || viewers
  const videoRef = useRef<HTMLVideoElement>(null)
  // A press held long enough to pause shouldn't also count as a tap.
  const downAt = useRef(0)
  const tap = (go: () => void, keyboard: boolean) => {
    if (keyboard || Date.now() - downAt.current < 250) go()
  }

  const close = useCallback(() => {
    if (window.history.length > 1) navigate(-1)
    else navigate('/')
  }, [navigate])

  const next = useCallback(() => {
    if (!group) return
    if (storyIndex < group.stories.length - 1) {
      setStoryIndex((i) => i + 1)
    } else if (groupIndex < groups.length - 1) {
      navigate(`/stories/${groups[groupIndex + 1].user.username}`, { replace: true, state: { fromTray } })
    } else {
      close()
    }
  }, [group, storyIndex, groupIndex, groups, navigate, fromTray, close])

  const prev = useCallback(() => {
    if (storyIndex > 0) setStoryIndex((i) => i - 1)
    else if (groupIndex > 0) navigate(`/stories/${groups[groupIndex - 1].user.username}`, { replace: true, state: { fromTray } })
  }, [storyIndex, groupIndex, groups, navigate, fromTray])

  // Record the view once per story, and mark it seen in the tray cache.
  useEffect(() => {
    if (!story || mine || story.viewed) return
    void post(`/api/stories/${story.id}/view`).catch(() => {})
    qc.setQueryData<StoryGroup[]>(storiesQuery.queryKey, (data) =>
      data?.map((g) => {
        if (g.user.id !== group?.user.id) return g
        const stories = g.stories.map((s) => (s.id === story.id ? { ...s, viewed: true } : s))
        return { ...g, stories, hasUnseen: stories.some((s) => !s.viewed) }
      }),
    )
  }, [story, mine, group?.user.id, qc])

  // Image timer. Videos drive progress from their own playback instead.
  // Progress lives in a ref so advancing happens outside any state updater
  // (StrictMode runs updaters twice, which would skip a story).
  const progressRef = useRef(0)
  useEffect(() => {
    progressRef.current = 0
    setProgress(0)
  }, [story?.id])

  useEffect(() => {
    if (!story || story.mimeType.startsWith('video/') || paused) return
    let raf = 0
    let last = performance.now()
    const tick = (now: number) => {
      progressRef.current += (now - last) / IMAGE_MS
      last = now
      if (progressRef.current >= 1) {
        setProgress(1)
        next()
        return
      }
      setProgress(progressRef.current)
      raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [story, paused, next])

  useEffect(() => {
    const v = videoRef.current
    if (!v) return
    if (paused) v.pause()
    else void v.play().catch(() => {})
  }, [paused, story?.id])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (menu || viewers) return
      if (e.key === 'ArrowRight') next()
      else if (e.key === 'ArrowLeft') prev()
      else if (e.key === 'Escape') close()
      else if (e.key === ' ') {
        e.preventDefault()
        setHeld((h) => !h)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [next, prev, close, menu, viewers])

  const remove = useMutation({
    mutationFn: () => del(`/api/stories/${story?.id}`),
    onSuccess: () => {
      toast('Story deleted')
      qc.invalidateQueries({ queryKey: ['stories'] })
      qc.invalidateQueries({ queryKey: ['profile'] })
      close()
    },
    onError: (err) => toastError(errorMessage(err)),
  })

  const loading = (fromTray && tray.isLoading) || single.isLoading

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#0b0b0b] text-white">
      <button onClick={close} aria-label="Close" className="absolute right-4 top-4 z-10 hidden size-10 place-items-center md:grid">
        <X size={28} />
      </button>

      {loading && <Spinner size={28} />}

      {!loading && !story && (
        <div className="text-center">
          <p className="font-display text-[32px]">No stories right now</p>
          <button onClick={close} className="mt-4 text-[14px] font-semibold text-white/80 underline">
            Go back
          </button>
        </div>
      )}

      {story && group && (
        <div className="flex items-center gap-4">
          <NavArrow side="left" visible={storyIndex > 0 || groupIndex > 0} onClick={prev} />

          <div
            className="relative aspect-[9/16] h-dvh max-w-[100vw] overflow-hidden bg-black md:h-[min(92dvh,860px)] md:rounded-[12px]"
            style={story.backgroundHex ? { background: story.backgroundHex } : undefined}
          >
            {story.mimeType.startsWith('video/') ? (
              <video
                key={story.id}
                ref={videoRef}
                src={story.url}
                autoPlay
                playsInline
                className="size-full object-contain"
                onTimeUpdate={(e) => {
                  const v = e.currentTarget
                  if (v.duration) setProgress(v.currentTime / v.duration)
                }}
                onEnded={next}
              />
            ) : (
              <>
                {/* A blurred copy fills the letterbox, so landscape photos don't float in black. */}
                <img key={`${story.id}-bg`} src={story.url} alt="" aria-hidden className="absolute inset-0 size-full scale-110 object-cover opacity-70 blur-2xl" />
                <img key={story.id} src={story.url} alt="" className="relative size-full object-contain" />
              </>
            )}

            {/* Legibility scrims behind the header and caption. */}
            <div className="pointer-events-none absolute inset-x-0 top-0 h-28 bg-gradient-to-b from-black/55 to-transparent" />
            {story.caption && (
              <div className="pointer-events-none absolute inset-x-0 bottom-0 h-40 bg-gradient-to-t from-black/60 to-transparent" />
            )}

            {/* Tap zones and hold-to-pause. */}
            <div
              className="absolute inset-0 flex"
              onPointerDown={() => {
                downAt.current = Date.now()
                setHeld(true)
              }}
              onPointerUp={() => setHeld(false)}
              onPointerLeave={() => setHeld(false)}
            >
              <button aria-label="Previous story" className="w-1/3" onClick={(e) => tap(prev, e.detail === 0)} />
              <button aria-label="Next story" className="flex-1" onClick={(e) => tap(next, e.detail === 0)} />
            </div>

            <header className="absolute inset-x-0 top-0 px-3 pt-3">
              <div className="flex gap-1">
                {group.stories.map((s, i) => (
                  <div key={s.id} className="h-[2px] flex-1 overflow-hidden rounded-full bg-white/35">
                    <div
                      className="h-full bg-white"
                      style={{ width: `${i < storyIndex ? 100 : i === storyIndex ? progress * 100 : 0}%` }}
                    />
                  </div>
                ))}
              </div>
              <div className="mt-3 flex items-center gap-2.5">
                <Link to={`/u/${group.user.username}`} className="flex items-center gap-2.5">
                  <Avatar user={group.user} size="sm" />
                  <span className="text-[14px] font-semibold">{group.user.username}</span>
                </Link>
                <span className="text-[13px] text-white/70">{formatRelative(story.createdAt)}</span>
                <div className="ml-auto flex items-center">
                  <button onClick={() => setHeld((h) => !h)} aria-label={paused ? 'Play' : 'Pause'} className="grid size-9 place-items-center">
                    {paused ? <Play size={18} fill="white" /> : <Pause size={18} fill="white" />}
                  </button>
                  {mine && (
                    <button onClick={() => setMenu(true)} aria-label="Story options" className="grid size-9 place-items-center">
                      <MoreHorizontal size={22} />
                    </button>
                  )}
                  <button onClick={close} aria-label="Close" className="grid size-9 place-items-center md:hidden">
                    <X size={24} />
                  </button>
                </div>
              </div>
            </header>

            {story.caption && (
              <p className="pointer-events-none absolute inset-x-0 bottom-16 px-6 text-center text-[17px] font-medium leading-snug [text-shadow:0_1px_8px_rgba(0,0,0,0.5)]">
                {story.caption}
              </p>
            )}

            {mine && (
              <button
                onClick={() => setViewers(true)}
                className="absolute bottom-5 left-5 flex items-center gap-2 text-[13px] font-semibold"
              >
                <Eye size={18} />
                {story.viewCount ? `Seen by ${story.viewCount}` : 'No views yet'}
              </button>
            )}
          </div>

          <NavArrow side="right" visible onClick={next} />
        </div>
      )}

      <ActionSheet
        open={menu}
        onClose={() => setMenu(false)}
        actions={[{ label: 'Delete story', tone: 'danger', onClick: () => remove.mutate() }]}
      />
      {story && mine && <ViewersDialog open={viewers} onClose={() => setViewers(false)} storyId={story.id} />}
    </div>
  )
}

function NavArrow({ side, visible, onClick }: { side: 'left' | 'right'; visible: boolean; onClick: () => void }) {
  const Icon = side === 'left' ? ChevronLeft : ChevronRight
  return (
    <button
      onClick={onClick}
      aria-label={side === 'left' ? 'Previous' : 'Next'}
      className={cn(
        'hidden size-8 place-items-center rounded-full bg-white/85 text-black md:grid',
        !visible && 'invisible',
      )}
    >
      <Icon size={20} strokeWidth={2.5} />
    </button>
  )
}

function ViewersDialog({ open, onClose, storyId }: { open: boolean; onClose: () => void; storyId: string }) {
  const { data, isLoading } = useQuery({
    queryKey: ['story-viewers', storyId],
    queryFn: () => get<Viewer[]>(`/api/stories/${storyId}/viewers`),
    enabled: open,
  })
  return (
    <Dialog open={open} onClose={onClose} title="Viewers">
      <div className="max-h-[60dvh] min-h-40 overflow-y-auto px-4 py-2 text-fg">
        {isLoading && (
          <div className="grid h-32 place-items-center text-fg-subtle">
            <Spinner />
          </div>
        )}
        {data?.length === 0 && <p className="py-10 text-center text-[14px] text-fg-muted">No one has seen this yet.</p>}
        {data?.map((v) => <UserRow key={v.user.id} user={v.user} subtitle={formatRelative(v.viewedAt)} onNavigate={onClose} />)}
      </div>
    </Dialog>
  )
}
