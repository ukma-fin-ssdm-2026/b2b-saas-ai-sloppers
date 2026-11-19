import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession, createSession, writeSession } from '../auth/session'
import {
  approveEvent,
  createEvent,
  deleteEventImage,
  getEvent,
  listEvents,
  listOrganizationEvents,
  rejectEvent,
  unpublishEvent,
  updateEvent,
  uploadEventImage,
} from './events'
import { EVENT_ID, ORG_ID, event } from '../test/support/testFixtures'

const page = {
  data: [event()],
  pagination: { offset: 0, limit: 20, total: 1, hasMore: false },
}

function stubFetch(response: Partial<Response> = {}) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    headers: new Headers({ 'Content-Type': 'application/json' }),
    json: async () => page,
    text: async () => '',
    ...response,
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function urlOf(mock: ReturnType<typeof stubFetch>) {
  return mock.mock.calls[0]?.[0] as string
}

function initOf(mock: ReturnType<typeof stubFetch>) {
  return mock.mock.calls[0]?.[1] as RequestInit
}

beforeEach(() => {
  writeSession(createSession('admin@ukma.edu.ua', 'admin', 'jwt'))
})

afterEach(() => {
  vi.unstubAllGlobals()
  clearSession()
})

describe('listEvents', () => {
  it('без параметрів — чистий шлях', async () => {
    const fetchMock = stubFetch()

    await listEvents()

    expect(urlOf(fetchMock)).toBe('/api/v1/events')
  })

  it('складає всі фільтри дайджесту в рядок запиту', async () => {
    const fetchMock = stubFetch()

    await listEvents({
      organizationId: ORG_ID,
      from: '2026-10-21T00:00:00Z',
      to: '2026-10-28T00:00:00Z',
      offset: 20,
      limit: 10,
    })

    const url = new URL(urlOf(fetchMock), 'http://localhost')
    expect(url.searchParams.get('organizationId')).toBe(ORG_ID)
    expect(url.searchParams.get('from')).toBe('2026-10-21T00:00:00Z')
    expect(url.searchParams.get('to')).toBe('2026-10-28T00:00:00Z')
    expect(url.searchParams.get('offset')).toBe('20')
    expect(url.searchParams.get('limit')).toBe('10')
  })

  it('повертає сторінку як є', async () => {
    stubFetch()

    await expect(listEvents()).resolves.toEqual(page)
  })
})

describe('listOrganizationEvents', () => {
  it('черга модерації: status і verified потрапляють у запит', async () => {
    const fetchMock = stubFetch()

    await listOrganizationEvents(ORG_ID, {
      status: 'PENDING',
      verified: false,
      limit: 50,
    })

    const url = new URL(urlOf(fetchMock), 'http://localhost')
    expect(url.pathname).toBe(`/api/v1/organizations/${ORG_ID}/events`)
    expect(url.searchParams.get('status')).toBe('PENDING')
    expect(url.searchParams.get('verified')).toBe('false')
    expect(url.searchParams.get('limit')).toBe('50')
  })

  it('verified=false не губиться як falsy', async () => {
    const fetchMock = stubFetch()

    await listOrganizationEvents(ORG_ID, { verified: false })

    expect(urlOf(fetchMock)).toContain('verified=false')
  })
})

describe('одна подія', () => {
  it('getEvent', async () => {
    const fetchMock = stubFetch({ json: async () => event() })

    await expect(getEvent(EVENT_ID)).resolves.toMatchObject({ id: EVENT_ID })
    expect(urlOf(fetchMock)).toBe(`/api/v1/events/${EVENT_ID}`)
  })

  it('createEvent надсилає POST у кабінет організації', async () => {
    const fetchMock = stubFetch({ status: 201, json: async () => event() })

    await createEvent(ORG_ID, {
      title: 'Нова подія',
      startsAt: '2030-01-15T18:00:00+02:00',
    })

    expect(urlOf(fetchMock)).toBe(`/api/v1/organizations/${ORG_ID}/events`)
    expect(initOf(fetchMock).method).toBe('POST')
    expect(JSON.parse(initOf(fetchMock).body as string)).toEqual({
      title: 'Нова подія',
      startsAt: '2030-01-15T18:00:00+02:00',
    })
  })

  it('updateEvent надсилає PATCH', async () => {
    const fetchMock = stubFetch({ json: async () => event() })

    await updateEvent(EVENT_ID, { title: 'Інша назва' })

    expect(initOf(fetchMock).method).toBe('PATCH')
  })
})

describe('модерація', () => {
  it.each([
    ['approve', approveEvent],
    ['reject', rejectEvent],
    ['unpublish', unpublishEvent],
  ])('%s — POST без тіла', async (action, call) => {
    const fetchMock = stubFetch({ json: async () => event() })

    await call(EVENT_ID)

    expect(urlOf(fetchMock)).toBe(`/api/v1/events/${EVENT_ID}/${action}`)
    expect(initOf(fetchMock).method).toBe('POST')
    expect(initOf(fetchMock).body).toBeUndefined()
  })
})

describe('зображення події', () => {
  it('upload надсилає FormData і не ставить Content-Type вручну', async () => {
    const fetchMock = stubFetch({ json: async () => event() })
    const file = new File(['x'], 'logo.png', { type: 'image/png' })

    await uploadEventImage(EVENT_ID, file)

    const init = initOf(fetchMock)
    expect(urlOf(fetchMock)).toBe(`/api/v1/events/${EVENT_ID}/image`)
    expect(init.method).toBe('PUT')
    expect(init.body).toBeInstanceOf(FormData)
    // межу multipart ставить браузер, тож заголовок має лишитися незаданим
    expect(new Headers(init.headers).get('Content-Type')).toBeNull()
  })

  it('delete на 204 не парсить тіло', async () => {
    const fetchMock = stubFetch({ status: 204, headers: new Headers() })

    await expect(deleteEventImage(EVENT_ID)).resolves.toBeUndefined()
    expect(initOf(fetchMock).method).toBe('DELETE')
  })
})
