import { useQuery } from '@tanstack/react-query'
import { getPermitDivisionAvailability } from '../../api/generated/sdk.gen'

export function useItineraryDivisionAvailability(
  permitId: string | undefined,
  divisionId: string | undefined,
  month: number,
  year: number
) {
  return useQuery({
    queryKey: ['permit-itinerary-availability', permitId, divisionId, year, month],
    queryFn: async () => {
      const result = await getPermitDivisionAvailability({
        path: { id: permitId!, divisionId: divisionId! },
        query: { month, year }
      })
      if (result.error) throw result
      return result.data!
    },
    enabled: !!permitId && !!divisionId,
    staleTime: 60_000
  })
}
