import { useEffect, useRef, type ReactNode } from 'react'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'

interface DialogProps {
  open: boolean
  onClose: () => void
  title?: ReactNode
  children: ReactNode
  className?: string
  /** Hide the header row entirely (for action sheets and media). */
  bare?: boolean
}

/**
 * Built on the native <dialog>: focus trapping, Escape to close, inert
 * background and top-layer stacking come from the browser, not a library.
 * On phones it docks to the bottom as a sheet.
 */
export function Dialog({ open, onClose, title, children, className, bare }: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    if (open && !el.open) el.showModal()
    if (!open && el.open) el.close()
  }, [open])

  // Scroll-lock the page behind the modal.
  useEffect(() => {
    if (!open) return
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = prev
    }
  }, [open])

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onCancel={(e) => {
        e.preventDefault()
        onClose()
      }}
      // A click that lands on the <dialog> itself (not its content) is the backdrop.
      onClick={(e) => {
        if (e.target === ref.current) onClose()
      }}
      className={cn(
        'm-auto w-[calc(100%-2rem)] max-w-md overflow-hidden rounded-[var(--radius-sheet)] bg-surface-elevated p-0 text-fg',
        'shadow-[var(--shadow-pop)] backdrop:animate-fade-in open:animate-rise',
        'max-md:mb-0 max-md:w-full max-md:max-w-full max-md:rounded-b-none max-md:pb-safe',
        className,
      )}
    >
      {open && (
        <>
          {!bare && (
            <header className="relative flex h-12 items-center justify-center border-b border-line px-12">
              <h2 className="truncate text-[15px] font-semibold">{title}</h2>
              <button
                onClick={onClose}
                aria-label="Close"
                className="absolute right-2 grid size-9 place-items-center rounded-full text-fg-muted hover:bg-surface-muted hover:text-fg"
              >
                <X size={20} />
              </button>
            </header>
          )}
          {children}
        </>
      )}
    </dialog>
  )
}

export interface SheetAction {
  label: string
  onClick: () => void
  tone?: 'danger' | 'strong'
}

/** The stacked list of actions behind a "•••" button. */
export function ActionSheet({
  open,
  onClose,
  actions,
}: {
  open: boolean
  onClose: () => void
  actions: SheetAction[]
}) {
  return (
    <Dialog open={open} onClose={onClose} bare className="max-w-sm">
      <ul className="divide-y divide-line text-center text-[14px]">
        {actions.map((a) => (
          <li key={a.label}>
            <button
              onClick={() => {
                onClose()
                a.onClick()
              }}
              className={cn(
                'h-12 w-full hover:bg-surface-muted',
                a.tone === 'danger' && 'font-semibold text-danger',
                a.tone === 'strong' && 'font-semibold',
              )}
            >
              {a.label}
            </button>
          </li>
        ))}
        <li>
          <button onClick={onClose} className="h-12 w-full text-fg-muted hover:bg-surface-muted">
            Cancel
          </button>
        </li>
      </ul>
    </Dialog>
  )
}

/** Yes/no confirmation for destructive actions. */
export function ConfirmDialog({
  open,
  onClose,
  onConfirm,
  title,
  body,
  confirmLabel,
  busy,
}: {
  open: boolean
  onClose: () => void
  onConfirm: () => void
  title: string
  body?: ReactNode
  confirmLabel: string
  busy?: boolean
}) {
  return (
    <Dialog open={open} onClose={onClose} bare className="max-w-sm">
      <div className="px-6 pb-4 pt-7 text-center">
        <h2 className="text-[17px] font-semibold">{title}</h2>
        {body && <p className="mt-1.5 text-[14px] text-fg-muted">{body}</p>}
      </div>
      <div className="divide-y divide-line border-t border-line text-[14px]">
        <button
          onClick={onConfirm}
          disabled={busy}
          className="h-12 w-full font-semibold text-danger hover:bg-surface-muted disabled:opacity-50"
        >
          {busy ? 'Working…' : confirmLabel}
        </button>
        <button onClick={onClose} className="h-12 w-full hover:bg-surface-muted">
          Cancel
        </button>
      </div>
    </Dialog>
  )
}
