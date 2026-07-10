import { usePermit } from './usePermit'
import { firstParagraph } from '../../utils/html'

interface Props {
  permitId: string
  selectedDivisionIds: string[]
  onChange: (divisionIds: string[]) => void
}

export function ZoneDivisionPicker({ permitId, selectedDivisionIds, onChange }: Props) {
  const { data: permit, isLoading, isError } = usePermit(permitId)

  function toggle(divisionId: string) {
    const next = selectedDivisionIds.includes(divisionId)
      ? selectedDivisionIds.filter((id) => id !== divisionId)
      : [...selectedDivisionIds, divisionId]
    onChange(next)
  }

  if (isLoading) {
    return (
      <div>
        <label className="mb-1 block text-xs font-medium text-forest-600">Zones</label>
        <div className="flex flex-wrap gap-1.5">
          {[80, 56, 64, 72, 48].map((w) => (
            <div key={w} className="h-6 animate-pulse rounded-full bg-forest-100" style={{ width: w }} />
          ))}
        </div>
      </div>
    )
  }

  if (isError || !permit) {
    return <p className="text-xs text-red-600">Couldn't load this permit's zones. Try again.</p>
  }

  return (
    <div>
      <label className="mb-1 block text-xs font-medium text-forest-600">
        Zones <span className="font-normal text-forest-400">({selectedDivisionIds.length} selected)</span>
      </label>
      <div className="flex max-h-48 flex-col gap-1 overflow-y-auto rounded-xl border border-forest-100 p-2">
        {permit.divisions.map((division) => {
          const active = selectedDivisionIds.includes(division.id)
          const excerpt = firstParagraph(division.description)
          return (
            <button
              key={division.id}
              type="button"
              onClick={() => toggle(division.id)}
              className={`flex flex-col rounded-lg px-2.5 py-1.5 text-left text-xs font-medium transition-colors ${
                active ? 'bg-forest-700 text-white hover:bg-forest-800' : 'text-forest-600 hover:bg-forest-100'
              }`}
            >
              <span>{division.name}</span>
              {excerpt && (
                <span className={`mt-0.5 line-clamp-1 font-normal ${active ? 'text-white/70' : 'text-forest-400'}`}>
                  {excerpt}
                </span>
              )}
            </button>
          )
        })}
      </div>
    </div>
  )
}
