import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import { ThemeProvider } from '../theme/ThemeContext'
import { writeSession } from '../auth/session'
import ThemeSwitcher from './ThemeSwitcher'

afterEach(() => {
  cleanup()
  sessionStorage.clear()
  localStorage.clear()
  document.documentElement.removeAttribute('data-theme')
})

function renderSwitcher() {
  writeSession({
    username: 'a@ukma.edu.ua',
    role: 'user',
    token: 'jwt',
    kind: 'api',
  })
  return render(
    <AuthProvider>
      <ThemeProvider>
        <ThemeSwitcher />
      </ThemeProvider>
    </AuthProvider>,
  )
}

describe('ThemeSwitcher', () => {
  it('renders all theme swatches and switches theme', async () => {
    const user = userEvent.setup()
    renderSwitcher()

    expect(screen.getByRole('group', { name: 'Тема оформлення' })).toBeInTheDocument()
    const white = screen.getByRole('button', { name: 'Біла' })
    await user.click(white)
    expect(white).toHaveAttribute('aria-pressed', 'true')
    expect(document.documentElement.dataset.theme).toBe('white')
  })
})
