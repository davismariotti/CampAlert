import { useQuery } from '@tanstack/react-query'
import { searchCampgrounds } from '../../api/generated/sdk.gen'
import { useDebounce } from '../../hooks/useDebounce'

export function useCampgroundSearch(query: string) {
  const debouncedQuery = useDebounce(query, 300)
  const enabled = debouncedQuery.trim().length >= 3

  return useQuery({
    queryKey: ['campgrounds', 'search', debouncedQuery],
    queryFn: () => searchCampgrounds({ query: { q: debouncedQuery } }).then((r) => r.data ?? []),
    enabled,
    staleTime: 30_000
  })
}
