import { client } from './generated/client.gen'
import type { AxiosError } from 'axios'

const AUTH_STORAGE_KEY = 'campalert_user'

export function clearAuthState() {
  localStorage.removeItem(AUTH_STORAGE_KEY)
}

export { AUTH_STORAGE_KEY }

// Populated by App.tsx once the router is created
let navigateFn: ((path: string) => void) | null = null

export function setNavigate(fn: (path: string) => void) {
  navigateFn = fn
}

client.instance.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      clearAuthState()
      navigateFn?.('/login')
    }
    return Promise.reject(error)
  }
)

export { client }
