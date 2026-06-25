import { Navigate } from 'react-router-dom'
import { useAuth } from '@/contexts/AuthContext'
import type { Role } from '@/types'
import type { ReactNode } from 'react'

interface Props {
  allowedRoles: Role[]
  children: ReactNode
}

export default function ProtectedRoute({ allowedRoles, children }: Props) {
  const { user, isLoading } = useAuth()

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center">
        <span className="text-muted">Loading…</span>
      </div>
    )
  }

  if (!user) return <Navigate to="/login" replace />
  if (!allowedRoles.includes(user.role)) return <Navigate to="/login" replace />

  return <>{children}</>
}
