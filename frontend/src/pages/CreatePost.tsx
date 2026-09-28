import { useEffect, useRef, useState, type DragEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, ArrowRight, Film, Globe, ImagePlus, Lock, Plus, Users, X } from 'lucide-react'
import { errorMessage, fetchMediaLimits, upload } from '@/lib/api'
import { prepareImage, prepareVideo, type PreparedMedia } from '@/lib/media'
import { cn, formatBytes, formatDuration, validateFile } from '@/lib/utils'
import { toast } from '@/stores/toast'
import { useAuth } from '@/stores/auth'
import { Avatar } from '@/components/ui/Avatar'
import { Button } from '@/components/ui/Button'
import { Input, Textarea } from '@/components/ui/Input'
import { EmptyState } from '@/components/ui/bits'
import { PageSpinner, Spinner } from '@/components/ui/Spinner'
import type { MediaLimits, Post, PostVisibility, StoryItem } from '@/lib/types'

type Mode = 'post' | 'story'

interface Staged {
  id: string
  source: File
  prepared?: PreparedMedia
  error?: string
}

export default function CreatePost() {
  const [params, setParams] = useSearchParams()
  const mode: Mode = params.get('mode') === 'story' ? 'story' : 'post'
  const { data: limits, isLoading } = useQuery({ queryKey: ['media-limits'], queryFn: fetchMediaLimits, staleTime: Infinity })

  if (isLoading) return <PageSpinner />
  if (!limits?.storageEnabled) {
    return (
      <EmptyState
        title="Uploads are switched off"
        body="Media storage isn’t configured on this server yet, so photos and videos can’t be shared."
        className="min-h-[60dvh] justify-center"
      />
    )
  }

  return (
    <div className="mx-auto w-full max-w-[920px] px-4 py-5 md:px-6 md:py-10">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="font-display text-[36px] leading-none">{mode === 'post' ? 'New post' : 'New story'}</h1>
        <div role="tablist" className="flex rounded-[var(--radius-control)] bg-surface-muted p-1 text-[13px] font-semibold">
          {(['post', 'story'] as const).map((m) => (
            <button
              key={m}
              role="tab"
              aria-selected={mode === m}
              onClick={() => setParams(m === 'story' ? { mode: 'story' } : {})}
              className={cn(
                'h-8 rounded-[8px] px-4 capitalize transition-colors',
                mode === m ? 'bg-surface-elevated shadow-sm' : 'text-fg-muted hover:text-fg',
              )}
            >
              {m}
            </button>
          ))}
        </div>
      </div>
      {mode === 'post' ? <PostComposer limits={limits} /> : <StoryComposer limits={limits} />}
    </div>
  )
}

// ---------------------------------------------------------------------
// Post
// ---------------------------------------------------------------------

