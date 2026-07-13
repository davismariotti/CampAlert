import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ProviderSelect } from '../components/ui/ProviderSelect'
import type { Provider } from '../api/generated/types.gen'

const recreationGov: Provider = { type: 'RECREATION_GOV', name: 'Recreation.gov' }
// Forces the multi-provider branch even though only RECREATION_GOV exists today — see design
// decision 8's risk mitigation in the openspec change for why this is tested this way.
const otherProvider = { type: 'RECREATION_GOV', name: 'Other Provider' } as Provider

describe('ProviderSelect', () => {
  it('renders nothing with a single provider', () => {
    const { container } = render(
      <ProviderSelect providers={[recreationGov]} selected={recreationGov} onChange={() => {}} />
    )

    expect(container).toBeEmptyDOMElement()
  })

  it('renders an option per provider when more than one exists', () => {
    render(<ProviderSelect providers={[recreationGov, otherProvider]} selected={recreationGov} onChange={() => {}} />)

    expect(screen.getByRole('button', { name: 'Recreation.gov' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Other Provider' })).toBeInTheDocument()
  })

  it('calls onChange with the clicked provider', async () => {
    const onChange = vi.fn()
    render(<ProviderSelect providers={[recreationGov, otherProvider]} selected={recreationGov} onChange={onChange} />)

    await userEvent.click(screen.getByRole('button', { name: 'Other Provider' }))

    expect(onChange).toHaveBeenCalledWith(otherProvider)
  })
})
