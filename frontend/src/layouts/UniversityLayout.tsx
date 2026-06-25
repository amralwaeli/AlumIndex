import { useState, useEffect, useCallback } from 'react'
import { Outlet, NavLink } from 'react-router-dom'
import { useAuth } from '@/contexts/AuthContext'
import api from '@/lib/api'
import { cn } from '@/lib/utils'
import {
  LayoutDashboard, Users, Heart, Bell,
  FileText, ClipboardList, Settings, LogOut,
} from 'lucide-react'

const nav = [
  { to: '/university/dashboard', label: 'Dashboard',       icon: LayoutDashboard },
  { to: '/university/alumni',    label: 'Alumni Database', icon: Users },
  { to: '/university/donors',    label: 'Donor Insights',  icon: Heart },
  { to: '/university/alerts',    label: 'Alerts',          icon: Bell },
  { to: '/university/reports',   label: 'Reports',         icon: FileText },
  { to: '/university/audit',     label: 'Audit Log',       icon: ClipboardList },
  { to: '/university/settings',  label: 'Settings',        icon: Settings },
]

function Initials({ name }: { name: string }) {
  const parts = (name ?? '').trim().split(/\s+/)
  const letters = parts.length >= 2
    ? (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
    : (name ?? '?').slice(0, 2).toUpperCase()
  return (
    <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-sapphire-soft text-xs font-semibold text-sapphire">
      {letters}
    </div>
  )
}

export default function UniversityLayout() {
  const { user, logout } = useAuth()
  const isAdmin = user?.role === 'admin'
  const [unreadAlerts, setUnreadAlerts] = useState(0)

  const refreshUnread = useCallback(() => {
    api.get<{ count: number }>('/api/alerts/unread-count')
      .then(res => setUnreadAlerts(res.data.count))
      .catch(() => { /* badge stays as-is */ })
  }, [])

  useEffect(() => {
    refreshUnread()
    window.addEventListener('alerts-changed', refreshUnread)
    return () => window.removeEventListener('alerts-changed', refreshUnread)
  }, [refreshUnread])

  return (
    <div className="flex h-screen bg-bone text-text font-sans">

      {/* Sidebar */}
      <aside className="flex w-60 flex-col border-r border-line bg-surface">

        {/* Brand */}
        <div className="flex h-14 items-center gap-3 px-4 border-b border-line">
          <img src="/utm-logo.png" alt="Universiti Teknologi Malaysia" className="h-8 w-auto object-contain shrink-0" />
          <span className="h-7 w-px bg-line shrink-0" aria-hidden="true" />
          <img src="/ascend-2030.png" alt="ASCEND 2030" className="h-8 w-auto object-contain shrink-0" />
        </div>

        {/* Role label */}
        <div className="px-5 pt-5 pb-3">
          <span className="font-mono text-[10px] font-semibold text-muted uppercase tracking-[0.15em]">
            {isAdmin ? 'Admin Portal' : 'Read Only'}
          </span>
        </div>

        {/* Nav */}
        <nav className="flex-1 space-y-0.5 px-3 pb-4 overflow-y-auto">
          {nav.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  'group flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-sapphire-soft text-sapphire'
                    : 'text-muted hover:bg-bone hover:text-text',
                )
              }
            >
              {({ isActive }) => (
                <>
                  <Icon className={cn('h-4 w-4 shrink-0 transition-colors', isActive ? 'text-sapphire' : 'text-muted group-hover:text-text')} />
                  <span className="flex-1">{label}</span>
                  {label === 'Alerts' && unreadAlerts > 0 && (
                    <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-danger px-1.5 text-[10px] font-semibold text-white">
                      {unreadAlerts > 99 ? '99+' : unreadAlerts}
                    </span>
                  )}
                </>
              )}
            </NavLink>
          ))}
        </nav>

        {/* User section */}
        <div className="border-t border-line p-4">
          <div className="flex items-center gap-3 mb-3">
            <Initials name={user?.fullName ?? user?.email ?? '?'} />
            <div className="min-w-0">
              <p className="text-xs font-medium text-text truncate">{user?.fullName}</p>
              <p className="text-[10px] text-muted truncate">{user?.email}</p>
            </div>
          </div>
          <button
            onClick={() => logout()}
            className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-xs text-muted hover:bg-bone hover:text-text transition-colors"
          >
            <LogOut className="h-3.5 w-3.5" />
            Sign out
          </button>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-y-auto bg-bone">
        <Outlet />
      </main>
    </div>
  )
}
