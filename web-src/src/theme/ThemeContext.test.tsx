import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import { writeSession } from '../auth/session'
import { ThemeProvider, useTheme } from './ThemeContext'
import { readSavedTheme } from './themes'

function Probe() {
  const { theme, setTheme } = useTheme()
  return (
    <div>
      <span data-testid="theme">{theme}</span>
      <button type="button" onClick={() => setTheme('grainient')}>
        grainient
      </button>
      <button type="button" onClick={() => setTheme('white')}>
        white
      </button>
    </div>
  )
}

afterEach(() => {
  cleanup()
  sessionStorage.clear()
  localStorage.clear()
  document.documentElement.removeAttribute('data-theme')
})

describe('ThemeContext', () => {
  it('persists theme for logged-in user and restores it', async () => {
    const user = userEvent.setup()
    writeSession({
      username: 'student@ukma.edu.ua',
      role: 'user',
      token: 'jwt',
      kind: 'api',
    })

    render(
      <AuthProvider>
        <ThemeProvider>
          <Probe />
        </ThemeProvider>
      </AuthProvider>,
    )

    expect(screen.getByTestId('theme')).toHaveTextContent('black')
    await user.click(screen.getByRole('button', { name: 'grainient' }))
    await waitFor(() => {
      expect(screen.getByTestId('theme')).toHaveTextContent('grainient')
      expect(document.documentElement.dataset.theme).toBe('grainient')
      expect(readSavedTheme('student@ukma.edu.ua')).toBe('grainient')
    })
  })

  it('throws when useTheme is outside provider', () => {
    expect(() => render(<Probe />)).toThrow(
      'useTheme must be used within ThemeProvider',
    )
  })
})
