import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import BoardLayout from './BoardLayout'

describe('BoardLayout', () => {
  it('renders board chrome and nav links', () => {
    render(
      <MemoryRouter initialEntries={['/board']}>
        <Routes>
          <Route path="/board" element={<BoardLayout />}>
            <Route index element={<p>outlet</p>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )

    expect(
      screen.getByRole('heading', { name: 'Дошка оголошень' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Відкриті' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Мої' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Мої відгуки' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Створити' })).toBeInTheDocument()
    expect(screen.getByText('outlet')).toBeInTheDocument()
  })
})
