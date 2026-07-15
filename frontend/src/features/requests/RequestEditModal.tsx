import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { getCampground, updateSearchRequest } from '../../api/generated/sdk.gen'
import { useApiMutation } from '../../hooks/useApiMutation'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { LoopPicker } from './LoopPicker'
import { EquipmentTypeFilter } from './EquipmentTypeFilter'
import { AmenityFilter } from './AmenityFilter'
import { CampsitePickerModal } from './CampsitePickerModal'
import { DateWindowFields } from './DateWindowFields'
import type { CampgroundResponse, SearchRequestResponse } from '../../api/generated/types.gen'
import { validateDateWindow, type DateMode } from '../../utils/dateWindow'

interface Props {
  request: SearchRequestResponse
  onClose: () => void
}

export function RequestEditModal({ request, onClose }: Props) {
  const queryClient = useQueryClient()
  const [name, setName] = useState(request.name)
  const [dateMode, setDateMode] = useState<DateMode>(request.searchEndDay ? 'flexible' : 'exact')
  const [startDay, setStartDay] = useState(request.startDay)
  const [searchEndDay, setSearchEndDay] = useState(request.searchEndDay ?? '')
  const [nights, setNights] = useState(request.nights)
  const [groupSize, setGroupSize] = useState(request.groupSize)
  const [loops, setLoops] = useState<string[] | null>(request.loops ?? null)
  const [siteIds, setSiteIds] = useState<string[] | null>(request.siteIds ?? null)
  const [equipmentType, setEquipmentType] = useState<string | null>(null)
  const [amenityIds, setAmenityIds] = useState<number[] | null>(request.amenityIds ?? null)
  const [showCampsiteModal, setShowCampsiteModal] = useState(false)
  const [completed, setCompleted] = useState(request.completed)
  const [error, setError] = useState<string | null>(null)

  const dateWindowError = validateDateWindow({
    mode: dateMode,
    startDay,
    nights,
    searchEndDay,
    providerType: request.provider.type
  })

  // Only fetched for providers that might expose equipment-type/amenity data (CampLife).
  const { data: catalog } = useQuery({
    queryKey: ['campground-catalog', request.campsiteId, request.provider.type],
    queryFn: async () => {
      const result = await getCampground({
        path: { id: request.campsiteId },
        query: { provider: request.provider.type }
      })
      if (result.error) throw result
      return result.data as CampgroundResponse
    },
    enabled: request.provider.type === 'CAMPLIFE',
    staleTime: 5 * 60 * 1000
  })

  const equipmentTypes = catalog?.equipmentTypes ?? []
  const amenities = catalog?.amenities ?? []

  const allowedGroupings = useMemo(() => {
    if (!equipmentType || !catalog) return null
    const groupings = new Set(
      Object.values(catalog.campsites)
        .filter((s) => (s.equipmentTypes ?? []).includes(equipmentType))
        .map((s) => s.loop)
    )
    return Array.from(groupings)
  }, [equipmentType, catalog])

  const mutation = useApiMutation({
    mutationFn: async () => {
      const result = await updateSearchRequest({
        path: { id: request.id },
        body: {
          name,
          startDay,
          nights,
          groupSize,
          campsiteId: request.campsiteId,
          loops: loops ?? undefined,
          siteIds: siteIds ?? undefined,
          amenityIds: amenityIds ?? undefined,
          completed,
          searchEndDay: dateMode === 'flexible' ? searchEndDay : undefined
        }
      })
      if (result.error) throw result
      return result.data!
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['search-requests'] })
      onClose()
    },
    onError: () => setError('Failed to update. Please try again.')
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-md">
        <h2 className="mb-4 font-semibold text-forest-900">Edit alert</h2>

        <div className="flex flex-col gap-4">
          <Input placeholder="Alert name" value={name} onChange={(e) => setName(e.target.value)} />
          <DateWindowFields
            mode={dateMode}
            onModeChange={setDateMode}
            startDay={startDay}
            onStartDayChange={setStartDay}
            searchEndDay={searchEndDay}
            onSearchEndDayChange={setSearchEndDay}
            nights={nights}
            providerType={request.provider.type}
            error={dateWindowError}
          />

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="mb-1 block text-xs font-medium text-forest-600">Nights</label>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setNights((n) => Math.max(1, n - 1))}
                  className="flex h-8 w-8 items-center justify-center rounded-lg border border-forest-200 text-forest-600 hover:bg-forest-100"
                >
                  −
                </button>
                <span className="w-6 text-center text-sm font-medium">{nights}</span>
                <button
                  type="button"
                  onClick={() => setNights((n) => n + 1)}
                  className="flex h-8 w-8 items-center justify-center rounded-lg border border-forest-200 text-forest-600 hover:bg-forest-100"
                >
                  +
                </button>
              </div>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-forest-600">Group size</label>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setGroupSize((n) => Math.max(1, n - 1))}
                  className="flex h-8 w-8 items-center justify-center rounded-lg border border-forest-200 text-forest-600 hover:bg-forest-100"
                >
                  −
                </button>
                <span className="w-6 text-center text-sm font-medium">{groupSize}</span>
                <button
                  type="button"
                  onClick={() => setGroupSize((n) => n + 1)}
                  className="flex h-8 w-8 items-center justify-center rounded-lg border border-forest-200 text-forest-600 hover:bg-forest-100"
                >
                  +
                </button>
              </div>
            </div>
          </div>

          <EquipmentTypeFilter equipmentTypes={equipmentTypes} selected={equipmentType} onChange={setEquipmentType} />

          <AmenityFilter amenities={amenities} selected={amenityIds} onChange={setAmenityIds} />

          <LoopPicker
            campgroundId={request.campsiteId}
            provider={request.provider}
            selectedLoops={loops}
            onChange={setLoops}
            allowedGroupings={allowedGroupings}
          />

          <div>
            <div className="flex items-center justify-between">
              <label className="block text-xs font-medium text-forest-600">Specific campsites</label>
              <button
                type="button"
                onClick={() => setShowCampsiteModal(true)}
                className="text-xs font-medium text-forest-600 hover:text-forest-800"
              >
                Choose specific campsites
              </button>
            </div>
            {siteIds && siteIds.length > 0 ? (
              <div className="mt-1.5 flex flex-wrap gap-1.5">
                {siteIds.map((id) => (
                  <span
                    key={id}
                    className="flex items-center gap-1 rounded-full bg-forest-700 px-2.5 py-0.5 text-xs font-medium text-white"
                  >
                    {id}
                    <button
                      type="button"
                      onClick={() =>
                        setSiteIds((prev) => {
                          const next = (prev ?? []).filter((s) => s !== id)
                          return next.length === 0 ? null : next
                        })
                      }
                      aria-label={`Remove site ${id}`}
                      className="hover:text-forest-200"
                    >
                      ×
                    </button>
                  </span>
                ))}
              </div>
            ) : (
              <p className="mt-1 text-xs text-forest-400">No specific sites selected — watching by grouping above</p>
            )}
          </div>

          <label className="flex items-center gap-2 text-sm text-forest-700">
            <input
              type="checkbox"
              checked={completed}
              onChange={(e) => setCompleted(e.target.checked)}
              className="rounded border-forest-300"
            />
            Mark as completed
          </label>

          {error && <p className="text-sm text-red-600">{error}</p>}

          <div className="flex justify-end gap-3">
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button loading={mutation.isPending} disabled={!!dateWindowError} onClick={() => mutation.mutate()}>
              Save
            </Button>
          </div>
        </div>
      </div>

      {showCampsiteModal && (
        <CampsitePickerModal
          campgroundId={request.campsiteId}
          provider={request.provider}
          selectedSiteIds={siteIds}
          onConfirm={(ids) => {
            setSiteIds(ids)
            setShowCampsiteModal(false)
          }}
          onClose={() => setShowCampsiteModal(false)}
        />
      )}
    </div>
  )
}
