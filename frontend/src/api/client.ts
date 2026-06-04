import { client } from './generated/client.gen'
import type { AxiosError } from 'axios'

const AUTH_STORAGE_KEY = 'campalert_user'

export function clearAuthState() {
  localStorage.removeItem(AUTH_STORAGE_KEY)
}

export { AUTH_STORAGE_KEY }

let navigateFn: ((path: string) => void) | null = null
let logoutFn: (() => void) | null = null

export function setNavigate(fn: (path: string) => void) {
  navigateFn = fn
}

export function setLogout(fn: () => void) {
  logoutFn = fn
}

client.instance.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      logoutFn?.()
      navigateFn?.('/login')
    }
    return Promise.reject(error)
  }
)

export { client }
