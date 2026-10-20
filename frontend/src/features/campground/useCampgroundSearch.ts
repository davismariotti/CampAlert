import { useQuery } from '@tanstack/react-query'
import { searchCampgrounds } from '../../api/generated/sdk.gen'
import type { ProviderType } from '../../api/generated/types.gen'
import { useDebounce } from '../../hooks/useDebounce'

interface Options {
  /** Scope the search to one provider; omitted searches every provider that takes part in unscoped search. */
  provider?: ProviderType
  /** Minimum trimmed query length before a search is issued (default 3). */
  minChars?: number
}

export function useCampgroundSearch(query: string, { provider, minChars = 3 }: Options = {}) {
  const debouncedQuery = useDebounce(query, 300)
  const enabled = debouncedQuery.trim().length >= minChars

  return useQuery({
    queryKey: ['campgrounds', 'search', provider ?? 'all', debouncedQuery],
    queryFn: () => searchCampgrounds({ query: { q: debouncedQuery, provider } }).then((r) => r.data ?? []),
    enabled,
    staleTime: 30_000
  })
}
