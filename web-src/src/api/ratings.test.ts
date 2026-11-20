import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  getOrganizationRating,
  rateAdvertAuthor,
  rateOrganization,
} from './ratings'

function mockFetch(status = 200, body: unknown = {}) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: status < 400,
    status,
    headers: new Headers({ 'Content-Type': 'application/json' }),
    json: async () => body,
    text: async () => JSON.stringify(body),
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function call(fetchMock: ReturnType<typeof vi.fn>) {
  const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
  return { url, method: init.method ?? 'GET', body: init.body as string }
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('API рейтингів', () => {
  it('рейтинг організації — GET …/rating', async () => {
    const fetchMock = mockFetch()
    await getOrganizationRating('org1')
    expect(call(fetchMock)).toMatchObject({
      url: '/api/v1/organizations/org1/rating',
      method: 'GET',
    })
  })

  it('оцінка організації — PUT з балом', async () => {
    const fetchMock = mockFetch()
    await rateOrganization('org1', 4)
    expect(call(fetchMock).method).toBe('PUT')
    expect(JSON.parse(call(fetchMock).body)).toEqual({ score: 4 })
  })

  it('оцінка автора — POST на рейтинг оголошення', async () => {
    const fetchMock = mockFetch(201)
    await rateAdvertAuthor('adv1', 5)
    expect(call(fetchMock)).toMatchObject({
      url: '/api/v1/adverts/adv1/rating',
      method: 'POST',
    })
    expect(JSON.parse(call(fetchMock).body)).toEqual({ score: 5 })
  })

  it('повторна оцінка автора — ApiError 409', async () => {
    mockFetch(409, {
      error: { code: 'ALREADY_RATED', message: 'Ви вже оцінили' },
    })
    await expect(rateAdvertAuthor('adv1', 5)).rejects.toMatchObject({
      status: 409,
    })
  })
})
