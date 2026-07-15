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
  })

  describe('candidateArrivalCount', () => {
    it('returns null when inputs are incomplete', () => {
      expect(candidateArrivalCount('', 2, '2026-07-08')).toBeNull()
      expect(candidateArrivalCount('2026-07-01', 2, '')).toBeNull()
    })

    it('computes the number of candidate arrival dates', () => {
      // startDay..searchEndDay-nights inclusive: Jul 1, 2, 3 -> 3 candidates
      expect(candidateArrivalCount('2026-07-01', 2, '2026-07-08')).toBe(6)
    })

    it('returns 1 for a single-window range (searchEndDay == startDay + nights)', () => {
      expect(candidateArrivalCount('2026-07-01', 2, '2026-07-03')).toBe(1)
    })

    it('returns null for an invalid (too-narrow) range', () => {
      expect(candidateArrivalCount('2026-07-01', 3, '2026-07-02')).toBeNull()
    })
  })

  describe('validateDateWindow', () => {
    it('requires a start date', () => {
      expect(
        validateDateWindow({ mode: 'exact', startDay: '', nights: 2, searchEndDay: '', providerType: 'RECREATION_GOV' })
      ).toMatch(/start date/i)
    })

    it(`rejects nights above ${MAX_NIGHTS}`, () => {
      expect(
        validateDateWindow({
          mode: 'exact',
          startDay: '2026-07-01',
          nights: MAX_NIGHTS + 1,
          searchEndDay: '',
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
          searchEndDay: '',
          providerType: 'RECREATION_GOV'
        })
      ).toBeNull()
    })

    it('flexible mode requires a searchEndDay', () => {
      expect(
        validateDateWindow({
          mode: 'flexible',
          startDay: '2026-07-01',
          nights: 2,
          searchEndDay: '',
          providerType: 'RECREATION_GOV'
        })
      ).toMatch(/checkout/i)
    })

    it('rejects a searchEndDay earlier than startDay + nights', () => {
      expect(
        validateDateWindow({
          mode: 'flexible',
          startDay: '2026-07-01',
          nights: 2,
          searchEndDay: '2026-07-02',
          providerType: 'RECREATION_GOV'
        })
      ).toMatch(/checkout/i)
    })

    it('accepts a searchEndDay exactly at startDay + nights', () => {
      expect(
        validateDateWindow({
          mode: 'flexible',
          startDay: '2026-07-01',
          nights: 2,
          searchEndDay: '2026-07-03',
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
          searchEndDay: '2026-08-15',
          providerType: 'RECREATION_GOV'
        })
      ).toMatch(/30 days/)
      expect(
        validateDateWindow({
          mode: 'flexible',
          startDay: '2026-07-01',
          nights: 2,
          searchEndDay: '2026-07-15',
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
          searchEndDay: '2026-07-30',
          providerType: 'RECREATION_GOV'
        })
      ).toBeNull()
      expect(
        validateDateWindow({
          mode: 'flexible',
          startDay: '2026-07-01',
          nights: 2,
          searchEndDay: '2026-07-10',
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
        searchEndDay: '',
        providerType: 'RECREATION_GOV'
      })
      expect(summary).toBe('Watching Jul 1 to Jul 3')
    })

    it('summarizes a flexible request', () => {
      const summary = dateWindowSummary({
        mode: 'flexible',
        startDay: '2026-07-01',
        nights: 2,
        searchEndDay: '2026-07-08',
        providerType: 'RECREATION_GOV'
      })
      expect(summary).toBe('Watching any 2-night stay between Jul 1 and Jul 8')
    })

    it('returns null for a flexible request with no searchEndDay yet', () => {
      expect(
        dateWindowSummary({
          mode: 'flexible',
          startDay: '2026-07-01',
          nights: 2,
          searchEndDay: '',
          providerType: 'RECREATION_GOV'
        })
      ).toBeNull()
    })
  })
})
