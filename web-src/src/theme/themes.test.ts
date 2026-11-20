import { afterEach, describe, expect, it } from 'vitest'
import {
  applyThemeToDocument,
  DEFAULT_THEME,
  isGrainientTheme,
  isThemeId,
  readSavedTheme,
  themeStorageKey,
  writeSavedTheme,
} from './themes'

describe('themes helpers', () => {
  afterEach(() => {
    document.documentElement.removeAttribute('data-theme')
    document.documentElement.style.colorScheme = ''
    localStorage.clear()
  })

  it('recognizes theme ids and grainient presets', () => {
    expect(isThemeId('black')).toBe(true)
    expect(isThemeId('grainient-mono')).toBe(true)
    expect(isThemeId('purple')).toBe(false)
    expect(isGrainientTheme('grainient')).toBe(true)
    expect(isGrainientTheme('grainient-teal')).toBe(true)
    expect(isGrainientTheme('grainient-mono')).toBe(true)
    expect(isGrainientTheme('white')).toBe(false)
    expect(DEFAULT_THEME).toBe('black')
  })

  it('applyThemeToDocument sets data-theme and color-scheme', () => {
    applyThemeToDocument('white')
    expect(document.documentElement.dataset.theme).toBe('white')
    expect(document.documentElement.style.colorScheme).toBe('light')

    applyThemeToDocument('grainient-mono')
    expect(document.documentElement.dataset.theme).toBe('grainient-mono')
    expect(document.documentElement.style.colorScheme).toBe('dark')

    applyThemeToDocument('black')
    expect(document.documentElement.style.colorScheme).toBe('dark')
  })

  it('persists theme per username in localStorage', () => {
    const key = themeStorageKey('Student@ukma.edu.ua')
    expect(key).toBe('community-theme:student@ukma.edu.ua')

    writeSavedTheme('Student@ukma.edu.ua', 'grainient-mono')
    expect(readSavedTheme('Student@ukma.edu.ua')).toBe('grainient-mono')
    expect(localStorage.getItem(key)).toBe('grainient-mono')

    expect(readSavedTheme('other@ukma.edu.ua')).toBeNull()
  })
})
