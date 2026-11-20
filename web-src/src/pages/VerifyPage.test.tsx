import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import VerifyPage from './VerifyPage'

const navigate = vi.fn()

vi.mock('react-router-dom', async () => {
  const actual =
    await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    useNavigate: () => navigate,
    useLocation: () => ({
      state: { email: 'a@ukma.edu.ua', hint: 'Код надіслано' },
      pathname: '/verify',
      search: '',
      hash: '',
      key: 'k',
    }),
  }
})

vi.mock('../api/auth', async () => {
  const actual = await vi.importActual<typeof import('../api/auth')>(
    '../api/auth',
  )
  return { ...actual, verifyApi: vi.fn(), resendCodeApi: vi.fn() }
})

const { verifyApi, resendCodeApi } = await import('../api/auth')

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('VerifyPage', () => {
  it('verifies code and navigates to login', async () => {
    const user = userEvent.setup()
    vi.mocked(verifyApi).mockResolvedValue(undefined)

    render(
      <AuthProvider>
        <MemoryRouter>
          <VerifyPage />
        </MemoryRouter>
      </AuthProvider>,
    )

    expect(screen.getByDisplayValue('a@ukma.edu.ua')).toBeInTheDocument()
    expect(screen.getByText('Код надіслано')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Код'), '123456')
    await user.click(screen.getByRole('button', { name: 'Підтвердити' }))

    await waitFor(() => {
      expect(verifyApi).toHaveBeenCalledWith('a@ukma.edu.ua', '123456')
      expect(navigate).toHaveBeenCalledWith('/login', {
        replace: true,
        state: { verifiedEmail: 'a@ukma.edu.ua' },
      })
    })
  })

  it('resends verification code', async () => {
    const user = userEvent.setup()
    vi.mocked(resendCodeApi).mockResolvedValue(undefined)

    render(
      <AuthProvider>
        <MemoryRouter>
          <VerifyPage />
        </MemoryRouter>
      </AuthProvider>,
    )

    await user.click(screen.getByRole('button', { name: 'Надіслати код знову' }))
    await waitFor(() =>
      expect(resendCodeApi).toHaveBeenCalledWith('a@ukma.edu.ua'),
    )
    expect(
      await screen.findByText(/код надіслано/i),
    ).toBeInTheDocument()
  })
})
