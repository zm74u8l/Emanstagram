import type { ReactNode } from 'react'
import { Wordmark } from '@/components/ui/bits'
import { Mosaic } from './Mosaic'

/**
 * Split layout for sign-in and sign-up: live photo mosaic on the left, a
 * plain form on the right with no card around it. On phones the mosaic
 * collapses to a short strip above the form.
 */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-dvh bg-surface lg:grid lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)]">
      <Mosaic className="hidden lg:block lg:h-dvh lg:sticky lg:top-0" columns={3} />
      <Mosaic className="h-36 lg:hidden [&_p]:hidden" columns={4} />

      <main className="flex min-h-[calc(100dvh-9rem)] flex-col px-6 pt-10 pb-8 sm:px-10 lg:min-h-dvh lg:justify-center lg:px-16 xl:px-24">
        <div className="w-full max-w-[380px] animate-rise lg:mx-0 mx-auto">
          <Wordmark to={null} className="text-[46px]" />
          <div className="mt-10">{children}</div>
        </div>
        <footer className="mx-auto mt-auto w-full max-w-[380px] pt-12 text-[12px] text-fg-subtle lg:mx-0 lg:mt-0 lg:pt-16">
          © {new Date().getFullYear()} Emanstagram
        </footer>
      </main>
    </div>
  )
}

/** A form-level error: a quiet bordered notice rather than a red slab. */
export function FormError({ children }: { children: ReactNode }) {
  return (
    <div role="alert" className="rounded-[var(--radius-control)] border border-danger/40 border-l-4 border-l-danger bg-danger/5 px-3.5 py-2.5 text-[13.5px] text-fg">
      {children}
    </div>
  )
}
