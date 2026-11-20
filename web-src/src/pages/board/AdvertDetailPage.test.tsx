import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdvertDetailPage from './AdvertDetailPage'
import type { Advert } from '../../types/advert'

vi.mock('../../api/adverts', async () => {
  const actual = await vi.importActual<typeof import('../../api/adverts')>(
    '../../api/adverts',
  )
  return {
    ...actual,
    getAdvert: vi.fn(),
    listAdvertReplies: vi.fn(),
    replyToAdvert: vi.fn(),
    withdrawAdvertReply: vi.fn(),
    closeAdvert: vi.fn(),
    deleteAdvert: vi.fn(),
    confirmAdvertReply: vi.fn(),
    rejectAdvertReply: vi.fn(),
  }
})

const {
  getAdvert,
  listAdvertReplies,
  replyToAdvert,
  withdrawAdvertReply,
  closeAdvert,
  deleteAdvert,
  confirmAdvertReply,
  rejectAdvertReply,
} = await import('../../api/adverts')

function advert(overrides: Partial<Advert> = {}): Advert {
  return {
    id: 'adv-1',
    type: 'TEAM',
    title: 'Шукаю дизайнера',
    description: 'Figma',
    slots: 1,
    deadline: '2026-12-01T12:00:00+02:00',
    status: 'ACTIVE',
    contact: null,
    own: false,
    authorName: 'Автор',
    myReplyStatus: null,
    myReplyId: null,
    createdAt: '2026-11-01T12:00:00+02:00',
    ...overrides,
  }
}

function renderDetail(path = '/board/adv-1') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/board/:advertId" element={<AdvertDetailPage />} />
        <Route path="/board/mine" element={<p>Mine</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('AdvertDetailPage', () => {
  it('renders advert details for a viewer', async () => {
    vi.mocked(getAdvert).mockResolvedValue(advert())
    vi.mocked(listAdvertReplies).mockResolvedValue([])

    renderDetail()

    expect(
      await screen.findByRole('heading', { name: 'Шукаю дизайнера' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Figma')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Відгукнутися' }),
    ).toBeInTheDocument()
  })

  it('submits a reply', async () => {
    const user = userEvent.setup()
    vi.mocked(getAdvert)
      .mockResolvedValueOnce(advert())
      .mockResolvedValueOnce(
        advert({ myReplyStatus: 'PENDING', myReplyId: 'r1' }),
      )
    vi.mocked(listAdvertReplies).mockResolvedValue([])
    vi.mocked(replyToAdvert).mockResolvedValue({
      id: 'r1',
      advertId: 'adv-1',
      userId: 'u2',
      userName: 'Я',
      message: 'Готовий',
      status: 'PENDING',
      createdAt: '2026-11-02T12:00:00+02:00',
      decidedAt: null,
    })

    renderDetail()
    await screen.findByRole('heading', { name: 'Шукаю дизайнера' })
    await user.type(
      screen.getByLabelText(/Повідомлення автору/),
      'Готовий',
    )
    await user.click(screen.getByRole('button', { name: 'Відгукнутися' }))

    await waitFor(() => {
      expect(replyToAdvert).toHaveBeenCalledWith('adv-1', 'Готовий')
      expect(screen.getByText('Відгук надіслано')).toBeInTheDocument()
    })
  })

  it('withdraws pending reply', async () => {
    const user = userEvent.setup()
    vi.mocked(getAdvert)
      .mockResolvedValueOnce(
        advert({ myReplyStatus: 'PENDING', myReplyId: 'r9' }),
      )
      .mockResolvedValueOnce(
        advert({ myReplyStatus: 'WITHDRAWN', myReplyId: 'r9' }),
      )
    vi.mocked(listAdvertReplies).mockResolvedValue([])
    vi.mocked(withdrawAdvertReply).mockResolvedValue(undefined)

    renderDetail()
    await user.click(
      await screen.findByRole('button', { name: 'Відкликати відгук' }),
    )
    await waitFor(() =>
      expect(withdrawAdvertReply).toHaveBeenCalledWith('adv-1', 'r9'),
    )
  })

  it('loads replies for the owner and confirms', async () => {
    const user = userEvent.setup()
    const pending = {
      id: 'r1',
      advertId: 'adv-1',
      userId: 'u2',
      userName: 'Аліса',
      message: 'Готова',
      status: 'PENDING' as const,
      createdAt: '2026-11-02T12:00:00+02:00',
      decidedAt: null,
    }
    vi.mocked(getAdvert).mockResolvedValue(advert({ own: true, contact: 'tg' }))
    vi.mocked(listAdvertReplies)
      .mockResolvedValueOnce([pending])
      .mockResolvedValueOnce([{ ...pending, status: 'CONFIRMED' }])
    vi.mocked(confirmAdvertReply).mockResolvedValue({
      ...pending,
      status: 'CONFIRMED',
    })

    renderDetail()

    expect(await screen.findByText('Аліса')).toBeInTheDocument()
    expect(screen.getByText(/Готова/)).toBeInTheDocument()
    expect(listAdvertReplies).toHaveBeenCalledWith('adv-1')
    expect(screen.getByText(/Контакт: tg/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Підтвердити' }))
    await waitFor(() =>
      expect(confirmAdvertReply).toHaveBeenCalledWith('adv-1', 'r1'),
    )
  })

  it('rejects a pending reply', async () => {
    const user = userEvent.setup()
    const pending = {
      id: 'r1',
      advertId: 'adv-1',
      userId: 'u2',
      userName: 'Аліса',
      message: 'Готова',
      status: 'PENDING' as const,
      createdAt: '2026-11-02T12:00:00+02:00',
      decidedAt: null,
    }
    vi.mocked(getAdvert).mockResolvedValue(advert({ own: true }))
    vi.mocked(listAdvertReplies)
      .mockResolvedValueOnce([pending])
      .mockResolvedValueOnce([{ ...pending, status: 'REJECTED' }])
    vi.mocked(rejectAdvertReply).mockResolvedValue({
      ...pending,
      status: 'REJECTED',
    })

    renderDetail()
    await user.click(await screen.findByRole('button', { name: 'Відхилити' }))
    await waitFor(() =>
      expect(rejectAdvertReply).toHaveBeenCalledWith('adv-1', 'r1'),
    )
  })

  it('closes and deletes own advert', async () => {
    const user = userEvent.setup()
    vi.stubGlobal('confirm', vi.fn(() => true))
    vi.mocked(getAdvert)
      .mockResolvedValueOnce(advert({ own: true }))
      .mockResolvedValueOnce(advert({ own: true, status: 'CLOSED' }))
    vi.mocked(listAdvertReplies).mockResolvedValue([])
    vi.mocked(closeAdvert).mockResolvedValue(undefined)
    vi.mocked(deleteAdvert).mockResolvedValue(undefined)

    renderDetail()
    await user.click(await screen.findByRole('button', { name: 'Закрити' }))
    await waitFor(() => expect(closeAdvert).toHaveBeenCalledWith('adv-1'))

    await user.click(screen.getByRole('button', { name: 'Видалити' }))
    await waitFor(() => expect(deleteAdvert).toHaveBeenCalledWith('adv-1'))
    expect(await screen.findByText('Mine')).toBeInTheDocument()
  })

  it('shows error when load fails', async () => {
    vi.mocked(getAdvert).mockRejectedValue(new Error('boom'))
    renderDetail()
    expect(await screen.findByText(/Не вдалося завантажити/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'До дошки' })).toBeInTheDocument()
  })
})
