export const DEFAULT_TIMEZONE = 'America/Los_Angeles'

const FALLBACK_TIMEZONES = [
  'America/Los_Angeles',
  'America/Denver',
  'America/Chicago',
  'America/New_York',
  'America/Anchorage',
  'Pacific/Honolulu',
  'UTC'
]

export function getTimezoneOptions() {
  const intlWithValues = Intl as typeof Intl & { supportedValuesOf?: (key: 'timeZone') => string[] }
  const zones = intlWithValues.supportedValuesOf?.('timeZone') ?? FALLBACK_TIMEZONES
  const all = Array.from(new Set(zones.includes(DEFAULT_TIMEZONE) ? zones : [DEFAULT_TIMEZONE, ...zones]))
  const american = all.filter((z) => z.startsWith('America/')).sort()
  const rest = all.filter((z) => !z.startsWith('America/')).sort()
  return [...american, ...rest]
}

export function getBrowserTimezone() {
  const detected = Intl.DateTimeFormat().resolvedOptions().timeZone
  return detected && getTimezoneOptions().includes(detected) ? detected : DEFAULT_TIMEZONE
}