function PostComposer({ limits }: { limits: MediaLimits }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const me = useAuth((s) => s.user)
  const [items, setItems] = useState<Staged[]>([])
  const [selected, setSelected] = useState(0)
  const [caption, setCaption] = useState('')
  const [location, setLocation] = useState('')
  const [visibility, setVisibility] = useState<PostVisibility>('PUBLIC')
  const [progress, setProgress] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  // Object URLs hold memory until revoked.
  const itemsRef = useRef(items)
  itemsRef.current = items
  useEffect(() => () => itemsRef.current.forEach((i) => i.prepared && URL.revokeObjectURL(i.prepared.previewUrl)), [])

  function add(files: File[]) {
    setError(null)
    const room = limits.maxCarouselItems - items.length
    if (files.length > room) setError(`A post can hold up to ${limits.maxCarouselItems} items.`)
    const accepted = files.slice(0, Math.max(0, room)).map((f) => ({ id: crypto.randomUUID(), source: f }))
    setItems((prev) => [...prev, ...accepted])

    // Prepare each file in the background: resize, blurhash, dimensions.
    accepted.forEach(async (item) => {
      const isVideo = item.source.type.startsWith('video/')
      try {
        if (isVideo) {
          const problem = validateFile(item.source, limits.maxVideoBytes, limits.allowedVideoTypes)
          if (problem) throw new Error(problem)
        }
        const prepared = isVideo ? await prepareVideo(item.source) : await prepareImage(item.source)
        if (!isVideo) {
          const problem = validateFile(prepared.file, limits.maxImageBytes, limits.allowedImageTypes)
          if (problem) throw new Error(problem)
        }
        setItems((prev) => prev.map((i) => (i.id === item.id ? { ...i, prepared } : i)))
      } catch (err) {
        setItems((prev) => prev.map((i) => (i.id === item.id ? { ...i, error: errorMessage(err) } : i)))
      }
    })
  }

  function remove(id: string) {
    setItems((prev) => {
      const target = prev.find((i) => i.id === id)
      if (target?.prepared) URL.revokeObjectURL(target.prepared.previewUrl)
      return prev.filter((i) => i.id !== id)
    })
    setSelected((s) => Math.max(0, Math.min(s, items.length - 2)))
  }

  function move(index: number, delta: number) {
    setItems((prev) => {
      const next = [...prev]
      const to = index + delta
      if (to < 0 || to >= next.length) return prev
      ;[next[index], next[to]] = [next[to], next[index]]
      return next
    })
    setSelected(index + delta)
  }

  const ready = items.length > 0 && items.every((i) => i.prepared && !i.error)
  const busy = progress !== null

  async function share() {
    if (!ready) return
    setError(null)
    const form = new FormData()
    items.forEach((i) => form.append('files', i.prepared!.file))
    form.append(
      'meta',
      JSON.stringify(
        items.map((i) => ({
          width: i.prepared!.width,
          height: i.prepared!.height,
          durationMs: i.prepared!.durationMs,
          blurhash: i.prepared!.blurhash,
        })),
      ),
    )
    if (caption.trim()) form.append('caption', caption.trim())
    if (location.trim()) form.append('location', location.trim())
    form.append('visibility', visibility)

    setProgress(0)
    try {
      const created = await upload<Post>('/api/posts', form, { onProgress: setProgress })
      qc.invalidateQueries({ queryKey: ['feed'] })
      qc.invalidateQueries({ queryKey: ['user-posts'] })
      qc.invalidateQueries({ queryKey: ['profile'] })
      qc.setQueryData(['post', created.id], created)
      toast('Your post has been shared')
      navigate(`/p/${created.id}`)
    } catch (err) {
      setError(errorMessage(err))
      setProgress(null)
    }
  }

  if (items.length === 0) {
    return <DropZone onFiles={add} multiple accept={[...limits.allowedImageTypes, ...limits.allowedVideoTypes, 'image/heic']} limits={limits} />
  }

  const current = items[Math.min(selected, items.length - 1)]
  const totalBytes = items.reduce((n, i) => n + (i.prepared?.file.size ?? 0), 0)

  return (
    <div className="grid gap-6 md:grid-cols-[minmax(0,1fr)_340px]">
      <section>
        <div className="relative grid aspect-square place-items-center overflow-hidden rounded-[var(--radius-card)] bg-black">
          {current.error ? (
            <p className="max-w-xs px-6 text-center text-[14px] text-white/80">{current.error}</p>
          ) : !current.prepared ? (
            <Spinner size={28} className="text-white/70" />
          ) : current.prepared.kind === 'video' ? (
            <video src={current.prepared.previewUrl} controls playsInline className="size-full object-contain" />
          ) : (
            <img src={current.prepared.previewUrl} alt="" className="size-full object-contain" />
          )}
        </div>

        <div className="mt-3 flex gap-2 overflow-x-auto pb-1 scrollbar-thin">
          {items.map((item, i) => (
            <div key={item.id} className="group relative shrink-0">
              <button
                onClick={() => setSelected(i)}
                className={cn(
                  'relative grid size-[72px] place-items-center overflow-hidden rounded-[8px] bg-surface-muted',
                  i === selected ? 'ring-2 ring-fg ring-offset-2 ring-offset-surface' : 'opacity-80 hover:opacity-100',
                  item.error && 'ring-2 ring-danger',
                )}
              >
                {item.prepared ? (
                  item.prepared.kind === 'video' ? (
                    <>
                      <video src={item.prepared.previewUrl} muted className="size-full object-cover" />
                      <span className="absolute bottom-1 left-1 flex items-center gap-0.5 rounded bg-black/60 px-1 text-[10px] text-white">
                        <Film size={10} /> {formatDuration(item.prepared.durationMs ?? 0)}
                      </span>
                    </>
                  ) : (
                    <img src={item.prepared.previewUrl} alt="" className="size-full object-cover" />
                  )
                ) : item.error ? (
                  <X size={18} className="text-danger" />
                ) : (
                  <Spinner size={16} className="text-fg-subtle" />
                )}
              </button>
              <button
                onClick={() => remove(item.id)}
                aria-label="Remove"
                className="absolute -right-1.5 -top-1.5 grid size-5 place-items-center rounded-full bg-surface-inverse text-fg-inverse"
              >
                <X size={12} strokeWidth={3} />
              </button>
              {i === selected && items.length > 1 && (
                <div className="mt-1 flex justify-between">
                  <button onClick={() => move(i, -1)} disabled={i === 0} aria-label="Move earlier" className="text-fg-muted disabled:opacity-30">
                    <ArrowLeft size={14} />
                  </button>
                  <button onClick={() => move(i, 1)} disabled={i === items.length - 1} aria-label="Move later" className="text-fg-muted disabled:opacity-30">
                    <ArrowRight size={14} />
                  </button>
                </div>
              )}
            </div>
          ))}
          {items.length < limits.maxCarouselItems && (
            <FilePickButton onFiles={add} accept={[...limits.allowedImageTypes, ...limits.allowedVideoTypes, 'image/heic']}>
              <span className="grid size-[72px] place-items-center rounded-[8px] border border-dashed border-line-strong text-fg-muted hover:text-fg">
                <Plus size={22} />
              </span>
            </FilePickButton>
          )}
        </div>
        <p className="mt-2 text-[12px] text-fg-subtle">
          {items.length} of {limits.maxCarouselItems} · {formatBytes(totalBytes)} after compression
        </p>
      </section>

      <section className="space-y-5">
        <div className="flex items-center gap-3">
          <Avatar user={me} size="sm" />
          <span className="text-[14px] font-semibold">{me?.username}</span>
        </div>
        <Textarea
          value={caption}
          onChange={(e) => setCaption(e.target.value)}
          maxLength={limits.maxCaptionLength}
          showCount
          placeholder="Write a caption… Use @ to mention people and # for tags."
          aria-label="Caption"
          className="min-h-36"
        />
        <Input label="Location" name="location" value={location} onChange={(e) => setLocation(e.target.value)} maxLength={160} placeholder="Add location" />

        <fieldset>
          <legend className="mb-1.5 text-[13px] font-medium">Who can see this</legend>
          <div className="grid grid-cols-3 gap-2">
            {(
              [
                ['PUBLIC', 'Everyone', Globe],
                ['FOLLOWERS', 'Followers', Users],
                ['PRIVATE', 'Only me', Lock],
              ] as const
            ).map(([value, label, Icon]) => (
              <button
                key={value}
                type="button"
                onClick={() => setVisibility(value)}
                aria-pressed={visibility === value}
                className={cn(
                  'flex flex-col items-center gap-1.5 rounded-[var(--radius-control)] border py-3 text-[12.5px] font-medium transition-colors',
                  visibility === value ? 'border-fg bg-surface-elevated' : 'border-line-strong text-fg-muted hover:text-fg',
                )}
              >
                <Icon size={18} />
                {label}
              </button>
            ))}
          </div>
        </fieldset>

        {error && <p className="text-[13.5px] text-danger">{error}</p>}

        <Button size="lg" className="relative w-full overflow-hidden" disabled={!ready || busy} onClick={share}>
          {busy && (
            <span className="absolute inset-y-0 left-0 bg-white/20 transition-[width]" style={{ width: `${(progress ?? 0) * 100}%` }} />
          )}
          <span className="relative">
            {busy ? (progress! < 1 ? `Uploading… ${Math.round(progress! * 100)}%` : 'Finishing…') : 'Share'}
          </span>
        </Button>
        {!ready && items.some((i) => !i.prepared && !i.error) && (
          <p className="text-center text-[12px] text-fg-subtle">Preparing your media…</p>
        )}
      </section>
    </div>
  )
}

