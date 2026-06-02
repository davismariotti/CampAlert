import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RegisterPage } from '../features/auth/RegisterPage'
import { AuthProvider } from '../features/auth/AuthContext'
import * as sdk from '../api/generated/sdk.gen'

function Wrapper() {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return (
    <MemoryRouter>
      <QueryClientProvider client={qc}>
        <AuthProvider>
          <RegisterPage />
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>
  )
}

describe('RegisterPage', () => {
  it('Create account button disabled when fields are empty', () => {
    render(<Wrapper />)
    expect(screen.getByRole('button', { name: /create account/i })).toBeDisabled()
  })

  it('shows email-taken error on 409', async () => {
    vi.spyOn(sdk, 'register').mockRejectedValueOnce(
      Object.assign(new Error(), { response: { status: 409 } })
    )
    render(<Wrapper />)
    await userEvent.type(screen.getByPlaceholderText('Email'), 'existing@b.com')
    await userEvent.type(screen.getByPlaceholderText('Password'), 'pass')
    await userEvent.click(screen.getByRole('button', { name: /create account/i }))
    await waitFor(() =>
      expect(
        screen.getByText('An account with this email already exists')
      ).toBeInTheDocument()
    )
  })
})
