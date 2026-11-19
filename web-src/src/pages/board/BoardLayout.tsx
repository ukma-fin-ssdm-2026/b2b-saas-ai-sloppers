import { NavLink, Outlet } from 'react-router-dom'
import '../OrganizationsPage.css'
import '../panel.css'

function BoardLayout() {
  return (
    <main className="panel-page">
      <div className="panel-page__inner panel-page__inner--wide">
        <p className="panel-page__eyebrow">Спільнота</p>
        <h1 className="panel-page__title">Дошка оголошень</h1>
        <p className="panel-page__lead">
          Шукайте команду на хакатон чи компанію для дозвілля. Контакт автора
          видно лише після підтвердження відгуку.
        </p>
        <nav className="panel-nav" aria-label="Дошка">
          <NavLink to="/board" end>
            Відкриті
          </NavLink>
          <NavLink to="/board/mine">Мої</NavLink>
          <NavLink to="/board/replied">Мої відгуки</NavLink>
          <NavLink to="/board/new">Створити</NavLink>
        </nav>
        <Outlet />
      </div>
    </main>
  )
}

export default BoardLayout
