import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
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

  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('saves timezone and refreshes stored auth user', async () => {
    const updateSpy = vi.spyOn(sdk, 'updateMe').mockResolvedValueOnce({
      data: {
        id: 1,
        email: 'user@test.com',
        timezone: 'America/Denver',
        verificationStatus: 'VERIFIED',
        pushoverOverrideEnabled: false
      },
      error: undefined
    } as Awaited<ReturnType<typeof sdk.updateMe>>)

    render(<Wrapper />)

    await userEvent.selectOptions(screen.getByLabelText('Timezone'), 'America/Denver')
    await userEvent.click(screen.getAllByRole('button', { name: /save settings/i })[0])

    await waitFor(() => expect(updateSpy).toHaveBeenCalledWith({ body: { timezone: 'America/Denver' } }))
    await waitFor(() => expect(JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY)!).timezone).toBe('America/Denver'))
    expect(screen.getByText('Settings saved.')).toBeInTheDocument()
  })

  it('saves Pushover keys and enables the override', async () => {
    const updateSpy = vi.spyOn(sdk, 'updateMe').mockResolvedValueOnce({
      data: {
        id: 1,
        email: 'user@test.com',
        timezone: 'America/Los_Angeles',
        verificationStatus: 'VERIFIED',
        pushoverApiToken: 'app-token-123',
        pushoverUserKey: 'user-key-456',
        pushoverOverrideEnabled: true
      },
      error: undefined
    } as Awaited<ReturnType<typeof sdk.updateMe>>)

    render(<Wrapper />)

    await userEvent.click(screen.getByRole('switch'))
    await userEvent.type(screen.getByPlaceholderText('Pushover application API token'), 'app-token-123')
    await userEvent.type(screen.getByPlaceholderText('Pushover user key'), 'user-key-456')
    await userEvent.click(screen.getAllByRole('button', { name: /save settings/i })[1])

    await waitFor(() =>
      expect(updateSpy).toHaveBeenCalledWith({
        body: {
          pushoverOverrideEnabled: true,
          pushoverApiToken: 'app-token-123',
          pushoverUserKey: 'user-key-456'
        }
      })
    )
    await waitFor(() => expect(JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY)!).pushoverOverrideEnabled).toBe(true))
  })

  it('seeds Pushover fields from the stored auth user and disables save when unchanged', () => {
    localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({
        id: 1,
        email: 'user@test.com',
        timezone: 'America/Los_Angeles',
        verificationStatus: 'VERIFIED',
        pushoverApiToken: 'existing-token',
        pushoverUserKey: 'existing-key',
        pushoverOverrideEnabled: true
      })
    )

    render(<Wrapper />)

    expect(screen.getByDisplayValue('existing-token')).toBeInTheDocument()
    expect(screen.getByDisplayValue('existing-key')).toBeInTheDocument()
    expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'true')
    expect(screen.getAllByRole('button', { name: /save settings/i })[1]).toBeDisabled()
  })

  it('blocks saving when enabling Pushover without both keys set', async () => {
    const updateSpy = vi.spyOn(sdk, 'updateMe')

    render(<Wrapper />)

    await userEvent.click(screen.getByRole('switch'))

    expect(screen.getByText('App token and user key are both required to enable Pushover.')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /save settings/i })[1]).toBeDisabled()
    expect(updateSpy).not.toHaveBeenCalled()
  })

  it('allows turning Pushover off without re-entering keys', async () => {
    localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({
        id: 1,
        email: 'user@test.com',
        timezone: 'America/Los_Angeles',
        verificationStatus: 'VERIFIED',
        pushoverApiToken: 'existing-token',
        pushoverUserKey: 'existing-key',
        pushoverOverrideEnabled: true
      })
    )

    render(<Wrapper />)

    await userEvent.click(screen.getByRole('switch'))

    expect(screen.queryByText('App token and user key are both required to enable Pushover.')).not.toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /save settings/i })[1]).not.toBeDisabled()
  })
})
