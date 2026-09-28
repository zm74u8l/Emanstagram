import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { cn } from '@/lib/utils'
import { Spinner } from './Spinner'

type Variant = 'primary' | 'accent' | 'secondary' | 'ghost' | 'danger' | 'link'
type Size = 'sm' | 'md' | 'lg' | 'icon' | 'icon-sm'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
}

/**
 * Primary is solid ink, not the accent colour. The accent is kept for
 * signals (active, unread, links), so an app full of buttons still reads
 * as calm.
 */
const VARIANTS: Record<Variant, string> = {
  primary: 'bg-surface-inverse text-fg-inverse hover:opacity-85 active:opacity-75',
  accent: 'bg-accent text-accent-fg hover:opacity-90 active:opacity-80',
  secondary: 'bg-surface-muted text-fg hover:bg-line active:bg-line-strong',
  ghost: 'text-fg hover:bg-surface-muted active:bg-line',
  danger: 'bg-danger text-white hover:opacity-90 active:opacity-80',
  link: 'text-accent hover:underline underline-offset-2 px-0 h-auto',
}

const SIZES: Record<Size, string> = {
  sm: 'h-8 px-3 text-[13px] gap-1.5',
  md: 'h-9 px-4 text-sm gap-2',
  lg: 'h-11 px-5 text-[15px] gap-2',
  icon: 'size-10 p-0',
  'icon-sm': 'size-8 p-0',
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'primary', size = 'md', loading, disabled, children, type = 'button', ...props }, ref) => (
    <button
      ref={ref}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(
        'relative inline-flex shrink-0 items-center justify-center rounded-[var(--radius-control)] font-semibold',
        'transition-[background-color,opacity,transform] duration-150 select-none',
        'disabled:opacity-40 disabled:pointer-events-none',
        VARIANTS[variant],
        SIZES[size],
        className,
      )}
      {...props}
    >
      {loading ? (
        <>
          <span className="invisible contents">{children}</span>
          <span className="absolute inset-0 grid place-items-center">
            <Spinner size={16} />
          </span>
        </>
      ) : (
        children
      )}
    </button>
  ),
)
Button.displayName = 'Button'
