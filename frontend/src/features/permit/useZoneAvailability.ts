import { useQuery } from '@tanstack/react-query'
import { getPermitAvailability } from '../../api/generated/sdk.gen'

/** `startDate` is any date within the month to preview (e.g. the first of the viewed month), formatted YYYY-MM-DD. */
export function useZoneAvailability(permitId: string | undefined, startDate: string) {
  const monthKey = startDate.slice(0, 7)

  return useQuery({
    queryKey: ['permit-zone-availability', permitId, monthKey],
    queryFn: async () => {
      const result = await getPermitAvailability({ path: { id: permitId! }, query: { startDate } })
      if (result.error) throw result
      return result.data!
    },
    enabled: !!permitId,
    staleTime: 60_000
  })
}
