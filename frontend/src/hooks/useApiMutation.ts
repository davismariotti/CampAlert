import { useMutation } from '@tanstack/react-query'
import type { UseMutationOptions, DefaultError } from '@tanstack/react-query'
import { useToast } from '../components/ui/useToast'

export function useApiMutation<TData = unknown, TError = DefaultError, TVariables = void, TContext = unknown>(
  options: UseMutationOptions<TData, TError, TVariables, TContext> & { errorMessage?: string }
) {
  const { showToast } = useToast()
  const { onError, errorMessage = 'Something went wrong. Please try again.', ...rest } = options
  return useMutation<TData, TError, TVariables, TContext>({
    ...rest,
    onError: onError ?? (() => showToast(errorMessage))
  })
}
