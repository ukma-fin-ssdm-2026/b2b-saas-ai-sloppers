import { describe, expect, it } from 'vitest'
import { formatRating, ratingCountLabel } from './rating'

describe('ratingCountLabel', () => {
  it.each([
    [1, '1 оцінка'],
    [2, '2 оцінки'],
    [4, '4 оцінки'],
    [5, '5 оцінок'],
    [11, '11 оцінок'],
    [12, '12 оцінок'],
    [14, '14 оцінок'],
    [21, '21 оцінка'],
    [22, '22 оцінки'],
    [111, '111 оцінок'],
  ])('%i → %s', (count, label) => {
    expect(ratingCountLabel(count)).toBe(label)
  })
})

describe('formatRating', () => {
  it('показує середнє з одним знаком і кількість', () => {
    expect(formatRating({ average: 4, count: 3 })).toBe('★ 4.0 · 3 оцінки')
    expect(formatRating({ average: 4.5, count: 12 })).toBe('★ 4.5 · 12 оцінок')
  })

  it('без оцінок або без рейтингу у відповіді — «Ще немає оцінок»', () => {
    expect(formatRating({ average: null, count: 0 })).toBe('Ще немає оцінок')
    expect(formatRating(undefined)).toBe('Ще немає оцінок')
  })
})
