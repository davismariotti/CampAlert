import { client } from './generated/client.gen'
import type { AxiosError } from 'axios'

const AUTH_STORAGE_KEY = 'campalert_user'

export function clearAuthState() {
  localStorage.removeItem(AUTH_STORAGE_KEY)
}

export { AUTH_STORAGE_KEY }

let navigateFn: ((path: string) => void) | null = null
let logoutFn: (() => void) | null = null
let suppress401 = false

export function setNavigate(fn: (path: string) => void) {
  navigateFn = fn
}

export function setLogout(fn: () => void) {
  logoutFn = fn
}

export function withoutRedirectOn401<T>(fn: () => Promise<T>): Promise<T> {
  suppress401 = true
  return fn().finally(() => {
    suppress401 = false
  })
}

function getCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

client.instance.interceptors.request.use((config) => {
  const method = config.method?.toUpperCase()
  if (method && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
    const token = getCsrfToken()
    if (token) {
      config.headers['X-XSRF-TOKEN'] = token
    }
  }
  return config
})

const PUBLIC_AUTH_PATHS = ['/reset-password', '/forgot-password', '/verify-email', '/register']

client.instance.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const onPublicAuthPage = PUBLIC_AUTH_PATHS.some((p) => window.location.pathname.startsWith(p))
    if (error.response?.status === 401 && !suppress401 && !onPublicAuthPage) {
      logoutFn?.()
      navigateFn?.('/login')
    }
    return Promise.reject(error)
  }
)

export { client }
