import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Monitor, Moon, Sun } from 'lucide-react'
import { ApiError, del, errorMessage, get, patch, post, tokenStore, upload } from '@/lib/api'
import { prepareAvatar, prepareImage } from '@/lib/media'
import { cn, formatBytes } from '@/lib/utils'
import { toast, toastError } from '@/stores/toast'
import { useAuth } from '@/stores/auth'
import { applyAccent, applyTheme, type Theme } from '@/hooks/useTheme'
import { useDebounced } from '@/hooks/useDebounced'
import { Avatar } from '@/components/ui/Avatar'
import { Button } from '@/components/ui/Button'
import { Input, Textarea } from '@/components/ui/Input'
import { ConfirmDialog } from '@/components/ui/Dialog'
import { Spinner } from '@/components/ui/Spinner'
import { UserRow } from '@/components/social'
import type { StorageUsage, TokenResponse, User, UserSummary } from '@/lib/types'

type Section = 'profile' | 'appearance' | 'account' | 'blocked'

const SECTIONS: { value: Section; label: string }[] = [
  { value: 'profile', label: 'Edit profile' },
  { value: 'appearance', label: 'Appearance' },
  { value: 'account', label: 'Password & sessions' },
  { value: 'blocked', label: 'Blocked accounts' },
]

export default function Settings() {
  const [section, setSection] = useState<Section>('profile')

  return (
    <div className="mx-auto flex w-full max-w-[935px] flex-col md:flex-row md:gap-10 md:px-6 md:py-10">
      <nav className="flex gap-1 overflow-x-auto border-b border-line px-4 py-3 scrollbar-none md:w-56 md:shrink-0 md:flex-col md:border-0 md:px-0 md:py-0">
        <h1 className="mb-4 hidden font-display text-[36px] leading-none md:block">Settings</h1>
        {SECTIONS.map((s) => (
          <button
            key={s.value}
            onClick={() => setSection(s.value)}
            className={cn(
              'h-10 shrink-0 rounded-[10px] px-3 text-left text-[14px] transition-colors',
              section === s.value ? 'bg-surface-muted font-semibold' : 'text-fg-muted hover:bg-surface-muted hover:text-fg',
            )}
          >
            {s.label}
          </button>
        ))}
      </nav>

      <div className="min-w-0 flex-1 px-4 py-6 md:px-0 md:py-0">
        {section === 'profile' && <EditProfile />}
        {section === 'appearance' && <Appearance />}
        {section === 'account' && <Account />}
        {section === 'blocked' && <Blocked />}
      </div>
    </div>
  )
}

function Heading({ title, body }: { title: string; body?: string }) {
  return (
    <div className="mb-6">
      <h2 className="text-[20px] font-semibold tracking-tight">{title}</h2>
      {body && <p className="mt-1 text-[14px] text-fg-muted">{body}</p>}
    </div>
  )
}

// ---------------------------------------------------------------------

