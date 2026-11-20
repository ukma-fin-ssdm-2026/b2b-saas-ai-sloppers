import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import { ThemeProvider, useTheme } from '../theme/ThemeContext'
import { writeSession } from '../auth/session'
import ThemeBackground from './ThemeBackground'

vi.mock('./Grainient', () => ({
  default: () => <div data-testid="grainient-mock" />,
}))

afterEach(() => {
  cleanup()
  sessionStorage.clear()
  localStorage.clear()
  document.documentElement.removeAttribute('data-theme')
})

function Probe() {
  const { setTheme } = useTheme()
  return (
    <button type="button" onClick={() => setTheme('grainient-mono')}>
      mono
    </button>
  )
}

describe('ThemeBackground', () => {
  it('renders Grainient only for grainient themes', async () => {
    const user = userEvent.setup()
    writeSession({
      username: 'a@ukma.edu.ua',
      role: 'user',
      token: 'jwt',
      kind: 'api',
    })

    render(
      <AuthProvider>
        <ThemeProvider>
          <ThemeBackground />
          <Probe />
        </ThemeProvider>
      </AuthProvider>,
    )

    expect(screen.queryByTestId('grainient-mock')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'mono' }))
    expect(await screen.findByTestId('grainient-mock')).toBeInTheDocument()
  })
})
