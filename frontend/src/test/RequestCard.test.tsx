import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RequestCard } from '../features/requests/RequestCard'
import * as sdk from '../api/generated/sdk.gen'
import type { SearchRequestResponse } from '../api/generated/types.gen'
import type { ReactNode } from 'react'

const request: SearchRequestResponse = {
  id: 1,
  startDay: '2026-07-10',
  nights: 2,
  groupSize: 4,
  campsiteId: 123,
  campgroundName: 'Upper Pines',
  name: 'Yosemite weekend',
  completed: false,
  stats: {
    totalChecks: 12,
    availableChecks: 3,
    availabilityRate: 0.25,
    avgAvailabilityWindowMinutes: 42,
    missedQuietHoursWindows: 1
  },
  provider: { type: 'RECREATION_GOV', name: 'Recreation.gov' }
}

function Wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return (
    <MemoryRouter>
      <QueryClientProvider client={qc}>{children}</QueryClientProvider>
    </MemoryRouter>
  )
}

describe('RequestCard', () => {
  it('opens stats modal from existing request stats without fetching detail', async () => {
    const detailSpy = vi.spyOn(sdk, 'getSearchRequest')

    render(<RequestCard request={request} />, { wrapper: Wrapper })

    await userEvent.click(screen.getByRole('button', { name: /stats/i }))

    expect(screen.getByRole('heading', { name: /alert stats/i })).toBeInTheDocument()
    expect(screen.getByText('12')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('25%')).toBeInTheDocument()
    expect(screen.getByText('42 min')).toBeInTheDocument()
    expect(screen.getByText('1')).toBeInTheDocument()
    expect(detailSpy).not.toHaveBeenCalled()
  })

  it('always shows a provider badge, even with a single provider in the system', () => {
    render(<RequestCard request={request} />, { wrapper: Wrapper })

    expect(screen.getByText('Recreation.gov')).toBeInTheDocument()
  })

  it("shows each card's own provider name when requests span more than one provider", () => {
    const otherProviderRequest: SearchRequestResponse = {
      ...request,
      id: 2,
      name: 'Other provider request',
      provider: { type: 'RECREATION_GOV', name: 'Some Other Provider' }
    }

    render(
      <>
        <RequestCard request={request} />
        <RequestCard request={otherProviderRequest} />
      </>,
      { wrapper: Wrapper }
    )

    expect(screen.getByText('Recreation.gov')).toBeInTheDocument()
    expect(screen.getByText('Some Other Provider')).toBeInTheDocument()
  })

  it('shows a specific-sites indicator instead of loop pills when siteIds is set', () => {
    const siteScopedRequest: SearchRequestResponse = {
      ...request,
      loops: ['North Loop'],
      siteIds: ['12345', '12346']
    }

    render(<RequestCard request={siteScopedRequest} />, { wrapper: Wrapper })

    expect(screen.getByText('2 specific sites')).toBeInTheDocument()
    expect(screen.queryByText('North Loop')).not.toBeInTheDocument()
  })

  it('shows loop pills when siteIds is empty', () => {
    const loopScopedRequest: SearchRequestResponse = {
      ...request,
      loops: ['North Loop'],
      siteIds: null
    }

    render(<RequestCard request={loopScopedRequest} />, { wrapper: Wrapper })

    expect(screen.getByText('North Loop')).toBeInTheDocument()
  })

  describe('flexible date window', () => {
    it('shows the configured range for a flexible request with no current match', () => {
      const flexRequest: SearchRequestResponse = { ...request, latestStartDay: '2026-07-18' }

      render(<RequestCard request={flexRequest} />, { wrapper: Wrapper })

      expect(screen.getByText(/Any 2 nights/)).toBeInTheDocument()
      expect(screen.getByText(/Jul 10-Jul 18/)).toBeInTheDocument()
      expect(screen.queryByText(/^Matched/)).not.toBeInTheDocument()
    })

    it('shows the matched dates prominently for a flexible request with a current match', () => {
      const matchedRequest: SearchRequestResponse = {
        ...request,
        latestStartDay: '2026-07-18',
        matchedStartDay: '2026-07-14',
        matchedEndDay: '2026-07-16'
      }

      render(<RequestCard request={matchedRequest} />, { wrapper: Wrapper })

      expect(screen.getByText(/Matched Jul 14.*Jul 16/)).toBeInTheDocument()
    })

    it('shows the compact exact-date meta line unchanged when latestStartDay is absent', () => {
      render(<RequestCard request={request} />, { wrapper: Wrapper })

      expect(screen.queryByText(/^Any/)).not.toBeInTheDocument()
      expect(screen.queryByText(/^Matched/)).not.toBeInTheDocument()
    })

    it('shows flexible range and matched-date context in the stats modal for a completed flexible request', async () => {
      const completedFlexRequest: SearchRequestResponse = {
        ...request,
        completed: true,
        latestStartDay: '2026-07-18',
        matchedStartDay: '2026-07-14',
        matchedEndDay: '2026-07-16'
      }

      render(<RequestCard request={completedFlexRequest} />, { wrapper: Wrapper })
      await userEvent.click(screen.getByRole('button', { name: /stats/i }))

      expect(screen.getByText(/Any 2 nights, arriving Jul 10-Jul 18.*Matched Jul 14.*Jul 16/)).toBeInTheDocument()
    })
  })
})
