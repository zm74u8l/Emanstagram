/** Direct messages and group chat. Real-time wiring lands in Phase 3. */
export default function Messages() {
  return (
    <div className="mx-auto w-full max-w-2xl px-4 py-6">
      <h1 className="mb-6 text-lg font-semibold">Messages</h1>
      <div className="space-y-1">
        {Array.from({ length: 6 }).map((_, i) => (
          <div
            key={i}
            className="flex items-center gap-3 rounded-xl p-3 hover:bg-surface-muted transition-colors"
          >
            <div className="size-12 shrink-0 animate-shimmer rounded-full bg-surface-muted" />
            <div className="flex-1 space-y-2">
              <div className="h-3 w-32 animate-shimmer rounded bg-surface-muted" />
              <div className="h-2.5 w-48 animate-shimmer rounded bg-surface-muted" />
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
