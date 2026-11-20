import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  confirmPasswordResetApi,
  loginApi,
  meApi,
  registerApi,
  requestPasswordResetApi,
  resendCodeApi,
  updateProfileApi,
  verifyApi,
} from './auth'

function mockFetch(json: unknown = {}) {
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

describe('auth API', () => {
  it('login/register/verify/resend hit expected paths', async () => {
    const fetchMock = mockFetch({ accessToken: 't' })
    await loginApi('a@ukma.edu.ua', 'password123')
    await registerApi({
      email: 'a@ukma.edu.ua',
      password: 'password123',
      firstName: ' A ',
      lastName: '',
    })
    await verifyApi('a@ukma.edu.ua', '123456')
    await resendCodeApi('a@ukma.edu.ua')

    expect(fetchMock.mock.calls.map((c) => String(c[0]))).toEqual([
      '/api/v1/auth/login',
      '/api/v1/auth/register',
      '/api/v1/auth/verify',
      '/api/v1/auth/resend-code',
    ])
    const registerBody = JSON.parse(
      String((fetchMock.mock.calls[1]?.[1] as RequestInit).body),
    )
    expect(registerBody).toEqual({
      email: 'a@ukma.edu.ua',
      password: 'password123',
      firstName: 'A',
      lastName: null,
    })
  })

  it('password reset and profile endpoints', async () => {
    const fetchMock = mockFetch({ id: 'u1' })
    await requestPasswordResetApi('a@ukma.edu.ua')
    await confirmPasswordResetApi('a@ukma.edu.ua', '111111', 'password123')
    await meApi('tok')
    await updateProfileApi('Імʼя', 'Прізвище')

    expect(fetchMock.mock.calls.map((c) => String(c[0]))).toEqual([
      '/api/v1/auth/password-reset',
      '/api/v1/auth/password-reset/confirm',
      '/api/v1/auth/me',
      '/api/v1/auth/me',
    ])
    expect(fetchMock.mock.calls[2]?.[1]).toMatchObject({
      headers: expect.any(Headers),
    })
  })
})
