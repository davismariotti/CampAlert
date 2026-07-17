import { ZoneRequestBuilder } from './ZoneRequestBuilder'
import { ItineraryRequestBuilder } from './ItineraryRequestBuilder'
import { TrailheadRequestBuilder } from './TrailheadRequestBuilder'
import type { PermitSearchResult } from '../../api/generated/types.gen'

interface Props {
  permit: PermitSearchResult
  onClear: () => void
  onSuccess?: () => void
}

function UnsupportedMessage({ message, onClear }: { message: string; onClear: () => void }) {
  return (
    <div className="mt-4 rounded-2xl bg-white p-6 shadow-sm">
      <p className="text-sm text-forest-600">
        {message}{' '}
        <button type="button" onClick={onClear} className="font-medium text-forest-800 underline hover:text-forest-900">
          Search for a different permit
        </button>
        .
      </p>
    </div>
  )
}

export function PermitRequestBuilder({ permit, onClear, onSuccess }: Props) {
  if (permit.type === 'ZONE') {
    return <ZoneRequestBuilder permit={permit} onClear={onClear} onSuccess={onSuccess} />
  }

  if (permit.type === 'ITINERARY') {
    return <ItineraryRequestBuilder permit={permit} onClear={onClear} onSuccess={onSuccess} />
  }

  if (permit.type === 'TRAILHEAD') {
    return <TrailheadRequestBuilder permit={permit} onClear={onClear} onSuccess={onSuccess} />
  }

  // isSupported distinguishes two different situations: the backend genuinely can't classify this
  // permit's reservation mechanism (never supportable), vs. it classified fine but this app build
  // predates that type's builder UI (a future type, once the backend ships it before the frontend does).
  return (
    <UnsupportedMessage
      onClear={onClear}
      message={
        permit.isSupported
          ? "CampAlert supports this permit, but this version of the app doesn't have its request builder yet."
          : "CampAlert doesn't support this permit's reservation type yet."
      }
    />
  )
}
