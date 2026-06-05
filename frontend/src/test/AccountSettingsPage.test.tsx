import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AccountSettingsPage } from '../features/account/AccountSettingsPage'
import { AuthProvider } from '../features/auth/AuthContext'
import { AUTH_STORAGE_KEY } from '../api/client'
import * as sdk from '../api/generated/sdk.gen'

function Wrapper() {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return (
    <MemoryRouter>
      <QueryClientProvider client={qc}>
        <AuthProvider>
          <AccountSettingsPage />
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>
  )
}

describe('AccountSettingsPage', () => {
  beforeEach(() => {
    localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({ id: 1, email: 'user@test.com', timezone: 'America/Los_Angeles' })
    )
  })

  it('saves timezone and refreshes stored auth user', async () => {
    const updateSpy = vi.spyOn(sdk, 'updateMe').mockResolvedValueOnce({
      data: { id: 1, email: 'user@test.com', timezone: 'America/Denver' },
      error: undefined
    } as Awaited<ReturnType<typeof sdk.updateMe>>)

    render(<Wrapper />)

    await userEvent.selectOptions(screen.getByLabelText('Timezone'), 'America/Denver')
    await userEvent.click(screen.getByRole('button', { name: /save settings/i }))

    await waitFor(() => expect(updateSpy).toHaveBeenCalledWith({ body: { timezone: 'America/Denver' } }))
    await waitFor(() => expect(JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY)!).timezone).toBe('America/Denver'))
    expect(screen.getByText('Settings saved.')).toBeInTheDocument()
  })
})