// ---------------------------------------------------------------------
// Story
// ---------------------------------------------------------------------

function StoryComposer({ limits }: { limits: MediaLimits }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const me = useAuth((s) => s.user)
  const [media, setMedia] = useState<PreparedMedia | null>(null)
  const [preparing, setPreparing] = useState(false)
  const [caption, setCaption] = useState('')
  const [progress, setProgress] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function pick(files: File[]) {
    const file = files[0]
    if (!file) return
    setError(null)
    setPreparing(true)
    try {
      const isVideo = file.type.startsWith('video/')
      if (isVideo) {
        const problem = validateFile(file, limits.maxStoryVideoBytes, limits.allowedVideoTypes)
        if (problem) throw new Error(problem)
      }
      const prepared = isVideo ? await prepareVideo(file) : await prepareImage(file, 1920)
      if (!isVideo) {
        const problem = validateFile(prepared.file, limits.maxImageBytes, limits.allowedImageTypes)
        if (problem) throw new Error(problem)
      }
      if (media) URL.revokeObjectURL(media.previewUrl)
      setMedia(prepared)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setPreparing(false)
    }
  }

  async function share() {
    if (!media) return
    const form = new FormData()
    form.append('file', media.file)
    if (caption.trim()) form.append('caption', caption.trim())
    setProgress(0)
    try {
      await upload<StoryItem>('/api/stories', form, { onProgress: setProgress })
      qc.invalidateQueries({ queryKey: ['stories'] })
      qc.invalidateQueries({ queryKey: ['profile'] })
      toast('Added to your story')
      navigate('/')
    } catch (err) {
      setError(errorMessage(err))
      setProgress(null)
    }
  }

  if (!media) {
    return (
      <>
        {preparing ? (
          <div className="grid h-80 place-items-center">
            <Spinner size={28} />
          </div>
        ) : (
          <DropZone onFiles={pick} accept={[...limits.allowedImageTypes, ...limits.allowedVideoTypes, 'image/heic']} limits={limits} story />
        )}
        {error && <p className="mt-3 text-center text-[13.5px] text-danger">{error}</p>}
      </>
    )
  }

  return (
    <div className="flex flex-col items-center gap-6 md:flex-row md:items-start md:justify-center">
      <div className="relative aspect-[9/16] w-full max-w-[300px] overflow-hidden rounded-[12px] bg-black">
        {media.kind === 'video' ? (
          <video src={media.previewUrl} autoPlay muted loop playsInline className="size-full object-cover" />
        ) : (
          <img src={media.previewUrl} alt="" className="size-full object-cover" />
        )}
        <div className="absolute inset-x-0 top-0 flex items-center gap-2 bg-gradient-to-b from-black/50 to-transparent p-3 text-[13px] font-semibold text-white">
          <Avatar user={me} size="xs" /> {me?.username}
        </div>
        {caption && (
          <p className="absolute inset-x-0 bottom-10 px-5 text-center text-[15px] font-medium text-white [text-shadow:0_1px_8px_rgba(0,0,0,0.6)]">
            {caption}
          </p>
        )}
        <button
          onClick={() => setMedia(null)}
          aria-label="Choose a different file"
          className="absolute right-3 top-3 grid size-8 place-items-center rounded-full bg-black/50 text-white"
        >
          <X size={16} />
        </button>
      </div>

      <div className="w-full max-w-[300px] space-y-4">
        <Input name="storyCaption" label="Caption" value={caption} onChange={(e) => setCaption(e.target.value)} maxLength={300} placeholder="Say something (optional)" />
        <p className="text-[12.5px] text-fg-muted">Stories disappear after 24 hours. You can see who viewed yours.</p>
        {error && <p className="text-[13.5px] text-danger">{error}</p>}
        <Button size="lg" className="w-full" loading={progress !== null} onClick={share}>
          Share to story
        </Button>
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------
// File picking
// ---------------------------------------------------------------------

function FilePickButton({
  onFiles,
  accept,
  multiple = true,
  children,
}: {
  onFiles: (files: File[]) => void
  accept: string[]
  multiple?: boolean
  children: React.ReactNode
}) {
  const input = useRef<HTMLInputElement>(null)
  return (
    <>
      <button type="button" onClick={() => input.current?.click()} className="shrink-0">
        {children}
      </button>
      <input
        ref={input}
        type="file"
        multiple={multiple}
        accept={accept.join(',')}
        className="hidden"
        onChange={(e) => {
          onFiles(Array.from(e.target.files ?? []))
          e.target.value = ''
        }}
      />
    </>
  )
}

function DropZone({
  onFiles,
  accept,
  multiple,
  limits,
  story,
}: {
  onFiles: (files: File[]) => void
  accept: string[]
  multiple?: boolean
  limits: MediaLimits
  story?: boolean
}) {
  const [over, setOver] = useState(false)
  const input = useRef<HTMLInputElement>(null)

  function onDrop(e: DragEvent) {
    e.preventDefault()
    setOver(false)
    const files = Array.from(e.dataTransfer.files).filter((f) => f.type.startsWith('image/') || f.type.startsWith('video/'))
    if (files.length) onFiles(multiple ? files : files.slice(0, 1))
  }

  return (
    <div
      onDragOver={(e) => {
        e.preventDefault()
        setOver(true)
      }}
      onDragLeave={() => setOver(false)}
      onDrop={onDrop}
      className={cn(
        'flex min-h-[420px] flex-col items-center justify-center rounded-[var(--radius-card)] border border-dashed px-6 text-center transition-colors',
        over ? 'border-fg bg-surface-muted' : 'border-line-strong',
      )}
    >
      <ImagePlus size={56} strokeWidth={1} />
      <p className="mt-4 font-display text-[28px] leading-tight">
        {story ? 'Add a photo or video to your story' : 'Drag photos and videos here'}
      </p>
      <p className="mt-2 max-w-sm text-[13px] text-fg-muted">
        Photos up to {formatBytes(limits.maxImageBytes)}, videos up to{' '}
        {formatBytes(story ? limits.maxStoryVideoBytes : limits.maxVideoBytes)}. Photos are resized in your browser
        before uploading, so large originals are fine.
      </p>
      <Button className="mt-6" onClick={() => input.current?.click()}>
        Select from device
      </Button>
      <input
        ref={input}
        type="file"
        multiple={multiple}
        accept={accept.join(',')}
        className="hidden"
        onChange={(e) => {
          onFiles(Array.from(e.target.files ?? []))
          e.target.value = ''
        }}
      />
    </div>
  )
}
