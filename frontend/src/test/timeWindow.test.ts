import { describe, it, expect } from 'vitest'
import { formatTimeOfDay, formatTimeWindow, timeOrUndefined, validateTimeWindow } from '../utils/timeWindow'

describe('timeWindow helpers', () => {
  describe('formatTimeOfDay', () => {
    it('formats morning, noon, afternoon, and midnight times in 12-hour form', () => {
      expect(formatTimeOfDay('09:05')).toBe('9:05 AM')
      expect(formatTimeOfDay('12:00')).toBe('12:00 PM')
      expect(formatTimeOfDay('13:30')).toBe('1:30 PM')
      expect(formatTimeOfDay('00:10')).toBe('12:10 AM')
      expect(formatTimeOfDay('23:50')).toBe('11:50 PM')
    })
  })

  describe('formatTimeWindow', () => {
    it('shows both bounds', () => {
      expect(formatTimeWindow('11:30', '14:00')).toBe('11:30 AM–2:00 PM')
    })

    it('words a single bound', () => {
      expect(formatTimeWindow('11:30', null)).toBe('from 11:30 AM')
      expect(formatTimeWindow(undefined, '14:00')).toBe('before 2:00 PM')
    })

    it('is null when neither bound is set', () => {
      expect(formatTimeWindow(null, null)).toBeNull()
      expect(formatTimeWindow('', '')).toBeNull()
    })
  })

  describe('validateTimeWindow', () => {
    it('accepts an ordered window, a single bound, and no bounds', () => {
      expect(validateTimeWindow('11:30', '14:00')).toBeNull()
      expect(validateTimeWindow('11:30', '')).toBeNull()
      expect(validateTimeWindow('', '14:00')).toBeNull()
      expect(validateTimeWindow('', '')).toBeNull()
    })

    it('rejects an inverted or equal window, matching the backend rule', () => {
      expect(validateTimeWindow('14:00', '11:30')).toMatch(/before/i)
      expect(validateTimeWindow('12:00', '12:00')).toMatch(/before/i)
    })
  })

  it('timeOrUndefined maps the form empty string to an omitted API field', () => {
    expect(timeOrUndefined('')).toBeUndefined()
    expect(timeOrUndefined('11:30')).toBe('11:30')
  })
})
