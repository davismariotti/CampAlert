import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ZoneRequestBuilder } from '../features/permit/ZoneRequestBuilder'
import * as sdk from '../api/generated/sdk.gen'
import type { PermitSearchResult } from '../api/generated/types.gen'
import type { ReactNode } from 'react'

const permit: PermitSearchResult = {
  id: 'p1',
  name: 'Desolation Wilderness',
  recareaName: 'Eldorado National Forest',
  type: 'ZONE',
  isSupported: true,
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

describe('ZoneRequestBuilder', () => {
  it("passes the selected permit's provider through to createPermitSearchRequest", async () => {
    vi.spyOn(sdk, 'getPermit').mockResolvedValue({
      data: {
        id: 'p1',
        name: 'Desolation Wilderness',
        recareaName: 'Eldorado National Forest',
        type: 'ZONE',
        maxGroupSize: null,
        divisions: [
          { id: 'd1', name: 'Zone 1', description: null, district: null, maxGroupSize: null, childDivisionIds: null }
        ]
      },
      error: undefined
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any)
    vi.spyOn(sdk, 'getPermitAvailability').mockResolvedValue({
      data: { divisions: {} },
      error: undefined
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any)
    const createSpy = vi.spyOn(sdk, 'createPermitSearchRequest').mockResolvedValue({
      data: { id: 1 },
      error: undefined
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any)

    render(<ZoneRequestBuilder permit={permit} onClear={() => {}} />, { wrapper: Wrapper })

    await userEvent.click(await screen.findByRole('button', { name: /zone 1/i }))
    // date input is the first empty input with type=date (mirrors RequestBuilder.test.tsx's approach)
    const nightInput = screen.getAllByDisplayValue('')[0] as HTMLInputElement
    await userEvent.type(nightInput, '2026-07-01')

    await userEvent.click(screen.getByRole('button', { name: /set alert/i }))

    expect(createSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        body: expect.objectContaining({ provider: { type: 'RECREATION_GOV', name: 'Recreation.gov' } })
      })
    )
  })
})
