/** Activity feed for likes, comments, follows and mentions. */
export default function Notifications() {
  return (
    <div className="mx-auto w-full max-w-lg px-4 py-6">
      <h1 className="mb-6 text-lg font-semibold">Activity</h1>
      <div className="space-y-1">
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} className="flex items-center gap-3 p-3">
            <div className="size-10 shrink-0 animate-shimmer rounded-full bg-surface-muted" />
            <div className="flex-1 space-y-2">
              <div className="h-3 w-3/4 animate-shimmer rounded bg-surface-muted" />
              <div className="h-2.5 w-20 animate-shimmer rounded bg-surface-muted" />
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
