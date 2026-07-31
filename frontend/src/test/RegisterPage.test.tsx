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

function Wrapper({ initialEntry = '/register' }: { initialEntry?: string } = {}) {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false }, queries: { retry: false } } })
  return (
    <MemoryRouter initialEntries={[initialEntry]}>
      <QueryClientProvider client={qc}>
        <AuthProvider>
          <RegisterPage />
          <LocationDisplay />
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>
  )
}

function mockRegistrationConfig(inviteOnlyEnabled: boolean) {
  vi.spyOn(sdk, 'getRegistrationConfig').mockResolvedValue({
    data: { inviteOnlyEnabled },
    error: undefined
  } as Awaited<ReturnType<typeof sdk.getRegistrationConfig>>)
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
    mockRegistrationConfig(false)
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

  it('shows an invite-only message instead of the form when invite-only mode is enabled and no invite is present', async () => {
    mockRegistrationConfig(true)
    render(<Wrapper />)

    await waitFor(() => expect(screen.getByText('Registration is invite-only')).toBeInTheDocument())
    expect(screen.queryByPlaceholderText('Email')).not.toBeInTheDocument()
  })

  it('still shows the form when invite-only mode is enabled but an invite token is present', async () => {
    mockRegistrationConfig(true)
    render(<Wrapper initialEntry="/register?inviteId=00000000-0000-0000-0000-000000000002&token=abc" />)

    await waitFor(() => expect(screen.getByPlaceholderText('Password')).toBeInTheDocument())
    expect(screen.queryByText('Registration is invite-only')).not.toBeInTheDocument()
  })

  it('pre-fills and locks the email field when an invited email is present in the URL', async () => {
    render(
      <Wrapper initialEntry="/register?inviteId=00000000-0000-0000-0000-000000000002&token=abc&email=invitee@test.com" />
    )

    const emailInput = await screen.findByPlaceholderText('Email')
    expect(emailInput).toHaveValue('invitee@test.com')
    expect(emailInput).toBeDisabled()
  })

  it('includes inviteId/inviteToken in the register submission when present', async () => {
    const registerSpy = vi.spyOn(sdk, 'register').mockResolvedValueOnce({
      data: { verificationId: '00000000-0000-0000-0000-000000000001', verificationStatus: 'PENDING_VERIFICATION' },
      error: undefined
    } as Awaited<ReturnType<typeof sdk.register>>)

    render(<Wrapper initialEntry="/register?inviteId=00000000-0000-0000-0000-000000000002&token=abc123" />)
    await userEvent.type(await screen.findByPlaceholderText('Email'), 'new@b.com')
    await userEvent.type(screen.getByPlaceholderText('Password'), 'password1')
    await userEvent.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() =>
      expect(registerSpy).toHaveBeenCalledWith({
        body: {
          email: 'new@b.com',
          password: 'password1',
          timezone: 'America/Denver',
          turnstileToken: 'test-turnstile-token',
          inviteId: '00000000-0000-0000-0000-000000000002',
          inviteToken: 'abc123'
        }
      })
    )
  })

  it.each([
    ['INVITE_INVALID_OR_EXPIRED', 'This invite link is invalid or has expired.'],
    ['INVITE_EXHAUSTED', 'This invite has already been used.'],
    ['INVITE_DEACTIVATED', 'This invite link is no longer valid.'],
    ['INVITE_EMAIL_MISMATCH', 'This invitation was sent to a different email address.']
  ])('shows a type-specific message for %s', async (code, expectedMessage) => {
    vi.spyOn(sdk, 'register').mockRejectedValueOnce(
      Object.assign(new Error(), { response: { status: 422, data: { code } } })
    )
    render(<Wrapper initialEntry="/register?inviteId=00000000-0000-0000-0000-000000000002&token=abc" />)

    await userEvent.type(await screen.findByPlaceholderText('Email'), 'new@b.com')
    await userEvent.type(screen.getByPlaceholderText('Password'), 'password1')
    await userEvent.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => expect(screen.getByText(expectedMessage)).toBeInTheDocument())
  })

  it('logs the user in directly when the invite auto-verifies (no verify-email redirect)', async () => {
    vi.spyOn(sdk, 'register').mockResolvedValueOnce({
      data: { verificationId: null, verificationStatus: 'VERIFIED' },
      error: undefined
    } as Awaited<ReturnType<typeof sdk.register>>)
    vi.spyOn(sdk, 'getMe').mockResolvedValueOnce({
      data: {
        id: 1,
        email: 'invitee@test.com',
        timezone: 'America/Denver',
        verificationStatus: 'VERIFIED',
        pushoverOverrideEnabled: false,
        permissions: ['VIEW_PROFILE']
      },
      error: undefined
    } as Awaited<ReturnType<typeof sdk.getMe>>)

    render(
      <Wrapper initialEntry="/register?inviteId=00000000-0000-0000-0000-000000000002&token=abc&email=invitee@test.com" />
    )
    await userEvent.type(screen.getByPlaceholderText('Password'), 'password1')
    await userEvent.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/'))
    expect(screen.queryByTestId('location')).not.toHaveTextContent('/verify-email')
  })
})
