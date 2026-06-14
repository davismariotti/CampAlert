import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ForgotPasswordPage } from '../features/auth/ForgotPasswordPage'
import * as sdk from '../api/generated/sdk.gen'

function Wrapper() {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return (
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <ForgotPasswordPage />
      </QueryClientProvider>
    </MemoryRouter>
  )
}

describe('ForgotPasswordPage', () => {
  afterEach(() => vi.restoreAllMocks())

  it('always shows the generic accepted confirmation', async () => {
    const forgotSpy = vi.spyOn(sdk, 'forgotPassword').mockResolvedValueOnce({
      data: undefined
    } as Awaited<ReturnType<typeof sdk.forgotPassword>>)
    render(<Wrapper />)

    await userEvent.type(screen.getByLabelText('Email'), 'unknown@test.com')
    await userEvent.click(screen.getByRole('button', { name: /send reset link/i }))

    await waitFor(() =>
      expect(forgotSpy).toHaveBeenCalledWith({
        body: { email: 'unknown@test.com' },
        throwOnError: true
      })
    )
    expect(screen.getByText('If an eligible account exists, a password reset link has been sent.')).toBeInTheDocument()
  })
})
