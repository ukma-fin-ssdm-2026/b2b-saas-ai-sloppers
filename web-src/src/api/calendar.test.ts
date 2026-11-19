import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearSession } from '../auth/session'
import { eventIcsPath, getGoogleCalendarLink } from './calendar'
import { EVENT_ID } from '../test/support/testFixtures'

afterEach(() => {
  vi.unstubAllGlobals()
  clearSession()
})

describe('getGoogleCalendarLink', () => {
  it('GET повертає посилання на Google Calendar', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: async () => ({ url: 'https://calendar.google.com/render?x=1' }),
      text: async () => '',
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(getGoogleCalendarLink(EVENT_ID)).resolves.toEqual({
      url: 'https://calendar.google.com/render?x=1',
    })
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      `/api/v1/events/${EVENT_ID}/calendar/google`,
    )
  })

  it('404 піднімається як ApiError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
        headers: new Headers(),
        text: async () => 'not found',
      }),
    )

    await expect(getGoogleCalendarLink(EVENT_ID)).rejects.toMatchObject({
      name: 'ApiError',
      status: 404,
    })
  })
})

describe('eventIcsPath', () => {
  it('складає шлях до .ics без запиту на сервер', () => {
    expect(eventIcsPath(EVENT_ID)).toBe(
      `/api/v1/events/${EVENT_ID}/calendar.ics`,
    )
  })
})
