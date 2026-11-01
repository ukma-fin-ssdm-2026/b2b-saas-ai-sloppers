import { describe, expect, it } from 'vitest'
import { DEMO_TOKEN } from './credentials'
import {
  clearSession,
  createSession,
  readSession,
  writeSession,
} from './session'

describe('session', () => {
  it('createSession зберігає username, role і demo-токен', () => {
    const session = createSession('bot', 'admin')
    expect(session).toEqual({
      username: 'bot',
      role: 'admin',
      token: DEMO_TOKEN,
    })
  })

  it('write/read round-trip', () => {
    writeSession(createSession('a@ukma.edu.ua', 'user'))
    expect(readSession()?.username).toBe('a@ukma.edu.ua')
    expect(readSession()?.role).toBe('user')
  })

  it('clearSession прибирає сесію', () => {
    writeSession(createSession('bot', 'admin'))
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
})
