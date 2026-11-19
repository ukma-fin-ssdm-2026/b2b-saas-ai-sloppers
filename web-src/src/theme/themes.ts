import type { GrainientProps } from '../components/Grainient'

export type ThemeId =
  | 'black'
  | 'white'
  | 'grainient'
  | 'grainient-teal'
  | 'grainient-mono'

export const THEME_IDS: ThemeId[] = [
  'black',
  'white',
  'grainient',
  'grainient-teal',
  'grainient-mono',
]

export const DEFAULT_THEME: ThemeId = 'black'

export const THEME_LABELS: Record<ThemeId, string> = {
  black: 'Чорна',
  white: 'Біла',
  grainient: 'Grainient — бурштин',
  'grainient-teal': 'Grainient — бірюза',
  'grainient-mono': 'Grainient — срібло',
}

export type GrainientPreset = Omit<
  GrainientProps,
  'className' | 'style' | 'children'
>

export const GRAINIENT_PRESETS: Record<
  Exclude<ThemeId, 'black' | 'white'>,
  GrainientPreset
> = {
  grainient: {
    colors: ['#120f17', '#2a0f0a', '#9a3412', '#f97316', '#fed7aa'],
    angle: 52,
    flow: 0.8,
    silk: 0.35,
    grainAmount: 0.08,
    scale: 1.05,
    speed: 1.05,
  },
  'grainient-teal': {
    colors: ['#071318', '#0e7490', '#2dd4bf', '#ccfbf1'],
    angle: 20,
    flow: 0.9,
    silk: 0.35,
    grainAmount: 0.08,
  },
  'grainient-mono': {
    colors: ['#0c0c0f', '#3f3f46', '#a1a1aa', '#fafafa'],
    angle: 40,
    flow: 0.8,
    silk: 1,
    grainAmount: 0.06,
    bands: 2,
    scale: 2.95,
    speed: 0,
    grainSize: 0.5,
    contrast: 1.06,
    saturation: 0.49,
    stir: 0.5,
  },
}

export function isGrainientTheme(
  theme: ThemeId,
): theme is Exclude<ThemeId, 'black' | 'white'> {
  return theme in GRAINIENT_PRESETS
}

export function isThemeId(value: unknown): value is ThemeId {
  return (
    typeof value === 'string' && (THEME_IDS as string[]).includes(value)
  )
}

const THEME_STORAGE_PREFIX = 'community-theme:'

export function themeStorageKey(username: string): string {
  return `${THEME_STORAGE_PREFIX}${username.trim().toLowerCase()}`
}

export function readSavedTheme(username: string): ThemeId | null {
  try {
    const raw = localStorage.getItem(themeStorageKey(username))
    return isThemeId(raw) ? raw : null
  } catch {
    return null
  }
}

export function writeSavedTheme(username: string, theme: ThemeId): void {
  try {
    localStorage.setItem(themeStorageKey(username), theme)
  } catch {
    // ignore quota / private mode
  }
}

export function applyThemeToDocument(theme: ThemeId): void {
  const root = document.documentElement
  root.dataset.theme = theme
  root.style.colorScheme =
    theme === 'white' || theme === 'grainient-mono' ? 'light' : 'dark'
}
