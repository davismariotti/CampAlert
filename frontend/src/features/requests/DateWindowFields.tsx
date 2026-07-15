import { Input } from '../../components/ui/Input'
import type { ProviderType } from '../../api/generated/types.gen'
import { candidateArrivalCount, dateWindowSummary, type DateMode } from '../../utils/dateWindow'

interface Props {
  mode: DateMode
  onModeChange: (mode: DateMode) => void
  startDay: string
  onStartDayChange: (value: string) => void
  searchEndDay: string
  onSearchEndDayChange: (value: string) => void
  nights: number
  providerType: ProviderType
  error?: string | null
}

/**
 * Shared exact/flexible date controls for the create and edit flows — one place for the date-mode
 * toggle, range inputs, computed summary, and candidate-count feedback so both forms agree on what's
 * valid and what the user sees (see design.md decisions 9/13).
 */
export function DateWindowFields({
  mode,
  onModeChange,
  startDay,
  onStartDayChange,
  searchEndDay,
  onSearchEndDayChange,
  nights,
  providerType,
  error
}: Props) {
  const summary = dateWindowSummary({ mode, startDay, nights, searchEndDay, providerType })
  const candidateCount = mode === 'flexible' ? candidateArrivalCount(startDay, nights, searchEndDay) : null

  return (
    <div className="flex flex-col gap-2">
      <div className="inline-flex w-fit rounded-lg border border-forest-200 p-0.5">
        {(['exact', 'flexible'] as const).map((m) => (
          <button
            key={m}
            type="button"
            onClick={() => onModeChange(m)}
            aria-pressed={mode === m}
            className={`rounded-md px-3 py-1 text-xs font-medium transition-colors ${
              mode === m ? 'bg-forest-700 text-white' : 'text-forest-600 hover:bg-forest-100'
            }`}
          >
            {m === 'exact' ? 'Exact dates' : 'Flexible window'}
          </button>
        ))}
      </div>

      {mode === 'exact' ? (
        <Input
          type="date"
          value={startDay}
          onChange={(e) => onStartDayChange(e.target.value)}
          aria-label="Arrival date"
        />
      ) : (
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-forest-600">Earliest arrival</label>
            <Input
              type="date"
              value={startDay}
              onChange={(e) => onStartDayChange(e.target.value)}
              aria-label="Earliest arrival"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-forest-600">Latest checkout</label>
            <Input
              type="date"
              value={searchEndDay}
              onChange={(e) => onSearchEndDayChange(e.target.value)}
              aria-label="Latest checkout"
            />
          </div>
        </div>
      )}

      {summary && !error && (
        <p className="text-xs text-forest-500">
          {summary}
          {candidateCount != null && ` — ${candidateCount} possible arrival date${candidateCount !== 1 ? 's' : ''}`}
        </p>
      )}
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  )
}
