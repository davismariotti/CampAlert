import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getCampgroundLoops } from '../../api/generated/sdk.gen'
import type { LoopInfo, Provider } from '../../api/generated/types.gen'

interface Props {
  campgroundId: number
  provider: Provider
  selectedLoops: string[] | null
  onChange: (loops: string[] | null) => void
  /** When non-null, only groupings in this list are shown/selectable — used by the equipment-type filter to narrow candidates without persisting a separate field (design.md decision 6). */
  allowedGroupings?: string[] | null
}

/** "Loops" for Recreation.gov's physical loop model, "Site Types" for CampLife's siteType groupings — the one deliberately cosmetic, provider-keyed exception called out in design.md decision 6. */
function groupingLabel(provider: Provider): string {
  return provider.type === 'CAMPLIFE' ? 'Site Types' : 'Loops'
}

export function LoopPicker({ campgroundId, provider, selectedLoops, onChange, allowedGroupings = null }: Props) {
  const {
    data: allLoops,
    isLoading,
    isError
  } = useQuery({
    queryKey: ['campground-loops', campgroundId, provider.type],
    queryFn: async () => {
      const result = await getCampgroundLoops({ path: { id: campgroundId }, query: { provider: provider.type } })
      if (result.error) throw result
      return result.data as LoopInfo[]
    },
    staleTime: 5 * 60 * 1000
  })

  const loops = allowedGroupings ? allLoops?.filter((l) => allowedGroupings.includes(l.name)) : allLoops
  const label = groupingLabel(provider)

  // When loops load and selectedLoops is null (no explicit selection yet),
  // default to all non-boat-in loops selected if any boat-in loops exist.
  useEffect(() => {
    if (!loops) return
    const hasBoatIn = loops.some((l) => l.boatInOnly)
    if (selectedLoops === null && hasBoatIn) {
      onChange(loops.filter((l) => !l.boatInOnly).map((l) => l.name))
    }
  }, [loops]) // eslint-disable-line react-hooks/exhaustive-deps

  if (isLoading) {
    return (
      <div>
        <label className="mb-1 block text-xs font-medium text-forest-600">{label}</label>
        <div className="flex flex-wrap gap-1.5">
          {[80, 56, 64, 72, 48].map((w) => (
            <div key={w} className="h-6 animate-pulse rounded-full bg-forest-100" style={{ width: w }} />
          ))}
        </div>
      </div>
    )
  }

  if (isError) {
    return <p className="text-xs text-forest-400">Couldn't load {label.toLowerCase()} — alert will watch all sites.</p>
  }

  if (!loops || loops.length === 0) return null

  const hasBoatIn = loops.some((l) => l.boatInOnly)
  const allSelected = selectedLoops === null
  // CampLife's availability endpoint only accepts a single siteTypeId at a time, unlike
  // Recreation.gov's multi-loop model — so the picker is single-select for CampLife.
  const singleSelect = provider.type === 'CAMPLIFE'

  function toggle(loop: LoopInfo) {
    if (singleSelect) {
      const isSelected = selectedLoops?.length === 1 && selectedLoops[0] === loop.name
      onChange(isSelected ? null : [loop.name])
      return
    }
    const currentNames = allSelected ? loops!.map((l) => l.name) : selectedLoops!
    const next = currentNames.includes(loop.name)
      ? currentNames.filter((n) => n !== loop.name)
      : [...currentNames, loop.name]
    // snap to null (all) only when there are no boat-in loops
    onChange(next.length === 0 && !hasBoatIn ? null : next)
  }

  const selectedCount = allSelected ? loops.length : loops.filter((l) => selectedLoops!.includes(l.name)).length
  const label2 = allSelected || selectedCount === loops.length ? 'all' : `${selectedCount} of ${loops.length}`

  return (
    <div>
      <label className="mb-1 block text-xs font-medium text-forest-600">
        {label} <span className="font-normal text-forest-400">({label2})</span>
      </label>
      <div className="flex flex-wrap gap-1.5">
        {loops.map((loop) => {
          const active = allSelected || selectedLoops!.includes(loop.name)
          return (
            <button
              key={loop.name}
              type="button"
              onClick={() => toggle(loop)}
              className={`rounded-full px-2.5 py-0.5 text-xs font-medium transition-colors ${
                active
                  ? 'bg-forest-700 text-white hover:bg-forest-800'
                  : 'bg-forest-100 text-forest-400 hover:bg-forest-200 hover:text-forest-600'
              }`}
            >
              {loop.boatInOnly ? `⛵ ${loop.name}` : loop.name}
            </button>
          )
        })}
      </div>
      {hasBoatIn && <p className="mt-1.5 text-xs text-forest-400">⛵ boat-in access only</p>}
    </div>
  )
}
