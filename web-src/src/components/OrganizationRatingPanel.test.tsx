import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { AuthProvider } from '../auth/AuthContext'
import { clearSession, createSession, writeSession } from '../auth/session'
import OrganizationRatingPanel from './OrganizationRatingPanel'

vi.mock('../api/ratings', () => ({
  getOrganizationRating: vi.fn(),
  rateOrganization: vi.fn(),
}))

const api = await import('../api/ratings')

function renderPanel(initial = { average: 4.5, count: 2 }) {
  return render(
    <AuthProvider>
      <MemoryRouter>
        <OrganizationRatingPanel
          organizationId="org1"
          initial={initial}
          loginHref="/login?from=%2Forganizations%2Forg1"
        />
      </MemoryRouter>
    </AuthProvider>,
  )
}

afterEach(() => {
  cleanup()
  clearSession()
  vi.resetAllMocks()
})

describe('OrganizationRatingPanel', () => {
  it('гість бачить середнє і посилання на вхід, без зірок', () => {
    renderPanel()

    expect(screen.getByText('★ 4.5 · 2 оцінки')).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: 'Увійдіть, щоб оцінити' }),
    ).toHaveAttribute('href', '/login?from=%2Forganizations%2Forg1')
    expect(screen.queryByRole('group', { name: 'Ваша оцінка' })).toBeNull()
    expect(api.getOrganizationRating).not.toHaveBeenCalled()
  })

  it('користувач бачить свою оцінку й може її змінити', async () => {
    writeSession(createSession('alice@ukma.edu.ua', 'user', 'jwt'))
    vi.mocked(api.getOrganizationRating).mockResolvedValue({
      organizationId: 'org1',
      rating: { average: 4.5, count: 2 },
      myScore: 4,
    })
    vi.mocked(api.rateOrganization).mockResolvedValue({
      organizationId: 'org1',
      rating: { average: 5, count: 2 },
      myScore: 5,
    })
    const user = userEvent.setup()
    renderPanel()

    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Оцінити на 4 з 5' }),
      ).toHaveAttribute('aria-pressed', 'true'),
    )
    await user.click(screen.getByRole('button', { name: 'Оцінити на 5 з 5' }))

    expect(api.rateOrganization).toHaveBeenCalledWith('org1', 5)
    expect(await screen.findByText('★ 5.0 · 2 оцінки')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Оцінити на 5 з 5' }),
    ).toHaveAttribute('aria-pressed', 'true')
  })

  it('голові показується відмова бекенду', async () => {
    writeSession(createSession('head@ukma.edu.ua', 'user', 'jwt'))
    vi.mocked(api.getOrganizationRating).mockResolvedValue({
      organizationId: 'org1',
      rating: { average: null, count: 0 },
      myScore: null,
    })
    vi.mocked(api.rateOrganization).mockRejectedValue(
      new ApiError(
        403,
        'forbidden',
        JSON.stringify({
          error: { message: 'Голова не може оцінювати власну організацію' },
        }),
      ),
    )
    const user = userEvent.setup()
    renderPanel({ average: null, count: 0 })

    expect(screen.getByText('Ще немає оцінок')).toBeInTheDocument()
    await user.click(
      await screen.findByRole('button', { name: 'Оцінити на 3 з 5' }),
    )

    expect(
      await screen.findByText('Голова не може оцінювати власну організацію'),
    ).toBeInTheDocument()
  })
})
