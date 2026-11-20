import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import RegisterPage from './RegisterPage'

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
  return { ...actual, registerApi: vi.fn() }
})

const { registerApi } = await import('../api/auth')

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('RegisterPage', () => {
  it('registers and navigates to verify', async () => {
    const user = userEvent.setup()
    vi.mocked(registerApi).mockResolvedValue({
      id: 'u1',
      email: 'a@ukma.edu.ua',
      firstName: 'A',
      lastName: 'B',
      status: 'PENDING',
      globalAdmin: false,
      headOf: [],
    })

    render(
      <AuthProvider>
        <MemoryRouter>
          <RegisterPage />
        </MemoryRouter>
      </AuthProvider>,
    )

    await user.type(screen.getByLabelText('Email'), 'a@ukma.edu.ua')
    await user.type(screen.getByLabelText('Ім’я (опційно)'), 'A')
    await user.type(screen.getByLabelText('Прізвище (опційно)'), 'B')
    await user.type(screen.getByLabelText('Пароль'), 'password123')
    await user.click(screen.getByRole('button', { name: /Зареєструватися|Створити/i }))

    await waitFor(() => {
      expect(registerApi).toHaveBeenCalled()
      expect(navigate).toHaveBeenCalledWith(
        '/verify',
        expect.objectContaining({
          replace: true,
          state: expect.objectContaining({ email: 'a@ukma.edu.ua' }),
        }),
      )
    })
  })
})
