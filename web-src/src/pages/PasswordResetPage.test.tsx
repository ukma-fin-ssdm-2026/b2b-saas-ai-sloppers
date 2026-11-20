import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import PasswordResetPage from './PasswordResetPage'

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
  return {
    ...actual,
    requestPasswordResetApi: vi.fn(),
    confirmPasswordResetApi: vi.fn(),
  }
})

const { requestPasswordResetApi, confirmPasswordResetApi } = await import(
  '../api/auth'
)

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('PasswordResetPage', () => {
  it('requests a code then confirms and navigates to login', async () => {
    const user = userEvent.setup()
    vi.mocked(requestPasswordResetApi).mockResolvedValue(undefined)
    vi.mocked(confirmPasswordResetApi).mockResolvedValue(undefined)

    render(
      <AuthProvider>
        <MemoryRouter>
          <PasswordResetPage />
        </MemoryRouter>
      </AuthProvider>,
    )

    await user.type(screen.getByLabelText('Email'), 'a.butyrin@ukma.edu.ua')
    await user.click(screen.getByRole('button', { name: 'Надіслати код' }))

    await waitFor(() => expect(requestPasswordResetApi).toHaveBeenCalled())
    expect(await screen.findByLabelText('Код')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Код'), '123456')
    await user.type(screen.getByLabelText('Новий пароль'), 'password123')
    await user.click(screen.getByRole('button', { name: 'Змінити пароль' }))

    await waitFor(() => {
      expect(confirmPasswordResetApi).toHaveBeenCalledWith(
        'a.butyrin@ukma.edu.ua',
        '123456',
        'password123',
      )
      expect(navigate).toHaveBeenCalledWith('/login', {
        replace: true,
        state: { verifiedEmail: 'a.butyrin@ukma.edu.ua' },
      })
    })
  })
})
