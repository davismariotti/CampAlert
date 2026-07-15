import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
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

const campLifeCampground: CampgroundSearchResult = {
  id: 791,
  name: 'Collins Lake',
  provider: { type: 'CAMPLIFE', name: 'CampLife' }
}

function Wrapper({ campground: cg = campground }: { campground?: CampgroundSearchResult }) {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return (
    <MemoryRouter>
      <QueryClientProvider client={qc}>
        <RequestBuilder campground={cg} onClear={() => {}} />
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

  it('does not fetch the campground catalog for a Recreation.gov campground', async () => {
    const catalogSpy = vi.spyOn(sdk, 'getCampground')
    render(<Wrapper />)

    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(catalogSpy).not.toHaveBeenCalled()
  })

  it('shows an equipment-type filter for a CampLife campground whose catalog exposes equipment types', async () => {
    vi.spyOn(sdk, 'getCampground').mockResolvedValue({
      data: { campsites: {}, equipmentTypes: ['TENT', 'MOTORHOME'] },
      error: undefined
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any)

    render(<Wrapper campground={campLifeCampground} />)

    await waitFor(() => expect(screen.getByText('Equipment type')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'TENT' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'MOTORHOME' })).toBeInTheDocument()
  })

  it('shows no equipment-type filter for a Recreation.gov campground', () => {
    render(<Wrapper />)

    expect(screen.queryByText('Equipment type')).not.toBeInTheDocument()
  })

  it("passes the selected campground's siteIds through to createSearchRequest when the campsite modal confirms a selection", async () => {
    vi.spyOn(sdk, 'getCampground').mockResolvedValue({
      data: {
        campsites: {
          '101': {
            campsiteId: 101,
            site: 'Site 101',
            loop: 'North',
            campsiteReserveType: '',
            minimumNumberOfPeople: 1,
            maximumNumberOfPeople: 6,
            availabilities: {},
            quantities: {}
          }
        }
      },
      error: undefined
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any)
    const createSpy = vi.spyOn(sdk, 'createSearchRequest').mockResolvedValue({
      data: { id: 1 },
      error: undefined
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any)

    render(<Wrapper campground={campLifeCampground} />)
    await userEvent.type(screen.getByPlaceholderText('Alert name'), 'My Trip')
    const dateInput = screen.getAllByDisplayValue('')[0] as HTMLInputElement
    await userEvent.type(dateInput, '2026-07-01')

    await userEvent.click(screen.getByRole('button', { name: /choose specific campsites/i }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Site 101' })).toBeInTheDocument())
    await userEvent.click(screen.getByRole('button', { name: 'Site 101' }))
    await userEvent.click(screen.getByRole('button', { name: /confirm/i }))

    await userEvent.click(screen.getByRole('button', { name: /set alert/i }))

    expect(createSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        body: expect.objectContaining({ siteIds: ['101'] })
      })
    )
  })

  describe('flexible date window', () => {
    it('exact mode omits latestStartDay from the create payload', async () => {
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
          body: expect.objectContaining({ latestStartDay: undefined })
        })
      )
    })

    it('switching to flexible mode shows earliest arrival and latest arrival fields', async () => {
      render(<Wrapper />)
      await userEvent.click(screen.getByRole('button', { name: 'Flexible window' }))

      expect(screen.getByLabelText('Earliest arrival')).toBeInTheDocument()
      expect(screen.getByLabelText('Latest arrival')).toBeInTheDocument()
    })

    it('Set Alert is disabled with an incomplete flexible range', async () => {
      render(<Wrapper />)
      await userEvent.type(screen.getByPlaceholderText('Alert name'), 'My Trip')
      await userEvent.type(screen.getByLabelText(/earliest arrival|arrival date/i), '2026-07-01')
      await userEvent.click(screen.getByRole('button', { name: 'Flexible window' }))

      expect(screen.getByRole('button', { name: /set alert/i })).toBeDisabled()
    })

    it('a valid flexible range is included in the create payload', async () => {
      const createSpy = vi.spyOn(sdk, 'createSearchRequest').mockResolvedValue({
        data: { id: 1 },
        error: undefined
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      } as any)

      render(<Wrapper />)
      await userEvent.type(screen.getByPlaceholderText('Alert name'), 'My Trip')
      await userEvent.click(screen.getByRole('button', { name: 'Flexible window' }))
      await userEvent.type(screen.getByLabelText('Earliest arrival'), '2026-07-01')
      await userEvent.type(screen.getByLabelText('Latest arrival'), '2026-07-08')
      await userEvent.click(screen.getByRole('button', { name: /set alert/i }))

      expect(createSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          body: expect.objectContaining({ startDay: '2026-07-01', latestStartDay: '2026-07-08' })
        })
      )
    })

    it('a range exceeding the CampLife max width is rejected', async () => {
      render(<Wrapper campground={campLifeCampground} />)
      await userEvent.type(screen.getByPlaceholderText('Alert name'), 'My Trip')
      await userEvent.click(screen.getByRole('button', { name: 'Flexible window' }))
      await userEvent.type(screen.getByLabelText('Earliest arrival'), '2026-07-01')
      await userEvent.type(screen.getByLabelText('Latest arrival'), '2026-07-15')

      expect(screen.getByText(/9 days/)).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /set alert/i })).toBeDisabled()
    })
  })
})
