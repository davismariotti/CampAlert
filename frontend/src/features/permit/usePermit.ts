import { useQuery } from '@tanstack/react-query'
import { getPermit } from '../../api/generated/sdk.gen'

export function usePermit(permitId: string | undefined) {
  return useQuery({
    queryKey: ['permits', permitId],
    queryFn: async () => {
      const result = await getPermit({ path: { id: permitId! } })
      if (result.error) throw result
      return result.data!
    },
    enabled: !!permitId,
    staleTime: 5 * 60 * 1000
  })
}
