import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../../auth/AuthContext'
import { clearSession, createSession, writeSession } from '../../auth/session'
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
  clearSession()
  vi.resetAllMocks()
})

describe('AdminHomePage', () => {
  it('показує email Global Admin з JWT-сесії', async () => {
    writeSession(createSession('a.butyrin@ukma.edu.ua', 'admin', 'jwt'))
    vi.mocked(listOrganizations).mockResolvedValue({
      data: [],
      pagination: { offset: 0, limit: 1, total: 2, hasMore: true },
    })
    vi.mocked(listEvents).mockResolvedValue({
      data: [],
      pagination: { offset: 0, limit: 1, total: 5, hasMore: true },
    })

    render(
      <AuthProvider>
        <MemoryRouter>
          <AdminHomePage />
        </MemoryRouter>
      </AuthProvider>,
    )

    expect(
      screen.getByText(/Увійди акаунтом Global Admin \(a\.butyrin@ukma\.edu\.ua\)/),
    ).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByText('2')).toBeInTheDocument()
      expect(screen.getByText('5')).toBeInTheDocument()
    })
  })
})
