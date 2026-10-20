import { describe, it, expect, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { TimeWindowFields } from '../features/requests/TimeWindowFields'

function renderFields(props: Partial<React.ComponentProps<typeof TimeWindowFields>> = {}) {
  const handlers = { onEarliestTimeChange: vi.fn(), onLatestTimeChange: vi.fn() }
  render(<TimeWindowFields earliestTime="" latestTime="" {...handlers} {...props} />)
  return handlers
}

describe('TimeWindowFields', () => {
  it('shows both optional time inputs and a local-time hint', () => {
    renderFields()

    expect(screen.getByLabelText('Earliest time')).toHaveAttribute('type', 'time')
    expect(screen.getByLabelText('Latest time')).toHaveAttribute('type', 'time')
    expect(screen.getByText(/local time/i)).toBeInTheDocument()
  })

  it('reflects the provided values', () => {
    renderFields({ earliestTime: '11:30', latestTime: '14:00' })

    expect(screen.getByLabelText('Earliest time')).toHaveValue('11:30')
    expect(screen.getByLabelText('Latest time')).toHaveValue('14:00')
  })

  it('reports changes through the handlers', () => {
    const handlers = renderFields()

    fireEvent.change(screen.getByLabelText('Earliest time'), { target: { value: '11:30' } })
    fireEvent.change(screen.getByLabelText('Latest time'), { target: { value: '14:00' } })

    expect(handlers.onEarliestTimeChange).toHaveBeenCalledWith('11:30')
    expect(handlers.onLatestTimeChange).toHaveBeenCalledWith('14:00')
  })

  it('shows an error message when given one', () => {
    renderFields({ error: 'Earliest time must be before latest time' })

    expect(screen.getByText('Earliest time must be before latest time')).toBeInTheDocument()
  })
})
