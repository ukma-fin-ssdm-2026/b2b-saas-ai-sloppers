import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../../auth/AuthContext'
import { createSession, writeSession, clearSession } from '../../auth/session'
import AdminLayout from './AdminLayout'

vi.mock('../../auth/confirmLogout', () => ({
  confirmLogout: vi.fn(() => true),
}))

const { confirmLogout } = await import('../../auth/confirmLogout')
const confirmMock = vi.mocked(confirmLogout)

afterEach(() => {
  cleanup()
  clearSession()
  vi.clearAllMocks()
})

function renderAdmin() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route path="/admin" element={<AdminLayout />}>
            <Route index element={<p>child</p>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('AdminLayout', () => {
  it('показує email з сесії без демо-мітки', () => {
    writeSession(createSession('a.butyrin@ukma.edu.ua', 'admin', 'jwt'))
    renderAdmin()

    expect(
      screen.getByText('Адмін · a.butyrin@ukma.edu.ua'),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/Створюй організації й події з фото/),
    ).toBeInTheDocument()
    expect(screen.queryByText(/демо/i)).not.toBeInTheDocument()
  })

  it('Вийти викликає confirm і чистить сесію', async () => {
    writeSession(createSession('a.butyrin@ukma.edu.ua', 'admin', 'jwt'))
    const user = userEvent.setup()
    renderAdmin()

    await user.click(screen.getByRole('button', { name: 'Вийти' }))
    expect(confirmMock).toHaveBeenCalled()
  })
})
