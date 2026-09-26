import type { ReactNode } from 'react'

/** Shared centred card for the login and register screens. */
export function AuthShell({
  title,
  subtitle,
  children,
}: {
  title: string
  subtitle: string
  children: ReactNode
}) {
  return (
    <main className="min-h-dvh grid place-items-center px-4 py-12 bg-surface">
      <div className="w-full max-w-sm animate-fade-in">
        <div className="mb-8 text-center">
          <div className="mb-3 text-4xl font-bold tracking-tight bg-gradient-to-r from-brand-400 to-brand-600 bg-clip-text text-transparent">
            Emanstagram
          </div>
          <h1 className="text-xl font-semibold text-fg">{title}</h1>
          <p className="mt-1 text-sm text-fg-muted">{subtitle}</p>
        </div>

        <div className="rounded-2xl border border-line bg-surface-elevated p-6 shadow-[var(--shadow-card)]">
          {children}
        </div>
      </div>
    </main>
  )
}
