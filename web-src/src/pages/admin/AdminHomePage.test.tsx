import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdminHomePage from './AdminHomePage'

vi.mock('../../api/organizations', async () => {
  const actual = await vi.importActual<typeof import('../../api/organizations')>(
    '../../api/organizations',
  )
  return { ...actual, listOrganizations: vi.fn() }
})

vi.mock('../../api/events', async () => {
  const actual = await vi.importActual<typeof import('../../api/events')>(
    '../../api/events',
  )
  return { ...actual, listEvents: vi.fn() }
})

const { listOrganizations } = await import('../../api/organizations')
const { listEvents } = await import('../../api/events')

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('AdminHomePage', () => {
  it('показує лічильники і дії без інструкцій', async () => {
    vi.mocked(listOrganizations).mockResolvedValue({
      data: [],
      pagination: { offset: 0, limit: 1, total: 2, hasMore: true },
    })
    vi.mocked(listEvents).mockResolvedValue({
      data: [],
      pagination: { offset: 0, limit: 1, total: 5, hasMore: true },
    })

    render(
      <MemoryRouter>
        <AdminHomePage />
      </MemoryRouter>,
    )

    expect(screen.queryByText(/Швидкий стенд/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/Увійди акаунтом Global Admin/i)).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Організації' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Усі події' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Публічний сайт' })).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByText('2')).toBeInTheDocument()
      expect(screen.getByText('5')).toBeInTheDocument()
    })
  })
})
