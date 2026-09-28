import { useEffect, useRef, useState } from 'react'
import { ChevronLeft, ChevronRight, Heart, Volume2, VolumeX } from 'lucide-react'
import { BlurImage } from '@/components/ui/bits'
import { cn } from '@/lib/utils'
import type { MediaItem } from '@/lib/types'

/**
 * Swipeable media for a post.
 *
 * Scroll-snap does the swiping natively (momentum and all) on touch
 * screens; arrows appear on hover for mouse users. The frame takes the first
 * item's aspect ratio, clamped between 4:5 portrait and 1.91:1 landscape,
 * as Instagram does, so a feed of mixed shapes still has a steady rhythm.
 */
export function MediaCarousel({
  media,
  onDoubleTap,
  className,
  fill,
  eager,
}: {
  media: MediaItem[]
  onDoubleTap?: () => void
  className?: string
  /**
   * Post detail: on desktop, fill the pane's height and letterbox the media
   * (object-contain) instead of cropping it. Phones keep the aspect ratio.
   */
  fill?: boolean
  eager?: boolean
}) {
  const track = useRef<HTMLDivElement>(null)
  const [index, setIndex] = useState(0)
  const [burst, setBurst] = useState(0)
  const [muted, setMuted] = useState(true)
  const lastTap = useRef(0)

  const first = media[0]
  const ratio = first?.width && first?.height ? clamp(first.width / first.height, 0.8, 1.91) : 1

  function go(to: number) {
    const el = track.current
    if (!el) return
    el.scrollTo({ left: to * el.clientWidth, behavior: 'smooth' })
  }

  function onScroll() {
    const el = track.current
    if (el) setIndex(Math.round(el.scrollLeft / el.clientWidth))
  }

  // Double-tap is detected by hand so it also works on touch screens,
  // where the dblclick event is unreliable.
  function onTap() {
    const now = Date.now()
    if (now - lastTap.current < 300) {
      onDoubleTap?.()
      setBurst((b) => b + 1)
      lastTap.current = 0
    } else {
      lastTap.current = now
    }
  }

  const hasVideo = media.some((m) => m.mimeType.startsWith('video/'))

  return (
    <div
      className={cn('group relative w-full select-none overflow-hidden bg-black', fill && 'md:h-full md:aspect-auto!', className)}
      style={{ aspectRatio: String(ratio) }}
    >
      <div
        ref={track}
        onScroll={onScroll}
        onClick={onTap}
        className="flex size-full snap-x snap-mandatory overflow-x-auto scrollbar-none"
      >
        {media.map((m, i) => (
          <div key={m.id} className="relative size-full shrink-0 snap-center snap-always">
            {m.mimeType.startsWith('video/') ? (
              <AutoVideo src={m.url} muted={muted} />
            ) : (
              <BlurImage
                src={m.url}
                blurhash={m.blurhash}
                eager={eager && i === 0}
                className="size-full bg-black"
                imgClassName={fill ? 'object-cover md:object-contain' : 'object-cover'}
              />
            )}
          </div>
        ))}
      </div>

      {burst > 0 && (
        <Heart
          key={burst}
          aria-hidden
          fill="white"
          strokeWidth={0}
          className="pointer-events-none absolute left-1/2 top-1/2 -ml-12 -mt-12 size-24 animate-pop drop-shadow-lg"
        />
      )}

      {media.length > 1 && (
        <>
          {index > 0 && (
            <CarouselArrow side="left" onClick={() => go(index - 1)} />
          )}
          {index < media.length - 1 && (
            <CarouselArrow side="right" onClick={() => go(index + 1)} />
          )}
          <div className="absolute right-3 top-3 rounded-full bg-black/60 px-2 py-0.5 text-[12px] font-medium tabular-nums text-white">
            {index + 1}/{media.length}
          </div>
          <div className="pointer-events-none absolute inset-x-0 bottom-3 flex justify-center gap-1">
            {media.map((m, i) => (
              <span
                key={m.id}
                className={cn('size-1.5 rounded-full transition-colors', i === index ? 'bg-white' : 'bg-white/45')}
              />
            ))}
          </div>
        </>
      )}

      {hasVideo && (
        <button
          onClick={(e) => {
            e.stopPropagation()
            setMuted((m) => !m)
          }}
          aria-label={muted ? 'Unmute' : 'Mute'}
          className="absolute bottom-3 right-3 grid size-8 place-items-center rounded-full bg-black/60 text-white"
        >
          {muted ? <VolumeX size={15} /> : <Volume2 size={15} />}
        </button>
      )}
    </div>
  )
}

function CarouselArrow({ side, onClick }: { side: 'left' | 'right'; onClick: () => void }) {
  const Icon = side === 'left' ? ChevronLeft : ChevronRight
  return (
    <button
      onClick={(e) => {
        e.stopPropagation()
        onClick()
      }}
      aria-label={side === 'left' ? 'Previous' : 'Next'}
      className={cn(
        'absolute top-1/2 hidden size-7 -translate-y-1/2 place-items-center rounded-full bg-white/85 text-black shadow',
        'opacity-0 transition-opacity group-hover:opacity-100 md:grid',
        side === 'left' ? 'left-3' : 'right-3',
      )}
    >
      <Icon size={18} strokeWidth={2.5} />
    </button>
  )
}

/** Plays only while on screen, so a long feed never runs a dozen videos at once. */
function AutoVideo({ src, muted }: { src?: string; muted: boolean }) {
  const ref = useRef<HTMLVideoElement>(null)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) void el.play().catch(() => {})
        else el.pause()
      },
      { threshold: 0.6 },
    )
    observer.observe(el)
    return () => observer.disconnect()
  }, [])
  return <video ref={ref} src={src} muted={muted} loop playsInline preload="metadata" className="size-full object-contain" />
}

function clamp(n: number, min: number, max: number) {
  return Math.min(max, Math.max(min, n))
}
