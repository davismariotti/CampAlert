import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RegisterPage } from '../features/auth/RegisterPage'
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
          <RegisterPage />
          <LocationDisplay />
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>
  )
}

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.spyOn(Intl.DateTimeFormat.prototype, 'resolvedOptions').mockReturnValue({
      locale: 'en-US',
      calendar: 'gregory',
      numberingSystem: 'latn',
      timeZone: 'America/Denver',
      year: 'numeric',
      month: 'numeric',
      day: 'numeric'
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('Create account button disabled when fields are empty', () => {
    render(<Wrapper />)
    expect(screen.getByRole('button', { name: /create account/i })).toBeDisabled()
  })

  it('shows email-taken error on 409', async () => {
    vi.spyOn(sdk, 'register').mockRejectedValueOnce(Object.assign(new Error(), { response: { status: 409 } }))
    render(<Wrapper />)
    await userEvent.type(screen.getByPlaceholderText('Email'), 'existing@b.com')
    await userEvent.type(screen.getByPlaceholderText('Password'), 'password1')
    await userEvent.click(screen.getByRole('button', { name: /create account/i }))
    await waitFor(() => expect(screen.getByText('An account with this email already exists')).toBeInTheDocument())
  })

  it('defaults timezone from browser and submits it on register', async () => {
    const registerSpy = vi.spyOn(sdk, 'register').mockResolvedValueOnce({
      data: {
        verificationId: '00000000-0000-0000-0000-000000000001',
        verificationStatus: 'PENDING_VERIFICATION'
      },
      error: undefined
    } as Awaited<ReturnType<typeof sdk.register>>)

    render(<Wrapper />)
    expect(screen.getByLabelText('Timezone')).toHaveValue('America/Denver')

    await userEvent.type(screen.getByPlaceholderText('Email'), 'new@b.com')
    await userEvent.type(screen.getByPlaceholderText('Password'), 'password1')
    await userEvent.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() =>
      expect(registerSpy).toHaveBeenCalledWith({
        body: {
          email: 'new@b.com',
          password: 'password1',
          timezone: 'America/Denver',
          turnstileToken: 'test-turnstile-token'
        }
      })
    )
    expect(screen.getByTestId('location')).toHaveTextContent(
      '/verify-email?verificationId=00000000-0000-0000-0000-000000000001'
    )
  })
})
