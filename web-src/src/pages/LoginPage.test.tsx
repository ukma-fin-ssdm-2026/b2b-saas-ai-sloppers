import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import { clearSession } from '../auth/session'
import LoginPage from './LoginPage'

const navigate = vi.fn()

vi.mock('react-router-dom', async () => {
  const actual =
    await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => navigate }
})

vi.mock('../api/auth', async () => {
  const actual = await vi.importActual<typeof import('../api/auth')>(
    '../api/auth',
  )
  return { ...actual, loginApi: vi.fn(), meApi: vi.fn() }
})

const { loginApi, meApi } = await import('../api/auth')

afterEach(() => {
  cleanup()
  clearSession()
  vi.resetAllMocks()
})

describe('LoginPage', () => {
  it('просить увійти через @ukma.edu.ua без згадки демо-логіну', () => {
    render(
      <AuthProvider>
        <MemoryRouter>
          <LoginPage />
        </MemoryRouter>
      </AuthProvider>,
    )

    expect(
      screen.getByText(/Увійдіть акаунтом @ukma.edu.ua/),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.queryByText(/Демо-логін/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/bot/i)).not.toBeInTheDocument()
  })

  it('logs in and navigates to destination', async () => {
    const user = userEvent.setup()
    vi.mocked(loginApi).mockResolvedValue({
      accessToken: 'tok',
      tokenType: 'Bearer',
      expiresIn: 3600,
    })
    vi.mocked(meApi).mockResolvedValue({
      id: 'u1',
      email: 'a@ukma.edu.ua',
      firstName: null,
      lastName: null,
      status: 'ACTIVE',
      globalAdmin: false,
      headOf: [],
    })

    render(
      <AuthProvider>
        <MemoryRouter
          initialEntries={[
            { pathname: '/login', state: { from: '/board/mine' } },
          ]}
        >
          <LoginPage />
        </MemoryRouter>
      </AuthProvider>,
    )

    await user.type(screen.getByLabelText('Email'), 'a@ukma.edu.ua')
    await user.type(screen.getByLabelText('Пароль'), 'password123')
    await user.click(screen.getByRole('button', { name: 'Увійти' }))

    await waitFor(() => {
      expect(loginApi).toHaveBeenCalledWith('a@ukma.edu.ua', 'password123')
      expect(navigate).toHaveBeenCalledWith('/board/mine', { replace: true })
    })
  })

  it('shows verified email banner and login error', async () => {
    const user = userEvent.setup()
    vi.mocked(loginApi).mockRejectedValue(new Error('fail'))

    render(
      <AuthProvider>
        <MemoryRouter
          initialEntries={[
            {
              pathname: '/login',
              state: { verifiedEmail: 'ok@ukma.edu.ua' },
            },
          ]}
        >
          <LoginPage />
        </MemoryRouter>
      </AuthProvider>,
    )

    expect(screen.getByText(/Email підтверджено/)).toBeInTheDocument()
    expect(screen.getByDisplayValue('ok@ukma.edu.ua')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Пароль'), 'bad')
    await user.click(screen.getByRole('button', { name: 'Увійти' }))
    expect(await screen.findByText(/Не вдалося увійти/)).toBeInTheDocument()
  })

  it('toggles password visibility', async () => {
    const user = userEvent.setup()
    render(
      <AuthProvider>
        <MemoryRouter>
          <LoginPage />
        </MemoryRouter>
      </AuthProvider>,
    )
    const pass = screen.getByLabelText('Пароль')
    expect(pass).toHaveAttribute('type', 'password')
    await user.click(screen.getByRole('button', { name: 'Показати пароль' }))
    expect(pass).toHaveAttribute('type', 'text')
  })
})
