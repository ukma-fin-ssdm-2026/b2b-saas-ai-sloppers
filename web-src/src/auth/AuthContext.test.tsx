import { useState } from 'react'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider, useAuth } from './AuthContext'
import { clearSession } from './session'

function Probe() {
  const { isAuthenticated, login, logout, session } = useAuth()
  return (
    <div>
      <span data-testid="auth">{isAuthenticated ? 'yes' : 'no'}</span>
      <span data-testid="user">{session?.username ?? ''}</span>
      <span data-testid="role">{session?.role ?? ''}</span>
      <button
        type="button"
        onClick={() => {
          void login('admin@ukma.edu.ua', 'secret12')
        }}
      >
        jwt-login
      </button>
      <button
        type="button"
        onClick={() => {
          void login('nobody@ukma.edu.ua', 'x')
        }}
      >
        bad-login
      </button>
      <button type="button" onClick={logout}>
        logout
      </button>
    </div>
  )
}

describe('AuthContext', () => {
  afterEach(() => {
    cleanup()
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('JWT-логін створює сесію; logout очищає', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async (input: RequestInfo | URL) => {
        const url = String(input)
        if (url.includes('/auth/login')) {
          return {
            ok: true,
            status: 200,
            headers: new Headers({ 'Content-Type': 'application/json' }),
            json: async () => ({ accessToken: 'jwt-token' }),
            text: async () => '',
          }
        }
        if (url.includes('/auth/me')) {
          return {
            ok: true,
            status: 200,
            headers: new Headers({ 'Content-Type': 'application/json' }),
            json: async () => ({
              id: '1',
              email: 'admin@ukma.edu.ua',
              status: 'ACTIVE',
              globalAdmin: true,
              headOf: [],
            }),
            text: async () => '',
          }
        }
        return {
          ok: false,
          status: 404,
          headers: new Headers(),
          text: async () => 'missing',
        }
      }),
    )

    const user = userEvent.setup()
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    expect(screen.getByTestId('auth')).toHaveTextContent('no')
    await user.click(screen.getByRole('button', { name: 'jwt-login' }))
    await waitFor(() =>
      expect(screen.getByTestId('auth')).toHaveTextContent('yes'),
    )
    expect(screen.getByTestId('user')).toHaveTextContent('admin@ukma.edu.ua')
    expect(screen.getByTestId('role')).toHaveTextContent('admin')

    await user.click(screen.getByRole('button', { name: 'logout' }))
    expect(screen.getByTestId('auth')).toHaveTextContent('no')
  })

  it('невдалий логін не створює сесію', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        headers: new Headers(),
        text: async () => 'unauthorized',
      }),
    )

    const user = userEvent.setup()
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await user.click(screen.getByRole('button', { name: 'bad-login' }))
    await waitFor(() =>
      expect(screen.getByTestId('auth')).toHaveTextContent('no'),
    )
  })

  it('логін працює навіть якщо /me впав; роль лишається user', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async (input: RequestInfo | URL) => {
        const url = String(input)
        if (url.includes('/auth/login')) {
          return {
            ok: true,
            status: 200,
            headers: new Headers({ 'Content-Type': 'application/json' }),
            json: async () => ({ accessToken: 'jwt-token' }),
            text: async () => '',
          }
        }
        return {
          ok: false,
          status: 500,
          headers: new Headers(),
          text: async () => 'boom',
        }
      }),
    )

    const user = userEvent.setup()
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await user.click(screen.getByRole('button', { name: 'jwt-login' }))
    await waitFor(() =>
      expect(screen.getByTestId('auth')).toHaveTextContent('yes'),
    )
    expect(screen.getByTestId('role')).toHaveTextContent('user')
  })

  it('ACCOUNT_NOT_VERIFIED повертає зрозумілу помилку', async () => {
    function ErrorProbe() {
      const { login } = useAuth()
      const [error, setError] = useState('')
      return (
        <div>
          <span data-testid="error">{error}</span>
          <button
            type="button"
            onClick={() => {
              void login('x@ukma.edu.ua', 'secret12').then((r) => {
                if (!r.ok) setError(r.error)
              })
            }}
          >
            try-login
          </button>
        </div>
      )
    }

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 403,
        headers: new Headers(),
        text: async () =>
          JSON.stringify({
            error: { code: 'ACCOUNT_NOT_VERIFIED', message: 'verify first' },
          }),
      }),
    )

    const user = userEvent.setup()
    render(
      <AuthProvider>
        <ErrorProbe />
      </AuthProvider>,
    )
    await user.click(screen.getByRole('button', { name: 'try-login' }))
    await waitFor(() =>
      expect(screen.getByTestId('error')).toHaveTextContent(
        'Акаунт ще не підтверджено',
      ),
    )
  })

  it('setSessionFromOutside пише і чистить сесію', async () => {
    function SessionProbe() {
      const { session, setSessionFromOutside, isAuthenticated } = useAuth()
      return (
        <div>
          <span data-testid="auth">{isAuthenticated ? 'yes' : 'no'}</span>
          <span data-testid="user">{session?.username ?? ''}</span>
          <button
            type="button"
            onClick={() =>
              setSessionFromOutside({
                username: 'a@ukma.edu.ua',
                role: 'admin',
                token: 'jwt',
                kind: 'api',
              })
            }
          >
            set
          </button>
          <button type="button" onClick={() => setSessionFromOutside(null)}>
            clear
          </button>
        </div>
      )
    }

    const user = userEvent.setup()
    render(
      <AuthProvider>
        <SessionProbe />
      </AuthProvider>,
    )
    await user.click(screen.getByRole('button', { name: 'set' }))
    expect(screen.getByTestId('auth')).toHaveTextContent('yes')
    expect(screen.getByTestId('user')).toHaveTextContent('a@ukma.edu.ua')
    await user.click(screen.getByRole('button', { name: 'clear' }))
    expect(screen.getByTestId('auth')).toHaveTextContent('no')
  })

  it('useAuth поза провайдером кидає', () => {
    function Bare() {
      useAuth()
      return null
    }
    expect(() => render(<Bare />)).toThrow(
      'useAuth must be used within AuthProvider',
    )
  })
})
