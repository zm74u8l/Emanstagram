import { useEffect, useRef } from 'react'
import { Spinner } from '@/components/ui/Spinner'

/**
 * Loads the next page when it scrolls into view. The generous rootMargin
 * starts the fetch well before the user reaches the end, so scrolling rarely
 * waits on the network.
 */
export function InfiniteSentinel({
  hasMore,
  loading,
  onLoadMore,
  rootMargin = '800px',
  root,
}: {
  hasMore: boolean
  loading: boolean
  onLoadMore: () => void
  rootMargin?: string
  root?: Element | null
}) {
  const ref = useRef<HTMLDivElement>(null)
  const latest = useRef(onLoadMore)
  latest.current = onLoadMore

  useEffect(() => {
    const el = ref.current
    if (!el || !hasMore) return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) latest.current()
      },
      { rootMargin, root },
    )
    observer.observe(el)
    return () => observer.disconnect()
  }, [hasMore, rootMargin, root])

  if (!hasMore) return null
  return (
    <div ref={ref} className="grid h-16 place-items-center text-fg-subtle">
      {loading && <Spinner size={20} />}
    </div>
  )
}
