import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import AdminLayout from './AdminLayout'

afterEach(() => {
  cleanup()
})

describe('AdminLayout', () => {
  it('показує чисту шапку без email, банера і Вийти', () => {
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route path="/admin" element={<AdminLayout />}>
            <Route index element={<p>child</p>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('Адмін')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Панель керування' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Огляд' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Організації' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Усі події' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Сайт →' })).toBeInTheDocument()
    expect(screen.queryByText(/@ukma\.edu\.ua/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/Створюй організації/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Вийти' })).not.toBeInTheDocument()
  })
})
