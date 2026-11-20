import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PasswordResetPage from './PasswordResetPage'
import { ApiError } from '../api/client'

vi.mock('../api/auth', async (importActual) => {
  const actual = await importActual<typeof import('../api/auth')>()
  return {
    ...actual,
    requestPasswordResetApi: vi.fn(),
    confirmPasswordResetApi: vi.fn(),
  }
})

const { confirmPasswordResetApi, requestPasswordResetApi } =
  await import('../api/auth')
const requestMock = vi.mocked(requestPasswordResetApi)
const confirmMock = vi.mocked(confirmPasswordResetApi)

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function LoginProbe() {
  const state = useLocation().state as { hint?: string } | null
  return <p>login: {state?.hint ?? 'без підказки'}</p>
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/password-reset']}>
      <Routes>
        <Route path="/password-reset" element={<PasswordResetPage />} />
        <Route path="/login" element={<LoginProbe />} />
      </Routes>
    </MemoryRouter>,
  )
}

async function requestCode(email = 'Student@UKMA.edu.ua  ') {
  const u = userEvent.setup()
  renderPage()
  await u.type(screen.getByLabelText('Email'), email)
  await u.click(screen.getByRole('button', { name: 'Надіслати код' }))
  return u
}

describe('PasswordResetPage', () => {
  it('нормалізує пошту перед запитом коду', async () => {
    requestMock.mockResolvedValue(undefined)

    await requestCode()

    expect(requestMock).toHaveBeenCalledWith('student@ukma.edu.ua')
  })

  it('після запиту показує крок з кодом і нейтральне повідомлення', async () => {
    requestMock.mockResolvedValue(undefined)

    await requestCode()

    // текст навмисно не каже, чи існує акаунт — бекенд теж не каже
    expect(
      await screen.findByText(/Якщо акаунт існує — код надіслано/),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Код')).toBeInTheDocument()
    expect(screen.getByLabelText('Новий пароль')).toBeInTheDocument()
  })

  it('задає новий пароль і веде на вхід із підказкою', async () => {
    requestMock.mockResolvedValue(undefined)
    confirmMock.mockResolvedValue(undefined)
    const u = await requestCode()
    await screen.findByLabelText('Код')

    await u.type(screen.getByLabelText('Код'), '123456')
    await u.type(screen.getByLabelText('Новий пароль'), 'new-password-1')
    await u.click(screen.getByRole('button', { name: 'Змінити пароль' }))

    expect(confirmMock).toHaveBeenCalledWith(
      'student@ukma.edu.ua',
      '123456',
      'new-password-1',
    )
    expect(
      await screen.findByText('login: Пароль змінено, увійдіть з новим.'),
    ).toBeInTheDocument()
  })

  it('невірний код показує повідомлення бекенду й лишає на кроці', async () => {
    requestMock.mockResolvedValue(undefined)
    const body = '{"error":{"message":"Невірний код підтвердження"}}'
    confirmMock.mockRejectedValue(new ApiError(400, body, body))
    const u = await requestCode()
    await screen.findByLabelText('Код')

    await u.type(screen.getByLabelText('Код'), '000000')
    await u.type(screen.getByLabelText('Новий пароль'), 'new-password-1')
    await u.click(screen.getByRole('button', { name: 'Змінити пароль' }))

    expect(
      await screen.findByText('Невірний код підтвердження'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Код')).toBeInTheDocument()
  })

  it('код можна надіслати повторно з другого кроку', async () => {
    requestMock.mockResolvedValue(undefined)
    const u = await requestCode()
    await screen.findByLabelText('Код')

    await u.click(screen.getByRole('button', { name: 'Надіслати код знову' }))

    expect(requestMock).toHaveBeenCalledTimes(2)
  })

  it('забагато спроб: 429 показується, крок не змінюється', async () => {
    const body = '{"error":{"message":"Спробуйте пізніше"}}'
    requestMock.mockRejectedValue(new ApiError(429, body, body))

    await requestCode()

    expect(await screen.findByText('Спробуйте пізніше')).toBeInTheDocument()
    expect(screen.queryByLabelText('Код')).not.toBeInTheDocument()
  })
})
