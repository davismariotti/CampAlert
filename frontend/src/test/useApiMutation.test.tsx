import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ToastProvider } from '../components/ui/Toast'
import { useApiMutation } from '../hooks/useApiMutation'
import type { UseMutationOptions } from '@tanstack/react-query'

function TriggerButton({ options }: { options: UseMutationOptions & { errorMessage?: string } }) {
  const mutation = useApiMutation(options)
  return <button onClick={() => mutation.mutate()}>trigger</button>
}

function Wrapper({ options }: { options: UseMutationOptions & { errorMessage?: string } }) {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return (
    <QueryClientProvider client={qc}>
      <ToastProvider>
        <TriggerButton options={options} />
      </ToastProvider>
    </QueryClientProvider>
  )
}

describe('useApiMutation', () => {
  it('shows default toast on error when no onError provided', async () => {
    render(
      <Wrapper
        options={{
          mutationFn: async () => {
            throw new Error('fail')
          }
        }}
      />
    )
    await userEvent.click(screen.getByRole('button'))
    await waitFor(() => expect(screen.getByText('Something went wrong. Please try again.')).toBeInTheDocument())
  })

  it('shows custom errorMessage in toast', async () => {
    render(
      <Wrapper
        options={{
          mutationFn: async () => {
            throw new Error('fail')
          },
          errorMessage: 'Custom error message.'
        }}
      />
    )
    await userEvent.click(screen.getByRole('button'))
    await waitFor(() => expect(screen.getByText('Custom error message.')).toBeInTheDocument())
  })

  it('calls custom onError instead of showing toast', async () => {
    const onError = vi.fn()
    render(
      <Wrapper
        options={{
          mutationFn: async () => {
            throw new Error('fail')
          },
          onError
        }}
      />
    )
    await userEvent.click(screen.getByRole('button'))
    await waitFor(() => expect(onError).toHaveBeenCalledOnce())
    expect(screen.queryByText('Something went wrong. Please try again.')).not.toBeInTheDocument()
  })
})
