import { describe, it, expect, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Nav } from '../components/Nav'
import { AuthProvider } from '../features/auth/AuthContext'
import { AUTH_STORAGE_KEY } from '../api/client'

function Wrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <MemoryRouter>
      <QueryClientProvider client={qc}>
        <AuthProvider>
          <Nav />
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>
  )
}

function storeUser(permissions: string[]) {
  localStorage.setItem(
    AUTH_STORAGE_KEY,
    JSON.stringify({
      id: 1,
      email: 'user@test.com',
      timezone: 'America/Los_Angeles',
      verificationStatus: 'VERIFIED',
      pushoverOverrideEnabled: false,
      permissions
    })
  )
}

describe('Nav admin link', () => {
  afterEach(() => localStorage.clear())

  it('is hidden for a user without VIEW_ADMIN_DASHBOARD', () => {
    storeUser(['VIEW_PROFILE', 'VIEW_SEARCH_REQUESTS'])
    render(<Wrapper />)
    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument()
  })

  it('is shown for a user with VIEW_ADMIN_DASHBOARD', () => {
    storeUser(['VIEW_PROFILE', 'VIEW_ADMIN_DASHBOARD'])
    render(<Wrapper />)
    expect(screen.getByRole('link', { name: 'Admin' })).toBeInTheDocument()
  })

  it('is hidden entirely when logged out', () => {
    render(<Wrapper />)
    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'My Alerts' })).not.toBeInTheDocument()
  })
})
