import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getCampground } from '../../api/generated/sdk.gen'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import type { CampgroundResponse, CampsiteResponse, Provider } from '../../api/generated/types.gen'

interface Props {
  campgroundId: number
  provider: Provider
  selectedSiteIds: string[] | null
  onConfirm: (siteIds: string[] | null) => void
  onClose: () => void
}

function pillClass(active: boolean): string {
  return `rounded-full px-2.5 py-0.5 text-xs font-medium transition-colors ${
    active
      ? 'bg-forest-700 text-white hover:bg-forest-800'
      : 'bg-forest-100 text-forest-400 hover:bg-forest-200 hover:text-forest-600'
  }`
}

export function CampsitePickerModal({ campgroundId, provider, selectedSiteIds, onConfirm, onClose }: Props) {
  const [groupingFilter, setGroupingFilter] = useState<string | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set(selectedSiteIds ?? []))

  const { data, isLoading, isError } = useQuery({
    queryKey: ['campground-catalog', campgroundId, provider.type],
    queryFn: async () => {
      const result = await getCampground({ path: { id: campgroundId }, query: { provider: provider.type } })
      if (result.error) throw result
      return result.data as CampgroundResponse
    },
    staleTime: 5 * 60 * 1000
  })

  const sites = useMemo(() => Object.entries(data?.campsites ?? {}) as [string, CampsiteResponse][], [data])

  const groupings = useMemo(
    () => Array.from(new Set(sites.map(([, s]) => s.loop).filter((l) => l.trim() !== ''))).sort(),
    [sites]
  )

  const filteredSites = sites.filter(([, s]) => groupingFilter === null || s.loop === groupingFilter)

  function toggleSite(siteId: string) {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(siteId)) next.delete(siteId)
      else next.add(siteId)
      return next
    })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div className="flex max-h-[85vh] w-full max-w-lg flex-col rounded-2xl bg-white p-6 shadow-md">
        <div className="mb-4 flex items-start justify-between gap-4">
          <h2 className="font-semibold text-forest-900">Choose specific campsites</h2>
          <button
            type="button"
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-lg text-forest-400 hover:bg-forest-100 hover:text-forest-700"
            aria-label="Close"
          >
            x
          </button>
        </div>

        {isLoading && (
          <div className="flex items-center justify-center py-10">
            <Spinner />
          </div>
        )}

        {isError && <p className="text-sm text-red-600">Couldn't load campsites. Please try again.</p>}

        {!isLoading && !isError && (
          <>
            {groupings.length > 0 && (
              <div className="mb-3">
                <label className="mb-1 block text-xs font-medium text-forest-600">Filter by grouping</label>
                <div className="flex flex-wrap gap-1.5">
                  <button
                    type="button"
                    onClick={() => setGroupingFilter(null)}
                    className={pillClass(groupingFilter === null)}
                  >
                    All
                  </button>
                  {groupings.map((g) => (
                    <button
                      key={g}
                      type="button"
                      onClick={() => setGroupingFilter(g === groupingFilter ? null : g)}
                      className={pillClass(groupingFilter === g)}
                    >
                      {g}
                    </button>
                  ))}
                </div>
              </div>
            )}

            <div className="mb-4 flex-1 overflow-y-auto rounded-xl border border-forest-100 p-3">
              {filteredSites.length === 0 ? (
                <p className="py-4 text-center text-sm text-forest-400">No sites match these filters</p>
              ) : (
                <div className="flex flex-wrap gap-1.5">
                  {filteredSites.map(([siteId, site]) => (
                    <button
                      key={siteId}
                      type="button"
                      onClick={() => toggleSite(siteId)}
                      className={pillClass(selected.has(siteId))}
                    >
                      {site.site}
                    </button>
                  ))}
                </div>
              )}
            </div>

            <p className="mb-4 text-xs text-forest-500">
              {selected.size === 0
                ? 'No specific sites selected'
                : `${selected.size} site${selected.size !== 1 ? 's' : ''} selected`}
            </p>
          </>
        )}

        <div className="flex justify-end gap-3">
          <Button variant="secondary" onClick={() => setSelected(new Set())}>
            Clear selection
          </Button>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={() => onConfirm(selected.size > 0 ? Array.from(selected) : null)}>Confirm</Button>
        </div>
      </div>
    </div>
  )
}
