import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminUserDetailPage } from '../features/admin/AdminUserDetailPage'
import * as sdk from '../api/generated/sdk.gen'
import type {
  AdminUserDetailResponse,
  PermitSearchRequestResponse,
  SearchRequestResponse
} from '../api/generated/types.gen'

const user: AdminUserDetailResponse = {
  id: 5,
  email: 'target@test.com',
  timezone: 'America/Los_Angeles',
  phoneNumbers: [],
  groups: [],
  effectiveCombinedQuota: { value: 5, source: 'GLOBAL_DEFAULT' },
  effectiveProviderQuotas: [
    { provider: 'RECREATION_GOV', value: null, source: 'GLOBAL_DEFAULT' },
    { provider: 'CAMPLIFE', value: null, source: 'GLOBAL_DEFAULT' },
    { provider: 'RESERVE_CALIFORNIA', value: 1, source: 'GLOBAL_DEFAULT' }
  ],
  effectiveProviderAccess: [
    { provider: 'RECREATION_GOV', value: true, source: 'GLOBAL_DEFAULT' },
    { provider: 'CAMPLIFE', value: true, source: 'GLOBAL_DEFAULT' },
    { provider: 'RESERVE_CALIFORNIA', value: false, source: 'GLOBAL_DEFAULT' }
  ]
}

function renderAt(path: string) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter initialEntries={[path]}>
      <QueryClientProvider client={qc}>
        <Routes>
          <Route path="/admin/users/:id" element={<AdminUserDetailPage />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>
  )
}

describe('AdminUserDetailPage back-to-search-results link', () => {
  afterEach(() => vi.restoreAllMocks())

  function stubQueries() {
    vi.spyOn(sdk, 'adminGetUser').mockResolvedValue({ data: user, error: undefined } as Awaited<
      ReturnType<typeof sdk.adminGetUser>
    >)
    vi.spyOn(sdk, 'adminListUserSearchRequests').mockResolvedValue({
      data: [] as SearchRequestResponse[],
      error: undefined
    } as Awaited<ReturnType<typeof sdk.adminListUserSearchRequests>>)
    vi.spyOn(sdk, 'adminListUserPermitSearchRequests').mockResolvedValue({
      data: [] as PermitSearchRequestResponse[],
      error: undefined
    } as Awaited<ReturnType<typeof sdk.adminListUserPermitSearchRequests>>)
  }

  it('renders a link back to the prior search query and page when a from param is present', async () => {
    stubQueries()
    renderAt('/admin/users/5?from=%2Fadmin%2Fusers%3Fquery%3Dalice%26page%3D1')

    await waitFor(() => expect(screen.getByRole('heading', { name: 'target@test.com' })).toBeInTheDocument())

    const backLink = screen.getByRole('link', { name: /back to search results/i })
    expect(backLink).toHaveAttribute('href', '/admin/users?query=alice&page=1')
  })

  it('renders no back link when no from param is present', async () => {
    stubQueries()
    renderAt('/admin/users/5')

    await waitFor(() => expect(screen.getByRole('heading', { name: 'target@test.com' })).toBeInTheDocument())

    expect(screen.queryByRole('link', { name: /back to search results/i })).not.toBeInTheDocument()
  })
})
