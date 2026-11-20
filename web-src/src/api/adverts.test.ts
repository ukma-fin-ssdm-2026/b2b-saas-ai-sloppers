import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  closeAdvert,
  createAdvert,
  deleteAdvert,
  getAdvert,
  listAdvertReplies,
  listAdverts,
  listMyAdverts,
  listRepliedAdverts,
  replyToAdvert,
  confirmAdvertReply,
  rejectAdvertReply,
  withdrawAdvertReply,
} from './adverts'
import type { Advert, OffsetPageAdvert } from '../types/advert'

const sampleAdvert: Advert = {
  id: 'b1000000-0000-4000-8000-000000000001',
  type: 'TEAM',
  title: 'Шукаю бекендера',
  description: 'Java',
  slots: 2,
  deadline: '2026-12-01T12:00:00+02:00',
  status: 'ACTIVE',
  contact: null,
  own: false,
  authorName: null,
  myReplyStatus: null,
  myReplyId: null,
  createdAt: '2026-11-01T12:00:00+02:00',
}

const emptyPage: OffsetPageAdvert = {
  data: [],
  pagination: { offset: 0, limit: 20, total: 0, hasMore: false },
}

function mockFetch(json: unknown = emptyPage) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    headers: new Headers({ 'Content-Type': 'application/json' }),
    json: async () => json,
    text: async () => '',
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('adverts API', () => {
  it('listAdverts builds query with type and paging', async () => {
    const fetchMock = mockFetch()
    await listAdverts({ type: 'LEISURE', offset: 20, limit: 10 })
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/v1/adverts?type=LEISURE&offset=20&limit=10',
    )
  })

  it('listMyAdverts and listRepliedAdverts hit scoped paths', async () => {
    const fetchMock = mockFetch()
    await listMyAdverts({ offset: 0, limit: 5 })
    await listRepliedAdverts()
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/adverts/mine?offset=0&limit=5')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/adverts/replied')
  })

  it('get/create/close/delete advert', async () => {
    const fetchMock = mockFetch(sampleAdvert)
    await getAdvert(sampleAdvert.id)
    await createAdvert({
      type: 'TEAM',
      title: 't',
      description: 'd',
      slots: 1,
      deadline: sampleAdvert.deadline,
      contact: '@x',
    })
    await closeAdvert(sampleAdvert.id)
    await deleteAdvert(sampleAdvert.id)

    expect(fetchMock.mock.calls[0]?.[0]).toBe(`/api/v1/adverts/${sampleAdvert.id}`)
    expect(fetchMock.mock.calls[1]?.[1]).toMatchObject({ method: 'POST' })
    expect(fetchMock.mock.calls[2]?.[0]).toContain('/close')
    expect(fetchMock.mock.calls[3]?.[1]).toMatchObject({ method: 'DELETE' })
  })

  it('reply lifecycle endpoints', async () => {
    const fetchMock = mockFetch({ id: 'r1' })
    await listAdvertReplies('a1')
    await replyToAdvert('a1', '  hi  ')
    await confirmAdvertReply('a1', 'r1')
    await rejectAdvertReply('a1', 'r1')
    await withdrawAdvertReply('a1', 'r1')

    expect(fetchMock.mock.calls.map((c) => String(c[0]))).toEqual([
      '/api/v1/adverts/a1/replies',
      '/api/v1/adverts/a1/replies',
      '/api/v1/adverts/a1/replies/r1/confirm',
      '/api/v1/adverts/a1/replies/r1/reject',
      '/api/v1/adverts/a1/replies/r1/withdraw',
    ])
    const replyBody = JSON.parse(
      String((fetchMock.mock.calls[1]?.[1] as RequestInit).body),
    )
    expect(replyBody).toEqual({ message: 'hi' })
  })
})
