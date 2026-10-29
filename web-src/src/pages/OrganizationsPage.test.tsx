import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import OrganizationsPage from './OrganizationsPage'
import { ApiError } from '../api/organizations'
import type { Organization } from '../types/organization'

vi.mock('../api/organizations', async () => {
  const actual = await vi.importActual<typeof import('../api/organizations')>(
    '../api/organizations',
  )
  return { ...actual, listOrganizations: vi.fn() }
})

const { listOrganizations } = await import('../api/organizations')
const listMock = vi.mocked(listOrganizations)

function organization(overrides: Partial<Organization> = {}): Organization {
  return {
    id: '3f6c1f0e-8a2b-4c1d-9e7a-1b2c3d4e5f60',
    name: 'Дебатний клуб НаУКМА',
    slug: 'debate-club',
    description: 'Щотижневі тренування',
    createdAt: '2026-03-01T10:00:00+02:00',
    updatedAt: '2026-03-01T10:00:00+02:00',
    ...overrides,
  }
}

function page(data: Organization[]) {
  return {
    data,
    pagination: { offset: 0, limit: 20, total: data.length, hasMore: false },
  }
}

afterEach(() => {
  vi.resetAllMocks()
})

describe('OrganizationsPage', () => {
  it('показує індикатор завантаження до відповіді бекенду', () => {
    listMock.mockReturnValue(new Promise(() => {}))

    render(<OrganizationsPage />)

    expect(screen.getByText('Завантаження…')).toBeInTheDocument()
  })

  it('запитує першу сторінку з двадцяти організацій', async () => {
    listMock.mockResolvedValue(page([]))

    render(<OrganizationsPage />)

    await waitFor(() =>
      expect(listMock).toHaveBeenCalledWith({ limit: 20, offset: 0 }),
    )
  })

  it('виводить назву, slug і опис кожної організації', async () => {
    listMock.mockResolvedValue(
      page([
        organization(),
        organization({
          id: 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d',
          name: 'Клуб програмування',
          slug: 'coding-club',
          description: undefined,
        }),
      ]),
    )

    render(<OrganizationsPage />)

    expect(await screen.findByText('Дебатний клуб НаУКМА')).toBeInTheDocument()
    expect(screen.getByText('debate-club')).toBeInTheDocument()
    expect(screen.getByText('Щотижневі тренування')).toBeInTheDocument()
    expect(screen.getByText('Клуб програмування')).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
  })

  it('повідомляє про порожній каталог, коли організацій немає', async () => {
    listMock.mockResolvedValue(page([]))

    render(<OrganizationsPage />)

    expect(
      await screen.findByText('Поки немає організацій.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('показує статус відповіді, коли бекенд повертає помилку', async () => {
    listMock.mockRejectedValue(new ApiError(503, 'service unavailable'))

    render(<OrganizationsPage />)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Failed to load (503)')
  })

  it('показує загальне повідомлення, коли помилка не від API', async () => {
    listMock.mockRejectedValue(new TypeError('network down'))

    render(<OrganizationsPage />)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Failed to load organizations')
    expect(alert).not.toHaveTextContent('network down')
  })

  it('не показує список і помилку одночасно', async () => {
    listMock.mockRejectedValue(new ApiError(500, 'boom'))

    render(<OrganizationsPage />)

    await screen.findByRole('alert')
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
    expect(screen.queryByText('Завантаження…')).not.toBeInTheDocument()
  })
})
