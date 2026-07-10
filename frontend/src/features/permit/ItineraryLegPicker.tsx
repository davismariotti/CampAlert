import { useMemo, useState } from 'react'
import { DivisionPicker } from './DivisionPicker'
import { useItineraryDivisionAvailability } from './useItineraryDivisionAvailability'
import { Button } from '../../components/ui/Button'
import { Spinner } from '../../components/ui/Spinner'
import type { PermitItineraryLegBody, PermitResponse } from '../../api/generated/types.gen'

function addDays(date: string, days: number): string {
  const d = new Date(`${date}T12:00:00`)
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}

function formatDate(dateStr: string) {
  return new Date(`${dateStr}T12:00:00`).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  })
}

interface LegNightAvailabilityProps {
  permitId: string
  divisionId: string
  date: string
}

/** Every leg's date is fixed before a division is even chosen (see ItineraryLegPicker) — this is a
 * read-only quota check for that date, not a picker. */
function LegNightAvailability({ permitId, divisionId, date }: LegNightAvailabilityProps) {
  const [year, month] = date.split('-').map(Number)
  const { data, isLoading, isError } = useItineraryDivisionAvailability(permitId, divisionId, month, year)
  const cell = data?.dates[date]
  const remaining = cell?.remaining ?? 0

  return (
    <div className="flex items-center gap-2 rounded-lg bg-forest-50 px-3 py-2 text-xs">
      <span className="font-medium text-forest-600">{formatDate(date)}:</span>
      {isLoading && <Spinner size="sm" />}
      {isError && <span className="text-red-600">Couldn't check availability.</span>}
      {!isLoading && !isError && (
        <span className={remaining > 0 ? 'text-forest-700' : 'text-forest-400'}>
          {cell ? `${remaining} remaining` : 'No data for this date'}
        </span>
      )}
    </div>
  )
}

interface Props {
  permit: PermitResponse
  legs: PermitItineraryLegBody[]
  groupSize: number
  /** The itinerary's first night, chosen once by the caller — see ItineraryRequestBuilder. */
  startDate: string
  onAddLeg: (leg: PermitItineraryLegBody) => void
}

/**
 * Builds one leg at a time. Leg 1's candidates are every division on the permit; every later leg is
 * constrained to the previous leg's childDivisionIds, mirroring the server-side adjacency check.
 *
 * Nights are always consecutive calendar days, and the first night is chosen once, above this
 * component, not here — every leg's date is fully determined by the time a division is picked
 * (startDate for leg 1, previous leg's date + 1 day after that), so this only ever shows a read-only
 * quota check, never a date picker.
 */
export function ItineraryLegPicker({ permit, legs, groupSize, startDate, onAddLeg }: Props) {
  const [pendingDivisionId, setPendingDivisionId] = useState<string | null>(null)

  const nextDate = legs.length === 0 ? startDate : addDays(legs[legs.length - 1].date, 1)

  const candidates = useMemo(() => {
    if (legs.length === 0) return permit.divisions
    const previous = permit.divisions.find((d) => d.id === legs[legs.length - 1].divisionId)
    const childIds = previous?.childDivisionIds ?? []
    return permit.divisions.filter((d) => childIds.includes(d.id))
  }, [permit, legs])

  function handleAdd() {
    if (!pendingDivisionId || !nextDate) return
    onAddLeg({ divisionId: pendingDivisionId, date: nextDate })
    setPendingDivisionId(null)
  }

  if (legs.length === 0 && !startDate) {
    return <p className="text-xs text-forest-400">Pick a start date above to begin building your itinerary.</p>
  }

  if (candidates.length === 0) {
    return <p className="text-xs text-forest-400">This site has no legal next-night continuations.</p>
  }

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-forest-100 p-3">
      <DivisionPicker
        candidates={candidates}
        groupSize={groupSize}
        selectedDivisionId={pendingDivisionId}
        onSelect={setPendingDivisionId}
      />

      {pendingDivisionId && (
        <LegNightAvailability permitId={permit.id} divisionId={pendingDivisionId} date={nextDate} />
      )}

      <Button type="button" variant="secondary" disabled={!pendingDivisionId} onClick={handleAdd}>
        Add leg
      </Button>
    </div>
  )
}
