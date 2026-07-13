import { useQuery } from '@tanstack/react-query'
import { searchPermits } from '../../api/generated/sdk.gen'
import type { ProviderType } from '../../api/generated/types.gen'
import { useDebounce } from '../../hooks/useDebounce'

export function usePermitSearch(query: string, provider: ProviderType) {
  const debouncedQuery = useDebounce(query, 300)
  const enabled = debouncedQuery.trim().length >= 3

  return useQuery({
    queryKey: ['permits', 'search', debouncedQuery, provider],
    queryFn: () => searchPermits({ query: { q: debouncedQuery, provider } }).then((r) => r.data ?? []),
    enabled,
    staleTime: 30_000
  })
}
