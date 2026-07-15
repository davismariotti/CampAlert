import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RequestEditModal } from '../features/requests/RequestEditModal'
import * as sdk from '../api/generated/sdk.gen'
import type { SearchRequestResponse } from '../api/generated/types.gen'

const baseRequest: SearchRequestResponse = {
  id: 1,
  startDay: '2026-07-10',
  nights: 2,
  groupSize: 4,
  campsiteId: 123,
  campgroundName: 'Upper Pines',
  name: 'Yosemite weekend',
  completed: false,
  stats: {
    totalChecks: 0,
    availableChecks: 0,
    availabilityRate: null,
    avgAvailabilityWindowMinutes: 0,
    missedQuietHoursWindows: 0
  },
  provider: { type: 'RECREATION_GOV', name: 'Recreation.gov' }
}

function Wrapper({ request, onClose = () => {} }: { request: SearchRequestResponse; onClose?: () => void }) {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return (
    <MemoryRouter>
      <QueryClientProvider client={qc}>
        <RequestEditModal request={request} onClose={onClose} />
      </QueryClientProvider>
    </MemoryRouter>
  )
}

describe('RequestEditModal date window', () => {
  it('initializes in exact mode when the request has no searchEndDay', () => {
    render(<Wrapper request={baseRequest} />)

    expect(screen.getByRole('button', { name: 'Exact dates' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.queryByLabelText('Earliest arrival')).not.toBeInTheDocument()
  })

  it('initializes in flexible mode when the request has a searchEndDay', () => {
    render(<Wrapper request={{ ...baseRequest, searchEndDay: '2026-07-18' }} />)

    expect(screen.getByRole('button', { name: 'Flexible window' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByLabelText('Earliest arrival')).toBeInTheDocument()
    expect(screen.getByLabelText('Latest checkout')).toHaveValue('2026-07-18')
  })

  it('switching to exact mode and saving omits searchEndDay from the payload', async () => {
    const updateSpy = vi.spyOn(sdk, 'updateSearchRequest').mockResolvedValue({
      data: { ...baseRequest },
      error: undefined
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any)

    render(<Wrapper request={{ ...baseRequest, searchEndDay: '2026-07-18' }} />)
    await userEvent.click(screen.getByRole('button', { name: 'Exact dates' }))
    await userEvent.click(screen.getByRole('button', { name: /save/i }))

    expect(updateSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        body: expect.objectContaining({ searchEndDay: undefined })
      })
    )
  })

  it('saving a valid flexible range includes searchEndDay in the payload', async () => {
    const updateSpy = vi.spyOn(sdk, 'updateSearchRequest').mockResolvedValue({
      data: { ...baseRequest },
      error: undefined
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any)

    render(<Wrapper request={{ ...baseRequest, searchEndDay: '2026-07-18' }} />)
    await userEvent.click(screen.getByRole('button', { name: /save/i }))

    expect(updateSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        body: expect.objectContaining({ searchEndDay: '2026-07-18' })
      })
    )
  })

  it('an invalid flexible range disables Save', async () => {
    render(<Wrapper request={{ ...baseRequest, searchEndDay: '2026-07-18' }} />)
    const checkoutInput = screen.getByLabelText('Latest checkout')
    await userEvent.clear(checkoutInput)
    await userEvent.type(checkoutInput, '2026-07-11')

    expect(screen.getByRole('button', { name: /save/i })).toBeDisabled()
  })

  it('switching from exact to flexible mode preserves the existing arrival date', async () => {
    render(<Wrapper request={baseRequest} />)
    await userEvent.click(screen.getByRole('button', { name: 'Flexible window' }))

    expect(screen.getByLabelText('Earliest arrival')).toHaveValue(baseRequest.startDay)
  })
})
