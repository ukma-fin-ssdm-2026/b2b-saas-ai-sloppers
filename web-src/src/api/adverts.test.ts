import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession, createSession, writeSession } from '../auth/session'
import {
  closeAdvert,
  createAdvert,
  deleteAdvert,
  getAdvert,
  listAdverts,
  listMyAdverts,
  listRepliedAdverts,
} from './adverts'
import type { Advert } from '../types/advert'

const ADVERT_ID = 'b1000000-0000-4000-8000-000000000001'

const advert: Advert = {
  id: ADVERT_ID,
  type: 'TEAM',
  title: 'Шукаю бекендера на хакатон',
  description: 'Бракує Java/Spring',
  slots: 2,
  deadline: '2026-11-20T18:00:00+02:00',
  status: 'ACTIVE',
  own: false,
  createdAt: '2026-11-16T12:00:00+02:00',
  contact: null,
  authorName: 'Теодора Гречко',
  myReplyStatus: null,
}

const page = {
  data: [advert],
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

function callOf(mock: ReturnType<typeof stubFetch>) {
  const [url, init] = mock.mock.calls[0] as [string, RequestInit]
  return { url, init }
}

beforeEach(() => {
  writeSession(createSession('student@ukma.edu.ua', 'student', 'jwt'))
})

afterEach(() => {
  vi.unstubAllGlobals()
  clearSession()
})

describe('listAdverts', () => {
  it('без параметрів — чистий шлях', async () => {
    const fetchMock = stubFetch()

    await expect(listAdverts()).resolves.toEqual(page)
    expect(callOf(fetchMock).url).toBe('/api/v1/adverts')
  })

  it('фільтр за типом і пагінація', async () => {
    const fetchMock = stubFetch()

    await listAdverts({ type: 'LEISURE', limit: 50, offset: 100 })

    const url = new URL(callOf(fetchMock).url, 'http://localhost')
    expect(url.searchParams.get('type')).toBe('LEISURE')
    expect(url.searchParams.get('limit')).toBe('50')
    expect(url.searchParams.get('offset')).toBe('100')
  })

  it('offset=0 не губиться як falsy', async () => {
    const fetchMock = stubFetch()

    await listAdverts({ offset: 0 })

    expect(callOf(fetchMock).url).toContain('offset=0')
  })
})

describe('свої та відгукнуті', () => {
  it('mine — окремий шлях', async () => {
    const fetchMock = stubFetch()

    await listMyAdverts({ limit: 10 })

    const url = new URL(callOf(fetchMock).url, 'http://localhost')
    expect(url.pathname).toBe('/api/v1/adverts/mine')
    expect(url.searchParams.get('limit')).toBe('10')
  })

  it('replied — окремий шлях', async () => {
    const fetchMock = stubFetch()

    await listRepliedAdverts()

    expect(callOf(fetchMock).url).toBe('/api/v1/adverts/replied')
  })
})

describe('одне оголошення', () => {
  it('getAdvert', async () => {
    const fetchMock = stubFetch({ json: async () => advert })

    await expect(getAdvert(ADVERT_ID)).resolves.toEqual(advert)
    expect(callOf(fetchMock).url).toBe(`/api/v1/adverts/${ADVERT_ID}`)
  })

  it('contact приходить null, поки відгук не підтверджено (FR-049)', async () => {
    stubFetch({ json: async () => advert })

    await expect(getAdvert(ADVERT_ID)).resolves.toMatchObject({
      contact: null,
    })
  })

  it('createAdvert надсилає POST з усіма полями', async () => {
    const fetchMock = stubFetch({ status: 201, json: async () => advert })

    await createAdvert({
      type: 'TEAM',
      title: 'Шукаю бекендера на хакатон',
      description: 'Бракує Java/Spring',
      slots: 2,
      deadline: '2026-11-20T18:00:00+02:00',
      contact: '@hackathon_seeker',
    })

    const { url, init } = callOf(fetchMock)
    expect(url).toBe('/api/v1/adverts')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({
      type: 'TEAM',
      title: 'Шукаю бекендера на хакатон',
      description: 'Бракує Java/Spring',
      slots: 2,
      deadline: '2026-11-20T18:00:00+02:00',
      contact: '@hackathon_seeker',
    })
  })

  it('дедлайн у минулому — 400 від бекенду', async () => {
    stubFetch({
      ok: false,
      status: 400,
      text: async () =>
        '{"error":{"code":"VALIDATION_ERROR","details":[{"field":"deadline"}]}}',
    })

    await expect(
      createAdvert({
        type: 'TEAM',
        title: 'Назва',
        description: 'Опис',
        slots: 1,
        deadline: '2020-01-01T00:00:00+02:00',
        contact: '@x',
      }),
    ).rejects.toMatchObject({ name: 'ApiError', status: 400 })
  })

  it('closeAdvert — POST на /close', async () => {
    const fetchMock = stubFetch({
      json: async () => ({ ...advert, status: 'CLOSED' }),
    })

    await expect(closeAdvert(ADVERT_ID)).resolves.toMatchObject({
      status: 'CLOSED',
    })
    expect(callOf(fetchMock).url).toBe(`/api/v1/adverts/${ADVERT_ID}/close`)
    expect(callOf(fetchMock).init.method).toBe('POST')
  })

  it('deleteAdvert — DELETE на 204', async () => {
    const fetchMock = stubFetch({ status: 204, headers: new Headers() })

    await expect(deleteAdvert(ADVERT_ID)).resolves.toBeUndefined()
    expect(callOf(fetchMock).init.method).toBe('DELETE')
  })
})
