import { describe, it, expect, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminHomePage } from '../features/admin/AdminHomePage'
import { AuthProvider } from '../features/auth/AuthContext'
import { AUTH_STORAGE_KEY } from '../api/client'

function Wrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <MemoryRouter initialEntries={['/admin']}>
      <QueryClientProvider client={qc}>
        <AuthProvider>
          <AdminHomePage />
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
      email: 'admin@test.com',
      timezone: 'America/Los_Angeles',
      verificationStatus: 'VERIFIED',
      pushoverOverrideEnabled: false,
      permissions
    })
  )
}

describe('AdminHomePage invite gating', () => {
  afterEach(() => localStorage.clear())

  it('shows the Invite button and Invites tab for an admin with MANAGE_INVITES', () => {
    storeUser(['VIEW_ADMIN_DASHBOARD', 'MANAGE_INVITES'])
    render(<Wrapper />)

    expect(screen.getByRole('button', { name: 'Invite' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Invites' })).toBeInTheDocument()
  })

  it('hides the Invite button and Invites tab for an admin without MANAGE_INVITES', () => {
    storeUser(['VIEW_ADMIN_DASHBOARD'])
    render(<Wrapper />)

    expect(screen.queryByRole('button', { name: 'Invite' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Invites' })).not.toBeInTheDocument()
  })

  it('always shows the Users tab for an admin', () => {
    storeUser(['VIEW_ADMIN_DASHBOARD'])
    render(<Wrapper />)

    expect(screen.getByRole('button', { name: 'Users' })).toBeInTheDocument()
  })
})