function EditProfile() {
  const me = useAuth((s) => s.user)!
  const setUser = useAuth((s) => s.setUser)
  const qc = useQueryClient()

  const [form, setForm] = useState({
    username: me.username,
    displayName: me.displayName ?? '',
    pronouns: me.pronouns ?? '',
    bio: me.bio ?? '',
    website: me.website ?? '',
    location: me.location ?? '',
  })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const set = (key: keyof typeof form) => (value: string) => setForm((f) => ({ ...f, [key]: value }))

  // Live username check, skipped when it hasn't changed.
  const wanted = useDebounced(form.username.trim(), 350)
  const availability = useQuery({
    queryKey: ['username-available', wanted],
    queryFn: () => get<{ available: boolean; message?: string }>(`/api/auth/username-available?username=${encodeURIComponent(wanted)}`),
    enabled: wanted.length >= 3 && wanted.toLowerCase() !== me.username.toLowerCase(),
  })

  const save = useMutation({
    mutationFn: () => patch<User>('/api/me', form),
    onSuccess: (user) => {
      setUser(user)
      setErrors({})
      qc.invalidateQueries({ queryKey: ['profile'] })
      toast('Profile saved')
    },
    onError: (err) => {
      if (err instanceof ApiError) {
        const fields = { ...err.fieldErrors }
        if (err.code === 'USERNAME_TAKEN') fields.username = err.message
        if (err.code === 'INVALID_WEBSITE') fields.website = err.message
        setErrors(fields)
        if (!Object.keys(fields).length) toastError(err.message)
      } else {
        toastError(errorMessage(err))
      }
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    save.mutate()
  }

  const usernameError =
    errors.username ?? (availability.data && !availability.data.available ? availability.data.message : undefined)

  return (
    <form onSubmit={submit} className="max-w-[560px]">
      <Heading title="Edit profile" />
      <PictureRow />
      <BannerRow />

      <div className="mt-8 space-y-5">
        <Input
          label="Username"
          name="username"
          value={form.username}
          onChange={(e) => set('username')(e.target.value)}
          error={usernameError}
          maxLength={30}
          autoCapitalize="none"
          trailing={availability.isFetching ? <Spinner size={14} className="text-fg-subtle" /> : availability.data?.available ? <Check size={16} className="text-emerald-600" /> : null}
        />
        <div className="grid gap-5 sm:grid-cols-[1fr_160px]">
          <Input label="Name" name="displayName" value={form.displayName} onChange={(e) => set('displayName')(e.target.value)} error={errors.displayName} maxLength={80} />
          <Input label="Pronouns" name="pronouns" value={form.pronouns} onChange={(e) => set('pronouns')(e.target.value)} error={errors.pronouns} maxLength={40} placeholder="she/her" />
        </div>
        <Textarea label="Bio" name="bio" value={form.bio} onChange={(e) => set('bio')(e.target.value)} error={errors.bio} maxLength={500} showCount />
        <Input label="Website" name="website" value={form.website} onChange={(e) => set('website')(e.target.value)} error={errors.website} placeholder="example.com" inputMode="url" />
        <Input label="Location" name="location" value={form.location} onChange={(e) => set('location')(e.target.value)} error={errors.location} maxLength={120} />
      </div>

      <Button type="submit" className="mt-8 min-w-32" loading={save.isPending}>
        Save
      </Button>
    </form>
  )
}

function PictureRow() {
  const me = useAuth((s) => s.user)!
  const setUser = useAuth((s) => s.setUser)
  const [busy, setBusy] = useState(false)
  const input = useRef<HTMLInputElement>(null)

  async function onPick(file?: File) {
    if (!file) return
    setBusy(true)
    try {
      const prepared = await prepareAvatar(file)
      const form = new FormData()
      form.append('file', prepared.file)
      setUser(await upload<User>('/api/me/avatar', form, { method: 'PUT' }))
      toast('Profile photo updated')
    } catch (err) {
      toastError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  async function onRemove() {
    try {
      setUser(await del<User>('/api/me/avatar'))
    } catch (err) {
      toastError(errorMessage(err))
    }
  }

  return (
    <div className="flex items-center gap-4 rounded-[var(--radius-card)] bg-surface-muted p-4">
      <div className="relative">
        <Avatar user={me} size="lg" />
        {busy && (
          <div className="absolute inset-0 grid place-items-center rounded-full bg-black/40 text-white">
            <Spinner size={18} />
          </div>
        )}
      </div>
      <div className="min-w-0 flex-1 leading-tight">
        <p className="truncate text-[15px] font-semibold">{me.username}</p>
        <p className="truncate text-[13px] text-fg-muted">{me.effectiveName}</p>
      </div>
      {me.avatarUrl && (
        <Button type="button" variant="ghost" size="sm" onClick={onRemove}>
          Remove
        </Button>
      )}
      <Button type="button" variant="accent" size="sm" onClick={() => input.current?.click()} disabled={busy}>
        Change photo
      </Button>
      <input ref={input} type="file" accept="image/*" className="hidden" onChange={(e) => onPick(e.target.files?.[0])} />
    </div>
  )
}

function BannerRow() {
  const me = useAuth((s) => s.user)!
  const setUser = useAuth((s) => s.setUser)
  const [busy, setBusy] = useState(false)
  const input = useRef<HTMLInputElement>(null)

  async function onPick(file?: File) {
    if (!file) return
    setBusy(true)
    try {
      const prepared = await prepareImage(file, 1800)
      const form = new FormData()
      form.append('file', prepared.file)
      setUser(await upload<User>('/api/me/banner', form, { method: 'PUT' }))
      toast('Banner updated')
    } catch (err) {
      toastError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mt-3">
      <div className="relative aspect-[3/1] overflow-hidden rounded-[var(--radius-card)] border border-dashed border-line-strong bg-surface-muted">
        {me.bannerUrl ? (
          <img src={me.bannerUrl} alt="" className="size-full object-cover" />
        ) : (
          <p className="grid size-full place-items-center text-[13px] text-fg-muted">No banner. Profiles look fine without one.</p>
        )}
        {busy && (
          <div className="absolute inset-0 grid place-items-center bg-black/40 text-white">
            <Spinner />
          </div>
        )}
      </div>
      <div className="mt-2 flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={() => input.current?.click()} disabled={busy}>
          {me.bannerUrl ? 'Change banner' : 'Add banner'}
        </Button>
        {me.bannerUrl && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => del<User>('/api/me/banner').then(setUser, (err) => toastError(errorMessage(err)))}
          >
            Remove
          </Button>
        )}
      </div>
      <input ref={input} type="file" accept="image/*" className="hidden" onChange={(e) => onPick(e.target.files?.[0])} />
    </div>
  )
}

// ---------------------------------------------------------------------

const ACCENTS = ['#6366f1', '#2563eb', '#0891b2', '#059669', '#ca8a04', '#ea580c', '#e11d48', '#c026d3', '#737373']

function Appearance() {
  const me = useAuth((s) => s.user)!
  const patchUser = useAuth((s) => s.patchUser)
  const [theme, setTheme] = useState<Theme>(me.theme.toLowerCase() as Theme)
  const [accent, setAccent] = useState(me.accentColor.toLowerCase())

  // Applied instantly, saved to the account so it follows you across devices.
  const persist = useMutation({
    mutationFn: (body: Partial<Pick<User, 'theme' | 'accentColor'>>) => patch<User>('/api/me', body),
    onError: (err) => toastError(errorMessage(err)),
  })

  function chooseTheme(next: Theme) {
    setTheme(next)
    applyTheme(next)
    const value = next.toUpperCase() as User['theme']
    patchUser({ theme: value })
    persist.mutate({ theme: value })
  }

  function chooseAccent(next: string) {
    setAccent(next)
    applyAccent(next)
    patchUser({ accentColor: next })
    persist.mutate({ accentColor: next })
  }

  const themes: { value: Theme; label: string; icon: ReactNode }[] = [
    { value: 'light', label: 'Light', icon: <Sun size={18} /> },
    { value: 'dark', label: 'Dark', icon: <Moon size={18} /> },
    { value: 'system', label: 'Match device', icon: <Monitor size={18} /> },
  ]

  return (
    <div className="max-w-[560px]">
      <Heading title="Appearance" body="Saved to your account, so it follows you to other devices." />

      <h3 className="mb-2 text-[13px] font-medium">Theme</h3>
      <div className="grid grid-cols-3 gap-2">
        {themes.map((t) => (
          <button
            key={t.value}
            onClick={() => chooseTheme(t.value)}
            aria-pressed={theme === t.value}
            className={cn(
              'flex flex-col items-center gap-2 rounded-[var(--radius-card)] border py-4 text-[13px] font-medium transition-colors',
              theme === t.value ? 'border-fg' : 'border-line-strong text-fg-muted hover:text-fg',
            )}
          >
            {t.icon}
            {t.label}
          </button>
        ))}
      </div>

      <h3 className="mb-1 mt-8 text-[13px] font-medium">Accent</h3>
      <p className="mb-3 text-[13px] text-fg-muted">Used sparingly: links, story rings, unread markers and the active tab.</p>
      <div className="flex flex-wrap gap-3">
        {ACCENTS.map((c) => (
          <button
            key={c}
            onClick={() => chooseAccent(c)}
            aria-label={`Accent ${c}`}
            aria-pressed={accent === c}
            className={cn(
              'grid size-9 place-items-center rounded-full transition-transform hover:scale-110',
              accent === c && 'ring-2 ring-fg ring-offset-2 ring-offset-surface',
            )}
            style={{ backgroundColor: c }}
          >
            {accent === c && <Check size={16} className="text-white" strokeWidth={3} />}
          </button>
        ))}
      </div>

      <div className="mt-8 rounded-[var(--radius-card)] border border-line p-4">
        <p className="text-[12px] font-semibold uppercase tracking-wide text-fg-subtle">Preview</p>
        <p className="mt-2 text-[14px]">
          <span className="font-semibold">{me.username}</span> liked a photo by{' '}
          <span className="text-accent">@someone</span> <span className="text-accent">#weekend</span>
        </p>
        <div className="mt-3 flex items-center gap-3">
          <Avatar user={me} ring="unseen" size="md" />
          <Button size="sm">Follow</Button>
          <span className="size-2 rounded-full bg-accent" />
        </div>
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------

function Account() {
  const navigate = useNavigate()
  const logout = useAuth((s) => s.logout)
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [confirmAll, setConfirmAll] = useState(false)

  const change = useMutation({
    mutationFn: () => post<TokenResponse>('/api/me/password', { currentPassword: current, newPassword: next }),
    onSuccess: (res) => {
      tokenStore.set(res.accessToken, res.refreshToken)
      setCurrent('')
      setNext('')
      setErrors({})
      toast('Password changed. Other devices have been signed out.')
    },
    onError: (err) => {
      if (err instanceof ApiError) {
        const fields = { ...err.fieldErrors }
        if (err.code === 'WRONG_PASSWORD') fields.currentPassword = err.message
        setErrors(fields)
      } else toastError(errorMessage(err))
    },
  })

  const logoutAll = useMutation({
    mutationFn: () => post('/api/auth/logout-all'),
    onSuccess: async () => {
      await logout()
      navigate('/login', { replace: true })
    },
    onError: (err) => toastError(errorMessage(err)),
  })

  return (
    <div className="max-w-[560px]">
      <StorageMeter />

      <Heading title="Change password" body="You'll stay signed in here. Every other device is signed out." />
      <form
        onSubmit={(e) => {
          e.preventDefault()
          change.mutate()
        }}
        className="space-y-4"
      >
        <Input label="Current password" name="currentPassword" type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} error={errors.currentPassword} />
        <Input label="New password" name="newPassword" type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} error={errors.newPassword} hint="At least 8 characters." />
        <Button type="submit" disabled={!current || next.length < 8} loading={change.isPending}>
          Change password
        </Button>
      </form>

      <div className="mt-12 border-t border-line pt-8">
        <Heading title="Sessions" />
        <div className="flex flex-wrap gap-2">
          <Button
            variant="secondary"
            onClick={async () => {
              await logout()
              navigate('/login', { replace: true })
            }}
          >
            Log out
          </Button>
          <Button variant="ghost" className="text-danger" onClick={() => setConfirmAll(true)}>
            Log out of all devices
          </Button>
        </div>
      </div>

      <ConfirmDialog
        open={confirmAll}
        onClose={() => setConfirmAll(false)}
        onConfirm={() => logoutAll.mutate()}
        busy={logoutAll.isPending}
        title="Log out everywhere?"
        body="Every device signed in to this account, including this one, will need to sign in again."
        confirmLabel="Log out everywhere"
      />
    </div>
  )
}

/** How much of the storage quota and today's upload budget is used. */
function StorageMeter() {
  const { data } = useQuery({ queryKey: ['storage'], queryFn: () => get<StorageUsage>('/api/me/storage') })
  if (!data) return null
  const pct = Math.min(100, (data.usedBytes / data.quotaBytes) * 100)
  return (
    <section className="mb-12 border-b border-line pb-8">
      <Heading title="Storage" body="Photos, videos, stories and chat attachments all count." />
      <div className="h-2 overflow-hidden rounded-full bg-surface-muted">
        <div className={cn('h-full rounded-full', pct > 90 ? 'bg-danger' : 'bg-fg')} style={{ width: `${Math.max(pct, 1)}%` }} />
      </div>
      <p className="mt-2 text-[13px] text-fg-muted">
        <span className="font-semibold text-fg">{formatBytes(data.usedBytes)}</span> of {formatBytes(data.quotaBytes)} used
      </p>
      <p className="mt-1 text-[13px] text-fg-muted">
        Today: {data.uploadsToday} of {data.dailyLimitFiles} uploads, {formatBytes(data.uploadedTodayBytes)} of{' '}
        {formatBytes(data.dailyLimitBytes)}
        {data.newAccount && ' (new accounts have a smaller daily allowance for their first day)'}
      </p>
    </section>
  )
}

function Blocked() {
  const qc = useQueryClient()
  const { data, isLoading } = useQuery({ queryKey: ['blocked'], queryFn: () => get<UserSummary[]>('/api/me/blocked') })
  const [pending, setPending] = useState<string | null>(null)

  useEffect(() => setPending(null), [data])

  async function unblock(u: UserSummary) {
    setPending(u.id)
    try {
      await del(`/api/users/${u.id}/block`)
      qc.invalidateQueries({ queryKey: ['blocked'] })
      qc.invalidateQueries({ queryKey: ['profile', u.username] })
      toast(`Unblocked @${u.username}`)
    } catch (err) {
      toastError(errorMessage(err))
      setPending(null)
    }
  }

  return (
    <div className="max-w-[560px]">
      <Heading title="Blocked accounts" body="They can’t see your posts or message you, and you won’t see theirs." />
      {isLoading && <Spinner />}
      {data?.length === 0 && <p className="text-[14px] text-fg-muted">You haven’t blocked anyone.</p>}
      {data?.map((u) => (
        <UserRow
          key={u.id}
          user={u}
          trailing={
            <Button variant="secondary" size="sm" loading={pending === u.id} onClick={() => unblock(u)}>
              Unblock
            </Button>
          }
        />
      ))}
    </div>
  )
}
