import { Link, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { logout } from '../api/generated/sdk.gen'
import { useAuth } from '../features/auth/AuthContext'
import { Button } from './ui/Button'

export function Nav() {
  const { user, logout: clearAuth } = useAuth()
  const navigate = useNavigate()

  const logoutMutation = useMutation({
    mutationFn: () => logout(),
    onSettled: () => {
      clearAuth()
      navigate('/login')
    },
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
        <Button
          variant="secondary"
          className="bg-transparent border-white/30 text-white hover:bg-white/10"
          loading={logoutMutation.isPending}
          onClick={() => logoutMutation.mutate()}
        >
          Sign out
        </Button>
      </div>
    </nav>
  )
}
