import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession, createSession, writeSession } from '../auth/session'
import { appointHead, listHeads, removeHead, roleLog } from './heads'
import type { OrganizationHead, RoleChange } from '../types/head'
import { ORG_ID } from '../test/support/testFixtures'

const USER_ID = '9b1c7a40-0000-4000-8000-000000000001'

const head: OrganizationHead = {
  userId: USER_ID,
  email: 'head@ukma.edu.ua',
  appointedBy: null,
  appointedAt: '2026-10-21T13:00:00+03:00',
}

function stubFetch(response: Partial<Response> = {}) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    headers: new Headers({ 'Content-Type': 'application/json' }),
    json: async () => [head],
    text: async () => '',
    ...response,
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function requestOf(mock: ReturnType<typeof stubFetch>) {
  const [url, init] = mock.mock.calls[0] as [string, RequestInit]
  return { url, init }
}

beforeEach(() => {
  writeSession(createSession('admin@ukma.edu.ua', 'admin', 'jwt'))
})

afterEach(() => {
  vi.unstubAllGlobals()
  clearSession()
})

describe('listHeads', () => {
  it('GET на /heads організації', async () => {
    const fetchMock = stubFetch()

    await expect(listHeads(ORG_ID)).resolves.toEqual([head])
    expect(requestOf(fetchMock).url).toBe(
      `/api/v1/organizations/${ORG_ID}/heads`,
    )
  })
})

describe('appointHead', () => {
  it('POST з поштою в тілі', async () => {
    const fetchMock = stubFetch({ status: 201, json: async () => head })

    await expect(
      appointHead(ORG_ID, { email: 'head@ukma.edu.ua' }),
    ).resolves.toEqual(head)

    const { url, init } = requestOf(fetchMock)
    expect(url).toBe(`/api/v1/organizations/${ORG_ID}/heads`)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({
      email: 'head@ukma.edu.ua',
    })
  })

  it('передає replacesUserId, коли голову замінюють (FR-012)', async () => {
    const fetchMock = stubFetch({ status: 201, json: async () => head })

    await appointHead(ORG_ID, {
      email: 'new@ukma.edu.ua',
      replacesUserId: USER_ID,
    })

    expect(JSON.parse(requestOf(fetchMock).init.body as string)).toEqual({
      email: 'new@ukma.edu.ua',
      replacesUserId: USER_ID,
    })
  })
})

describe('removeHead', () => {
  it('DELETE з 204 не парсить тіло', async () => {
    const fetchMock = stubFetch({ status: 204, headers: new Headers() })

    await expect(removeHead(ORG_ID, USER_ID)).resolves.toBeUndefined()

    const { url, init } = requestOf(fetchMock)
    expect(url).toBe(`/api/v1/organizations/${ORG_ID}/heads/${USER_ID}`)
    expect(init.method).toBe('DELETE')
  })

  it('останнього голову зняти не можна: 409 піднімається як ApiError', async () => {
    stubFetch({
      ok: false,
      status: 409,
      text: async () => '{"error":{"code":"LAST_HEAD","message":"Не можна"}}',
    })

    await expect(removeHead(ORG_ID, USER_ID)).rejects.toMatchObject({
      name: 'ApiError',
      status: 409,
    })
  })
})

describe('roleLog', () => {
  it('GET на /role-log повертає журнал', async () => {
    const entry: RoleChange = {
      id: 'c0ffee00-0000-4000-8000-000000000001',
      organizationId: ORG_ID,
      targetUserId: USER_ID,
      targetEmail: 'head@ukma.edu.ua',
      actorUserId: null,
      actorEmail: 'admin@ukma.edu.ua',
      action: 'APPOINTED',
      createdAt: '2026-10-21T13:00:00+03:00',
    }
    const fetchMock = stubFetch({ json: async () => [entry] })

    await expect(roleLog(ORG_ID)).resolves.toEqual([entry])
    expect(requestOf(fetchMock).url).toBe(
      `/api/v1/organizations/${ORG_ID}/role-log`,
    )
  })
})
