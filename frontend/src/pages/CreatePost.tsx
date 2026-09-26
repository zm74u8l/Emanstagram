import { useRef, useState, type ChangeEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Image as ImageIcon, X, Film } from 'lucide-react'
import { fetchMediaLimits } from '@/lib/api'
import { formatBytes, validateFile } from '@/lib/utils'
import { Button } from '@/components/ui/Button'
import type { MediaLimits } from '@/lib/types'

interface Staged {
  file: File
  kind: 'image' | 'video'
  previewUrl: string
}

/**
 * Post composer.
 *
 * <p>Stage 1 of the three upload gates: the browser validates size and type
 * against limits fetched from /api/config/public, so a bad file is rejected
 * before the user wastes bandwidth. Gate 2 is Spring's multipart limit and
 * gate 3 is the storage bucket policy.
 */
export default function CreatePost() {
  const inputRef = useRef<HTMLInputElement>(null)
  const [staged, setStaged] = useState<Staged[]>([])
  const [caption, setCaption] = useState('')
  const [error, setError] = useState<string | null>(null)

  const { data: limits } = useQuery<MediaLimits>({
    queryKey: ['media-limits'],
    queryFn: fetchMediaLimits,
    staleTime: Infinity,
  })

  function onPick(e: ChangeEvent<HTMLInputElement>) {
    setError(null)
    const files = Array.from(e.target.files ?? [])
    if (files.length === 0) return

    if (!limits) {
      setError('Still loading upload settings. Try again in a moment.')
      return
    }
    if (!limits.storageEnabled) {
      setError('Uploads are not enabled on this server yet.')
      return
    }

    const next: Staged[] = []
    for (const file of files) {
      if (staged.length + next.length >= limits.maxCarouselItems) {
        setError(`You can add up to ${limits.maxCarouselItems} items.`)
        break
      }

      const isVideo = file.type.startsWith('video/')
      const problem = validateFile(
        file,
        isVideo ? limits.maxVideoBytes : limits.maxImageBytes,
        isVideo ? limits.allowedVideoTypes : limits.allowedImageTypes,
      )
      if (problem) {
        setError(problem)
        continue
      }

      next.push({
        file,
        kind: isVideo ? 'video' : 'image',
        previewUrl: URL.createObjectURL(file),
      })
    }

    setStaged((prev) => [...prev, ...next])
    if (inputRef.current) inputRef.current.value = ''
  }

  function removeAt(index: number) {
    setStaged((prev) => {
      URL.revokeObjectURL(prev[index].previewUrl)
      return prev.filter((_, i) => i !== index)
    })
  }

  return (
    <div className="mx-auto w-full max-w-lg px-4 py-6">
      <h1 className="mb-6 text-lg font-semibold">New post</h1>

      {error && (
        <div
          role="alert"
          className="mb-4 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-500"
        >
          {error}
        </div>
      )}

      <div className="space-y-4 rounded-2xl border border-line bg-surface-elevated p-5">
        <div>
          <label
            htmlFor="caption"
            className="mb-1.5 block text-sm font-medium text-fg-muted"
          >
            Caption
          </label>
          <textarea
            id="caption"
            value={caption}
            onChange={(e) => setCaption(e.target.value)}
            rows={4}
            maxLength={limits?.maxCaptionLength ?? 2200}
            placeholder="Write a caption..."
            className="w-full resize-none rounded-xl border border-line bg-surface px-3 py-2.5 text-sm text-fg placeholder:text-fg-subtle focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/50"
          />
          <p className="mt-1 text-right text-xs text-fg-subtle">
            {caption.length}/{limits?.maxCaptionLength ?? 2200}
          </p>
        </div>

        {staged.length > 0 && (
          <div className="grid grid-cols-3 gap-2">
            {staged.map((item, i) => (
              <div
                key={item.previewUrl}
                className="group relative aspect-square overflow-hidden rounded-xl bg-surface-muted"
              >
                {item.kind === 'video' ? (
                  <>
                    <video
                      src={item.previewUrl}
                      className="size-full object-cover"
                      muted
                      playsInline
                    />
                    <Film
                      size={16}
                      className="absolute bottom-1 left-1 text-white drop-shadow"
                    />
                  </>
                ) : (
                  <img
                    src={item.previewUrl}
                    alt=""
                    className="size-full object-cover"
                  />
                )}
                <button
                  type="button"
                  onClick={() => removeAt(i)}
                  aria-label="Remove item"
                  className="absolute right-1 top-1 grid size-6 place-items-center rounded-full bg-black/70 text-white opacity-0 transition-opacity group-hover:opacity-100 focus-visible:opacity-100"
                >
                  <X size={14} />
                </button>
              </div>
            ))}
          </div>
        )}

        <input
          ref={inputRef}
          type="file"
          multiple
          accept="image/*,video/*"
          onChange={onPick}
          className="hidden"
        />

        <div className="flex items-center gap-3">
          <Button
            type="button"
            variant="secondary"
            onClick={() => inputRef.current?.click()}
            disabled={!limits?.storageEnabled}
          >
            <ImageIcon size={16} />
            Add media
          </Button>

          <span className="text-xs text-fg-subtle">
            {staged.length}/{limits?.maxCarouselItems ?? 10} selected
          </span>

          <Button type="button" className="ml-auto" disabled>
            Share
          </Button>
        </div>

        {limits && (
          <p className="border-t border-line pt-3 text-xs leading-relaxed text-fg-subtle">
            Images up to {formatBytes(limits.maxImageBytes)} · videos up to{' '}
            {formatBytes(limits.maxVideoBytes)}
            <br />
            Videos are compressed in your browser before uploading, so larger
            source files are fine.
          </p>
        )}
      </div>
    </div>
  )
}
