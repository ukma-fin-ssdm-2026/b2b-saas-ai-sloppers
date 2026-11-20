import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import { writeSession } from '../auth/session'
import AppHeader from './AppHeader'

vi.mock('../auth/confirmLogout', () => ({
  confirmLogout: vi.fn(() => true),
}))

vi.mock('./ThemeSwitcher', () => ({
  default: () => <div data-testid="theme-switcher" />,
}))

afterEach(() => {
  cleanup()
  sessionStorage.clear()
  vi.resetAllMocks()
})

describe('AppHeader', () => {
  it('shows login for guests', () => {
    render(
      <AuthProvider>
        <MemoryRouter>
          <AppHeader />
        </MemoryRouter>
      </AuthProvider>,
    )
    expect(screen.getByRole('link', { name: 'Вхід' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Дошка' })).not.toBeInTheDocument()
  })

  it('shows board/profile and logout when authenticated', async () => {
    const user = userEvent.setup()
    writeSession({
      username: 'a@ukma.edu.ua',
      role: 'user',
      token: 'jwt',
      kind: 'api',
    })

    render(
      <AuthProvider>
        <MemoryRouter>
          <AppHeader />
        </MemoryRouter>
      </AuthProvider>,
    )

    expect(screen.getByRole('link', { name: 'Дошка' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Профіль' })).toBeInTheDocument()
    expect(screen.getByTestId('theme-switcher')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Вийти' }))
    expect(screen.getByRole('link', { name: 'Вхід' })).toBeInTheDocument()
  })
})
