import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route, useSearchParams } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminUsersPage } from '../features/admin/AdminUsersPage'
import * as sdk from '../api/generated/sdk.gen'
import type { AdminUserListResponse } from '../api/generated/types.gen'
import type { ReactNode } from 'react'

function emptyList(): AdminUserListResponse {
  return { items: [], total: 0, page: 0, pageSize: 20 }
}

function Wrapper({ children, initialEntry = '/admin/users' }: { children: ReactNode; initialEntry?: string }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <MemoryRouter initialEntries={[initialEntry]}>
      <QueryClientProvider client={qc}>{children}</QueryClientProvider>
    </MemoryRouter>
  )
}

describe('AdminUsersPage', () => {
  afterEach(() => vi.restoreAllMocks())

  it('debounces the search field instead of querying on every keystroke', async () => {
    const listSpy = vi
      .spyOn(sdk, 'adminListUsers')
      .mockResolvedValue({ data: emptyList(), error: undefined } as Awaited<ReturnType<typeof sdk.adminListUsers>>)

    render(<AdminUsersPage />, { wrapper: Wrapper })

    await waitFor(() => expect(listSpy).toHaveBeenCalledWith({ query: { query: undefined, page: 0, pageSize: 20 } }))
    listSpy.mockClear()

    await userEvent.type(screen.getByPlaceholderText(/search by email or phone/i), 'alice')

    // Not called on every keystroke - only after the debounce window settles.
    expect(listSpy).not.toHaveBeenCalled()

    await waitFor(() => expect(listSpy).toHaveBeenCalledWith({ query: { query: 'alice', page: 0, pageSize: 20 } }), {
      timeout: 2000
    })
  })

  it('the clear button resets the search field and reloads the unfiltered list', async () => {
    const listSpy = vi
      .spyOn(sdk, 'adminListUsers')
      .mockResolvedValue({ data: emptyList(), error: undefined } as Awaited<ReturnType<typeof sdk.adminListUsers>>)

    render(<AdminUsersPage />, { wrapper: Wrapper })
    await waitFor(() => expect(listSpy).toHaveBeenCalled())

    const input = screen.getByPlaceholderText(/search by email or phone/i)
    await userEvent.type(input, 'bob')
    await waitFor(() => expect(listSpy).toHaveBeenCalledWith({ query: { query: 'bob', page: 0, pageSize: 20 } }))
    listSpy.mockClear()

    await userEvent.click(screen.getByRole('button', { name: /clear search/i }))

    expect(input).toHaveValue('')
    await waitFor(() => expect(listSpy).toHaveBeenCalledWith({ query: { query: undefined, page: 0, pageSize: 20 } }))
  })

  it('navigates to the user detail page with a from param encoding the current search', async () => {
    vi.spyOn(sdk, 'adminListUsers').mockResolvedValue({
      data: { items: [{ id: 42, email: 'target@test.com', lastLoginAt: null }], total: 1, page: 0, pageSize: 20 },
      error: undefined
    } as Awaited<ReturnType<typeof sdk.adminListUsers>>)

    function DetailStub() {
      const [params] = useSearchParams()
      return <div>from={params.get('from')}</div>
    }

    render(
      <MemoryRouter initialEntries={['/admin/users?query=alice&page=1']}>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <Routes>
            <Route path="/admin/users" element={<AdminUsersPage />} />
            <Route path="/admin/users/:id" element={<DetailStub />} />
          </Routes>
        </QueryClientProvider>
      </MemoryRouter>
    )

    await waitFor(() => expect(screen.getByText('target@test.com')).toBeInTheDocument())
    await userEvent.click(screen.getByText('target@test.com'))

    expect(await screen.findByText('from=/admin/users?query=alice&page=1')).toBeInTheDocument()
  })
})
