import { Link } from 'react-router-dom'
import { useAuth } from '../features/auth/useAuth'
import { ProfileDropdown } from './ProfileDropdown'

export function Nav() {
  const { user } = useAuth()

  if (!user) return null

  return (
    <nav className="flex h-14 items-center justify-between bg-forest-800 px-6 text-white">
      <Link to="/" className="flex items-center gap-2">
        <img src="/logo.png" alt="CampAlert" className="h-8 w-8 rounded" />
        <span className="font-semibold">CampAlert</span>
      </Link>

      <div className="flex items-center gap-4">
        <Link to="/requests" className="text-sm font-medium text-white/90 hover:text-white">
          My Alerts
        </Link>
        <Link
          to="/"
          className="rounded-lg border border-white/30 px-3 py-1.5 text-sm font-medium text-white/90 hover:bg-white/10 hover:text-white"
        >
          + New Alert
        </Link>
      </div>

      <ProfileDropdown />
    </nav>
  )
}
