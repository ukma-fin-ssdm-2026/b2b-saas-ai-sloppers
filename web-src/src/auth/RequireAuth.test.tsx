import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { AuthProvider } from './AuthContext'
import RequireAuth from './RequireAuth'
import { clearSession, createSession, writeSession } from './session'

afterEach(() => {
  cleanup()
  clearSession()
})

function LoginProbe() {
  const location = useLocation()
  const state = location.state as { from?: string } | null
  return <p>login from: {state?.from ?? 'немає'}</p>
}

function renderGuarded(entry = '/admin') {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route
            path="/admin"
            element={
              <RequireAuth>
                <p>захищений вміст</p>
              </RequireAuth>
            }
          />
          <Route path="/login" element={<LoginProbe />} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('RequireAuth', () => {
  it('без сесії перекидає на /login', () => {
    renderGuarded()

    expect(screen.queryByText('захищений вміст')).not.toBeInTheDocument()
    expect(screen.getByText(/^login from:/)).toBeInTheDocument()
  })

  it('передає шлях, з якого прийшли, щоб після входу повернути назад', () => {
    renderGuarded('/admin')

    expect(screen.getByText('login from: /admin')).toBeInTheDocument()
  })

  it('із сесією показує вміст', () => {
    writeSession(createSession('admin@ukma.edu.ua', 'admin', 'jwt'))

    renderGuarded()

    expect(screen.getByText('захищений вміст')).toBeInTheDocument()
    expect(screen.queryByText(/^login from:/)).not.toBeInTheDocument()
  })
})
