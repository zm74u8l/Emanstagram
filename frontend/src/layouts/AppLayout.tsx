import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, NavLink, Outlet, useLocation, useMatch, useNavigate } from 'react-router-dom'
import {
  Bookmark,
  Compass,
  Heart,
  Home,
  Menu,
  Moon,
  PlusSquare,
  Search,
  Send,
  Settings,
  ShieldCheck,
  Sun,
  LogOut,
} from 'lucide-react'
import { patch } from '@/lib/api'
import { useAuth } from '@/stores/auth'
import { useRealtimeBridge, useUnreadCounts } from '@/hooks/useRealtimeBridge'
import { applyTheme } from '@/hooks/useTheme'
import { Avatar } from '@/components/ui/Avatar'
import { Wordmark } from '@/components/ui/bits'
import { SearchBox } from '@/components/search/Search'
import { cn } from '@/lib/utils'

/**
 * The signed-in shell.
 *
 * Desktop: a left rail, icons only up to 1280px and labelled beyond. Search
 * opens as a panel beside it. Phones: a slim top bar (wordmark + activity)
 * and a bottom tab bar; both hide inside a chat thread to give it the
 * whole screen.
 */
export default function AppLayout() {
  useRealtimeBridge()
  const me = useAuth((s) => s.user)
  const unread = useUnreadCounts()
  const location = useLocation()
  const inThread = useMatch('/messages/:conversationId')
  const [searchOpen, setSearchOpen] = useState(false)

  // Close the search panel whenever the route changes.
  useEffect(() => setSearchOpen(false), [location.pathname])

  if (!me) return null
  const compact = searchOpen

  return (
    <div className="min-h-dvh bg-surface">
      {/* ---------------- desktop rail ---------------- */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 hidden flex-col border-r border-line bg-surface px-3 pb-5 pt-7 md:flex',
          'w-[72px] transition-[width] duration-200',
          !compact && 'xl:w-[244px]',
        )}
      >
        <div className="mb-8 flex h-10 items-center px-2.5">
          <Link to="/" aria-label="Home" className={cn('font-display text-[30px] leading-none', !compact && 'xl:hidden')}>
            E
          </Link>
          {!compact && <Wordmark className="hidden text-[30px] xl:inline" />}
        </div>

        <nav className="flex flex-1 flex-col gap-1">
          <RailLink to="/" end icon={Home} label="Home" compact={compact} />
          <RailButton icon={Search} label="Search" active={searchOpen} compact={compact} onClick={() => setSearchOpen((o) => !o)} />
          <RailLink to="/explore" icon={Compass} label="Explore" compact={compact} />
          <RailLink to="/messages" icon={Send} label="Messages" badge={unread.messages} compact={compact} />
          <RailLink to="/notifications" icon={Heart} label="Notifications" dot={unread.notifications > 0} compact={compact} />
          <RailLink to="/create" icon={PlusSquare} label="Create" compact={compact} />
          <NavLink
            to={`/u/${me.username}`}
            className={({ isActive }) => railItem(isActive)}
          >
            {({ isActive }) => (
              <>
                <Avatar user={me} size="xs" className={cn(isActive && 'rounded-full ring-2 ring-fg ring-offset-1 ring-offset-surface')} />
                <span className={cn('hidden', !compact && 'xl:inline')}>Profile</span>
              </>
            )}
          </NavLink>
        </nav>

        <MoreMenu compact={compact} />
      </aside>

      {/* Search panel beside the rail. */}
      {searchOpen && (
        <>
          <div className="fixed inset-0 z-30 hidden md:block" onClick={() => setSearchOpen(false)} aria-hidden />
          <section
            aria-label="Search"
            className="fixed inset-y-0 left-[72px] z-40 hidden w-[380px] animate-fade-in flex-col rounded-r-[18px] border-r border-line bg-surface px-5 pt-7 shadow-[var(--shadow-pop)] md:flex"
          >
            <h2 className="mb-6 text-[24px] font-semibold tracking-tight">Search</h2>
            <SearchBox autoFocus onNavigate={() => setSearchOpen(false)} className="min-h-0 flex-1" />
          </section>
        </>
      )}

      {/* ---------------- phone top bar ---------------- */}
      {!inThread && (
        <header className="sticky top-0 z-30 flex h-[52px] items-center justify-between border-b border-line bg-surface/90 px-4 backdrop-blur-xl md:hidden">
          <Wordmark className="text-[26px]" />
          <Link to="/notifications" aria-label="Notifications" className="relative grid size-10 place-items-center">
            <Heart size={24} />
            {unread.notifications > 0 && <span className="absolute right-2 top-2 size-2 rounded-full bg-like" />}
          </Link>
        </header>
      )}

      <main className={cn('md:pl-[72px]', !compact && 'xl:pl-[244px]', !inThread && 'pb-[56px] md:pb-0')}>
        <Outlet />
      </main>

      {/* ---------------- phone tab bar ---------------- */}
      {!inThread && (
        <nav className="fixed inset-x-0 bottom-0 z-30 grid h-[52px] grid-cols-5 border-t border-line bg-surface/95 backdrop-blur-xl pb-safe md:hidden">
          <TabLink to="/" end icon={Home} label="Home" />
          <TabLink to="/explore" icon={Search} label="Search" />
          <TabLink to="/create" icon={PlusSquare} label="Create" />
          <TabLink to="/messages" icon={Send} label="Messages" badge={unread.messages} />
          <NavLink to={`/u/${me.username}`} aria-label="Profile" className="grid place-items-center">
            {({ isActive }) => (
              <Avatar user={me} size="xs" className={cn(isActive && 'rounded-full ring-2 ring-fg ring-offset-1 ring-offset-surface')} />
            )}
          </NavLink>
        </nav>
      )}
    </div>
  )
}

