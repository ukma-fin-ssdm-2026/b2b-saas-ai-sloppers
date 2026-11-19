import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import { writeSession } from '../auth/session'
import ProfilePage from './ProfilePage'

vi.mock('../api/auth', async () => {
  const actual = await vi.importActual<typeof import('../api/auth')>(
    '../api/auth',
  )
  return {
    ...actual,
    meApi: vi.fn(),
    updateProfileApi: vi.fn(),
  }
})

const { meApi, updateProfileApi } = await import('../api/auth')

function renderAuthed() {
  writeSession({
    username: 'a.butyrin@ukma.edu.ua',
    role: 'user',
    token: 'jwt',
    kind: 'api',
  })
  return render(
    <AuthProvider>
      <MemoryRouter>
        <ProfilePage />
      </MemoryRouter>
    </AuthProvider>,
  )
}

afterEach(() => {
  cleanup()
  sessionStorage.clear()
  vi.resetAllMocks()
})

describe('ProfilePage', () => {
  it('loads profile and saves names', async () => {
    const user = userEvent.setup()
    vi.mocked(meApi).mockResolvedValue({
      id: 'u1',
      email: 'a.butyrin@ukma.edu.ua',
      firstName: 'Artem',
      lastName: 'B',
      status: 'ACTIVE',
      globalAdmin: false,
      headOf: [],
    })
    vi.mocked(updateProfileApi).mockResolvedValue({
      id: 'u1',
      email: 'a.butyrin@ukma.edu.ua',
      firstName: 'Артем',
      lastName: 'Бутирін',
      status: 'ACTIVE',
      globalAdmin: false,
      headOf: [],
    })

    renderAuthed()

    expect(await screen.findByDisplayValue('Artem')).toBeInTheDocument()
    await user.clear(screen.getByLabelText('Ім’я'))
    await user.type(screen.getByLabelText('Ім’я'), 'Артем')
    await user.clear(screen.getByLabelText('Прізвище'))
    await user.type(screen.getByLabelText('Прізвище'), 'Бутирін')
    await user.click(screen.getByRole('button', { name: 'Зберегти' }))

    await waitFor(() => {
      expect(updateProfileApi).toHaveBeenCalledWith('Артем', 'Бутирін')
      expect(screen.getByText('Профіль збережено')).toBeInTheDocument()
    })
  })

  it('redirects guests to login', () => {
    render(
      <AuthProvider>
        <MemoryRouter>
          <ProfilePage />
        </MemoryRouter>
      </AuthProvider>,
    )
    expect(screen.queryByRole('heading', { name: 'Профіль' })).not.toBeInTheDocument()
  })
})
