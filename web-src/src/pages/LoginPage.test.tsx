import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import { clearSession } from '../auth/session'
import LoginPage from './LoginPage'

afterEach(() => {
  cleanup()
  clearSession()
  vi.restoreAllMocks()
})

describe('LoginPage', () => {
  it('просить увійти через @ukma.edu.ua без згадки демо-логіну', () => {
    render(
      <AuthProvider>
        <MemoryRouter>
          <LoginPage />
        </MemoryRouter>
      </AuthProvider>,
    )

    expect(
      screen.getByText(/Увійдіть акаунтом @ukma.edu.ua/),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.queryByText(/Демо-логін/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/bot/i)).not.toBeInTheDocument()
  })
})
