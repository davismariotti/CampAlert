import { Input } from '../../components/ui/Input'

interface Props {
  earliestTime: string
  onEarliestTimeChange: (value: string) => void
  latestTime: string
  onLatestTimeChange: (value: string) => void
  error?: string | null
}

/**
 * Optional time-of-day window for Global Entry requests — appointments must start at or after the
 * earliest time and before the latest. Both are local to the enrollment center and either may be left
 * blank (open-ended); `''` means unset.
 */
export function TimeWindowFields({ earliestTime, onEarliestTimeChange, latestTime, onLatestTimeChange, error }: Props) {
  return (
    <div className="flex flex-col gap-2">
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="mb-1 block text-xs font-medium text-forest-600">Earliest time (optional)</label>
          <Input
            type="time"
            step={600}
            value={earliestTime}
            onChange={(e) => onEarliestTimeChange(e.target.value)}
            aria-label="Earliest time"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-forest-600">Latest time (optional)</label>
          <Input
            type="time"
            step={600}
            value={latestTime}
            onChange={(e) => onLatestTimeChange(e.target.value)}
            aria-label="Latest time"
          />
        </div>
      </div>
      <p className="text-xs text-forest-400">
        Appointment start time, in the enrollment center&apos;s local time. Leave blank for any time.
      </p>
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  )
}
