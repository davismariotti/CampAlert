import { useQuery } from '@tanstack/react-query'
import { searchCampgrounds } from '../../api/generated/sdk.gen'
import type { ProviderType } from '../../api/generated/types.gen'
import { useDebounce } from '../../hooks/useDebounce'

export function useCampgroundSearch(query: string, provider: ProviderType) {
  const debouncedQuery = useDebounce(query, 300)
  const enabled = debouncedQuery.trim().length >= 3

  return useQuery({
    queryKey: ['campgrounds', 'search', debouncedQuery, provider],
    queryFn: () => searchCampgrounds({ query: { q: debouncedQuery, provider } }).then((r) => r.data ?? []),
    enabled,
    staleTime: 30_000
  })
}
