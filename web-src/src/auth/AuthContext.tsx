import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { ApiError, loginApi, meApi } from '../api/auth'
import { DEMO_PASSWORD, DEMO_USER } from './credentials'
import { loginMock } from './mockAuthApi'
import {
  clearSession,
  createSession,
  readSession,
  writeSession,
  type AuthSession,
} from './session'

type LoginResult = { ok: true } | { ok: false; error: string }

type AuthContextValue = {
  session: AuthSession | null
  isAuthenticated: boolean
  isDemo: boolean
  login: (username: string, password: string) => Promise<LoginResult>
  logout: () => void
  setSessionFromOutside: (session: AuthSession | null) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function messageFromApiError(err: unknown, fallback: string): string {
  if (!(err instanceof ApiError)) return fallback
  try {
    const parsed = JSON.parse(err.body) as {
      error?: { message?: string; code?: string }
    }
    if (parsed.error?.code === 'ACCOUNT_NOT_VERIFIED') {
      return 'Акаунт ще не підтверджено. Перейдіть на /verify.'
    }
    if (parsed.error?.message) return parsed.error.message
  } catch {
    // body is plain text
  }
  if (err.status === 401) return 'Невірна пошта або пароль'
  return err.message || fallback
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(() => readSession())

  const setSessionFromOutside = useCallback((next: AuthSession | null) => {
    if (next) writeSession(next)
    else clearSession()
    setSession(next)
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const trimmed = username.trim()

    if (trimmed === DEMO_USER && password === DEMO_PASSWORD) {
      const next = createSession(trimmed, 'admin', undefined, 'demo')
      writeSession(next)
      setSession(next)
      return { ok: true as const }
    }

    try {
      const tokens = await loginApi(trimmed, password)
      let role: AuthSession['role'] = 'user'
      try {
        const me = await meApi(tokens.accessToken)
        role = me.globalAdmin ? 'admin' : 'user'
      } catch {
        // Token is still usable even if /me fails transiently.
      }
      const next = createSession(
        trimmed.toLowerCase(),
        role,
        tokens.accessToken,
        'api',
      )
      writeSession(next)
      setSession(next)
      return { ok: true as const }
    } catch (apiErr) {
      const mock = loginMock(trimmed, password)
      if (mock.ok) {
        const next = createSession(mock.data.email, mock.data.role, undefined, 'demo')
        writeSession(next)
        setSession(next)
        return { ok: true as const }
      }

      return {
        ok: false as const,
        error: messageFromApiError(
          apiErr,
          mock.error || 'Не вдалося увійти',
        ),
      }
    }
  }, [])

  const logout = useCallback(() => {
    clearSession()
    setSession(null)
  }, [])

  const value = useMemo(
    () => ({
      session,
      isAuthenticated: session !== null,
      isDemo: session?.kind === 'demo',
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
