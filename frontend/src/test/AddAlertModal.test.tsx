import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AddAlertModal } from '../features/requests/AddAlertModal'
import { AuthContext, type AuthUser } from '../features/auth/authState'
import * as sdk from '../api/generated/sdk.gen'
import type { CampgroundSearchResult, ProviderType } from '../api/generated/types.gen'

function renderModal(enabledProviders: ProviderType[]) {
  const user: AuthUser = {
    id: 1,
    email: 'u@test.com',
    timezone: 'America/Los_Angeles',
    verificationStatus: 'VERIFIED',
    pushoverOverrideEnabled: false,
    permissions: [],
    enabledProviders
  }
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <AuthContext.Provider value={{ user, login: () => {}, logout: () => {} }}>
      <MemoryRouter>
        <QueryClientProvider client={qc}>
          <AddAlertModal onClose={() => {}} />
        </QueryClientProvider>
      </MemoryRouter>
    </AuthContext.Provider>
  )
}

function stubSearch(results: CampgroundSearchResult[]) {
  return vi.spyOn(sdk, 'searchCampgrounds').mockResolvedValue({
    data: results,
    error: undefined
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  } as any)
}

const sfCenter: CampgroundSearchResult = {
  id: 5446,
  name: 'SF Enrollment Center — San Francisco, CA',
  provider: { type: 'GLOBAL_ENTRY', name: 'Global Entry' }
}

afterEach(() => vi.restoreAllMocks())

describe('AddAlertModal Global Entry tab', () => {
  it('shows Campground, Permit, and Global Entry tabs when the user has Global Entry access', () => {
    renderModal(['RECREATION_GOV', 'GLOBAL_ENTRY'])

    expect(screen.getByRole('button', { name: 'Campground' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Permit' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Global Entry' })).toBeInTheDocument()
  })

  it('shows no Global Entry tab, or mention of it, without access', () => {
    renderModal(['RECREATION_GOV', 'CAMPLIFE'])

    expect(screen.getByRole('button', { name: 'Campground' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Permit' })).toBeInTheDocument()
    expect(screen.queryByText(/global entry/i)).not.toBeInTheDocument()
  })

  it('searches enrollment centers scoped to Global Entry from two characters, so state codes work', async () => {
    const searchSpy = stubSearch([sfCenter])
    renderModal(['GLOBAL_ENTRY'])

    await userEvent.click(screen.getByRole('button', { name: 'Global Entry' }))
    expect(screen.getByText('Search for an enrollment center to start watching.')).toBeInTheDocument()
    await userEvent.type(screen.getByPlaceholderText('Search by center, city, or state...'), 'CA')

    await waitFor(() => expect(searchSpy).toHaveBeenCalledWith({ query: { q: 'CA', provider: 'GLOBAL_ENTRY' } }))
    expect(await screen.findByText('SF Enrollment Center — San Francisco, CA')).toBeInTheDocument()
  })

  it('selecting a center opens the builder for it and hides the tab strip', async () => {
    stubSearch([sfCenter])
    renderModal(['GLOBAL_ENTRY'])

    await userEvent.click(screen.getByRole('button', { name: 'Global Entry' }))
    await userEvent.type(screen.getByPlaceholderText('Search by center, city, or state...'), 'San')
    await userEvent.click(await screen.findByText('SF Enrollment Center — San Francisco, CA'))

    expect(await screen.findByPlaceholderText('Alert name')).toBeInTheDocument()
    expect(screen.getByLabelText('Earliest time')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Permit' })).not.toBeInTheDocument()
  })

  it('the Campground tab keeps its 3-character minimum and searches without a provider scope', async () => {
    const searchSpy = stubSearch([])
    renderModal(['GLOBAL_ENTRY'])

    await userEvent.type(screen.getByPlaceholderText('Search for a campground...'), 'ca')
    await new Promise((resolve) => setTimeout(resolve, 450))
    expect(searchSpy).not.toHaveBeenCalled()

    await userEvent.type(screen.getByPlaceholderText('Search for a campground...'), 'm')
    await waitFor(() => expect(searchSpy).toHaveBeenCalledWith({ query: { q: 'cam', provider: undefined } }))
  })
})
