import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  addBannedWord,
  banUser,
  createComplaint,
  dismissComplaint,
  listComplaints,
  removeBannedWord,
  resolveComplaint,
  unbanUser,
} from './moderation'

const USER_ID = 'a1000000-0000-4000-8000-000000000001'
const COMPLAINT_ID = 'c1000000-0000-4000-8000-000000000001'

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

function lastCall(fetchMock: ReturnType<typeof vi.fn>) {
  const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
  return { url, method: init.method ?? 'GET', body: init.body as string }
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('API модерації', () => {
  it('скарга: POST /complaints з ціллю й категорією', async () => {
    const fetchMock = mockFetch(201)

    await createComplaint({
      targetType: 'ADVERT',
      targetId: USER_ID,
      category: 'SPAM',
    })

    const call = lastCall(fetchMock)
    expect(call.url).toBe('/api/v1/complaints')
    expect(call.method).toBe('POST')
    expect(JSON.parse(call.body)).toEqual({
      targetType: 'ADVERT',
      targetId: USER_ID,
      category: 'SPAM',
    })
  })

  it('черга: фільтр за станом і пагінація в рядку запиту', async () => {
    const fetchMock = mockFetch(200, {
      data: [],
      pagination: { offset: 0, limit: 50, total: 0, hasMore: false },
    })

    await listComplaints({ status: 'OPEN', limit: 50, offset: 0 })

    expect(lastCall(fetchMock).url).toBe(
      '/api/v1/moderation/complaints?status=OPEN&limit=50&offset=0',
    )
  })

  it('черга без фільтрів — без рядка запиту', async () => {
    const fetchMock = mockFetch(200, {
      data: [],
      pagination: { offset: 0, limit: 20, total: 0, hasMore: false },
    })

    await listComplaints()

    expect(lastCall(fetchMock).url).toBe('/api/v1/moderation/complaints')
  })

  it('рішення: порожній коментар не надсилається, заповнений — обрізаний', async () => {
    const fetchMock = mockFetch()
    await resolveComplaint(COMPLAINT_ID, '   ')
    expect(lastCall(fetchMock).url).toBe(
      `/api/v1/moderation/complaints/${COMPLAINT_ID}/resolve`,
    )
    expect(JSON.parse(lastCall(fetchMock).body)).toEqual({})

    const second = mockFetch()
    await dismissComplaint(COMPLAINT_ID, '  Порушення немає ')
    expect(lastCall(second).url).toBe(
      `/api/v1/moderation/complaints/${COMPLAINT_ID}/dismiss`,
    )
    expect(JSON.parse(lastCall(second).body)).toEqual({
      resolution: 'Порушення немає',
    })
  })

  it('бан і розблокування користувача — POST і DELETE на /ban', async () => {
    const fetchMock = mockFetch()
    await banUser(USER_ID, { reason: 'Спам', expiresAt: null })
    expect(lastCall(fetchMock).url).toBe(
      `/api/v1/moderation/users/${USER_ID}/ban`,
    )
    expect(lastCall(fetchMock).method).toBe('POST')
    expect(JSON.parse(lastCall(fetchMock).body)).toEqual({
      reason: 'Спам',
      expiresAt: null,
    })

    const second = mockFetch()
    await unbanUser(USER_ID)
    expect(lastCall(second).method).toBe('DELETE')
  })

  it('слово автофільтра: обрізається перед надсиланням, видалення — DELETE', async () => {
    const fetchMock = mockFetch(201)
    await addBannedWord('  казино ')
    expect(JSON.parse(lastCall(fetchMock).body)).toEqual({ word: 'казино' })

    const second = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      headers: new Headers(),
      text: async () => '',
    })
    vi.stubGlobal('fetch', second)
    await expect(removeBannedWord('w1')).resolves.toBeUndefined()
    expect(lastCall(second).url).toBe('/api/v1/moderation/banned-words/w1')
  })

  it('помилка бекенду стає ApiError зі статусом', async () => {
    mockFetch(409, {
      error: { code: 'ALREADY_BANNED', message: 'Користувач уже заблокований' },
    })

    await expect(banUser(USER_ID, { reason: 'x' })).rejects.toMatchObject({
      status: 409,
    })
  })
})
