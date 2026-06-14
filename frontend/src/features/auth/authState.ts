import { createContext } from 'react'
import type { AuthResponse } from '../../api/generated/types.gen'

export type AuthUser = AuthResponse & { verificationStatus: 'VERIFIED' }

export interface AuthContextValue {
  user: AuthUser | null
  login: (user: AuthResponse) => void
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
