import { useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { decode } from 'blurhash'
import { cn } from '@/lib/utils'
import { useToasts } from '@/stores/toast'

/** The serif wordmark. Deliberately plain: no gradient, no icon. */
export function Wordmark({ className, to = '/' }: { className?: string; to?: string | null }) {
  const text = <span className={cn('font-display text-[28px] leading-none tracking-tight', className)}>Emanstagram</span>
  return to ? (
    <Link to={to} aria-label="Emanstagram home" className="inline-block">
      {text}
    </Link>
  ) : (
    text
  )
}

export function VerifiedBadge({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" aria-label="Verified" className={cn('inline-block size-[14px] shrink-0 text-accent', className)}>
      <path
        fill="currentColor"
        d="M12 1.5l2.6 1.9 3.2-.2 1 3.1 2.6 1.9-1 3.1 1 3.1-2.6 1.9-1 3.1-3.2-.2L12 22.5l-2.6-1.9-3.2.2-1-3.1-2.6-1.9 1-3.1-1-3.1 2.6-1.9 1-3.1 3.2.2z"
      />
      <path d="M8 12.3l2.6 2.6L16.2 9.3" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={cn('animate-shimmer rounded-md bg-surface-muted', className)} />
}

export function EmptyState({
  icon,
  title,
  body,
  action,
  className,
}: {
  icon?: ReactNode
  title: string
  body?: ReactNode
  action?: ReactNode
  className?: string
}) {
  return (
    <div className={cn('flex flex-col items-center px-6 py-16 text-center', className)}>
      {icon && (
        <div className="mb-4 grid size-16 place-items-center rounded-full border-2 border-fg text-fg">{icon}</div>
      )}
      <h2 className="font-display text-[30px] leading-tight">{title}</h2>
      {body && <p className="mt-2 max-w-sm text-[14px] text-fg-muted">{body}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

const hashCache = new Map<string, string>()

/** Decodes a blurhash into a tiny data URL, cached so each hash is only decoded once. */
function blurDataUrl(hash: string): string | undefined {
  const cached = hashCache.get(hash)
  if (cached) return cached
  try {
    const pixels = decode(hash, 32, 32)
    const canvas = document.createElement('canvas')
    canvas.width = 32
    canvas.height = 32
    const ctx = canvas.getContext('2d')
    if (!ctx) return undefined
    const data = ctx.createImageData(32, 32)
    data.data.set(pixels)
    ctx.putImageData(data, 0, 0)
    const url = canvas.toDataURL()
    hashCache.set(hash, url)
    return url
  } catch {
    return undefined
  }
}

/**
 * An image that shows its blurhash until the real pixels arrive, then fades
 * them in. Stops the feed flashing grey boxes on a slow connection.
 */
export function BlurImage({
  src,
  blurhash,
  alt = '',
  className,
  imgClassName,
  eager,
}: {
  src?: string
  blurhash?: string
  alt?: string
  className?: string
  imgClassName?: string
  eager?: boolean
}) {
  const [loaded, setLoaded] = useState(false)
  const placeholder = useMemo(() => (blurhash ? blurDataUrl(blurhash) : undefined), [blurhash])
  return (
    <div
      className={cn('relative overflow-hidden bg-surface-muted', className)}
      style={placeholder ? { backgroundImage: `url(${placeholder})`, backgroundSize: 'cover' } : undefined}
    >
      {src && (
        <img
          src={src}
          alt={alt}
          loading={eager ? 'eager' : 'lazy'}
          decoding="async"
          onLoad={() => setLoaded(true)}
          className={cn(
            'size-full object-cover transition-opacity duration-300',
            loaded ? 'opacity-100' : 'opacity-0',
            imgClassName,
          )}
        />
      )}
    </div>
  )
}

export function Toaster() {
  const toasts = useToasts((s) => s.toasts)
  const dismiss = useToasts((s) => s.dismiss)
  return (
    <div
      aria-live="polite"
      className="pointer-events-none fixed inset-x-0 bottom-20 z-[60] flex flex-col items-center gap-2 px-4 md:bottom-8"
    >
      {toasts.map((t) => (
        <div
          key={t.id}
          className={cn(
            'pointer-events-auto flex max-w-md animate-rise items-center gap-4 rounded-full px-5 py-2.5 text-[14px] shadow-[var(--shadow-pop)]',
            t.tone === 'error' ? 'bg-danger text-white' : 'bg-surface-inverse text-fg-inverse',
          )}
        >
          <span>{t.message}</span>
          {t.action && (
            <button
              onClick={() => {
                t.action?.onClick()
                dismiss(t.id)
              }}
              className="font-semibold text-accent"
            >
              {t.action.label}
            </button>
          )}
        </div>
      ))}
    </div>
  )
}

/** Uppercase tab strip with a top indicator, used on profiles and the admin queue. */
export function Tabs<T extends string>({
  tabs,
  value,
  onChange,
  className,
}: {
  tabs: { value: T; label: string; icon?: ReactNode }[]
  value: T
  onChange: (value: T) => void
  className?: string
}) {
  return (
    <div role="tablist" className={cn('flex justify-center gap-12 border-t border-line', className)}>
      {tabs.map((t) => {
        const active = t.value === value
        return (
          <button
            key={t.value}
            role="tab"
            aria-selected={active}
            onClick={() => onChange(t.value)}
            className={cn(
              '-mt-px flex h-12 items-center gap-1.5 border-t text-[12px] font-semibold uppercase tracking-[0.08em] transition-colors',
              active ? 'border-fg text-fg' : 'border-transparent text-fg-subtle hover:text-fg-muted',
            )}
          >
            {t.icon}
            {t.label}
          </button>
        )
      })}
    </div>
  )
}
