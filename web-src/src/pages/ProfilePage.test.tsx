import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ProfilePage from './ProfilePage'
import { ApiError } from '../api/client'
import type { UserResponse } from '../api/auth'
import { clearSession, createSession, writeSession } from '../auth/session'

vi.mock('../api/auth', async (importActual) => {
  const actual = await importActual<typeof import('../api/auth')>()
  return { ...actual, meApi: vi.fn(), updateProfileApi: vi.fn() }
})

const { meApi, updateProfileApi } = await import('../api/auth')
const meMock = vi.mocked(meApi)
const updateMock = vi.mocked(updateProfileApi)

function user(overrides: Partial<UserResponse> = {}): UserResponse {
  return {
    id: '9b1c7a40-0000-4000-8000-000000000001',
    email: 'student@ukma.edu.ua',
    globalAdmin: false,
    headOf: [],
    ...overrides,
  }
}

beforeEach(() => {
  writeSession(createSession('student@ukma.edu.ua', 'student', 'jwt'))
  meMock.mockResolvedValue(user())
})

afterEach(() => {
  cleanup()
  clearSession()
  vi.clearAllMocks()
})

describe('ProfilePage', () => {
  it('підставляє пошту з профілю і не дає її змінити', async () => {
    render(<ProfilePage />)

    const email = (await screen.findByLabelText('Email')) as HTMLInputElement
    expect(email.value).toBe('student@ukma.edu.ua')
    expect(email).toBeDisabled()
  })

  it('порожні імена дають порожні поля, а не undefined у значенні', async () => {
    render(<ProfilePage />)

    const first = (await screen.findByLabelText("Ім'я")) as HTMLInputElement
    expect(first.value).toBe('')
  })

  it('наявні імена підставляються у форму', async () => {
    meMock.mockResolvedValue(user({ firstName: 'Теодора', lastName: 'Гречко' }))
    render(<ProfilePage />)

    expect(
      ((await screen.findByLabelText("Ім'я")) as HTMLInputElement).value,
    ).toBe('Теодора')
    expect((screen.getByLabelText('Прізвище') as HTMLInputElement).value).toBe(
      'Гречко',
    )
  })

  it('зберігає обрізані імена й показує підтвердження', async () => {
    updateMock.mockResolvedValue(
      user({ firstName: 'Максим', lastName: 'Костинюк' }),
    )
    const u = userEvent.setup()
    render(<ProfilePage />)
    await screen.findByLabelText("Ім'я")

    await u.type(screen.getByLabelText("Ім'я"), '  Максим  ')
    await u.type(screen.getByLabelText('Прізвище'), '  Костинюк  ')
    await u.click(screen.getByRole('button', { name: 'Зберегти' }))

    expect(updateMock).toHaveBeenCalledWith({
      firstName: 'Максим',
      lastName: 'Костинюк',
    })
    expect(await screen.findByText('Профіль збережено')).toBeInTheDocument()
  })

  it('помилка валідації з бекенду показується як є', async () => {
    const body =
      '{"error":{"message":"Запит містить невалідні поля","details":[{"field":"firstName","message":"Лише літери"}]}}'
    updateMock.mockRejectedValue(new ApiError(400, body, body))
    const u = userEvent.setup()
    render(<ProfilePage />)
    await screen.findByLabelText("Ім'я")

    await u.type(screen.getByLabelText("Ім'я"), 'Max123')
    await u.type(screen.getByLabelText('Прізвище'), 'K')
    await u.click(screen.getByRole('button', { name: 'Зберегти' }))

    expect(
      await screen.findByText(/firstName: Лише літери/),
    ).toBeInTheDocument()
  })

  it('помилка завантаження не ховає форму', async () => {
    meMock.mockRejectedValue(new ApiError(401, 'no', 'no'))
    render(<ProfilePage />)

    expect(
      await screen.findByRole('button', { name: 'Зберегти' }),
    ).toBeInTheDocument()
  })
})
