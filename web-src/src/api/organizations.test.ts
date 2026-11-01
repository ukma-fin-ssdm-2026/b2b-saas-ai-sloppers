import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, listOrganizations } from './organizations'
import type { OffsetPageOrganization } from '../types/organization'

const emptyPage: OffsetPageOrganization = {
  data: [],
  pagination: { offset: 0, limit: 20, total: 0, hasMore: false },
}

function mockFetch(response: Partial<Response>) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => emptyPage,
    text: async () => '',
    ...response,
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('listOrganizations', () => {
  it('запитує каталог без параметрів, коли фільтрів немає', async () => {
    const fetchMock = mockFetch({})

    await listOrganizations()

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/organizations')
  })

  it('додає limit, offset і пошуковий запит у рядок запиту', async () => {
    const fetchMock = mockFetch({})

    await listOrganizations({ limit: 20, offset: 40, q: 'клуб' })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/organizations?offset=40&limit=20&q=%D0%BA%D0%BB%D1%83%D0%B1',
    )
  })

  it('не додає порожній пошуковий запит', async () => {
    const fetchMock = mockFetch({})

    await listOrganizations({ q: '' })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/organizations')
  })

  it('кидає ApiError зі статусом, коли бекенд відповідає помилкою', async () => {
    mockFetch({
      ok: false,
      status: 400,
      text: async () => 'limit має бути ≤ 100',
    })

    await expect(listOrganizations({ limit: 101 })).rejects.toMatchObject({
      name: 'ApiError',
      status: 400,
      message: 'limit має бути ≤ 100',
    })
  })

  it('підставляє загальне повідомлення, коли тіло помилки порожнє', async () => {
    mockFetch({ ok: false, status: 500, text: async () => '' })

    await expect(listOrganizations()).rejects.toThrow(
      'Request failed with status 500',
    )
  })

  it('повертає сторінку організацій, коли запит успішний', async () => {
    const page: OffsetPageOrganization = {
      data: [
        {
          id: '3f6c1f0e-8a2b-4c1d-9e7a-1b2c3d4e5f60',
          name: 'Дебатний клуб НаУКМА',
          slug: 'debate-club',
          createdAt: '2026-03-01T10:00:00+02:00',
          updatedAt: '2026-03-01T10:00:00+02:00',
        },
      ],
      pagination: { offset: 0, limit: 20, total: 1, hasMore: false },
    }
    mockFetch({ json: async () => page })

    await expect(listOrganizations()).resolves.toEqual(page)
  })
})

describe('ApiError', () => {
  it('зберігає статус відповіді та лишається екземпляром Error', () => {
    const error = new ApiError(404, 'not found')

    expect(error).toBeInstanceOf(Error)
    expect(error.status).toBe(404)
    expect(error.name).toBe('ApiError')
  })
})
