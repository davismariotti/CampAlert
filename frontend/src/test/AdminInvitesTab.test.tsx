import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminInvitesTab } from '../features/admin/AdminInvitesTab'
import * as sdk from '../api/generated/sdk.gen'
import type { AdminInviteListResponse } from '../api/generated/types.gen'
import type { ReactNode } from 'react'

function emptyList(): AdminInviteListResponse {
  return { items: [], total: 0, page: 0, pageSize: 20 }
}

function Wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <MemoryRouter>
      <QueryClientProvider client={qc}>{children}</QueryClientProvider>
    </MemoryRouter>
  )
}

describe('AdminInvitesTab', () => {
  afterEach(() => vi.restoreAllMocks())

  it('defaults to includeInactive=false', async () => {
    const listSpy = vi
      .spyOn(sdk, 'adminListInvites')
      .mockResolvedValue({ data: emptyList(), error: undefined } as Awaited<ReturnType<typeof sdk.adminListInvites>>)

    render(<AdminInvitesTab />, { wrapper: Wrapper })

    await waitFor(() =>
      expect(listSpy).toHaveBeenCalledWith({ query: { page: 0, pageSize: 20, includeInactive: false } })
    )
  })

  it('toggling "show expired, deactivated, and used invites" refetches with includeInactive=true', async () => {
    const listSpy = vi
      .spyOn(sdk, 'adminListInvites')
      .mockResolvedValue({ data: emptyList(), error: undefined } as Awaited<ReturnType<typeof sdk.adminListInvites>>)

    render(<AdminInvitesTab />, { wrapper: Wrapper })
    await waitFor(() => expect(screen.getByRole('switch')).toBeInTheDocument())
    listSpy.mockClear()

    await userEvent.click(screen.getByRole('switch'))

    await waitFor(() =>
      expect(listSpy).toHaveBeenCalledWith({ query: { page: 0, pageSize: 20, includeInactive: true } })
    )
  })

  it('shows redemption progress and status for each invite', async () => {
    vi.spyOn(sdk, 'adminListInvites').mockResolvedValue({
      data: {
        items: [
          {
            id: '00000000-0000-0000-0000-000000000001',
            type: 'LINK',
            email: null,
            status: 'ACTIVE',
            maxUses: 100,
            usedCount: 5,
            createdByEmail: 'admin@test.com',
            createdAt: '2026-01-01T00:00:00Z',
            expiresAt: '2026-01-08T00:00:00Z'
          }
        ],
        total: 1,
        page: 0,
        pageSize: 20
      },
      error: undefined
    } as Awaited<ReturnType<typeof sdk.adminListInvites>>)

    render(<AdminInvitesTab />, { wrapper: Wrapper })

    await waitFor(() => expect(screen.getByText('5 of 100 redeemed')).toBeInTheDocument())
    expect(screen.getByText('ACTIVE')).toBeInTheDocument()
  })

  it('only shows the deactivate action for active invites', async () => {
    vi.spyOn(sdk, 'adminListInvites').mockResolvedValue({
      data: {
        items: [
          {
            id: '00000000-0000-0000-0000-000000000001',
            type: 'EMAIL',
            email: 'a@test.com',
            status: 'ACTIVE',
            maxUses: 1,
            usedCount: 0,
            createdByEmail: 'admin@test.com',
            createdAt: '2026-01-01T00:00:00Z',
            expiresAt: '2026-01-08T00:00:00Z'
          },
          {
            id: '00000000-0000-0000-0000-000000000002',
            type: 'EMAIL',
            email: 'b@test.com',
            status: 'REDEEMED',
            maxUses: 1,
            usedCount: 1,
            createdByEmail: 'admin@test.com',
            createdAt: '2026-01-01T00:00:00Z',
            expiresAt: '2026-01-08T00:00:00Z'
          }
        ],
        total: 2,
        page: 0,
        pageSize: 20
      },
      error: undefined
    } as Awaited<ReturnType<typeof sdk.adminListInvites>>)

    render(<AdminInvitesTab />, { wrapper: Wrapper })

    await waitFor(() => expect(screen.getAllByRole('button', { name: /deactivate/i })).toHaveLength(1))
  })
})
