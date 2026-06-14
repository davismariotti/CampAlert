import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ResetPasswordPage } from '../features/auth/ResetPasswordPage'
import * as sdk from '../api/generated/sdk.gen'

const resetId = '00000000-0000-0000-0000-000000000001'
const token = 'a'.repeat(64)

function Wrapper() {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return (
    <MemoryRouter initialEntries={[`/reset-password?resetId=${resetId}&token=${token}`]}>
      <QueryClientProvider client={queryClient}>
        <ResetPasswordPage />
      </QueryClientProvider>
    </MemoryRouter>
  )
}

async function enterPasswords(password: string, confirmation = password) {
  await userEvent.type(screen.getByLabelText('New password'), password)
  await userEvent.type(screen.getByLabelText('Confirm new password'), confirmation)
}

describe('ResetPasswordPage', () => {
  afterEach(() => vi.restoreAllMocks())

  it('validates that passwords match', async () => {
    render(<Wrapper />)
    await enterPasswords('newPassword1!', 'differentPassword1!')
    expect(screen.getByText('Passwords do not match.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /reset password/i })).toBeDisabled()
  })

  it('submits once and routes the user back to sign in after success', async () => {
    const resetSpy = vi.spyOn(sdk, 'resetPassword').mockResolvedValueOnce({
      data: undefined
    } as Awaited<ReturnType<typeof sdk.resetPassword>>)
    render(<Wrapper />)
    await enterPasswords('newPassword1!')
    await userEvent.click(screen.getByRole('button', { name: /reset password/i }))

    await waitFor(() =>
      expect(resetSpy).toHaveBeenCalledWith({
        body: { resetId, token, newPassword: 'newPassword1!' },
        throwOnError: true
      })
    )
    expect(screen.getByText('Your password has been reset.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /continue to sign in/i })).toHaveAttribute('href', '/?login=1')
  })

  it('shows an invalid-or-expired error and prevents token replay', async () => {
    const resetSpy = vi.spyOn(sdk, 'resetPassword').mockRejectedValueOnce(
      Object.assign(new Error(), {
        response: { status: 422, data: { message: 'Invalid reset', code: 'RESET_INVALID_OR_EXPIRED' } }
      })
    )
    render(<Wrapper />)
    await enterPasswords('newPassword1!')
    await userEvent.click(screen.getByRole('button', { name: /reset password/i }))

    expect(await screen.findByText('This password reset link is invalid or expired.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /reset password/i })).toBeDisabled()
    expect(resetSpy).toHaveBeenCalledTimes(1)
  })
})
