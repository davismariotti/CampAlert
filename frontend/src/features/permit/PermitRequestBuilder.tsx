import { ZoneRequestBuilder } from './ZoneRequestBuilder'
import { ItineraryRequestBuilder } from './ItineraryRequestBuilder'
import type { PermitSearchResult } from '../../api/generated/types.gen'

interface Props {
  permit: PermitSearchResult
  onClear: () => void
  onSuccess?: () => void
}

export function PermitRequestBuilder({ permit, onClear, onSuccess }: Props) {
  if (permit.type === 'ZONE') {
    return <ZoneRequestBuilder permit={permit} onClear={onClear} onSuccess={onSuccess} />
  }

  if (permit.type === 'ITINERARY') {
    return <ItineraryRequestBuilder permit={permit} onClear={onClear} onSuccess={onSuccess} />
  }

  return (
    <div className="mt-4 rounded-2xl bg-white p-6 shadow-sm">
      <p className="text-sm text-forest-600">
        CampAlert doesn't support this permit's reservation type yet.{' '}
        <button type="button" onClick={onClear} className="font-medium text-forest-800 underline hover:text-forest-900">
          Search for a different permit
        </button>
        .
      </p>
    </div>
  )
}
