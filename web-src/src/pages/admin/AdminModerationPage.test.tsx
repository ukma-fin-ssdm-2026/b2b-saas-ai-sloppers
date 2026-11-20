import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import type { Ban, BannedWord, Complaint } from '../../types/moderation'
import AdminModerationPage from './AdminModerationPage'

vi.mock('../../api/moderation', () => ({
  listComplaints: vi.fn(),
  resolveComplaint: vi.fn(),
  dismissComplaint: vi.fn(),
  banUser: vi.fn(),
  unbanUser: vi.fn(),
  listBans: vi.fn(),
  listBannedWords: vi.fn(),
  addBannedWord: vi.fn(),
  removeBannedWord: vi.fn(),
}))

const api = await import('../../api/moderation')

const AUTHOR_ID = 'a1000000-0000-4000-8000-000000000001'
const DAY_MS = 24 * 60 * 60 * 1000

function complaint(overrides: Partial<Complaint> = {}): Complaint {
  return {
    id: 'c1',
    reporterId: 'r1',
    authorId: AUTHOR_ID,
    authorName: 'Олена Коваль',
    targetType: 'ADVERT',
    targetId: 'b1',
    category: 'SPAM',
    comment: 'Реклама курсів',
    status: 'OPEN',
    resolvedBy: null,
    resolution: null,
    createdAt: '2026-10-21T10:00:00Z',
    resolvedAt: null,
    ...overrides,
  }
}

function ban(overrides: Partial<Ban> = {}): Ban {
  return {
    id: 'ban1',
    userId: AUTHOR_ID,
    bannedBy: 'admin',
    automatic: false,
    reason: 'Спам',
    expiresAt: '2026-10-28T10:00:00Z',
    active: true,
    createdAt: '2026-10-21T10:00:00Z',
    liftedAt: null,
    liftedBy: null,
    ...overrides,
  }
}

