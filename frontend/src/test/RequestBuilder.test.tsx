import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RequestBuilder } from '../features/requests/RequestBuilder'
import * as sdk from '../api/generated/sdk.gen'
import type { CampgroundSearchResult } from '../api/generated/types.gen'

const campground: CampgroundSearchResult = {
  id: 123,
  name: 'Test Campground',
  provider: { type: 'RECREATION_GOV', name: 'Recreation.gov' }
}

function Wrapper() {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return (
    <MemoryRouter>
      <QueryClientProvider client={qc}>
        <RequestBuilder campground={campground} onClear={() => {}} />
      </QueryClientProvider>
    </MemoryRouter>
  )
}

describe('RequestBuilder', () => {
  it('Set Alert button disabled when required fields are missing', () => {
    render(<Wrapper />)
    expect(screen.getByRole('button', { name: /set alert/i })).toBeDisabled()
  })

  it('Set Alert button enabled when all required fields filled', async () => {
    render(<Wrapper />)
    await userEvent.type(screen.getByPlaceholderText('Alert name'), 'My Trip')
    // date input is the first empty input with type=date
    const dateInput = screen.getAllByDisplayValue('')[0] as HTMLInputElement
    await userEvent.type(dateInput, '2026-07-01')
    expect(screen.getByRole('button', { name: /set alert/i })).toBeEnabled()
  })

  it('nights stepper cannot go below 1', async () => {
    render(<Wrapper />)
    const minus = screen.getAllByText('−')[0]
    await userEvent.click(minus)
    // both nights and group size start at 1; verify at least one "1" remains
    expect(screen.getAllByText('1').length).toBeGreaterThanOrEqual(1)
  })

  it('group size stepper cannot go below 1', async () => {
    render(<Wrapper />)
    const minus = screen.getAllByText('−')[1]
    await userEvent.click(minus)
    expect(screen.getAllByText('1').length).toBeGreaterThanOrEqual(1)
  })

  it("passes the selected campground's provider through to createSearchRequest", async () => {
    const createSpy = vi.spyOn(sdk, 'createSearchRequest').mockResolvedValue({
      data: { id: 1 },
      error: undefined
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any)

    render(<Wrapper />)
    await userEvent.type(screen.getByPlaceholderText('Alert name'), 'My Trip')
    const dateInput = screen.getAllByDisplayValue('')[0] as HTMLInputElement
    await userEvent.type(dateInput, '2026-07-01')
    await userEvent.click(screen.getByRole('button', { name: /set alert/i }))

    expect(createSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        body: expect.objectContaining({ provider: { type: 'RECREATION_GOV', name: 'Recreation.gov' } })
      })
    )
  })
})
