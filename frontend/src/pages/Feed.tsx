/**
 * Feed skeleton.
 *
 * <p>Placeholder for the post feed (Phase 2). Intentionally shows the real
 * layout shape and shimmer loading so the design can be reviewed before the
 * Post entity exists on the backend.
 */
export default function Feed() {
  return (
    <div className="mx-auto w-full max-w-lg px-4 py-6">
      <h1 className="mb-6 text-lg font-semibold">Home</h1>

      <div className="space-y-6">
        {Array.from({ length: 3 }).map((_, i) => (
          <article
            key={i}
            className="overflow-hidden rounded-2xl border border-line bg-surface-elevated"
          >
            <div className="flex items-center gap-3 p-4">
              <div className="size-10 shrink-0 animate-shimmer rounded-full bg-surface-muted" />
              <div className="flex-1 space-y-2">
                <div className="h-3 w-28 animate-shimmer rounded bg-surface-muted" />
                <div className="h-2.5 w-16 animate-shimmer rounded bg-surface-muted" />
              </div>
            </div>

            <div className="aspect-square w-full animate-shimmer bg-surface-muted" />

            <div className="space-y-2.5 p-4">
              <div className="h-3 w-24 animate-shimmer rounded bg-surface-muted" />
              <div className="h-3 w-full animate-shimmer rounded bg-surface-muted" />
              <div className="h-3 w-2/3 animate-shimmer rounded bg-surface-muted" />
            </div>
          </article>
        ))}
      </div>
    </div>
  )
}
