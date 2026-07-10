import { useMemo, useState } from 'react'
import { firstParagraph, htmlToParagraphs } from '../../utils/html'
import type { PermitDivision } from '../../api/generated/types.gen'

interface Props {
  candidates: PermitDivision[]
  groupSize: number
  selectedDivisionId: string | null
  onSelect: (divisionId: string) => void
}

export function DivisionPicker({ candidates, groupSize, selectedDivisionId, onSelect }: Props) {
  const [search, setSearch] = useState('')
  const [district, setDistrict] = useState<string | null>(null)

  const districts = useMemo(() => {
    const set = new Set<string>()
    candidates.forEach((d) => {
      if (d.district) set.add(d.district)
    })
    return Array.from(set).sort()
  }, [candidates])

  const filtered = candidates.filter((d) => {
    if (district && d.district !== district) return false
    if (search.trim() && !d.name.toLowerCase().includes(search.trim().toLowerCase())) return false
    return true
  })

  const selectedDivision = candidates.find((d) => d.id === selectedDivisionId) ?? null

  return (
    <div>
      <input
        type="text"
        placeholder="Search divisions..."
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        className="mb-2 w-full rounded-lg border border-forest-200 px-3 py-1.5 text-xs text-forest-900 focus:border-forest-500 focus:outline-none"
      />

      {districts.length > 1 && (
        <div className="mb-2 flex flex-wrap gap-1">
          <button
            type="button"
            onClick={() => setDistrict(null)}
            className={`rounded-full px-2 py-0.5 text-xs font-medium ${
              district === null ? 'bg-forest-700 text-white' : 'bg-forest-100 text-forest-500 hover:bg-forest-200'
            }`}
          >
            All
          </button>
          {districts.map((d) => (
            <button
              key={d}
              type="button"
              onClick={() => setDistrict(d)}
              className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                district === d ? 'bg-forest-700 text-white' : 'bg-forest-100 text-forest-500 hover:bg-forest-200'
              }`}
            >
              {d}
            </button>
          ))}
        </div>
      )}

      <div className="flex max-h-48 flex-col gap-1 overflow-y-auto rounded-xl border border-forest-100 p-2">
        {filtered.length === 0 && <p className="px-2 py-1 text-xs text-forest-400">No divisions match.</p>}
        {filtered.map((division) => {
          const active = selectedDivisionId === division.id
          const disabled = division.maxGroupSize != null && division.maxGroupSize < groupSize
          const excerpt = firstParagraph(division.description)
          return (
            <button
              key={division.id}
              type="button"
              disabled={disabled}
              title={disabled ? `Max group size: ${division.maxGroupSize}` : undefined}
              onClick={() => onSelect(division.id)}
              className={`flex flex-col rounded-lg px-2.5 py-1.5 text-left text-xs font-medium transition-colors ${
                disabled
                  ? 'cursor-not-allowed opacity-40'
                  : active
                    ? 'bg-forest-700 text-white hover:bg-forest-800'
                    : 'text-forest-600 hover:bg-forest-100'
              }`}
            >
              <span>
                {division.name}
                {disabled && <span className="ml-1 font-normal">(max {division.maxGroupSize})</span>}
              </span>
              {division.district && (
                <span className={`mt-0.5 font-normal ${active ? 'text-white/70' : 'text-forest-400'}`}>
                  {division.district}
                </span>
              )}
              {excerpt && (
                <span className={`mt-0.5 line-clamp-1 font-normal ${active ? 'text-white/70' : 'text-forest-400'}`}>
                  {excerpt}
                </span>
              )}
            </button>
          )
        })}
      </div>

      {selectedDivision?.description && (
        <div className="mt-2 rounded-xl bg-forest-50 p-3 text-xs text-forest-700">
          <p className="mb-1 font-medium text-forest-800">{selectedDivision.name}</p>
          {htmlToParagraphs(selectedDivision.description).map((paragraph, i) => (
            <p key={i} className={i > 0 ? 'mt-1.5' : undefined}>
              {paragraph}
            </p>
          ))}
        </div>
      )}
    </div>
  )
}
