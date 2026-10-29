import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import { DEMO_PASSWORD, DEMO_USER } from './credentials'
import { AuthProvider, useAuth } from './AuthContext'
import { registerMock, verifyMock } from './mockAuthApi'

function Probe() {
  const { isAuthenticated, login, logout, session } = useAuth()
  return (
    <div>
      <span data-testid="auth">{isAuthenticated ? 'yes' : 'no'}</span>
      <span data-testid="user">{session?.username ?? ''}</span>
      <button
        type="button"
        onClick={() => {
          login(DEMO_USER, DEMO_PASSWORD)
        }}
      >
        demo-login
      </button>
      <button
        type="button"
        onClick={() => {
          login('nobody@ukma.edu.ua', 'x')
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
  })

  it('demo-логін створює admin-сесію; logout очищає', async () => {
    const user = userEvent.setup()
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    expect(screen.getByTestId('auth')).toHaveTextContent('no')
    await user.click(screen.getByRole('button', { name: 'demo-login' }))
    expect(screen.getByTestId('auth')).toHaveTextContent('yes')
    expect(screen.getByTestId('user')).toHaveTextContent(DEMO_USER)

    await user.click(screen.getByRole('button', { name: 'logout' }))
    expect(screen.getByTestId('auth')).toHaveTextContent('no')
  })

  it('логін зареєстрованого користувача після verify', async () => {
    const reg = registerMock('ctx@ukma.edu.ua', 'secret12')
    expect(reg.ok).toBe(true)
    if (!reg.ok) return
    expect(verifyMock('ctx@ukma.edu.ua', reg.data.demoCode).ok).toBe(true)

    function UserLoginProbe() {
      const { isAuthenticated, login, session } = useAuth()
      return (
        <div>
          <span data-testid="auth">{isAuthenticated ? 'yes' : 'no'}</span>
          <span data-testid="role">{session?.role ?? ''}</span>
          <button
            type="button"
            onClick={() => {
              login('ctx@ukma.edu.ua', 'secret12')
            }}
          >
            user-login
          </button>
        </div>
      )
    }

    const user = userEvent.setup()
    render(
      <AuthProvider>
        <UserLoginProbe />
      </AuthProvider>,
    )
    await user.click(screen.getByRole('button', { name: 'user-login' }))
    expect(screen.getByTestId('auth')).toHaveTextContent('yes')
    expect(screen.getByTestId('role')).toHaveTextContent('user')
  })
})
