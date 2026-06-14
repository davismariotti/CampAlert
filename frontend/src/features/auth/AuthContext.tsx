import { useState, type ReactNode } from 'react'
import { AUTH_STORAGE_KEY } from '../../api/client'
import type { AuthResponse } from '../../api/generated/types.gen'
import { AuthContext, type AuthUser } from './authState'
import { DEFAULT_TIMEZONE } from '../../utils/timezones'

function isVerifiedUser(user: AuthResponse): user is AuthUser {
  return user.verificationStatus === 'VERIFIED'
}

function loadFromStorage(): AuthUser | null {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<AuthUser>
    if (!parsed.id || !parsed.email || parsed.verificationStatus !== 'VERIFIED') return null
    return {
      id: parsed.id,
      email: parsed.email,
      timezone: parsed.timezone ?? DEFAULT_TIMEZONE,
      verificationStatus: 'VERIFIED'
    }
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(loadFromStorage)

  function login(user: AuthResponse) {
    if (!isVerifiedUser(user)) return
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(user))
    setUser(user)
  }

  function logout() {
    localStorage.removeItem(AUTH_STORAGE_KEY)
    setUser(null)
  }

  return <AuthContext.Provider value={{ user, login, logout }}>{children}</AuthContext.Provider>
}
