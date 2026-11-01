import { DEMO_TOKEN } from './credentials'

export type AuthRole = 'admin' | 'user'

/** demo = FE-only Lab gate; api = real JWT from backend */
export type AuthKind = 'demo' | 'api'

export interface AuthSession {
  username: string
  role: AuthRole
  token: string
  kind: AuthKind
}

const SESSION_KEY = 'community-auth-session'

function normalizeSession(raw: unknown): AuthSession | null {
  if (!raw || typeof raw !== 'object') return null
  const parsed = raw as Partial<AuthSession>
  if (!parsed.username || !parsed.token || !parsed.role) return null
  const kind: AuthKind =
    parsed.kind === 'api' || parsed.kind === 'demo'
      ? parsed.kind
      : parsed.token === DEMO_TOKEN
        ? 'demo'
        : 'api'
  return {
    username: parsed.username,
    role: parsed.role,
    token: parsed.token,
    kind,
  }
}

export function readSession(): AuthSession | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return null
    return normalizeSession(JSON.parse(raw) as unknown)
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

export function isDemoSession(session: AuthSession | null = readSession()): boolean {
  return session?.kind === 'demo' || session?.token === DEMO_TOKEN
}

export function createSession(
  username: string,
  role: AuthRole,
  token = DEMO_TOKEN,
  kind: AuthKind = token === DEMO_TOKEN ? 'demo' : 'api',
): AuthSession {
  return { username, role, token, kind }
}

/** @deprecated use createSession */
export function createDemoSession(username: string): AuthSession {
  return createSession(username, 'admin')
}
