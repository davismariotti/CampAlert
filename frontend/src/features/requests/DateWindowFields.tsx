import { Input } from '../../components/ui/Input'
import type { ProviderType } from '../../api/generated/types.gen'
import { candidateArrivalCount, dateWindowSummary, isGlobalEntry, type DateMode } from '../../utils/dateWindow'

interface Props {
  mode: DateMode
  onModeChange: (mode: DateMode) => void
  startDay: string
  onStartDayChange: (value: string) => void
  latestStartDay: string
  onLatestStartDayChange: (value: string) => void
  nights: number
  providerType: ProviderType
  error?: string | null
}

/**
 * Shared exact/flexible date controls for the create and edit flows — one place for the date-mode
 * toggle, range inputs, computed summary, and candidate-count feedback so both forms agree on what's
 * valid and what the user sees (see design.md decisions 9/13). Both flexible-mode bounds are arrival
 * dates — "earliest arrival" and "latest arrival" — so the user never has to compute a checkout date
 * themselves to express the range they mean.
 */
export function DateWindowFields({
  mode,
  onModeChange,
  startDay,
  onStartDayChange,
  latestStartDay,
  onLatestStartDayChange,
  nights,
  providerType,
  error
}: Props) {
  const summary = dateWindowSummary({ mode, startDay, nights, latestStartDay, providerType })
  const candidateCount = mode === 'flexible' ? candidateArrivalCount(startDay, latestStartDay) : null
  // Global Entry appointments are single days, not stays, so "arrival" wording becomes plain day wording.
  const globalEntry = isGlobalEntry(providerType)
  const exactLabel = globalEntry ? 'Date' : 'Arrival date'
  const earliestLabel = globalEntry ? 'Earliest day' : 'Earliest arrival'
  const latestLabel = globalEntry ? 'Latest day' : 'Latest arrival'
  const candidateNoun = globalEntry ? 'day' : 'arrival date'

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
          aria-label={exactLabel}
        />
      ) : (
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-forest-600">{earliestLabel}</label>
            <Input
              type="date"
              value={startDay}
              onChange={(e) => onStartDayChange(e.target.value)}
              aria-label={earliestLabel}
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-forest-600">{latestLabel}</label>
            <Input
              type="date"
              value={latestStartDay}
              onChange={(e) => onLatestStartDayChange(e.target.value)}
              aria-label={latestLabel}
            />
          </div>
        </div>
      )}

      {summary && !error && (
        <p className="text-xs text-forest-500">
          {summary}
          {candidateCount != null && ` — ${candidateCount} possible ${candidateNoun}${candidateCount !== 1 ? 's' : ''}`}
        </p>
      )}
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  )
}
