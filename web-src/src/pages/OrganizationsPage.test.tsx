import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { forwardRef, useImperativeHandle } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import { ApiError } from '../api/organizations'
import { ThemeProvider } from '../theme/ThemeContext'
import type { Organization } from '../types/organization'
import OrganizationsPage from './OrganizationsPage'

vi.mock('../api/organizations', async () => {
  const actual = await vi.importActual<typeof import('../api/organizations')>(
    '../api/organizations',
  )
  return { ...actual, listOrganizations: vi.fn() }
})

const focusIndexMock = vi.fn()

vi.mock('../components/CircularCarousel', () => {
  const MockCarousel = forwardRef(function MockCarousel(
    {
      items,
    }: {
      items?: { title?: string }[]
    },
    ref,
  ) {
    useImperativeHandle(ref, () => ({
      focusIndex: (index: number) => focusIndexMock(index),
    }))
    return (
      <div
        role="region"
        aria-label="Image carousel"
        data-testid="mock-carousel"
      >
        {(items ?? []).map((item, index) => (
          <span key={`${item.title ?? 'item'}-${index}`}>{item.title}</span>
        ))}
      </div>
    )
  })
  return { default: MockCarousel }
})

const { listOrganizations } = await import('../api/organizations')
const listMock = vi.mocked(listOrganizations)

function organization(overrides: Partial<Organization> = {}): Organization {
  return {
    id: '3f6c1f0e-8a2b-4c1d-9e7a-1b2c3d4e5f60',
    name: 'Дебатний клуб НаУКМА',
    slug: 'debate-club',
    autoPublish: false,
    description: 'Щотижневі тренування',
    createdAt: '2026-03-01T10:00:00+02:00',
    updatedAt: '2026-03-01T10:00:00+02:00',
    ...overrides,
  }
}

function page(
  data: Organization[],
  pagination: Partial<{
    offset: number
    limit: number
    total: number
    hasMore: boolean
  }> = {},
) {
  return {
    data,
    pagination: {
      offset: 0,
      limit: 100,
      total: data.length,
      hasMore: false,
      ...pagination,
    },
  }
}

function renderPage() {
  return render(
    <AuthProvider>
      <ThemeProvider>
        <MemoryRouter>
          <OrganizationsPage />
        </MemoryRouter>
      </ThemeProvider>
    </AuthProvider>,
  )
}

afterEach(() => {
  vi.resetAllMocks()
  focusIndexMock.mockReset()
})

describe('OrganizationsPage', () => {
  it('показує індикатор завантаження до відповіді бекенду', () => {
    listMock.mockReturnValue(new Promise(() => {}))

    renderPage()

    expect(screen.getByText('Завантаження…')).toBeInTheDocument()
  })

  it('запитує всі організації сторінками по 100', async () => {
    listMock.mockResolvedValue(page([]))

    renderPage()

    await waitFor(() =>
      expect(listMock).toHaveBeenCalledWith({ limit: 100, offset: 0 }),
    )
  })

  it('виводить назви організацій у каруселі й алфавітному списку', async () => {
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

    renderPage()

    const nav = await screen.findByRole('navigation', {
      name: 'Алфавітний список',
    })
    expect(
      within(nav).getByRole('link', { name: 'Дебатний клуб НаУКМА' }),
    ).toBeInTheDocument()
    expect(
      within(nav).getByRole('link', { name: 'Клуб програмування' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('region', { name: 'Image carousel' }),
    ).toBeInTheDocument()
  })

  it('сортує алфавітний список: англійські назви, потім українські', async () => {
    listMock.mockResolvedValue(
      page([
        organization({ id: '1', name: 'КУТ', slug: 'kut' }),
        organization({ id: '2', name: 'Radio KVIT', slug: 'radio-kvit' }),
        organization({ id: '3', name: 'Бадді НаУКМА', slug: 'buddy' }),
        organization({ id: '4', name: 'FIDo', slug: 'fido' }),
      ]),
    )

    renderPage()

    const nav = await screen.findByRole('navigation', {
      name: 'Алфавітний список',
    })
    const links = within(nav)
      .getAllByRole('link')
      .map((el) => el.textContent)

    expect(links).toEqual(['FIDo', 'Radio KVIT', 'Бадді НаУКМА', 'КУТ'])
  })

  it('наведення на назву в списку крутить карусель до індексу в API-порядку', async () => {
    const user = userEvent.setup()
    const orgs = [
      organization({ id: 'org-fido', name: 'FIDo', slug: 'fido' }),
      organization({ id: 'org-kut', name: 'КУТ', slug: 'kut' }),
      organization({ id: 'org-radio', name: 'Radio KVIT', slug: 'radio' }),
    ]
    listMock.mockResolvedValue(page(orgs))

    renderPage()

    const nav = await screen.findByRole('navigation', {
      name: 'Алфавітний список',
    })
    // Directory sorted EN→UK: FIDo, Radio KVIT, КУТ
    // Carousel keeps API order: FIDo=0, КУТ=1, Radio=2
    await user.hover(within(nav).getByRole('link', { name: 'КУТ' }))
    expect(focusIndexMock).toHaveBeenCalledWith(1)

    await user.hover(within(nav).getByRole('link', { name: 'Radio KVIT' }))
    expect(focusIndexMock).toHaveBeenCalledWith(2)
  })

  it('повідомляє про порожній каталог, коли організацій немає', async () => {
    listMock.mockResolvedValue(page([]))

    renderPage()

    expect(
      await screen.findByText('Поки немає організацій.'),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('region', { name: 'Image carousel' }),
    ).not.toBeInTheDocument()
  })

  it('показує статус відповіді, коли бекенд повертає помилку', async () => {
    listMock.mockRejectedValue(new ApiError(503, 'service unavailable'))

    renderPage()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Не вдалося завантажити (503)')
  })

  it('показує загальне повідомлення, коли помилка не від API', async () => {
    listMock.mockRejectedValue(new TypeError('network down'))

    renderPage()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Не вдалося завантажити організації')
    expect(alert).not.toHaveTextContent('network down')
  })

  it('не показує карусель і помилку одночасно', async () => {
    listMock.mockRejectedValue(new ApiError(500, 'boom'))

    renderPage()

    await screen.findByRole('alert')
    expect(
      screen.queryByRole('region', { name: 'Image carousel' }),
    ).not.toBeInTheDocument()
    expect(screen.queryByText('Завантаження…')).not.toBeInTheDocument()
  })
})
