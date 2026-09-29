import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, post } from '@/lib/api'
import { useAuth } from '@/stores/auth'
import { AuthLayout, FormError } from '@/components/auth/AuthLayout'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import type { TokenResponse } from '@/lib/types'

export default function Login() {
  const navigate = useNavigate()
  const location = useLocation()
  const applyTokens = useAuth((s) => s.applyTokens)

  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [params] = useSearchParams()
  const suspendedNotice = params.get('suspended') === '1'

  // RequireAuth stashes where the user was headed; send them back there.
  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? '/'

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      const res = await post<TokenResponse>('/api/auth/login', { identifier: identifier.trim(), password })
      applyTokens(res.accessToken, res.refreshToken, res.user)
      navigate(from, { replace: true })
    } catch (err) {
      if (err instanceof ApiError) {
        setError(Object.keys(err.fieldErrors).length ? null : err.message)
        setFieldErrors(err.fieldErrors)
      } else {
        setError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout>
      <h1 className="text-[22px] font-semibold tracking-tight">Sign in</h1>
      <p className="mt-1 text-[14px] text-fg-muted">Use your username or email address.</p>

      <form onSubmit={onSubmit} className="mt-7 space-y-4" noValidate>
        {error ? (
          <FormError>{error}</FormError>
        ) : (
          suspendedNotice && (
            <FormError>You were signed out because this account has been suspended.</FormError>
          )
        )}

        <Input
          label="Username or email"
          name="identifier"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          autoFocus
          value={identifier}
          onChange={(e) => setIdentifier(e.target.value)}
          error={fieldErrors.identifier}
          required
        />

        <Input
          label="Password"
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
          required
        />

        <Button type="submit" size="lg" className="w-full" loading={submitting}>
          Sign in
        </Button>
      </form>

      <p className="mt-8 border-t border-line pt-6 text-[14px] text-fg-muted">
        New to Emanstagram?{' '}
        <Link to="/register" className="font-semibold text-fg underline-offset-4 hover:underline">
          Create an account
        </Link>
      </p>
    </AuthLayout>
  )
}
