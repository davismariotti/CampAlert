/** "13:05" -> "1:05 PM". Inputs are the 24-hour `HH:mm` strings the API and `<input type="time">` both use. */
export function formatTimeOfDay(hhmm: string): string {
  const [hours, minutes] = hhmm.split(':').map(Number)
  const period = hours >= 12 ? 'PM' : 'AM'
  const hour12 = hours % 12 === 0 ? 12 : hours % 12
  return `${hour12}:${String(minutes).padStart(2, '0')} ${period}`
}

/**
 * Display form of a Global Entry time-of-day window, or null when neither bound is set — "11:30 AM–2:00 PM",
 * "from 11:30 AM" (no upper bound), "before 2:00 PM" (no lower bound). Times are local to the enrollment center.
 */
export function formatTimeWindow(earliest?: string | null, latest?: string | null): string | null {
  if (earliest && latest) return `${formatTimeOfDay(earliest)}–${formatTimeOfDay(latest)}`
  if (earliest) return `from ${formatTimeOfDay(earliest)}`
  if (latest) return `before ${formatTimeOfDay(latest)}`
  return null
}

/** Same rule the backend enforces: when both bounds are set, earliest must be strictly before latest. `''` means unset. */
export function validateTimeWindow(earliest: string, latest: string): string | null {
  if (earliest && latest && earliest >= latest) return 'Earliest time must be before latest time'
  return null
}

/** Maps a form's `''`-means-unset time field to the API's omitted/undefined form. */
export function timeOrUndefined(value: string): string | undefined {
  return value === '' ? undefined : value
}
