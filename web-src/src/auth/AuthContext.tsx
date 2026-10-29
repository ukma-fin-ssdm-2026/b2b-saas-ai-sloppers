import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { DEMO_PASSWORD, DEMO_USER } from './credentials'
import {
  clearSession,
  createDemoSession,
  readSession,
  writeSession,
  type AuthSession,
} from './session'

type AuthContextValue = {
  session: AuthSession | null
  isAuthenticated: boolean
  login: (username: string, password: string) => { ok: true } | { ok: false; error: string }
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(() => readSession())

  const login = useCallback((username: string, password: string) => {
    const userOk = username.trim() === DEMO_USER
    const passOk = password === DEMO_PASSWORD
    if (!userOk || !passOk) {
      return { ok: false as const, error: 'Невірний логін або пароль' }
    }
    const next = createDemoSession(username.trim())
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
    }),
    [session, login, logout],
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
