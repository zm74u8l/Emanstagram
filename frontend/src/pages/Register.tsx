import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Check, X } from 'lucide-react'
import { ApiError, get, post } from '@/lib/api'
import { useAuth } from '@/stores/auth'
import { AuthLayout, FormError } from '@/components/auth/AuthLayout'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Spinner } from '@/components/ui/Spinner'
import { useDebounced } from '@/hooks/useDebounced'
import { cn } from '@/lib/utils'
import type { TokenResponse } from '@/lib/types'

type Availability = { state: 'idle' | 'checking' } | { state: 'done'; available: boolean; message?: string }

export default function Register() {
  const navigate = useNavigate()
  const applyTokens = useAuth((s) => s.applyTokens)

  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [availability, setAvailability] = useState<Availability>({ state: 'idle' })

  // Check the username while the person types, so a clash is caught
  // before they've filled in the rest of the form.
  const debounced = useDebounced(username.trim(), 350)
  useEffect(() => {
    if (debounced.length < 3) {
      setAvailability({ state: 'idle' })
      return
    }
    let cancelled = false
    setAvailability({ state: 'checking' })
    get<{ available: boolean; message?: string }>(
      `/api/auth/username-available?username=${encodeURIComponent(debounced)}`,
    )
      .then((r) => !cancelled && setAvailability({ state: 'done', ...r }))
      .catch(() => !cancelled && setAvailability({ state: 'idle' }))
    return () => {
      cancelled = true
    }
  }, [debounced])

  const passwordOk = password.length >= 8

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      const res = await post<TokenResponse>('/api/auth/register', {
        username: username.trim(),
        email: email.trim(),
        password,
      })
      applyTokens(res.accessToken, res.refreshToken, res.user)
      navigate('/', { replace: true })
    } catch (err) {
      if (err instanceof ApiError) {
        const fields = { ...err.fieldErrors }
        if (err.code === 'USERNAME_TAKEN') fields.username = err.message
        if (err.code === 'EMAIL_TAKEN') fields.email = err.message
        setFieldErrors(fields)
        setError(Object.keys(fields).length ? null : err.message)
      } else {
        setError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const usernameTrailing =
    availability.state === 'checking' ? (
      <Spinner size={16} className="text-fg-subtle" />
    ) : availability.state === 'done' ? (
      availability.available ? (
        <Check size={18} className="text-emerald-600" aria-label="Available" />
      ) : (
        <X size={18} className="text-danger" aria-label="Unavailable" />
      )
    ) : null

  const usernameError =
    fieldErrors.username ??
    (availability.state === 'done' && !availability.available ? availability.message : undefined)

  return (
    <AuthLayout>
      <h1 className="text-[22px] font-semibold tracking-tight">Create an account</h1>
      <p className="mt-1 text-[14px] text-fg-muted">It takes less than a minute.</p>

      <form onSubmit={onSubmit} className="mt-7 space-y-4" noValidate>
        {error && <FormError>{error}</FormError>}

        <Input
          label="Username"
          name="username"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          autoFocus
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          error={usernameError}
          hint="Letters, numbers, dots and underscores."
          trailing={usernameTrailing}
          maxLength={30}
          required
        />

        <Input
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={fieldErrors.email}
          required
        />

        <Input
          label="Password"
          name="password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
          hint={
            <span className={cn('inline-flex items-center gap-1', passwordOk && 'text-emerald-600')}>
              {passwordOk && <Check size={13} />}
              At least 8 characters
            </span>
          }
          required
        />

        <Button type="submit" size="lg" className="w-full" loading={submitting}>
          Create account
        </Button>
      </form>

      <p className="mt-8 border-t border-line pt-6 text-[14px] text-fg-muted">
        Already have an account?{' '}
        <Link to="/login" className="font-semibold text-fg underline-offset-4 hover:underline">
          Sign in
        </Link>
      </p>
    </AuthLayout>
  )
}
