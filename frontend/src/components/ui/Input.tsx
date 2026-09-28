import { forwardRef, useState, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

interface FieldProps {
  label?: string
  error?: string
  hint?: ReactNode
  /** Rendered inside the field on the right, e.g. an availability tick. */
  trailing?: ReactNode
}

const FIELD =
  'w-full rounded-[var(--radius-control)] border bg-surface-elevated text-fg placeholder:text-fg-subtle ' +
  'transition-[border-color,box-shadow] duration-150 outline-none ' +
  'focus:border-fg-muted focus:ring-3 focus:ring-accent/15 disabled:opacity-50'

function Label({ htmlFor, children }: { htmlFor: string; children: ReactNode }) {
  return (
    <label htmlFor={htmlFor} className="block text-[13px] font-medium text-fg">
      {children}
    </label>
  )
}

function Message({ id, error, hint }: { id: string; error?: string; hint?: ReactNode }) {
  if (error) {
    return (
      <p id={`${id}-error`} role="alert" className="text-[12.5px] text-danger">
        {error}
      </p>
    )
  }
  if (hint) return <p className="text-[12.5px] text-fg-muted">{hint}</p>
  return null
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, label, error, hint, trailing, id, type, ...props }, ref) => {
    const inputId = id ?? props.name ?? ''
    const [revealed, setRevealed] = useState(false)
    const isPassword = type === 'password'

    return (
      <div className="w-full space-y-1.5">
        {label && <Label htmlFor={inputId}>{label}</Label>}
        <div className="relative">
          <input
            ref={ref}
            id={inputId}
            type={isPassword && revealed ? 'text' : type}
            aria-invalid={error ? 'true' : undefined}
            aria-describedby={error ? `${inputId}-error` : undefined}
            className={cn(
              FIELD,
              'h-11 px-3.5 text-[15px]',
              (isPassword || trailing) && 'pr-16',
              error ? 'border-danger focus:border-danger' : 'border-line-strong',
              className,
            )}
            {...props}
          />
          {isPassword ? (
            <button
              type="button"
              onClick={() => setRevealed((r) => !r)}
              className="absolute inset-y-0 right-0 px-3.5 text-[13px] font-semibold text-fg-muted hover:text-fg"
              aria-label={revealed ? 'Hide password' : 'Show password'}
            >
              {revealed ? 'Hide' : 'Show'}
            </button>
          ) : (
            trailing && <div className="absolute inset-y-0 right-0 flex items-center pr-3.5">{trailing}</div>
          )}
        </div>
        <Message id={inputId} error={error} hint={hint} />
      </div>
    )
  },
)
Input.displayName = 'Input'

type InputProps = InputHTMLAttributes<HTMLInputElement> & FieldProps

type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & FieldProps & { showCount?: boolean }

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, label, error, hint, id, showCount, maxLength, value, ...props }, ref) => {
    const inputId = id ?? props.name ?? ''
    const length = typeof value === 'string' ? value.length : 0
    return (
      <div className="w-full space-y-1.5">
        {label && <Label htmlFor={inputId}>{label}</Label>}
        <textarea
          ref={ref}
          id={inputId}
          value={value}
          maxLength={maxLength}
          aria-invalid={error ? 'true' : undefined}
          className={cn(
            FIELD,
            'min-h-24 resize-none px-3.5 py-2.5 text-[15px] leading-relaxed',
            error ? 'border-danger' : 'border-line-strong',
            className,
          )}
          {...props}
        />
        <div className="flex items-start justify-between gap-3">
          <Message id={inputId} error={error} hint={hint} />
          {showCount && maxLength && (
            <span
              className={cn(
                'ml-auto text-[12px] tabular-nums text-fg-subtle',
                length > maxLength * 0.9 && 'text-fg-muted',
              )}
            >
              {length.toLocaleString()} / {maxLength.toLocaleString()}
            </span>
          )}
        </div>
      </div>
    )
  },
)
Textarea.displayName = 'Textarea'
