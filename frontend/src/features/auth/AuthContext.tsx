import { useState, type ReactNode } from 'react'
import { AUTH_STORAGE_KEY } from '../../api/client'
import { AuthContext, type AuthUser } from './authState'

function loadFromStorage(): AuthUser | null {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY)
    return raw ? (JSON.parse(raw) as AuthUser) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(loadFromStorage)

  function login(user: AuthUser) {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(user))
    setUser(user)
  }

  function logout() {
    localStorage.removeItem(AUTH_STORAGE_KEY)
    setUser(null)
  }

  return <AuthContext.Provider value={{ user, login, logout }}>{children}</AuthContext.Provider>
}
