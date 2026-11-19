import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession, createSession, writeSession } from '../auth/session'
import {
  getOrganizationSubscription,
  subscribeToOrganization,
  unsubscribeFromOrganization,
} from './subscriptions'
import { ORG_ID } from '../test/support/testFixtures'

function stubFetch(response: Partial<Response> = {}) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    headers: new Headers({ 'Content-Type': 'application/json' }),
    json: async () => ({ subscribed: true }),
    text: async () => '',
    ...response,
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const PATH = `/api/v1/organizations/${ORG_ID}/subscription`

beforeEach(() => {
  writeSession(createSession('student@ukma.edu.ua', 'student', 'jwt'))
})

afterEach(() => {
  vi.unstubAllGlobals()
  clearSession()
})

describe('підписка на організацію', () => {
  it('статус читається GET-ом', async () => {
    const fetchMock = stubFetch()

    await expect(getOrganizationSubscription(ORG_ID)).resolves.toEqual({
      subscribed: true,
    })
    expect(fetchMock.mock.calls[0]?.[0]).toBe(PATH)
  })

  it('підписка — POST на той самий шлях', async () => {
    const fetchMock = stubFetch({ status: 204, headers: new Headers() })

    await expect(subscribeToOrganization(ORG_ID)).resolves.toBeUndefined()

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(PATH)
    expect(init.method).toBe('POST')
  })

  it('відписка — DELETE на той самий шлях', async () => {
    const fetchMock = stubFetch({ status: 204, headers: new Headers() })

    await expect(unsubscribeFromOrganization(ORG_ID)).resolves.toBeUndefined()

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(PATH)
    expect(init.method).toBe('DELETE')
  })

  it('гість без токена отримує ApiError 401', async () => {
    clearSession()
    stubFetch({ ok: false, status: 401, text: async () => 'unauthorized' })

    await expect(subscribeToOrganization(ORG_ID)).rejects.toMatchObject({
      name: 'ApiError',
      status: 401,
    })
  })
})
