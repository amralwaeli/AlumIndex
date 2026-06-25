import { Outlet, NavLink } from 'react-router-dom'
import { useAuth } from '@/contexts/AuthContext'
import { cn } from '@/lib/utils'
import { Users, Upload, Settings, LogOut, LayoutDashboard } from 'lucide-react'

const nav = [
  { to: '/operator/overview',   label: 'Overview',    icon: LayoutDashboard },
  { to: '/operator/customers',  label: 'Customers',   icon: Users },
  { to: '/operator/import',     label: 'Data Import', icon: Upload },
  { to: '/operator/permissions',label: 'Permissions', icon: Settings },
]

function Initials({ name }: { name: string }) {
  const parts = (name ?? '').trim().split(/\s+/)
  const letters = parts.length >= 2
    ? (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
    : (name ?? '?').slice(0, 2).toUpperCase()
  return (
    <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-sapphire/20 text-xs font-semibold text-sapphire">
      {letters}
    </div>
  )
}

export default function SuperAdminLayout() {
  const { user, logout } = useAuth()

  return (
    <div className="flex h-screen bg-ink text-ink-text font-sans">

      {/* Sidebar */}
      <aside className="flex w-60 flex-col border-r border-ink-line bg-ink-panel">

        {/* Brand */}
        <div className="flex h-14 items-center gap-3 px-4 border-b border-ink-line">
          <img src="/utm-logo.png" alt="Universiti Teknologi Malaysia" className="h-8 w-auto object-contain shrink-0" />
          <span className="h-7 w-px bg-ink-line shrink-0" aria-hidden="true" />
          <img src="/ascend-2030.png" alt="ASCEND 2030" className="h-8 w-auto object-contain shrink-0" />
        </div>

        {/* Shell label */}
        <div className="px-5 pt-5 pb-3">
          <span className="font-mono text-[10px] font-semibold text-ink-muted uppercase tracking-[0.15em]">
            Operator Console
          </span>
        </div>

        {/* Nav */}
        <nav className="flex-1 space-y-0.5 px-3 pb-4">
          {nav.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  'group flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-sapphire/10 text-sapphire'
                    : 'text-ink-muted hover:bg-ink-line hover:text-ink-text',
                )
              }
            >
              {({ isActive }) => (
                <>
                  <Icon className={cn('h-4 w-4 shrink-0 transition-colors', isActive ? 'text-sapphire' : 'text-ink-muted group-hover:text-ink-text')} />
                  {label}
                </>
              )}
            </NavLink>
          ))}
        </nav>

        {/* User section */}
        <div className="border-t border-ink-line p-4">
          <div className="flex items-center gap-3 mb-3">
            <Initials name={user?.email ?? 'OP'} />
            <div className="min-w-0">
              <p className="text-xs font-medium text-ink-text truncate">{user?.email}</p>
              <p className="text-[10px] text-ink-muted capitalize">{user?.role ?? 'operator'}</p>
            </div>
          </div>
          <button
            onClick={() => logout()}
            className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-xs text-ink-muted hover:bg-ink-line hover:text-ink-text transition-colors"
          >
            <LogOut className="h-3.5 w-3.5" />
            Sign out
          </button>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-y-auto">
        <Outlet />
      </main>
    </div>
  )
}
