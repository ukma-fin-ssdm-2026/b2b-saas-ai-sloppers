import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import BoardListPage from './BoardListPage'
import type { Advert } from '../../types/advert'

vi.mock('../../api/adverts', async () => {
  const actual = await vi.importActual<typeof import('../../api/adverts')>(
    '../../api/adverts',
  )
  return {
    ...actual,
    listAdverts: vi.fn(),
    listMyAdverts: vi.fn(),
    listRepliedAdverts: vi.fn(),
  }
})

const { listAdverts, listMyAdverts, listRepliedAdverts } = await import(
  '../../api/adverts'
)

function advert(overrides: Partial<Advert> = {}): Advert {
  return {
    id: 'b1000000-0000-4000-8000-000000000001',
    type: 'TEAM',
    title: 'Шукаю бекендера',
    description: 'Java',
    slots: 2,
    deadline: '2026-12-01T12:00:00+02:00',
    status: 'ACTIVE',
    contact: null,
    own: false,
    authorName: 'Тест',
    myReplyStatus: null,
    myReplyId: null,
    createdAt: '2026-11-01T12:00:00+02:00',
    ...overrides,
  }
}

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('BoardListPage', () => {
  it('loads open adverts and shows titles', async () => {
    vi.mocked(listAdverts).mockResolvedValue({
      data: [advert()],
      pagination: { offset: 0, limit: 20, total: 1, hasMore: false },
    })

    render(
      <MemoryRouter>
        <BoardListPage scope="open" />
      </MemoryRouter>,
    )

    expect(await screen.findByText('Шукаю бекендера')).toBeInTheDocument()
    expect(listAdverts).toHaveBeenCalled()
  })

  it('uses mine and replied endpoints for scoped lists', async () => {
    vi.mocked(listMyAdverts).mockResolvedValue({
      data: [],
      pagination: { offset: 0, limit: 20, total: 0, hasMore: false },
    })
    vi.mocked(listRepliedAdverts).mockResolvedValue({
      data: [advert({ title: 'Мій відгук' })],
      pagination: { offset: 0, limit: 20, total: 1, hasMore: false },
    })

    const { rerender } = render(
      <MemoryRouter>
        <BoardListPage scope="mine" />
      </MemoryRouter>,
    )
    await waitFor(() => expect(listMyAdverts).toHaveBeenCalled())
    expect(await screen.findByText('Поки немає оголошень.')).toBeInTheDocument()

    rerender(
      <MemoryRouter>
        <BoardListPage scope="replied" />
      </MemoryRouter>,
    )
    expect(await screen.findByText('Мій відгук')).toBeInTheDocument()
  })

  it('shows API error message', async () => {
    const { ApiError } = await import('../../api/adverts')
    vi.mocked(listAdverts).mockRejectedValue(
      new ApiError(500, '{"error":{"message":"Дошка недоступна"}}'),
    )

    render(
      <MemoryRouter>
        <BoardListPage scope="open" />
      </MemoryRouter>,
    )

    expect(await screen.findByText('Дошка недоступна')).toBeInTheDocument()
  })

  it('filters open list by type', async () => {
    const user = userEvent.setup()
    vi.mocked(listAdverts).mockResolvedValue({
      data: [],
      pagination: { offset: 0, limit: 20, total: 0, hasMore: false },
    })

    render(
      <MemoryRouter>
        <BoardListPage scope="open" />
      </MemoryRouter>,
    )

    await screen.findByText('Поки немає оголошень.')
    await user.selectOptions(screen.getByLabelText('Тип'), 'LEISURE')
    await waitFor(() =>
      expect(listAdverts).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'LEISURE' }),
      ),
    )
  })
})
