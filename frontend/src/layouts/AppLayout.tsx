import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import {
  Home,
  Compass,
  MessageSquare,
  Bell,
  Plus,
  LogOut,
} from 'lucide-react'
import { useAuth } from '@/stores/auth'
import { Avatar } from '@/components/ui/Avatar'
import { cn } from '@/lib/utils'

const NAV = [
  { to: '/', label: 'Home', icon: Home, end: true },
  { to: '/explore', label: 'Explore', icon: Compass },
  { to: '/messages', label: 'Messages', icon: MessageSquare },
  { to: '/notifications', label: 'Notifications', icon: Bell },
]

export default function AppLayout() {
  const user = useAuth((s) => s.user)
  const logout = useAuth((s) => s.logout)
  const navigate = useNavigate()

  async function onLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-dvh bg-surface">
      {/* Desktop side rail */}
      <aside className="hidden md:flex fixed inset-y-0 left-0 w-16 lg:w-64 flex-col border-r border-line bg-surface-elevated z-30">
        <div className="flex h-16 items-center px-4 lg:px-6">
          <span className="hidden lg:block text-lg font-bold bg-gradient-to-r from-brand-400 to-brand-600 bg-clip-text text-transparent">
            Emanstagram
          </span>
        </div>

        <nav className="flex-1 space-y-1 px-2 lg:px-3">
          {NAV.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-accent/10 text-accent'
                    : 'text-fg-muted hover:bg-surface-muted hover:text-fg',
                )
              }
            >
              <Icon size={20} className="shrink-0" />
              <span className="hidden lg:inline">{label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="border-t border-line p-3 space-y-1">
          <NavLink
            to="/settings"
            className="flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-fg-muted hover:bg-surface-muted hover:text-fg"
          >
            <Avatar user={user} size="xs" />
            <span className="hidden lg:inline truncate">{user?.effectiveName}</span>
          </NavLink>
          <button
            onClick={onLogout}
            className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-fg-muted hover:bg-surface-muted hover:text-fg"
          >
            <LogOut size={20} className="shrink-0" />
            <span className="hidden lg:inline">Log out</span>
          </button>
        </div>
      </aside>

      {/* Mobile top bar */}
      <header className="md:hidden sticky top-0 z-30 h-14 flex items-center justify-between px-4 border-b border-line bg-surface/80 backdrop-blur-lg">
        <span className="text-base font-bold bg-gradient-to-r from-brand-400 to-brand-600 bg-clip-text text-transparent">
          Emanstagram
        </span>
        <NavLink to="/settings" aria-label="Profile">
          <Avatar user={user} size="sm" />
        </NavLink>
      </header>

      {/* Content: offset for the rail on desktop, clear of the mobile bar */}
      <main className="md:pl-16 lg:pl-64 pb-16 md:pb-0">
        <Outlet />
      </main>

      {/* Mobile bottom tab bar */}
      <nav className="md:hidden fixed bottom-0 inset-x-0 z-30 h-16 grid grid-cols-5 border-t border-line bg-surface/90 backdrop-blur-lg pb-[env(safe-area-inset-bottom)]">
        {NAV.slice(0, 2).map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            aria-label={label}
            className={({ isActive }) =>
              cn('grid place-items-center', isActive ? 'text-accent' : 'text-fg-subtle')
            }
          >
            <Icon size={22} />
          </NavLink>
        ))}

        <NavLink
          to="/create"
          aria-label="Create post"
          className="grid place-items-center"
        >
          <span className="grid size-11 place-items-center rounded-2xl bg-accent text-accent-fg shadow-[var(--shadow-pop)] active:scale-95 transition-transform">
            <Plus size={24} />
          </span>
        </NavLink>

        {NAV.slice(2).map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            aria-label={label}
            className={({ isActive }) =>
              cn('grid place-items-center', isActive ? 'text-accent' : 'text-fg-subtle')
            }
          >
            <Icon size={22} />
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