function page(items: Complaint[]) {
  return {
    data: items,
    pagination: { offset: 0, limit: 50, total: items.length, hasMore: false },
  }
}

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminModerationPage />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.mocked(api.listComplaints).mockResolvedValue(page([complaint()]))
  vi.mocked(api.listBannedWords).mockResolvedValue([])
})

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('AdminModerationPage', () => {
  it('показує відкриті скарги з типом, причиною, автором і коментарем', async () => {
    renderPage()

    const list = await screen.findByRole('list', { name: 'Скарги' })
    expect(within(list).getByText('Оголошення')).toBeInTheDocument()
    expect(within(list).getByText('Спам')).toBeInTheDocument()
    expect(within(list).getByText(/Олена Коваль/)).toBeInTheDocument()
    expect(within(list).getByText('«Реклама курсів»')).toBeInTheDocument()
    expect(api.listComplaints).toHaveBeenCalledWith({
      status: 'OPEN',
      limit: 50,
      offset: 0,
    })
  })

  it('«Прибрати» надсилає коментар і перезавантажує чергу', async () => {
    vi.mocked(api.resolveComplaint).mockResolvedValue(
      complaint({ status: 'RESOLVED' }),
    )
    const user = userEvent.setup()
    renderPage()

    await user.type(
      await screen.findByLabelText(/Коментар до рішення/),
      'Реклама',
    )
    vi.mocked(api.listComplaints).mockResolvedValue(page([]))
    await user.click(screen.getByRole('button', { name: 'Прибрати' }))

    expect(api.resolveComplaint).toHaveBeenCalledWith('c1', 'Реклама')
    expect(await screen.findByText(/Оголошення прибрано/)).toBeInTheDocument()
    expect(await screen.findByText('Скарг немає.')).toBeInTheDocument()
  })

  it('помилка рішення показує повідомлення бекенду', async () => {
    vi.mocked(api.dismissComplaint).mockRejectedValue(
      new ApiError(
        409,
        'conflict',
        JSON.stringify({ error: { message: 'Скаргу вже розглянуто' } }),
      ),
    )
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Відхилити' }))

    expect(await screen.findByText('Скаргу вже розглянуто')).toBeInTheDocument()
  })

  it('фільтр «Усі» запитує чергу без стану й прибирає старе повідомлення', async () => {
    vi.mocked(api.dismissComplaint).mockResolvedValue(
      complaint({ status: 'DISMISSED' }),
    )
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Відхилити' }))
    expect(
      await screen.findByText('Скаргу відхилено, контент лишився'),
    ).toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Стан'), 'ALL')

    expect(
      screen.queryByText('Скаргу відхилено, контент лишився'),
    ).not.toBeInTheDocument()

    await waitFor(() =>
      expect(api.listComplaints).toHaveBeenLastCalledWith({
        status: undefined,
        limit: 50,
        offset: 0,
      }),
    )
  })

  it('«Заблокувати автора» підставляє його ID, бан на 7 днів показує історію', async () => {
    vi.mocked(api.banUser).mockResolvedValue(ban())
    vi.mocked(api.listBans).mockResolvedValue([ban()])
    const user = userEvent.setup()
    renderPage()

    await user.click(
      await screen.findByRole('button', { name: 'Заблокувати автора' }),
    )
    expect(screen.getByLabelText('ID користувача')).toHaveValue(AUTHOR_ID)
    await user.type(screen.getByLabelText('Причина'), 'Спам')
    const before = Date.now()
    await user.click(screen.getByRole('button', { name: 'Заблокувати' }))
    const after = Date.now()

    const [userId, request] = vi.mocked(api.banUser).mock.calls[0]
    expect(userId).toBe(AUTHOR_ID)
    expect(request.reason).toBe('Спам')
    const expiresAt = Date.parse(request.expiresAt ?? '')
    expect(expiresAt).toBeGreaterThanOrEqual(before + 7 * DAY_MS)
    expect(expiresAt).toBeLessThanOrEqual(after + 7 * DAY_MS)
    const history = await screen.findByRole('list', {
      name: 'Історія блокувань',
    })
    expect(within(history).getByText('Діє')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Розблокувати' }),
    ).toBeInTheDocument()
  })

  it('безстроковий бан надсилає expiresAt: null', async () => {
    vi.mocked(api.banUser).mockResolvedValue(ban({ expiresAt: null }))
    vi.mocked(api.listBans).mockResolvedValue([ban({ expiresAt: null })])
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText('ID користувача'), AUTHOR_ID)
    await user.type(screen.getByLabelText('Причина'), 'Образи')
    await user.selectOptions(screen.getByLabelText('Строк'), 'permanent')
    await user.click(screen.getByRole('button', { name: 'Заблокувати' }))

    expect(api.banUser).toHaveBeenCalledWith(AUTHOR_ID, {
      reason: 'Образи',
      expiresAt: null,
    })
    expect(
      await screen.findByText('Заблоковано безстроково'),
    ).toBeInTheDocument()
  })

  it('розблокування знімає активний бан і оновлює історію', async () => {
    vi.mocked(api.listBans)
      .mockResolvedValueOnce([ban()])
      .mockResolvedValueOnce([
        ban({ active: false, liftedAt: '2026-10-22T10:00:00Z' }),
      ])
    vi.mocked(api.unbanUser).mockResolvedValue(ban({ active: false }))
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText('ID користувача'), AUTHOR_ID)
    await user.click(screen.getByRole('button', { name: 'Історія' }))
    await user.click(
      await screen.findByRole('button', { name: 'Розблокувати' }),
    )

    expect(api.unbanUser).toHaveBeenCalledWith(AUTHOR_ID)
    expect(await screen.findByText('Знято')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Розблокувати' }),
    ).not.toBeInTheDocument()
  })

  it('заборонені слова: додавання й видалення оновлюють список', async () => {
    const words: BannedWord[] = [
      {
        id: 'w1',
        word: 'казино',
        addedBy: null,
        createdAt: '2026-10-21T10:00:00Z',
      },
    ]
    vi.mocked(api.addBannedWord).mockResolvedValue(words[0])
    vi.mocked(api.removeBannedWord).mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText('Список порожній.')).toBeInTheDocument()
    vi.mocked(api.listBannedWords).mockResolvedValue(words)
    await user.type(screen.getByLabelText('Нове слово'), 'казино')
    await user.click(screen.getByRole('button', { name: 'Додати' }))

    expect(api.addBannedWord).toHaveBeenCalledWith('казино')
    const list = await screen.findByRole('list', { name: 'Заборонені слова' })
    expect(within(list).getByText('казино')).toBeInTheDocument()

    vi.mocked(api.listBannedWords).mockResolvedValue([])
    await user.click(screen.getByRole('button', { name: 'Прибрати «казино»' }))
    expect(api.removeBannedWord).toHaveBeenCalledWith('w1')
    expect(await screen.findByText('Список порожній.')).toBeInTheDocument()
  })
})
