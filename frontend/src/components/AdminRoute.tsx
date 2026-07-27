import { Navigate } from 'react-router-dom'
import { useAuth } from '../features/auth/useAuth'
import { isAdmin } from '../features/auth/permissions'
import type { ReactNode } from 'react'

export function AdminRoute({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/" replace />
  if (!isAdmin(user)) return <Navigate to="/requests" replace />
  return <>{children}</>
}
