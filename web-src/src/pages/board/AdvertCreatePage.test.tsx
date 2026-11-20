import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdvertCreatePage from './AdvertCreatePage'

const navigate = vi.fn()

vi.mock('react-router-dom', async () => {
  const actual =
    await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    useNavigate: () => navigate,
  }
})

vi.mock('../../api/adverts', async () => {
  const actual = await vi.importActual<typeof import('../../api/adverts')>(
    '../../api/adverts',
  )
  return { ...actual, createAdvert: vi.fn() }
})

const { createAdvert } = await import('../../api/adverts')

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('AdvertCreatePage', () => {
  it('creates an advert and navigates to its page', async () => {
    const user = userEvent.setup()
    vi.mocked(createAdvert).mockResolvedValue({
      id: 'new-id',
      type: 'TEAM',
      title: 'Hack',
      description: 'Need FE',
      slots: 2,
      deadline: '2030-01-01T12:00:00.000Z',
      status: 'ACTIVE',
      contact: '@me',
      own: true,
      authorName: null,
      myReplyStatus: null,
      myReplyId: null,
      createdAt: '2026-11-01T12:00:00+02:00',
    })

    render(
      <MemoryRouter>
        <AdvertCreatePage />
      </MemoryRouter>,
    )

    await user.type(screen.getByLabelText('Заголовок'), 'Hack')
    await user.type(screen.getByLabelText('Опис'), 'Need FE')
    await user.clear(screen.getByLabelText('Кількість місць'))
    await user.type(screen.getByLabelText('Кількість місць'), '2')
    await user.type(screen.getByLabelText('Контакт'), '@me')
    await user.click(screen.getByRole('button', { name: 'Опублікувати' }))

    await waitFor(() => {
      expect(createAdvert).toHaveBeenCalled()
      expect(navigate).toHaveBeenCalledWith('/board/new-id', { replace: true })
    })
  })

  it('shows error when create fails', async () => {
    const user = userEvent.setup()
    const { ApiError } = await import('../../api/adverts')
    vi.mocked(createAdvert).mockRejectedValue(
      new ApiError(400, '{"error":{"message":"Дедлайн у минулому"}}'),
    )

    render(
      <MemoryRouter>
        <AdvertCreatePage />
      </MemoryRouter>,
    )

    await user.type(screen.getByLabelText('Заголовок'), 'Hack')
    await user.type(screen.getByLabelText('Опис'), 'Need FE')
    await user.type(screen.getByLabelText('Контакт'), '@me')
    await user.click(screen.getByRole('button', { name: 'Опублікувати' }))

    expect(await screen.findByText('Дедлайн у минулому')).toBeInTheDocument()
  })
})
