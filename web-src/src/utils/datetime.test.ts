import { describe, expect, it } from 'vitest'
import {
  dateInputToFromParam,
  dateInputToToParam,
  formatEventDateTime,
  toDatetimeLocalValue,
} from './datetime'

describe('datetime utils', () => {
  describe('dateInputToFromParam / dateInputToToParam', () => {
    it('from — початок UTC-дня', () => {
      expect(dateInputToFromParam('2026-10-21')).toBe('2026-10-21T00:00:00Z')
    })

    it('to — виключна верхня межа (наступна північ UTC)', () => {
      expect(dateInputToToParam('2026-10-21')).toBe('2026-10-22T00:00:00Z')
    })
  })

  describe('formatEventDateTime', () => {
    it('форматує валідний ISO; поламаний рядок повертає як є', () => {
      const formatted = formatEventDateTime('2026-10-21T18:00:00+03:00')
      expect(formatted.length).toBeGreaterThan(0)
      expect(formatEventDateTime('not-a-date')).toBe('not-a-date')
    })
  })

  describe('toDatetimeLocalValue', () => {
    it('повертає порожній рядок для невалідної дати', () => {
      expect(toDatetimeLocalValue('bad')).toBe('')
    })

    it('повертає YYYY-MM-DDTHH:mm для валідного ISO', () => {
      expect(toDatetimeLocalValue('2026-10-21T15:30:00.000Z')).toMatch(
        /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/,
      )
    })
  })
})
