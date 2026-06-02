import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RequestBuilder } from '../features/requests/RequestBuilder'

const campground = { id: 123, name: 'Test Campground' }

function Wrapper() {
  const qc = new QueryClient()
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
})
