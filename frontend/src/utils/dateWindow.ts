import type { ProviderType } from '../api/generated/types.gen'
import { ALL_PROVIDERS } from './providers'

export type DateMode = 'exact' | 'flexible'

// Mirrors the backend's campfinder.search.max-nights (all providers, exact or flexible).
export const MAX_NIGHTS = 21

// Mirrors the backend's campfinder.search.providers.<key>.max-range-width-days — latestStartDay -
// startDay, i.e. candidate count minus one, independent of nights. CampLife and ReserveCalifornia
// both fire one API call per candidate window (in parallel), hence the much tighter cap than
// Recreation.gov's month-cached, in-memory scan.
const MAX_RANGE_WIDTH_DAYS: Record<ProviderType, number> = {
  RECREATION_GOV: 30,
  CAMPLIFE: 9,
  RESERVE_CALIFORNIA: 9
}

export function maxRangeWidthDaysFor(providerType: ProviderType): number {
  return MAX_RANGE_WIDTH_DAYS[providerType]
}

function providerFriendlyName(providerType: ProviderType): string {
  return ALL_PROVIDERS.find((p) => p.type === providerType)?.name ?? providerType
}

function parseDateOnly(dateStr: string): Date {
  const [y, m, d] = dateStr.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1, d))
}

function formatDateOnly(date: Date): string {
  return date.toISOString().slice(0, 10)
}

export function addDays(dateStr: string, days: number): string {
  const d = parseDateOnly(dateStr)
  d.setUTCDate(d.getUTCDate() + days)
  return formatDateOnly(d)
}

export function daysBetween(startDay: string, endDay: string): number {
  return Math.round((parseDateOnly(endDay).getTime() - parseDateOnly(startDay).getTime()) / 86_400_000)
}

/** Short display form, e.g. "Jul 1" — matches RequestCard's date formatting. */
export function formatShortDate(dateStr: string): string {
  return new Date(`${dateStr}T12:00:00`).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

/**
 * Number of candidate arrival dates in a flexible range (every date from startDay through
 * latestStartDay inclusive), or null when the inputs can't produce one yet. Independent of nights.
 */
export function candidateArrivalCount(startDay: string, latestStartDay: string): number | null {
  if (!startDay || !latestStartDay) return null
  const count = daysBetween(startDay, latestStartDay) + 1
  return count > 0 ? count : null
}

export interface DateWindowInput {
  mode: DateMode
  startDay: string
  nights: number
  latestStartDay: string
  providerType: ProviderType
}

/** Returns the first validation error, or null when the window is valid — same rules the backend enforces. */
export function validateDateWindow({
  mode,
  startDay,
  nights,
  latestStartDay,
  providerType
}: DateWindowInput): string | null {
  if (!startDay) return 'Start date is required'
  if (nights < 1 || nights > MAX_NIGHTS) return `Nights must be between 1 and ${MAX_NIGHTS}`
  if (mode === 'exact') return null

  if (!latestStartDay) return 'Latest arrival date is required for a flexible search'
  if (latestStartDay < startDay) return `Latest arrival must be on or after ${formatShortDate(startDay)}`

  const maxWidth = maxRangeWidthDaysFor(providerType)
  const rangeWidth = daysBetween(startDay, latestStartDay)
  if (rangeWidth > maxWidth)
    return `Range can't be more than ${maxWidth} days for ${providerFriendlyName(providerType)}`

  return null
}

/** Plain-language summary shown below the date controls — arrival-only, so no derived checkout math is shown. */
export function dateWindowSummary({ mode, startDay, nights, latestStartDay }: DateWindowInput): string | null {
  if (!startDay || nights < 1) return null

  if (mode === 'exact') {
    return `Watching ${formatShortDate(startDay)} to ${formatShortDate(addDays(startDay, nights))}`
  }

  if (!latestStartDay || latestStartDay < startDay) return null
  return `Watching any ${nights}-night stay, arriving between ${formatShortDate(startDay)} and ${formatShortDate(latestStartDay)}`
}
