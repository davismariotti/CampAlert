import { useState, useMemo } from 'react'
import { useZoneAvailability } from './useZoneAvailability'
import { usePermit } from './usePermit'
import { Spinner } from '../../components/ui/Spinner'

interface Props {
  permitId: string
  divisionIds: string[]
}

function firstOfMonth(offsetMonths: number): string {
  const d = new Date()
  d.setDate(1)
  d.setMonth(d.getMonth() + offsetMonths)
  return d.toISOString().slice(0, 10)
}

function daysInMonth(startDate: string): string[] {
  const [year, month] = startDate.split('-').map(Number)
  const count = new Date(year, month, 0).getDate()
  return Array.from({ length: count }, (_, i) => `${startDate.slice(0, 8)}${String(i + 1).padStart(2, '0')}`)
}

export function ZoneAvailabilityGrid({ permitId, divisionIds }: Props) {
  const [monthOffset, setMonthOffset] = useState(0)
  const startDate = useMemo(() => firstOfMonth(monthOffset), [monthOffset])
  const { data: permit } = usePermit(permitId)
  const { data, isLoading, isError } = useZoneAvailability(permitId, startDate)

  if (divisionIds.length === 0) {
    return <p className="text-xs text-forest-400">Select one or more zones to preview availability.</p>
  }

  const dates = daysInMonth(startDate)
  const monthLabel = new Date(`${startDate}T12:00:00`).toLocaleDateString('en-US', { month: 'long', year: 'numeric' })

  return (
    <div>
      <div className="mb-2 flex items-center justify-between">
        <label className="text-xs font-medium text-forest-600">Availability preview</label>
        <div className="flex items-center gap-2 text-xs">
          <button
            type="button"
            onClick={() => setMonthOffset((m) => m - 1)}
            className="rounded-lg px-1.5 py-0.5 text-forest-500 hover:bg-forest-100"
          >
            ‹
          </button>
          <span className="text-forest-700">{monthLabel}</span>
          <button
            type="button"
            onClick={() => setMonthOffset((m) => m + 1)}
            className="rounded-lg px-1.5 py-0.5 text-forest-500 hover:bg-forest-100"
          >
            ›
          </button>
        </div>
      </div>

      {isLoading && (
        <div className="flex items-center justify-center py-6">
          <Spinner size="sm" />
        </div>
      )}
      {isError && <p className="text-xs text-red-600">Couldn't load availability for this month.</p>}

      {!isLoading && !isError && (
        <div className="overflow-x-auto rounded-xl border border-forest-100">
          <table className="min-w-full border-collapse text-xs">
            <thead>
              <tr>
                <th className="sticky left-0 z-10 bg-white px-2 py-1.5 text-left font-medium text-forest-500">Zone</th>
                {dates.map((date) => (
                  <th key={date} className="px-1.5 py-1.5 text-center font-medium text-forest-400">
                    {Number(date.slice(8, 10))}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {divisionIds.map((divisionId) => {
                const name = permit?.divisions.find((d) => d.id === divisionId)?.name ?? divisionId
                const byDate = data?.divisions[divisionId] ?? {}
                return (
                  <tr key={divisionId} className="border-t border-forest-50">
                    <td className="sticky left-0 z-10 whitespace-nowrap bg-white px-2 py-1 font-medium text-forest-700">
                      {name}
                    </td>
                    {dates.map((date) => {
                      const cell = byDate[date]
                      const remaining = cell?.remaining ?? 0
                      return (
                        <td
                          key={date}
                          className={`px-1.5 py-1 text-center ${remaining > 0 ? 'text-forest-700' : 'text-forest-300'}`}
                        >
                          {cell ? remaining : '—'}
                        </td>
                      )
                    })}
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
