import { describe, it, expect } from 'vitest'
import {
  MAX_NIGHTS,
  addDays,
  candidateArrivalCount,
  dateWindowSummary,
  daysBetween,
  maxRangeWidthDaysFor,
  validateDateWindow
} from '../utils/dateWindow'

describe('dateWindow helpers', () => {
  it('addDays advances calendar days, including across month boundaries', () => {
    expect(addDays('2026-07-30', 3)).toBe('2026-08-02')
  })

  it('daysBetween returns the calendar-day span', () => {
    expect(daysBetween('2026-07-01', '2026-07-08')).toBe(7)
  })

  it('maxRangeWidthDaysFor mirrors the backend caps', () => {
    expect(maxRangeWidthDaysFor('RECREATION_GOV')).toBe(30)
    expect(maxRangeWidthDaysFor('CAMPLIFE')).toBe(9)
    expect(maxRangeWidthDaysFor('RESERVE_CALIFORNIA')).toBe(9)
    expect(maxRangeWidthDaysFor('GLOBAL_ENTRY')).toBe(60)
  })

  describe('Global Entry', () => {
    const base = { mode: 'flexible' as const, nights: 1, providerType: 'GLOBAL_ENTRY' as const }

    it('allows a 60-day range and rejects 61 days', () => {
      expect(validateDateWindow({ ...base, startDay: '2026-07-01', latestStartDay: '2026-08-30' })).toBeNull()
      expect(validateDateWindow({ ...base, startDay: '2026-07-01', latestStartDay: '2026-08-31' })).toBe(
        "Range can't be more than 60 days for Global Entry"
      )
    })

    it('uses day wording instead of arrival wording', () => {
      expect(validateDateWindow({ ...base, startDay: '', latestStartDay: '' })).toBe('Date is required')
      expect(validateDateWindow({ ...base, startDay: '2026-07-01', latestStartDay: '' })).toBe(
        'Latest day is required for a flexible search'
      )
      expect(validateDateWindow({ ...base, startDay: '2026-07-10', latestStartDay: '2026-07-01' })).toBe(
        'Latest day must be on or after Jul 10'
      )
    })

    it('summarizes appointment days rather than stays', () => {
      expect(dateWindowSummary({ ...base, mode: 'exact', startDay: '2026-07-01', latestStartDay: '' })).toBe(
        'Watching Jul 1'
      )
      expect(dateWindowSummary({ ...base, startDay: '2026-07-01', latestStartDay: '2026-07-08' })).toBe(
        'Watching any day between Jul 1 and Jul 8'
      )
    })
  })

  describe('candidateArrivalCount', () => {
    it('returns null when inputs are incomplete', () => {
      expect(candidateArrivalCount('', '2026-07-08')).toBeNull()
      expect(candidateArrivalCount('2026-07-01', '')).toBeNull()
    })

    it('computes the number of candidate arrival dates, independent of nights', () => {
      // startDay..latestStartDay inclusive: Jul 1 through Jul 8 -> 8 candidates
      expect(candidateArrivalCount('2026-07-01', '2026-07-08')).toBe(8)
    })

    it('returns 1 for a single-candidate range (latestStartDay == startDay)', () => {
      expect(candidateArrivalCount('2026-07-01', '2026-07-01')).toBe(1)
    })

    it('returns null when latestStartDay is before startDay', () => {
      expect(candidateArrivalCount('2026-07-05', '2026-07-01')).toBeNull()
    })
  })

  describe('validateDateWindow', () => {
    it('requires a start date', () => {
      expect(
        validateDateWindow({
          mode: 'exact',
          startDay: '',
          nights: 2,
          latestStartDay: '',
          providerType: 'RECREATION_GOV'
        })
      ).toMatch(/start date/i)
    })

    it(`rejects nights above ${MAX_NIGHTS}`, () => {
      expect(
        validateDateWindow({
          mode: 'exact',
          startDay: '2026-07-01',
          nights: MAX_NIGHTS + 1,
          latestStartDay: '',
          providerType: 'RECREATION_GOV'
        })
      ).toMatch(new RegExp(String(MAX_NIGHTS)))
    })

    it('exact mode is valid with just a start date and valid nights', () => {
      expect(
        validateDateWindow({
          mode: 'exact',
          startDay: '2026-07-01',
          nights: 2,
          latestStartDay: '',
          providerType: 'RECREATION_GOV'
        })
      ).toBeNull()
    })

    it('flexible mode requires a latestStartDay', () => {
      expect(
        validateDateWindow({
          mode: 'flexible',
          startDay: '2026-07-01',
          nights: 2,
          latestStartDay: '',
          providerType: 'RECREATION_GOV'
        })
      ).toMatch(/arrival/i)
    })

    it('rejects a latestStartDay earlier than startDay', () => {
      expect(
        validateDateWindow({
          mode: 'flexible',
          startDay: '2026-07-05',
          nights: 2,
          latestStartDay: '2026-07-01',
          providerType: 'RECREATION_GOV'
        })
      ).toMatch(/arrival/i)
    })

    it('accepts a latestStartDay equal to startDay, regardless of nights', () => {
      expect(
        validateDateWindow({
          mode: 'flexible',
          startDay: '2026-07-01',
          nights: 5,
          latestStartDay: '2026-07-01',
          providerType: 'RECREATION_GOV'
        })
      ).toBeNull()
    })

    it('rejects a range wider than the provider max', () => {
      expect(
        validateDateWindow({
          mode: 'flexible',
          startDay: '2026-07-01',
          nights: 2,
          latestStartDay: '2026-08-15',
          providerType: 'RECREATION_GOV'
        })
      ).toMatch(/30 days/)
      expect(
        validateDateWindow({
          mode: 'flexible',
          startDay: '2026-07-01',
          nights: 2,
          latestStartDay: '2026-07-15',
          providerType: 'CAMPLIFE'
        })
      ).toMatch(/9 days/)
    })

    it('accepts a range within the provider max', () => {
      expect(
        validateDateWindow({
          mode: 'flexible',
          startDay: '2026-07-01',
          nights: 2,
          latestStartDay: '2026-07-31',
          providerType: 'RECREATION_GOV'
        })
      ).toBeNull()
      expect(
        validateDateWindow({
          mode: 'flexible',
          startDay: '2026-07-01',
          nights: 2,
          latestStartDay: '2026-07-10',
          providerType: 'CAMPLIFE'
        })
      ).toBeNull()
    })
  })

  describe('dateWindowSummary', () => {
    it('summarizes an exact-date request', () => {
      const summary = dateWindowSummary({
        mode: 'exact',
        startDay: '2026-07-01',
        nights: 2,
        latestStartDay: '',
        providerType: 'RECREATION_GOV'
      })
      expect(summary).toBe('Watching Jul 1 to Jul 3')
    })

    it('summarizes a flexible request using only arrival dates', () => {
      const summary = dateWindowSummary({
        mode: 'flexible',
        startDay: '2026-07-01',
        nights: 2,
        latestStartDay: '2026-07-08',
        providerType: 'RECREATION_GOV'
      })
      expect(summary).toBe('Watching any 2-night stay, arriving between Jul 1 and Jul 8')
    })

    it('returns null for a flexible request with no latestStartDay yet', () => {
      expect(
        dateWindowSummary({
          mode: 'flexible',
          startDay: '2026-07-01',
          nights: 2,
          latestStartDay: '',
          providerType: 'RECREATION_GOV'
        })
      ).toBeNull()
    })
  })
})