type IconType = typeof Home

function railItem(active: boolean) {
  return cn(
    'group relative flex h-12 items-center gap-4 rounded-[10px] px-2.5 text-[15px] transition-colors hover:bg-surface-muted',
    active && 'font-semibold',
  )
}

function Badge({ count }: { count: number }) {
  if (count <= 0) return null
  return (
    <span className="absolute -right-2 -top-1.5 grid h-[18px] min-w-[18px] place-items-center rounded-full border-2 border-surface bg-like px-1 text-[10px] font-bold leading-none text-white">
      {count > 9 ? '9+' : count}
    </span>
  )
}

function RailIcon({ icon: Icon, active, badge, dot }: { icon: IconType; active: boolean; badge?: number; dot?: boolean }) {
  return (
    <span className="relative">
      <Icon size={24} strokeWidth={active ? 2.5 : 1.75} className="transition-transform group-hover:scale-105" />
      {badge !== undefined && <Badge count={badge} />}
      {dot && <span className="absolute -right-0.5 -top-0.5 size-2 rounded-full border border-surface bg-like" />}
    </span>
  )
}

function RailLink({
  to,
  icon,
  label,
  end,
  badge,
  dot,
  compact,
}: {
  to: string
  icon: IconType
  label: string
  end?: boolean
  badge?: number
  dot?: boolean
  compact: boolean
}) {
  return (
    <NavLink to={to} end={end} className={({ isActive }) => railItem(isActive)} title={label}>
      {({ isActive }) => (
        <>
          {isActive && <span className="absolute -left-3 top-1/2 h-5 w-[3px] -translate-y-1/2 rounded-r-full bg-accent" />}
          <RailIcon icon={icon} active={isActive} badge={badge} dot={dot} />
          <span className={cn('hidden', !compact && 'xl:inline')}>{label}</span>
        </>
      )}
    </NavLink>
  )
}

function RailButton({
  icon,
  label,
  active,
  compact,
  onClick,
}: {
  icon: IconType
  label: string
  active: boolean
  compact: boolean
  onClick: () => void
}) {
  return (
    <button onClick={onClick} className={cn(railItem(active), active && 'ring-1 ring-line-strong')} title={label}>
      <RailIcon icon={icon} active={active} />
      <span className={cn('hidden', !compact && 'xl:inline')}>{label}</span>
    </button>
  )
}

function TabLink({ to, icon: Icon, label, end, badge }: { to: string; icon: IconType; label: string; end?: boolean; badge?: number }) {
  return (
    <NavLink to={to} end={end} aria-label={label} className="grid place-items-center">
      {({ isActive }) => (
        <span className="relative">
          <Icon size={25} strokeWidth={isActive ? 2.5 : 1.75} />
          {badge !== undefined && <Badge count={badge} />}
        </span>
      )}
    </NavLink>
  )
}

/** Settings, saved, appearance, moderation and sign-out, tucked behind "More". */
function MoreMenu({ compact }: { compact: boolean }) {
  const me = useAuth((s) => s.user)
  const patchUser = useAuth((s) => s.patchUser)
  const logout = useAuth((s) => s.logout)
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const dark = document.documentElement.classList.contains('dark')

  function toggleAppearance() {
    const next = dark ? 'light' : 'dark'
    applyTheme(next)
    patchUser({ theme: next === 'dark' ? 'DARK' : 'LIGHT' })
    void patch('/api/me', { theme: next === 'dark' ? 'DARK' : 'LIGHT' }).catch(() => {})
    setOpen(false)
  }

  const moderator = me?.role === 'MODERATOR' || me?.role === 'ADMIN'

  return (
    <div ref={ref} className="relative">
      {open && (
        <div
          role="menu"
          className="absolute bottom-14 left-0 w-[266px] animate-rise overflow-hidden rounded-[16px] border border-line bg-surface-elevated p-2 shadow-[var(--shadow-pop)]"
        >
          <MenuItem icon={<Settings size={20} />} label="Settings" onClick={() => navigate('/settings')} close={() => setOpen(false)} />
          <MenuItem icon={<Bookmark size={20} />} label="Saved" onClick={() => navigate('/saved')} close={() => setOpen(false)} />
          <MenuItem
            icon={dark ? <Sun size={20} /> : <Moon size={20} />}
            label={dark ? 'Switch to light' : 'Switch to dark'}
            onClick={toggleAppearance}
            close={() => {}}
          />
          {moderator && (
            <MenuItem icon={<ShieldCheck size={20} />} label="Moderation" onClick={() => navigate('/admin')} close={() => setOpen(false)} />
          )}
          <div className="my-1.5 h-px bg-line" />
          <MenuItem
            icon={<LogOut size={20} />}
            label="Log out"
            onClick={async () => {
              await logout()
              navigate('/login', { replace: true })
            }}
            close={() => setOpen(false)}
          />
        </div>
      )}
      <button onClick={() => setOpen((o) => !o)} className={cn(railItem(open), 'w-full')} aria-haspopup="menu" aria-expanded={open}>
        <Menu size={24} strokeWidth={open ? 2.5 : 1.75} />
        <span className={cn('hidden', !compact && 'xl:inline')}>More</span>
      </button>
    </div>
  )
}

function MenuItem({ icon, label, onClick, close }: { icon: ReactNode; label: string; onClick: () => void; close: () => void }) {
  return (
    <button
      role="menuitem"
      onClick={() => {
        close()
        onClick()
      }}
      className="flex h-11 w-full items-center gap-3 rounded-[10px] px-3 text-[14px] hover:bg-surface-muted"
    >
      {icon}
      {label}
    </button>
  )
}
