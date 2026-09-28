import { Link } from 'react-router-dom'
import { Wordmark } from '@/components/ui/bits'

export default function NotFound() {
  return (
    <div className="flex min-h-dvh flex-col bg-surface px-6 py-8">
      <Wordmark className="text-[28px]" />
      <div className="m-auto max-w-md text-center">
        <p className="font-display text-[120px] leading-none text-fg-subtle">404</p>
        <h1 className="mt-2 font-display text-[36px] leading-tight">This page isn’t here</h1>
        <p className="mt-2 text-[14px] text-fg-muted">The link may be broken, or the post may have been deleted.</p>
        <Link
          to="/"
          className="mt-7 inline-flex h-11 items-center rounded-[var(--radius-control)] bg-surface-inverse px-6 text-[15px] font-semibold text-fg-inverse hover:opacity-85"
        >
          Back to Emanstagram
        </Link>
      </div>
    </div>
  )
}
