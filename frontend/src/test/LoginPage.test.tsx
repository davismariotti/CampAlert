import { afterEach, describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { LoginModal } from '../components/LoginModal'
import { AuthProvider } from '../features/auth/AuthContext'
import * as sdk from '../api/generated/sdk.gen'

function LocationDisplay() {
  const location = useLocation()
  return <span data-testid="location">{`${location.pathname}${location.search}`}</span>
}

function Wrapper() {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return (
    <MemoryRouter>
      <QueryClientProvider client={qc}>
        <AuthProvider>
          <LoginModal onClose={() => {}} />
          <LocationDisplay />
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>
  )
}

describe('LoginModal', () => {
  afterEach(() => vi.restoreAllMocks())

  it('Sign in button disabled when fields are empty', () => {
    render(<Wrapper />)
    expect(screen.getByRole('button', { name: /sign in/i })).toBeDisabled()
  })

  it('Sign in button enabled when both fields have values', async () => {
    render(<Wrapper />)
    await userEvent.type(screen.getByPlaceholderText('Email'), 'a@b.com')
    await userEvent.type(screen.getByPlaceholderText('Password'), 'pass')
    expect(screen.getByRole('button', { name: /sign in/i })).toBeEnabled()
  })

  it('shows error on 401', async () => {
    vi.spyOn(sdk, 'login').mockRejectedValueOnce(
      Object.assign(new Error(), { response: { status: 401, data: { message: 'Invalid credentials' } } })
    )
    render(<Wrapper />)
    await userEvent.type(screen.getByPlaceholderText('Email'), 'a@b.com')
    await userEvent.type(screen.getByPlaceholderText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    await waitFor(() => expect(screen.getByText('Invalid email or password')).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: /resend verification email/i })).not.toBeInTheDocument()
  })

  it('routes valid credentials for an unverified account into email verification', async () => {
    const verificationId = '00000000-0000-0000-0000-000000000001'
    vi.spyOn(sdk, 'login').mockRejectedValueOnce(
      Object.assign(new Error(), {
        response: {
          status: 401,
          data: { message: 'Email not verified', code: 'EMAIL_NOT_VERIFIED', verificationId }
        }
      })
    )

    render(<Wrapper />)
    await userEvent.type(screen.getByPlaceholderText('Email'), 'pending@test.com')
    await userEvent.type(screen.getByPlaceholderText('Password'), 'password1')
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }))

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent(`/verify-email?verificationId=${verificationId}`)
    )
  })
})
