import { describe, it, expect, vi, afterEach } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RequestBuilder } from '../features/requests/RequestBuilder'
import { RequestEditModal } from '../features/requests/RequestEditModal'
import { RequestCard } from '../features/requests/RequestCard'
import * as sdk from '../api/generated/sdk.gen'
import type { CampgroundSearchResult, SearchRequestResponse } from '../api/generated/types.gen'

const center: CampgroundSearchResult = {
  id: 5446,
  name: 'SF Enrollment Center — San Francisco, CA',
  provider: { type: 'GLOBAL_ENTRY', name: 'Global Entry' }
}

const baseRequest: SearchRequestResponse = {
  id: 7,
  startDay: '2026-10-27',
  nights: 1,
  groupSize: 1,
  campsiteId: 5446,
  campgroundName: 'SF Enrollment Center — San Francisco, CA',
  name: 'SF Global Entry',
  completed: false,
  stats: {
    totalChecks: 4,
    availableChecks: 1,
    availabilityRate: 0.25,
    avgAvailabilityWindowMinutes: 10,
    missedQuietHoursWindows: 0
  },
  provider: { type: 'GLOBAL_ENTRY', name: 'Global Entry' }
}

function Providers({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false }, queries: { retry: false } } })
  return (
    <MemoryRouter>
      <QueryClientProvider client={qc}>{children}</QueryClientProvider>
    </MemoryRouter>
  )
}

function stubCreate() {
  return vi.spyOn(sdk, 'createSearchRequest').mockResolvedValue({
    data: { id: 1 },
    error: undefined
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  } as any)
}

function stubUpdate() {
  return vi.spyOn(sdk, 'updateSearchRequest').mockResolvedValue({
    data: baseRequest,
    error: undefined
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  } as any)
}

afterEach(() => vi.restoreAllMocks())

describe('RequestBuilder for a Global Entry center', () => {
  function renderBuilder() {
    return render(<RequestBuilder campground={center} onClear={() => {}} />, { wrapper: Providers })
  }

  it('hides the campsite-only controls and fetches no campsite catalog data', async () => {
    const catalogSpy = vi.spyOn(sdk, 'getCampground')
    const loopsSpy = vi.spyOn(sdk, 'getCampgroundLoops')

    renderBuilder()
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(screen.queryByText('Nights')).not.toBeInTheDocument()
    expect(screen.queryByText('Group size')).not.toBeInTheDocument()
    expect(screen.queryByText('Specific campsites')).not.toBeInTheDocument()
    expect(screen.queryByText('Equipment type')).not.toBeInTheDocument()
    expect(catalogSpy).not.toHaveBeenCalled()
    expect(loopsSpy).not.toHaveBeenCalled()
  })

  it('shows the time-of-day window and day wording', () => {
    renderBuilder()

    expect(screen.getByLabelText('Earliest time')).toBeInTheDocument()
    expect(screen.getByLabelText('Latest time')).toBeInTheDocument()
    expect(screen.getByLabelText('Date')).toBeInTheDocument()
  })

  it('defaults the alert name to the center name without the city suffix', () => {
    renderBuilder()

    expect(screen.getByPlaceholderText('Alert name')).toHaveValue(`SF Enrollment Center ${new Date().getFullYear()}`)
  })

  it('submits fixed nights and group size, no campsite filters, and the time window', async () => {
    const createSpy = stubCreate()
    renderBuilder()

    await userEvent.type(screen.getByLabelText('Date'), '2026-10-27')
    fireEvent.change(screen.getByLabelText('Earliest time'), { target: { value: '11:30' } })
    fireEvent.change(screen.getByLabelText('Latest time'), { target: { value: '14:00' } })
    await userEvent.click(screen.getByRole('button', { name: /set alert/i }))

    expect(createSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        body: expect.objectContaining({
          campsiteId: 5446,
          nights: 1,
          groupSize: 1,
          loops: undefined,
          siteIds: undefined,
          amenityIds: undefined,
          earliestTime: '11:30',
          latestTime: '14:00',
          provider: center.provider
        })
      })
    )
  })

  it('omits a time bound left blank', async () => {
    const createSpy = stubCreate()
    renderBuilder()

    await userEvent.type(screen.getByLabelText('Date'), '2026-10-27')
    fireEvent.change(screen.getByLabelText('Earliest time'), { target: { value: '13:00' } })
    await userEvent.click(screen.getByRole('button', { name: /set alert/i }))

    expect(createSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        body: expect.objectContaining({ earliestTime: '13:00', latestTime: undefined })
      })
    )
  })

  it('a flexible range uses the 60-day limit and day labels', async () => {
    renderBuilder()
    await userEvent.click(screen.getByRole('button', { name: 'Flexible window' }))

    expect(screen.getByLabelText('Earliest day')).toBeInTheDocument()
    expect(screen.getByLabelText('Latest day')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Earliest day'), '2026-07-01')
    await userEvent.type(screen.getByLabelText('Latest day'), '2026-08-31')
    expect(screen.getByText("Range can't be more than 60 days for Global Entry")).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /set alert/i })).toBeDisabled()
  })

  it('an inverted window shows an error and blocks submitting', async () => {
    renderBuilder()

    await userEvent.type(screen.getByLabelText('Date'), '2026-10-27')
    fireEvent.change(screen.getByLabelText('Earliest time'), { target: { value: '14:00' } })
    fireEvent.change(screen.getByLabelText('Latest time'), { target: { value: '11:30' } })

    expect(screen.getByText('Earliest time must be before latest time')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /set alert/i })).toBeDisabled()
  })

  it('other providers show no time-of-day inputs', () => {
    const campground: CampgroundSearchResult = {
      id: 123,
      name: 'Test Campground',
      provider: { type: 'RECREATION_GOV', name: 'Recreation.gov' }
    }
    render(<RequestBuilder campground={campground} onClear={() => {}} />, { wrapper: Providers })

    expect(screen.queryByLabelText('Earliest time')).not.toBeInTheDocument()
    expect(screen.getByText('Nights')).toBeInTheDocument()
  })
})

