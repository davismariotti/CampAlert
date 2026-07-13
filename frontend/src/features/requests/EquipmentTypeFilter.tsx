interface Props {
  equipmentTypes: string[]
  selected: string | null
  onChange: (equipmentType: string | null) => void
}

/**
 * UI-only filter that narrows which groupings/sites are displayed for selection (e.g. hiding
 * "Lodging Rentals" when "Tent" is chosen) — rendered only when the campground's catalog exposes
 * equipment-type data (e.g. CampLife). Selecting a value here is never persisted as its own field on
 * the search request; its only effect is which grouping pills/sites the user sees (design.md decision 6).
 */
export function EquipmentTypeFilter({ equipmentTypes, selected, onChange }: Props) {
  if (equipmentTypes.length === 0) return null

  return (
    <div>
      <label className="mb-1 block text-xs font-medium text-forest-600">Equipment type</label>
      <div className="flex flex-wrap gap-1.5">
        <button
          type="button"
          onClick={() => onChange(null)}
          className={`rounded-full px-2.5 py-0.5 text-xs font-medium transition-colors ${
            selected === null
              ? 'bg-forest-700 text-white hover:bg-forest-800'
              : 'bg-forest-100 text-forest-400 hover:bg-forest-200 hover:text-forest-600'
          }`}
        >
          All
        </button>
        {equipmentTypes.map((type) => (
          <button
            key={type}
            type="button"
            onClick={() => onChange(type === selected ? null : type)}
            className={`rounded-full px-2.5 py-0.5 text-xs font-medium transition-colors ${
              selected === type
                ? 'bg-forest-700 text-white hover:bg-forest-800'
                : 'bg-forest-100 text-forest-400 hover:bg-forest-200 hover:text-forest-600'
            }`}
          >
            {type}
          </button>
        ))}
      </div>
    </div>
  )
}
