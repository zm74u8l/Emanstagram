/** Explore/discover feed. Placeholder for Phase 2. */
export default function Explore() {
  return (
    <div className="mx-auto w-full max-w-lg px-4 py-6">
      <h1 className="mb-6 text-lg font-semibold">Explore</h1>
      <div className="grid grid-cols-3 gap-1">
        {Array.from({ length: 9 }).map((_, i) => (
          <div
            key={i}
            className="aspect-square animate-shimmer rounded-lg bg-surface-muted"
          />
        ))}
      </div>
    </div>
  )
}
