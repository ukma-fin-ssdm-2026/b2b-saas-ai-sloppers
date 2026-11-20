import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { useAuth } from '../auth/AuthContext'
import {
  applyThemeToDocument,
  DEFAULT_THEME,
  readSavedTheme,
  writeSavedTheme,
  type ThemeId,
} from './themes'

type ThemeContextValue = {
  theme: ThemeId
  setTheme: (theme: ThemeId) => void
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

export function ThemeProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated, session } = useAuth()
  const [theme, setThemeState] = useState<ThemeId>(DEFAULT_THEME)

  useEffect(() => {
    if (isAuthenticated && session?.username) {
      setThemeState(readSavedTheme(session.username) ?? DEFAULT_THEME)
      return
    }
    setThemeState(DEFAULT_THEME)
  }, [isAuthenticated, session?.username])

  useEffect(() => {
    applyThemeToDocument(theme)
  }, [theme])

  const setTheme = useCallback(
    (next: ThemeId) => {
      setThemeState(next)
      if (isAuthenticated && session?.username) {
        writeSavedTheme(session.username, next)
      }
    },
    [isAuthenticated, session?.username],
  )

  const value = useMemo(() => ({ theme, setTheme }), [theme, setTheme])

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext)
  if (!ctx) {
    throw new Error('useTheme must be used within ThemeProvider')
  }
  return ctx
}
