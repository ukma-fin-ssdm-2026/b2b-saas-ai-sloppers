import { DEMO_TOKEN } from './credentials'

export type AuthRole = 'admin' | 'user'

export interface AuthSession {
  username: string
  role: AuthRole
  token: string
}

const SESSION_KEY = 'community-auth-session'

export function readSession(): AuthSession | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as AuthSession
    if (!parsed?.username || !parsed?.token || !parsed?.role) return null
    return parsed
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
  token = DEMO_TOKEN,
): AuthSession {
  return { username, role, token }
}

/** @deprecated use createSession */
export function createDemoSession(username: string): AuthSession {
  return createSession(username, 'admin')
}
