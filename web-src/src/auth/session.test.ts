import { describe, expect, it } from 'vitest'
import {
  clearSession,
  createSession,
  readSession,
  writeSession,
} from './session'

describe('session', () => {
  it('createSession зберігає username, role і JWT', () => {
    const session = createSession('a@ukma.edu.ua', 'admin', 'jwt-token')
    expect(session).toEqual({
      username: 'a@ukma.edu.ua',
      role: 'admin',
      token: 'jwt-token',
      kind: 'api',
    })
  })

  it('write/read round-trip', () => {
    writeSession(createSession('a@ukma.edu.ua', 'user', 'jwt-token'))
    expect(readSession()?.username).toBe('a@ukma.edu.ua')
    expect(readSession()?.role).toBe('user')
  })

  it('clearSession прибирає сесію', () => {
    writeSession(createSession('a@ukma.edu.ua', 'admin', 'jwt-token'))
    clearSession()
    expect(readSession()).toBeNull()
  })

  it('поламаний JSON у storage дає null', () => {
    sessionStorage.setItem('community-auth-session', '{not-json')
    expect(readSession()).toBeNull()
  })

  it('неповний обʼєкт сесії дає null', () => {
    sessionStorage.setItem(
      'community-auth-session',
      JSON.stringify({ username: 'x' }),
    )
    expect(readSession()).toBeNull()
  })

  it('legacy demo-сесію відкидає', () => {
    sessionStorage.setItem(
      'community-auth-session',
      JSON.stringify({
        username: 'bot',
        role: 'admin',
        token: 'demo-admin-token',
        kind: 'demo',
      }),
    )
    expect(readSession()).toBeNull()
  })
})
