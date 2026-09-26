import { Link } from 'react-router-dom'

export default function NotFound() {
  return (
    <div className="grid min-h-dvh place-items-center bg-surface px-4">
      <div className="text-center">
        <p className="text-6xl font-bold bg-gradient-to-r from-brand-400 to-brand-600 bg-clip-text text-transparent">
          404
        </p>
        <h1 className="mt-4 text-lg font-semibold">This page does not exist</h1>
        <p className="mt-1 text-sm text-fg-muted">
          The link may be broken, or the post may have been deleted.
        </p>
        <Link
          to="/"
          className="mt-6 inline-block rounded-xl bg-accent px-5 py-2.5 text-sm font-medium text-accent-fg hover:opacity-90 transition-opacity"
        >
          Back to home
        </Link>
      </div>
    </div>
  )
}
