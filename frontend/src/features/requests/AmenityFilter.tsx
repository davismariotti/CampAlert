import type { AmenityOption } from '../../api/generated/types.gen'

interface Props {
  amenities: AmenityOption[]
  selected: number[] | null
  onChange: (amenityIds: number[] | null) => void
}

/**
 * Request-level amenity filter. Unlike the equipment-type filter, this is NOT resolved locally —
 * the selected ids are persisted on the request and sent as the availability call's `cgAmenity`
 * field on every poll; CampLife's own `isFiltered` response flag determines matches (see the design
 * conversation this shipped from — CampLife exposes no reliable way to know a site's amenities
 * without asking its own availability endpoint). Multiple selections combine with AND semantics
 * (verified against real traffic): a site must have every selected amenity to match.
 */
export function AmenityFilter({ amenities, selected, onChange }: Props) {
  if (amenities.length === 0) return null

  function toggle(id: number) {
    const current = selected ?? []
    const next = current.includes(id) ? current.filter((a) => a !== id) : [...current, id]
    onChange(next.length === 0 ? null : next)
  }

  return (
    <div>
      <label className="mb-1 block text-xs font-medium text-forest-600">Amenities</label>
      <div className="flex flex-wrap gap-1.5">
        {amenities.map((amenity) => {
          const active = selected?.includes(amenity.id) ?? false
          return (
            <button
              key={amenity.id}
              type="button"
              onClick={() => toggle(amenity.id)}
              className={`rounded-full px-2.5 py-0.5 text-xs font-medium transition-colors ${
                active
                  ? 'bg-forest-700 text-white hover:bg-forest-800'
                  : 'bg-forest-100 text-forest-400 hover:bg-forest-200 hover:text-forest-600'
              }`}
            >
              {amenity.name}
            </button>
          )
        })}
      </div>
    </div>
  )
}
