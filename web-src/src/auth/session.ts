export type AuthRole = 'admin' | 'user'

/** Real JWT session from backend auth. Legacy FE-only "demo" sessions are dropped. */
export type AuthKind = 'api'

export interface AuthSession {
  username: string
  role: AuthRole
  token: string
  kind: AuthKind
}

const SESSION_KEY = 'community-auth-session'
const LEGACY_DEMO_TOKEN = 'demo-admin-token'

function normalizeSession(raw: unknown): AuthSession | null {
  if (!raw || typeof raw !== 'object') return null
  const parsed = raw as {
    username?: string
    role?: AuthRole
    token?: string
    kind?: string
  }
  if (!parsed.username || !parsed.token || !parsed.role) return null
  // Drop old bot/demo Lab gate sessions — only real JWT is accepted.
  if (parsed.kind === 'demo' || parsed.token === LEGACY_DEMO_TOKEN) return null
  return {
    username: parsed.username,
    role: parsed.role,
    token: parsed.token,
    kind: 'api',
  }
}

export function readSession(): AuthSession | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const session = normalizeSession(JSON.parse(raw) as unknown)
    if (!session) {
      sessionStorage.removeItem(SESSION_KEY)
      return null
    }
    return session
  } catch {
    return null
  }
}

export function writeSession(session: AuthSession): void {
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
}

export function clearSession(): void {
  sessionStorage.removeItem(SESSION_KEY)
}

export function createSession(
  username: string,
  role: AuthRole,
  token: string,
): AuthSession {
  return { username, role, token, kind: 'api' }
}
