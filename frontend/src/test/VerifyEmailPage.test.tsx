import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { VerifyEmailPage } from '../features/auth/VerifyEmailPage'
import * as sdk from '../api/generated/sdk.gen'

const verificationId = '00000000-0000-0000-0000-000000000001'

function Wrapper({ initialEntry = `/verify-email?verificationId=${verificationId}` }: { initialEntry?: string }) {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return (
    <MemoryRouter initialEntries={[initialEntry]}>
      <QueryClientProvider client={queryClient}>
        <Routes>
          <Route path="/verify-email" element={<VerifyEmailPage />} />
          <Route path="/" element={<div>Sign in destination</div>} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>
  )
}

describe('VerifyEmailPage', () => {
  afterEach(() => vi.restoreAllMocks())

  it('submits the URL verificationId and routes successful verification to sign in', async () => {
    const verifySpy = vi.spyOn(sdk, 'verifyEmail').mockResolvedValueOnce({
      data: undefined
    } as Awaited<ReturnType<typeof sdk.verifyEmail>>)
    render(<Wrapper />)

    await userEvent.type(screen.getByLabelText('Verification code'), '123456')
    await userEvent.click(screen.getByRole('button', { name: /verify email/i }))

    await waitFor(() =>
      expect(verifySpy).toHaveBeenCalledWith({
        body: { verificationId, code: '123456' },
        throwOnError: true
      })
    )
    expect(await screen.findByText('Sign in destination')).toBeInTheDocument()
  })

  it('shows a retry message for a wrong code', async () => {
    vi.spyOn(sdk, 'verifyEmail').mockRejectedValueOnce(
      Object.assign(new Error(), {
        response: { status: 422, data: { message: 'Invalid code', code: 'VERIFICATION_CODE_INVALID' } }
      })
    )
    render(<Wrapper />)

    await userEvent.type(screen.getByLabelText('Verification code'), '000000')
    await userEvent.click(screen.getByRole('button', { name: /verify email/i }))

    expect(await screen.findByText('That code is incorrect. Try again.')).toBeInTheDocument()
  })

  it('falls back to a verificationId from navigation state', async () => {
    const verifySpy = vi.spyOn(sdk, 'verifyEmail').mockResolvedValueOnce({
      data: undefined
    } as Awaited<ReturnType<typeof sdk.verifyEmail>>)
    const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
    render(
      <MemoryRouter initialEntries={[{ pathname: '/verify-email', state: { verificationId } }]}>
        <QueryClientProvider client={queryClient}>
          <Routes>
            <Route path="/verify-email" element={<VerifyEmailPage />} />
            <Route path="/" element={<div>Sign in destination</div>} />
          </Routes>
        </QueryClientProvider>
      </MemoryRouter>
    )

    await userEvent.type(screen.getByLabelText('Verification code'), '123456')
    await userEvent.click(screen.getByRole('button', { name: /verify email/i }))

    await waitFor(() =>
      expect(verifySpy).toHaveBeenCalledWith({
        body: { verificationId, code: '123456' },
        throwOnError: true
      })
    )
  })

  it('shows a resend prompt when the attempt limit is exceeded', async () => {
    vi.spyOn(sdk, 'verifyEmail').mockRejectedValueOnce(
      Object.assign(new Error(), {
        response: {
          status: 422,
          data: { message: 'Attempt limit reached', code: 'VERIFICATION_CODE_ATTEMPTS_EXCEEDED' }
        }
      })
    )
    render(<Wrapper />)

    await userEvent.type(screen.getByLabelText('Verification code'), '000000')
    await userEvent.click(screen.getByRole('button', { name: /verify email/i }))

    expect(await screen.findByText('Too many incorrect attempts. Request a new code.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /resend verification email/i })).toBeInTheDocument()
  })

  it('resends with a generic confirmation and starts a cooldown', async () => {
    const resendSpy = vi.spyOn(sdk, 'resendVerification').mockResolvedValueOnce({
      data: undefined
    } as Awaited<ReturnType<typeof sdk.resendVerification>>)
    render(<Wrapper />)

    await userEvent.type(screen.getByLabelText('Email for resend'), 'pending@test.com')
    await userEvent.click(screen.getByRole('button', { name: /resend verification email/i }))

    await waitFor(() =>
      expect(resendSpy).toHaveBeenCalledWith({
        body: { email: 'pending@test.com' },
        throwOnError: true
      })
    )
    expect(screen.getByText('If that account can be verified, a new code has been sent.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /resend available in 60s/i })).toBeDisabled()
  })
})
