import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { DEMO_PASSWORD, DEMO_USER } from './credentials'
import { loginMock } from './mockAuthApi'
import {
  clearSession,
  createSession,
  readSession,
  writeSession,
  type AuthSession,
} from './session'

type AuthContextValue = {
  session: AuthSession | null
  isAuthenticated: boolean
  login: (
    username: string,
    password: string,
  ) => { ok: true } | { ok: false; error: string }
  logout: () => void
  setSessionFromOutside: (session: AuthSession | null) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(() => readSession())

  const setSessionFromOutside = useCallback((next: AuthSession | null) => {
    if (next) writeSession(next)
    else clearSession()
    setSession(next)
  }, [])

  const login = useCallback((username: string, password: string) => {
    const trimmed = username.trim()
    if (trimmed === DEMO_USER && password === DEMO_PASSWORD) {
      const next = createSession(trimmed, 'admin')
      writeSession(next)
      setSession(next)
      return { ok: true as const }
    }

    const mock = loginMock(trimmed, password)
    if (!mock.ok) {
      return { ok: false as const, error: mock.error }
    }
    const next = createSession(mock.data.email, mock.data.role)
    writeSession(next)
    setSession(next)
    return { ok: true as const }
  }, [])

  const logout = useCallback(() => {
    clearSession()
    setSession(null)
  }, [])

  const value = useMemo(
    () => ({
      session,
      isAuthenticated: session !== null,
      login,
      logout,
      setSessionFromOutside,
    }),
    [session, login, logout, setSessionFromOutside],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return ctx
}