describe('RequestEditModal for a Global Entry request', () => {
  const windowed: SearchRequestResponse = { ...baseRequest, earliestTime: '11:30', latestTime: '14:00' }

  function renderModal(request: SearchRequestResponse) {
    return render(<RequestEditModal request={request} onClose={() => {}} />, { wrapper: Providers })
  }

  it('prefills the window and hides the campsite-only controls', () => {
    renderModal(windowed)

    expect(screen.getByLabelText('Earliest time')).toHaveValue('11:30')
    expect(screen.getByLabelText('Latest time')).toHaveValue('14:00')
    expect(screen.queryByText('Nights')).not.toBeInTheDocument()
    expect(screen.queryByText('Specific campsites')).not.toBeInTheDocument()
  })

  it('saves the window with fixed nights and group size', async () => {
    const updateSpy = stubUpdate()
    renderModal(windowed)

    await userEvent.click(screen.getByRole('button', { name: /save/i }))

    expect(updateSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        body: expect.objectContaining({ nights: 1, groupSize: 1, earliestTime: '11:30', latestTime: '14:00' })
      })
    )
  })

  it('clearing a bound leaves it out of the update so the server clears it', async () => {
    const updateSpy = stubUpdate()
    renderModal(windowed)

    fireEvent.change(screen.getByLabelText('Latest time'), { target: { value: '' } })
    await userEvent.click(screen.getByRole('button', { name: /save/i }))

    expect(updateSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        body: expect.objectContaining({ earliestTime: '11:30', latestTime: undefined })
      })
    )
  })

  it('an inverted window disables Save', () => {
    renderModal(windowed)

    fireEvent.change(screen.getByLabelText('Latest time'), { target: { value: '10:00' } })

    expect(screen.getByRole('button', { name: /save/i })).toBeDisabled()
  })
})

describe('RequestCard for a Global Entry request', () => {
  it('shows the day range and window, with no nights or people', () => {
    const request: SearchRequestResponse = {
      ...baseRequest,
      latestStartDay: '2026-11-03',
      earliestTime: '11:30',
      latestTime: '14:00'
    }
    render(<RequestCard request={request} />, { wrapper: Providers })

    expect(screen.getByText('Oct 27–Nov 3 · 11:30 AM–2:00 PM')).toBeInTheDocument()
    expect(screen.queryByText(/night/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/person|people/i)).not.toBeInTheDocument()
    expect(screen.getByText('Global Entry')).toBeInTheDocument()
  })

  it('says any time when there is no window and shows a single day without a range', () => {
    render(<RequestCard request={baseRequest} />, { wrapper: Providers })

    expect(screen.getByText('Oct 27 · any time')).toBeInTheDocument()
  })

  it('shows the held match: count and earliest slot', () => {
    const request: SearchRequestResponse = {
      ...baseRequest,
      matchedStartDay: '2026-10-27',
      matchedStartTime: '11:40',
      matchedSlotCount: 3
    }
    render(<RequestCard request={request} />, { wrapper: Providers })

    expect(screen.getByText('3 appointments · earliest Oct 27, 11:40 AM')).toBeInTheDocument()
  })

  it('uses the singular for one appointment', () => {
    const request: SearchRequestResponse = {
      ...baseRequest,
      matchedStartDay: '2026-10-27',
      matchedStartTime: '09:10',
      matchedSlotCount: 1
    }
    render(<RequestCard request={request} />, { wrapper: Providers })

    expect(screen.getByText('1 appointment · earliest Oct 27, 9:10 AM')).toBeInTheDocument()
  })

  it('shows no match line when nothing is currently matched', () => {
    render(<RequestCard request={baseRequest} />, { wrapper: Providers })

    expect(screen.queryByText(/earliest/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Matched/)).not.toBeInTheDocument()
  })

  it('the stats modal describes appointments, not a stay', async () => {
    const request: SearchRequestResponse = {
      ...baseRequest,
      latestStartDay: '2026-11-03',
      earliestTime: '11:30',
      latestTime: '14:00',
      matchedStartDay: '2026-10-27',
      matchedStartTime: '11:40',
      matchedSlotCount: 2
    }
    render(<RequestCard request={request} />, { wrapper: Providers })

    await userEvent.click(screen.getByRole('button', { name: /stats/i }))

    expect(screen.getByRole('heading', { name: /alert stats/i })).toBeInTheDocument()
    expect(screen.queryByText(/Any 1 night/)).not.toBeInTheDocument()
    expect(screen.queryByText(/arriving/)).not.toBeInTheDocument()
  })
})
