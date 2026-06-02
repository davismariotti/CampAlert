import { Link, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { logout } from '../api/generated/sdk.gen'
import { useAuth } from '../features/auth/AuthContext'
import { Spinner } from './ui/Spinner'

export function Nav() {
  const { user, logout: clearAuth } = useAuth()
  const navigate = useNavigate()

  const logoutMutation = useMutation({
    mutationFn: () => logout(),
    onSettled: () => {
      clearAuth()
      navigate('/login')
    }
  })

  if (!user) return null

  return (
    <nav className="flex h-14 items-center justify-between bg-forest-800 px-6 text-white">
      <Link to="/" className="flex items-center gap-2">
        <img src="/logo.png" alt="CampAlert" className="h-8 w-8 rounded" />
        <span className="font-semibold">CampAlert</span>
      </Link>

      <Link to="/requests" className="text-sm font-medium text-white/90 hover:text-white">
        My Alerts
      </Link>

      <div className="flex items-center gap-3">
        <span className="text-sm text-white/70">{user.email}</span>
        <button
          type="button"
          disabled={logoutMutation.isPending}
          onClick={() => logoutMutation.mutate()}
          className="inline-flex items-center gap-2 rounded-xl border border-white/30 px-4 py-2 text-sm font-medium text-white hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {logoutMutation.isPending && <Spinner size="sm" />}
          Sign out
        </button>
      </div>
    </nav>
  )
}
