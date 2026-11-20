import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import BoardPage from './BoardPage'
import { ApiError } from '../api/client'
import type { Advert } from '../types/advert'

vi.mock('../api/adverts', () => ({
  listAdverts: vi.fn(),
  createAdvert: vi.fn(),
  closeAdvert: vi.fn(),
  deleteAdvert: vi.fn(),
}))

const { closeAdvert, createAdvert, deleteAdvert, listAdverts } =
  await import('../api/adverts')
const listMock = vi.mocked(listAdverts)
const createMock = vi.mocked(createAdvert)
const closeMock = vi.mocked(closeAdvert)
const deleteMock = vi.mocked(deleteAdvert)

const ADVERT_ID = 'b1000000-0000-4000-8000-000000000001'

function advert(overrides: Partial<Advert> = {}): Advert {
  return {
    id: ADVERT_ID,
    type: 'TEAM',
    title: 'Шукаю бекендера на хакатон',
    description: 'Бракує Java/Spring',
    slots: 2,
    deadline: '2026-11-20T18:00:00+02:00',
    status: 'ACTIVE',
    own: false,
    createdAt: '2026-11-16T12:00:00+02:00',
    contact: null,
    authorName: 'Теодора Гречко',
    myReplyStatus: null,
    ...overrides,
  }
}

function pageOf(items: Advert[]) {
  return {
    data: items,
    pagination: {
      offset: 0,
      limit: 20,
      total: items.length,
      hasMore: false,
    },
  }
}

beforeEach(() => {
  listMock.mockResolvedValue(pageOf([advert()]))
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('BoardPage', () => {
  it('показує оголошення з типом, місцями й автором', async () => {
    render(<BoardPage />)

    const card = (
      await screen.findByText('Шукаю бекендера на хакатон')
    ).closest('li') as HTMLElement
    // «Команда» є і в бейджі картки, і в списку фільтра, тож шукаємо в картці
    expect(within(card).getByText('Команда')).toBeInTheDocument()
    expect(within(card).getByText(/місць: 2/)).toBeInTheDocument()
    expect(within(card).getByText(/Теодора Гречко/)).toBeInTheDocument()
  })

  it('без контакту не показує чужий контакт (FR-049)', async () => {
    render(<BoardPage />)
    await screen.findByText('Шукаю бекендера на хакатон')

    expect(screen.queryByText(/Контакт:/)).not.toBeInTheDocument()
  })

  it('підтверджений відгук відкриває контакт', async () => {
    listMock.mockResolvedValue(
      pageOf([advert({ contact: '@seeker', myReplyStatus: 'CONFIRMED' })]),
    )
    render(<BoardPage />)

    expect(await screen.findByText('Контакт: @seeker')).toBeInTheDocument()
    expect(screen.getByText(/відгук підтверджено/)).toBeInTheDocument()
  })

  it('фільтр за типом перезапитує список', async () => {
    const user = userEvent.setup()
    render(<BoardPage />)
    await screen.findByText('Шукаю бекендера на хакатон')

    await user.selectOptions(screen.getByLabelText('Тип оголошення'), 'LEISURE')

    await waitFor(() =>
      expect(listMock).toHaveBeenLastCalledWith({
        type: 'LEISURE',
        limit: 20,
      }),
    )
  })

  it('«Усі» надсилає запит без типу', async () => {
    render(<BoardPage />)
    await screen.findByText('Шукаю бекендера на хакатон')

    expect(listMock).toHaveBeenCalledWith({ type: undefined, limit: 20 })
  })

  it('кнопки автора видно лише на своєму оголошенні', async () => {
    render(<BoardPage />)
    await screen.findByText('Шукаю бекендера на хакатон')

    expect(
      screen.queryByRole('button', { name: 'Закрити' }),
    ).not.toBeInTheDocument()
  })

  it('своє активне оголошення можна закрити', async () => {
    listMock.mockResolvedValue(pageOf([advert({ own: true })]))
    closeMock.mockResolvedValue(advert({ own: true, status: 'CLOSED' }))
    const user = userEvent.setup()
    render(<BoardPage />)
    await screen.findByText('Шукаю бекендера на хакатон')

    await user.click(screen.getByRole('button', { name: 'Закрити' }))

    expect(closeMock).toHaveBeenCalledWith(ADVERT_ID)
    expect(await screen.findByText('Оголошення закрито')).toBeInTheDocument()
  })

  it('закрите своє оголошення вже не має кнопки «Закрити»', async () => {
    listMock.mockResolvedValue(
      pageOf([advert({ own: true, status: 'CLOSED' })]),
    )
    render(<BoardPage />)
    await screen.findByText('Шукаю бекендера на хакатон')

    expect(
      screen.queryByRole('button', { name: 'Закрити' }),
    ).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Видалити' })).toBeInTheDocument()
  })

  it('своє оголошення можна видалити', async () => {
    listMock.mockResolvedValue(pageOf([advert({ own: true })]))
    deleteMock.mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(<BoardPage />)
    await screen.findByText('Шукаю бекендера на хакатон')

    await user.click(screen.getByRole('button', { name: 'Видалити' }))

    expect(deleteMock).toHaveBeenCalledWith(ADVERT_ID)
  })

  it('створення: дедлайн іде на бекенд з часовим зсувом, поля обрізаються', async () => {
    createMock.mockResolvedValue(advert({ own: true }))
    const user = userEvent.setup()
    render(<BoardPage />)
    await screen.findByText('Шукаю бекендера на хакатон')

    await user.click(screen.getByRole('button', { name: 'Нове оголошення' }))
    await user.type(screen.getByLabelText('Заголовок'), '  Шукаю дизайнера  ')
    await user.type(screen.getByLabelText('Опис'), '  Потрібен макет  ')
    await user.clear(screen.getByLabelText('Місць'))
    await user.type(screen.getByLabelText('Місць'), '3')
    await user.type(screen.getByLabelText('Дедлайн'), '2026-11-20T18:00')
    await user.type(screen.getByLabelText('Контакт'), '  @designer  ')
    await user.click(screen.getByRole('button', { name: 'Опублікувати' }))

    const body = createMock.mock.calls[0]?.[0]
    expect(body).toMatchObject({
      type: 'TEAM',
      title: 'Шукаю дизайнера',
      description: 'Потрібен макет',
      slots: 3,
      contact: '@designer',
    })
    // datetime-local не має зсуву, тож перетворюємо на ISO
    expect(body?.deadline).toMatch(/Z$/)
    expect(
      await screen.findByText('Оголошення опубліковано'),
    ).toBeInTheDocument()
  })

  it('порожній список показує підказку, а не порожнечу', async () => {
    listMock.mockResolvedValue(pageOf([]))
    render(<BoardPage />)

    expect(
      await screen.findByText('Активних оголошень немає'),
    ).toBeInTheDocument()
  })

  it('помилка завантаження показує повідомлення бекенду', async () => {
    const body = '{"error":{"message":"Потрібен вхід"}}'
    listMock.mockRejectedValue(new ApiError(401, body, body))
    render(<BoardPage />)

    expect(await screen.findByText('Потрібен вхід')).toBeInTheDocument()
  })
})
