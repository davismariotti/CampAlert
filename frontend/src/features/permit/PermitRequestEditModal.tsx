import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { updatePermitSearchRequest } from '../../api/generated/sdk.gen'
import { useApiMutation } from '../../hooks/useApiMutation'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { usePermit } from './usePermit'
import { ZoneDivisionPicker } from './ZoneDivisionPicker'
import { ZoneAvailabilityGrid } from './ZoneAvailabilityGrid'
import { ItineraryLegPicker } from './ItineraryLegPicker'
import type { PermitItineraryLegBody, PermitSearchRequestResponse } from '../../api/generated/types.gen'

function formatDate(dateStr: string) {
  return new Date(`${dateStr}T12:00:00`).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  })
}

interface Props {
  request: PermitSearchRequestResponse
  onClose: () => void
}

export function PermitRequestEditModal({ request, onClose }: Props) {
  const queryClient = useQueryClient()
  const { data: permitDetail } = usePermit(request.permitId)
  const isZone = request.searchType === 'ZONE'

  const [name, setName] = useState(request.name)
  const [groupSize, setGroupSize] = useState(request.groupSize)
  const [completed, setCompleted] = useState(request.completed)
  const [error, setError] = useState<string | null>(null)

  const [divisionIds, setDivisionIds] = useState<string[]>(request.zoneTarget?.divisionIds ?? [])
  const [night, setNight] = useState(request.zoneTarget?.startDay ?? '')

  const [legs, setLegs] = useState<PermitItineraryLegBody[]>(request.itineraryTarget?.legs ?? [])
  const [startDate, setStartDate] = useState(request.itineraryTarget?.legs[0]?.date ?? '')

  function divisionName(divisionId: string) {
    return permitDetail?.divisions.find((d) => d.id === divisionId)?.name ?? divisionId
  }

  function legConflict(leg: PermitItineraryLegBody): string | null {
    const division = permitDetail?.divisions.find((d) => d.id === leg.divisionId)
    if (division?.maxGroupSize != null && division.maxGroupSize < groupSize) {
      return `Group size (${groupSize}) exceeds this site's limit of ${division.maxGroupSize}`
    }
    return null
  }

  const hasConflict = !isZone && legs.some((leg) => legConflict(leg) != null)

  const mutation = useApiMutation({
    mutationFn: async () => {
      const result = await updatePermitSearchRequest({
        path: { id: request.id },
        body: {
          name,
          permitId: request.permitId,
          permitName: request.permitName,
          groupSize,
          searchType: request.searchType,
          // Zone quota is only consumed on the first night — startDay/endDay model a flexibility
          // window, but date-range flexibility isn't built yet, so both are set to the same night.
          zoneTarget: isZone ? { divisionIds, startDay: night, endDay: night } : undefined,
          itineraryTarget: !isZone ? { legs } : undefined,
          completed
        }
      })
      if (result.error) throw result
      return result.data!
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['permit-search-requests'] })
      onClose()
    },
    onError: () => setError('Failed to update. Please try again.')
  })

  const canSubmit = isZone
    ? name.trim() !== '' && groupSize >= 1 && divisionIds.length > 0 && night !== ''
    : name.trim() !== '' && groupSize >= 1 && legs.length > 0 && !hasConflict

  function removeLeg(index: number) {
    setLegs((prev) => prev.slice(0, index))
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-md">
        <h2 className="mb-4 font-semibold text-forest-900">Edit alert</h2>

        <div className="flex flex-col gap-4">
          <Input placeholder="Alert name" value={name} onChange={(e) => setName(e.target.value)} />

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

          {isZone && (
            <>
              <div>
                <label className="mb-1 block text-xs font-medium text-forest-600">Entry Night</label>
                <Input type="date" value={night} onChange={(e) => setNight(e.target.value)} />
              </div>
              <ZoneDivisionPicker
                permitId={request.permitId}
                selectedDivisionIds={divisionIds}
                onChange={setDivisionIds}
              />
              <ZoneAvailabilityGrid permitId={request.permitId} divisionIds={divisionIds} />
            </>
          )}

          {!isZone && permitDetail && (
            <>
              <div>
                <label className="mb-1 block text-xs font-medium text-forest-600">First night</label>
                <Input
                  type="date"
                  value={startDate}
                  disabled={legs.length > 0}
                  onChange={(e) => setStartDate(e.target.value)}
                />
              </div>

              <div>
                <label className="mb-1 block text-xs font-medium text-forest-600">
                  Itinerary{' '}
                  <span className="font-normal text-forest-400">
                    ({legs.length} {legs.length === 1 ? 'night' : 'nights'})
                  </span>
                </label>
                {legs.length > 0 && (
                  <ol className="mb-2 flex flex-col gap-1.5">
                    {legs.map((leg, i) => {
                      const conflict = legConflict(leg)
                      return (
                        <li
                          key={`${leg.divisionId}-${leg.date}`}
                          className={`flex items-center justify-between rounded-lg px-3 py-2 text-xs ${
                            conflict ? 'border border-red-200 bg-red-50' : 'bg-forest-50'
                          }`}
                        >
                          <div>
                            <span className="font-medium text-forest-800">
                              Night {i + 1}: {divisionName(leg.divisionId)}
                            </span>
                            <span className="ml-2 text-forest-500">{formatDate(leg.date)}</span>
                            {conflict && <p className="mt-0.5 text-red-600">{conflict}</p>}
                          </div>
                          <button
                            type="button"
                            onClick={() => removeLeg(i)}
                            className="ml-2 shrink-0 text-forest-400 hover:text-red-600"
                          >
                            Remove
                          </button>
                        </li>
                      )
                    })}
                  </ol>
                )}
                <ItineraryLegPicker
                  permit={permitDetail}
                  legs={legs}
                  groupSize={groupSize}
                  startDate={startDate}
                  onAddLeg={(leg) => setLegs((prev) => [...prev, leg])}
                />
              </div>
            </>
          )}

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
            <Button loading={mutation.isPending} disabled={!canSubmit} onClick={() => mutation.mutate()}>
              Save
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
