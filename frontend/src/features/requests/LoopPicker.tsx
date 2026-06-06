import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getCampgroundLoops } from '../../api/generated/sdk.gen'
import type { LoopInfo } from '../../api/generated/types.gen'

interface Props {
  campgroundId: number
  selectedLoops: string[] | null
  onChange: (loops: string[] | null) => void
}

export function LoopPicker({ campgroundId, selectedLoops, onChange }: Props) {
  const {
    data: loops,
    isLoading,
    isError
  } = useQuery({
    queryKey: ['campground-loops', campgroundId],
    queryFn: async () => {
      const result = await getCampgroundLoops({ path: { id: campgroundId } })
      if (result.error) throw result
      return result.data as LoopInfo[]
    },
    staleTime: 5 * 60 * 1000
  })

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
        <label className="mb-1 block text-xs font-medium text-forest-600">Loops</label>
        <div className="flex flex-wrap gap-1.5">
          {[80, 56, 64, 72, 48].map((w) => (
            <div key={w} className="h-6 animate-pulse rounded-full bg-forest-100" style={{ width: w }} />
          ))}
        </div>
      </div>
    )
  }

  if (isError) {
    return <p className="text-xs text-forest-400">Couldn't load loops — alert will watch all sites.</p>
  }

  if (!loops || loops.length === 0) return null

  const hasBoatIn = loops.some((l) => l.boatInOnly)
  const allSelected = selectedLoops === null

  function toggle(loop: LoopInfo) {
    const currentNames = allSelected ? loops!.map((l) => l.name) : selectedLoops!
    const next = currentNames.includes(loop.name)
      ? currentNames.filter((n) => n !== loop.name)
      : [...currentNames, loop.name]
    // snap to null (all) only when there are no boat-in loops
    onChange(next.length === 0 && !hasBoatIn ? null : next)
  }

  const selectedCount = allSelected ? loops.length : selectedLoops!.length
  const label = allSelected || selectedCount === loops.length ? 'all' : `${selectedCount} of ${loops.length}`

  return (
    <div>
      <label className="mb-1 block text-xs font-medium text-forest-600">
        Loops <span className="font-normal text-forest-400">({label})</span>
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
