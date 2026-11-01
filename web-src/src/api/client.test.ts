import { afterEach, describe, expect, it, vi } from 'vitest'
import { writeSession, createSession, clearSession } from '../auth/session'
import { apiFetch, ApiError } from './client'

describe('apiFetch', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    clearSession()
  })

  it('успішний JSON GET', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({ 'Content-Type': 'application/json' }),
        json: async () => ({ hello: 'world' }),
        text: async () => '',
      }),
    )

    const data = await apiFetch<{ hello: string }>('/ping', { skipAuth: true })
    expect(data.hello).toBe('world')
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(vi.mocked(fetch).mock.calls[0]?.[0]).toBe('/api/v1/ping')
  })

  it('підставляє Bearer з сесії', async () => {
    writeSession(createSession('bot', 'admin'))
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      headers: new Headers(),
      text: async () => '',
    })
    vi.stubGlobal('fetch', fetchMock)

    await apiFetch('/secure')
    const init = fetchMock.mock.calls[0]?.[1] as RequestInit
    const headers = new Headers(init.headers)
    expect(headers.get('Authorization')).toBe('Bearer demo-admin-token')
  })

  it('киє ApiError на non-OK', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
        headers: new Headers(),
        text: async () => 'missing',
      }),
    )

    await expect(apiFetch('/nope', { skipAuth: true })).rejects.toBeInstanceOf(
      ApiError,
    )
    await expect(apiFetch('/nope', { skipAuth: true })).rejects.toMatchObject({
      status: 404,
    })
  })

  it('на 401 очищає сесію і редіректить на login', async () => {
    writeSession(createSession('bot', 'admin'))
    const assign = vi.fn()
    vi.stubGlobal('location', {
      pathname: '/admin',
      search: '',
      assign,
    })
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        headers: new Headers(),
        text: async () => 'unauthorized',
      }),
    )

    await expect(apiFetch('/secure')).rejects.toBeInstanceOf(ApiError)
    expect(sessionStorage.getItem('community-auth-session')).toBeNull()
    expect(assign).toHaveBeenCalledWith(
      expect.stringContaining('/login?from='),
    )
  })
})
