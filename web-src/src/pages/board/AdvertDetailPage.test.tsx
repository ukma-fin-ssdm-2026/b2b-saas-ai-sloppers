import { cleanup, render, screen } from '@testing-library/react'
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
  }
})

const { getAdvert, listAdvertReplies } = await import('../../api/adverts')

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

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('AdvertDetailPage', () => {
  it('renders advert details for a viewer', async () => {
    vi.mocked(getAdvert).mockResolvedValue(advert())
    vi.mocked(listAdvertReplies).mockResolvedValue([])

    render(
      <MemoryRouter initialEntries={['/board/adv-1']}>
        <Routes>
          <Route path="/board/:advertId" element={<AdvertDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(
      await screen.findByRole('heading', { name: 'Шукаю дизайнера' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Figma')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Відгукнутися' }),
    ).toBeInTheDocument()
  })

  it('loads replies for the owner', async () => {
    vi.mocked(getAdvert).mockResolvedValue(advert({ own: true }))
    vi.mocked(listAdvertReplies).mockResolvedValue([
      {
        id: 'r1',
        advertId: 'adv-1',
        userId: 'u2',
        userName: 'Аліса',
        message: 'Готова',
        status: 'PENDING',
        createdAt: '2026-11-02T12:00:00+02:00',
        decidedAt: null,
      },
    ])

    render(
      <MemoryRouter initialEntries={['/board/adv-1']}>
        <Routes>
          <Route path="/board/:advertId" element={<AdvertDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText('Аліса')).toBeInTheDocument()
    expect(screen.getByText(/Готова/)).toBeInTheDocument()
    expect(listAdvertReplies).toHaveBeenCalledWith('adv-1')
  })
})
