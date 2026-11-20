import { describe, expect, it } from 'vitest'
import {
  compareOrgNames,
  sortOrganizationsByName,
  startsWithLatin,
} from './orgSort'

describe('startsWithLatin', () => {
  it('detects Latin vs Cyrillic starters', () => {
    expect(startsWithLatin('FIDo')).toBe(true)
    expect(startsWithLatin('  Radio KVIT')).toBe(true)
    expect(startsWithLatin('Бадді НаУКМА')).toBe(false)
    expect(startsWithLatin('КУ')).toBe(false)
  })
})

describe('compareOrgNames / sortOrganizationsByName', () => {
  it('puts English names before Ukrainian, each group A–Z', () => {
    const sorted = sortOrganizationsByName([
      { name: 'КУТ' },
      { name: 'Radio KVIT' },
      { name: 'FIDo' },
      { name: 'Бадді НаУКМА' },
      { name: 'KMA Gift Shop' },
      { name: 'o.poetry.kma' },
    ])

    expect(sorted.map((o) => o.name)).toEqual([
      'FIDo',
      'KMA Gift Shop',
      'o.poetry.kma',
      'Radio KVIT',
      'Бадді НаУКМА',
      'КУТ',
    ])
  })

  it('is stable for equal Latin/Cyrillic class via localeCompare', () => {
    expect(compareOrgNames({ name: 'Alpha' }, { name: 'Bravo' })).toBeLessThan(0)
    expect(compareOrgNames({ name: 'Яблуко' }, { name: 'Абрикос' })).toBeGreaterThan(
      0,
    )
  })
})
