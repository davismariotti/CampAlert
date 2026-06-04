import { useRef, useState, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { logout } from '../api/generated/sdk.gen'
import { useAuth } from '../features/auth/useAuth'
import { useApiMutation } from '../hooks/useApiMutation'

export function ProfileDropdown() {
  const { user, logout: clearAuth } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  const logoutMutation = useApiMutation({
    mutationFn: async () => {
      const result = await logout()
      if (result.error) throw result
    },
    onError: () => {},
    onSettled: () => {
      clearAuth()
      navigate('/')
    }
  })

  useEffect(() => {
    if (!open) return
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    function handleEscape(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [open])

  if (!user) return null

  const initial = user.email.charAt(0).toUpperCase()

  return (
    <div className="relative" ref={containerRef}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex h-8 w-8 items-center justify-center rounded-full bg-forest-600 text-sm font-semibold text-white hover:bg-forest-500 focus:outline-none focus:ring-2 focus:ring-white/50"
        aria-label="Account menu"
      >
        {initial}
      </button>

      {open && (
        <div className="absolute right-0 top-full z-50 mt-2 w-56 overflow-hidden rounded-xl border border-forest-700 bg-forest-900 shadow-lg">
          <div className="px-4 py-3">
            <p className="truncate text-xs text-white/50">{user.email}</p>
          </div>
          <div className="border-t border-forest-700" />
          <Link
            to="/phone-numbers"
            onClick={() => setOpen(false)}
            className="flex w-full items-center px-4 py-2.5 text-sm text-white/80 hover:bg-forest-700 hover:text-white"
          >
            Phone Numbers
          </Link>
          <div className="border-t border-forest-700" />
          <button
            type="button"
            disabled={logoutMutation.isPending}
            onClick={() => logoutMutation.mutate()}
            className="flex w-full items-center px-4 py-2.5 text-sm text-white/80 hover:bg-forest-700 hover:text-white disabled:opacity-50"
          >
            Sign out
          </button>
        </div>
      )}
    </div>
  )
}
