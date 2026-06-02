import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { LoginPage } from '../features/auth/LoginPage'
import { AuthProvider } from '../features/auth/AuthContext'
import * as sdk from '../api/generated/sdk.gen'

function Wrapper() {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return (
    <MemoryRouter>
      <QueryClientProvider client={qc}>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>
  )
}

describe('LoginPage', () => {
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
    vi.spyOn(sdk, 'login').mockRejectedValueOnce(Object.assign(new Error(), { response: { status: 401 } }))
    render(<Wrapper />)
    await userEvent.type(screen.getByPlaceholderText('Email'), 'a@b.com')
    await userEvent.type(screen.getByPlaceholderText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    await waitFor(() => expect(screen.getByText('Invalid email or password')).toBeInTheDocument())
  })
})
